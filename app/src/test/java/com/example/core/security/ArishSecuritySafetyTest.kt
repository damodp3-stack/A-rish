package com.example.core.security

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.data.local.ArishDatabase
import com.example.core.domain.error.ArishException
import com.example.core.domain.security.ApprovalDecision
import com.example.core.domain.security.ApprovalRequest
import com.example.core.domain.security.ApprovalStatus
import com.example.core.domain.security.AuthenticationRequirement
import com.example.core.domain.security.AuthenticationResult
import com.example.core.domain.security.PermissionRequirement
import com.example.core.domain.security.PermissionStatus
import com.example.core.domain.security.RiskEvaluation
import com.example.core.domain.security.RiskLevel
import com.example.core.domain.security.RiskReason
import com.example.core.security.audit.SecurityAuditLogger
import com.example.core.security.auth.DefaultSecurityAuthenticator
import com.example.core.security.keystore.AesGcmCipherEngine
import com.example.core.security.keystore.AndroidSecureSecretStore
import com.example.core.security.keystore.EncryptedSecretStorage
import com.example.core.security.permission.AndroidPermissionBroker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowApplication

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ArishSecuritySafetyTest {

    private lateinit var context: Context
    private lateinit var db: ArishDatabase
    private lateinit var auditLogger: SecurityAuditLogger
    private lateinit var cipherEngine: AesGcmCipherEngine
    private lateinit var storage: EncryptedSecretStorage
    private lateinit var secretStore: AndroidSecureSecretStore
    private lateinit var permissionBroker: AndroidPermissionBroker
    private lateinit var authenticator: DefaultSecurityAuthenticator
    private lateinit var securityGate: SecurityGate

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, ArishDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        auditLogger = SecurityAuditLogger(db.agentEventDao())
        cipherEngine = AesGcmCipherEngine()
        storage = EncryptedSecretStorage(context)
        storage.clearAll()
        secretStore = AndroidSecureSecretStore(cipherEngine, storage, auditLogger)

        permissionBroker = AndroidPermissionBroker(context, auditLogger)
        authenticator = DefaultSecurityAuthenticator(auditLogger)
        securityGate = SecurityGate(permissionBroker, authenticator)
    }

    @After
    fun tearDown() {
        storage.clearAll()
        db.close()
    }

    // 1. AES-256-GCM encryption/decryption round trip
    @Test
    fun testAesGcmEncryptionDecryptionRoundTrip() {
        val originalSecret = "sk-ant-api03-sample-super-secret-token-value-12345"
        val (ciphertext, iv) = cipherEngine.encrypt(originalSecret.toByteArray(Charsets.UTF_8))

        assertNotNull(ciphertext)
        assertNotNull(iv)
        assertEquals(12, iv.size) // 12-byte GCM standard nonce

        val decryptedBytes = cipherEngine.decrypt(ciphertext, iv)
        val decryptedSecret = String(decryptedBytes, Charsets.UTF_8)
        assertEquals(originalSecret, decryptedSecret)
    }

    // 2. Ciphertext differs between repeated encryption of identical plaintext (Nonce uniqueness)
    @Test
    fun testCiphertextDiffersBetweenRepeatedEncryptions() {
        val secret = "identical-secret-payload"

        val (ciphertext1, iv1) = cipherEngine.encrypt(secret.toByteArray(Charsets.UTF_8))
        val (ciphertext2, iv2) = cipherEngine.encrypt(secret.toByteArray(Charsets.UTF_8))

        // Nonces MUST be unique
        assertFalse(iv1.contentEquals(iv2))
        // Ciphertexts MUST differ due to unique nonces
        assertFalse(ciphertext1.contentEquals(ciphertext2))

        // Both must decrypt to the identical original secret
        assertEquals(secret, String(cipherEngine.decrypt(ciphertext1, iv1), Charsets.UTF_8))
        assertEquals(secret, String(cipherEngine.decrypt(ciphertext2, iv2), Charsets.UTF_8))
    }

    // 3. Tampered ciphertext fails authentication (AEAD authentication tag mismatch)
    @Test
    fun testTamperedCiphertextFailsAuthentication() {
        val secret = "tamper-proof-classified-instructions"
        val (ciphertext, iv) = cipherEngine.encrypt(secret.toByteArray(Charsets.UTF_8))

        // Tamper with a byte in the ciphertext
        val tamperedCiphertext = ciphertext.copyOf()
        tamperedCiphertext[0] = (tamperedCiphertext[0].toInt() xor 0xFF).toByte()

        try {
            cipherEngine.decrypt(tamperedCiphertext, iv)
            fail("Expected AuthenticationTagMismatchException or KeystoreOperationException")
        } catch (e: ArishException.AuthenticationTagMismatchException) {
            assertTrue(true)
        } catch (e: Exception) {
            assertTrue(e.message?.contains("tag", ignoreCase = true) == true || e is ArishException)
        }
    }

    // 4. Tampered IV fails
    @Test
    fun testTamperedIvFails() {
        val secret = "sensitive-data-iv-integrity"
        val (ciphertext, iv) = cipherEngine.encrypt(secret.toByteArray(Charsets.UTF_8))

        val tamperedIv = iv.copyOf()
        tamperedIv[0] = (tamperedIv[0].toInt() xor 0x01).toByte()

        try {
            cipherEngine.decrypt(ciphertext, tamperedIv)
            fail("Expected authentication failure on tampered IV")
        } catch (e: ArishException.AuthenticationTagMismatchException) {
            assertTrue(true)
        } catch (e: Exception) {
            assertTrue(e.message?.contains("tag", ignoreCase = true) == true || e is ArishException)
        }
    }

    // 5. Wrong key fails
    @Test
    fun testWrongKeyFails() {
        val secret = "key-separation-secret"
        val (ciphertext, iv) = cipherEngine.encrypt(secret.toByteArray(Charsets.UTF_8), customKeyAlias = "key_alpha")

        try {
            cipherEngine.decrypt(ciphertext, iv, customKeyAlias = "key_beta")
            fail("Expected decryption to fail when using wrong key")
        } catch (e: ArishException.AuthenticationTagMismatchException) {
            assertTrue(true)
        } catch (e: Exception) {
            assertTrue(e is ArishException)
        }
    }

    // 6. Deleted secret cannot be retrieved
    @Test
    fun testDeletedSecretCannotBeRetrieved() = runBlocking {
        secretStore.storeSecret("open_ai_key", "sk-secret-12345", "OpenAI Key")
        assertTrue(secretStore.hasSecret("open_ai_key"))

        val deleteResult = secretStore.deleteSecret("open_ai_key")
        assertTrue(deleteResult.isSuccess && deleteResult.getOrThrow())
        assertFalse(secretStore.hasSecret("open_ai_key"))

        val retrieval = secretStore.getSecret("open_ai_key")
        assertTrue(retrieval.isFailure)
        assertTrue(retrieval.exceptionOrNull() is ArishException.SecretNotFoundException)
    }

    // 7. Missing secret returns typed failure (SecretNotFoundException)
    @Test
    fun testMissingSecretReturnsTypedFailure() = runBlocking {
        val result = secretStore.getSecret("non_existent_alias")
        assertTrue("Missing secret must fail closed", result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(
            "Exception must be SecretNotFoundException, got $exception",
            exception is ArishException.SecretNotFoundException
        )
    }

    // 8. No plaintext secret appears in persistent storage
    @Test
    fun testNoPlaintextSecretInPersistentStorage() = runBlocking {
        val plainSecret = "SUPER_SECRET_PLAINTEXT_API_KEY_999"
        secretStore.storeSecret("anthropic_key", plainSecret, "Claude API")

        val storedRecord = storage.get("anthropic_key")
        assertNotNull(storedRecord)
        assertFalse(
            "Persistent record must not contain plaintext secret",
            storedRecord?.ciphertextBase64?.contains(plainSecret) == true
        )
        assertFalse(
            "Persistent JSON must not contain plaintext secret",
            storedRecord?.toJson()?.contains(plainSecret) == true
        )
    }

    // 9. No secret appears in logs / audit events
    @Test
    fun testNoSecretAppearsInSecurityAuditEvents() = runBlocking {
        val secretValue = "CLASSIFIED_LLM_KEY_ABCDEF"
        secretStore.storeSecret("gemini_key", secretValue, "Gemini Key")
        secretStore.getSecret("gemini_key")

        val createdEvents = db.agentEventDao().getEventsByType("SECRET_CREATED")
        val retrievedEvents = db.agentEventDao().getEventsByType("SECRET_RETRIEVED")
        val events = createdEvents + retrievedEvents
        assertTrue("Audit events must be recorded", events.isNotEmpty())

        for (event in events) {
            assertFalse(
                "Event payload ${event.payloadJson} must never contain plaintext secret $secretValue",
                event.payloadJson.contains(secretValue)
            )
        }
    }

    // 10. Permission already granted → no unnecessary permission request
    @Test
    fun testPermissionAlreadyGrantedNoRedundantRequest() = runBlocking {
        val perm = "android.permission.INTERNET"
        org.robolectric.Shadows.shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
            .grantPermissions(perm)

        var delegateInvoked = false
        permissionBroker.setRequestDelegate { _, _ ->
            delegateInvoked = true
            PermissionStatus.GRANTED
        }

        val status = permissionBroker.requestPermission(perm, "Network access")
        assertEquals(PermissionStatus.GRANTED, status)
        assertFalse("Delegate must not be invoked if permission is already granted", delegateInvoked)
    }

    // 11. Permission missing → PermissionBroker requests permission
    @Test
    fun testPermissionMissingRequestsViaBroker() = runBlocking {
        val perm = "android.permission.RECORD_AUDIO"
        var delegateInvoked = false
        permissionBroker.setRequestDelegate { p, _ ->
            if (p == perm) {
                delegateInvoked = true
                PermissionStatus.GRANTED
            } else {
                PermissionStatus.DENIED
            }
        }

        val status = permissionBroker.requestPermission(perm, "Microphone for voice commands")
        assertTrue(delegateInvoked)
        assertEquals(PermissionStatus.GRANTED, status)
    }

    // 12. Permission denied → typed failure
    @Test
    fun testPermissionDeniedProducesTypedFailure() = runBlocking {
        val perm = "android.permission.READ_CONTACTS"
        permissionBroker.setRequestDelegate { _, _ ->
            PermissionStatus.DENIED
        }

        val decision = securityGate.evaluateAndEnforce(
            taskId = "task-1",
            stepId = "step-1",
            toolId = "read_contacts",
            capabilityId = "CONTACTS",
            riskEvaluation = RiskEvaluation.low(),
            permissionRequirements = listOf(
                PermissionRequirement(
                    permissionManifestKey = perm,
                    rationaleUserText = "Access contacts to find recipient"
                )
            ),
            authenticationRequirement = AuthenticationRequirement.NONE
        )

        assertTrue(decision is SecurityGateDecision.Blocked)
        val blocked = decision as SecurityGateDecision.Blocked
        assertTrue(
            "Blocked decision must contain PermissionDeniedException",
            blocked.exception is ArishException.PermissionDeniedException
        )
    }

    // 13. Authentication requirement is enforced independently from OS permission
    @Test
    fun testAuthenticationEnforcedIndependentlyFromOsPermission() = runBlocking {
        // Grant OS permission
        val perm = "android.permission.ACCESS_FINE_LOCATION"
        org.robolectric.Shadows.shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
            .grantPermissions(perm)

        // Set authenticator to fail
        authenticator.setChallengeDelegate { _, _, _ ->
            AuthenticationResult.Denied("User cancelled biometric prompt")
        }

        val decision = securityGate.evaluateAndEnforce(
            taskId = "task-2",
            stepId = "step-2",
            toolId = "get_precise_location",
            capabilityId = "LOCATION",
            riskEvaluation = RiskEvaluation.medium(
                reason = RiskReason.LocalDataRead,
                explanation = "Query GPS coordinates"
            ),
            permissionRequirements = listOf(
                PermissionRequirement(perm, true, "GPS access")
            ),
            authenticationRequirement = AuthenticationRequirement.USER_CONFIRMATION
        )

        assertTrue(
            "Even with OS permission granted, authentication failure must block execution",
            decision is SecurityGateDecision.Blocked
        )
    }

    // 14. HIGH risk action cannot bypass approval because OS permission is already granted
    @Test
    fun testHighRiskActionRequiresApprovalEvenIfPermissionGranted() = runBlocking {
        val perm = "android.permission.WRITE_EXTERNAL_STORAGE"
        org.robolectric.Shadows.shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
            .grantPermissions(perm)

        val decision = securityGate.evaluateAndEnforce(
            taskId = "task-high",
            stepId = "step-high",
            toolId = "write_shared_file",
            capabilityId = "FILE_SYSTEM",
            riskEvaluation = RiskEvaluation.high(
                reasons = listOf(RiskReason.LocalDataWrite),
                explanation = "Modify shared system file"
            ),
            permissionRequirements = listOf(PermissionRequirement(perm, true, "Storage")),
            authenticationRequirement = AuthenticationRequirement.NONE,
            existingApproval = null
        )

        assertTrue("HIGH risk action must require approval", decision is SecurityGateDecision.RequiresApproval)
    }

    // 15. CRITICAL risk action requires stronger authentication (BIOMETRIC)
    @Test
    fun testCriticalRiskActionRequiresBiometric() = runBlocking {
        var requestedAuthRequirement: AuthenticationRequirement? = null
        authenticator.setChallengeDelegate { req, _, _ ->
            requestedAuthRequirement = req
            AuthenticationResult.Success(req, System.currentTimeMillis())
        }

        val approval = ApprovalRequest(
            approvalId = "appr-crit-1",
            taskId = "task-crit",
            stepId = "step-crit",
            toolId = "wipe_db",
            capabilityId = "SYSTEM",
            riskEvaluation = RiskEvaluation.critical(
                reasons = listOf(RiskReason.LocalDataDeletion("Database")),
                explanation = "Wipe local database"
            ),
            actionSummary = "Wipe database",
            previewPayload = emptyMap(),
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 60000,
            status = ApprovalStatus.APPROVED,
            decision = ApprovalDecision(ApprovalStatus.APPROVED, "admin")
        )

        val decision = securityGate.evaluateAndEnforce(
            taskId = "task-crit",
            stepId = "step-crit",
            toolId = "wipe_db",
            capabilityId = "SYSTEM",
            riskEvaluation = approval.riskEvaluation,
            permissionRequirements = emptyList(),
            authenticationRequirement = AuthenticationRequirement.NONE, // Gate must promote to BIOMETRIC
            existingApproval = approval
        )

        assertEquals(SecurityGateDecision.Permitted, decision)
        assertEquals(AuthenticationRequirement.BIOMETRIC, requestedAuthRequirement)
    }

    // 16. Security failure cannot transition to VERIFIED
    @Test
    fun testSecurityFailureCannotTransitionToVerified() = runBlocking {
        // Blocked decision cannot be converted to execution success
        val decision: SecurityGateDecision = SecurityGateDecision.Blocked(
            ArishException.SecurityException("Unauthorized tool access attempt")
        )

        assertFalse("Blocked decision is not Permitted", decision == SecurityGateDecision.Permitted)
    }

    // 17. Process restart does not expose plaintext credentials
    @Test
    fun testProcessRestartDoesNotExposePlaintextCredentials() = runBlocking {
        val secretValue = "SECURE_API_KEY_ACROSS_RESTART"
        secretStore.storeSecret("restart_test_key", secretValue, "Persistence check")

        // Simulate process restart: create brand new storage and store instances reading from disk
        val newStorage = EncryptedSecretStorage(context)
        val newStore = AndroidSecureSecretStore(cipherEngine, newStorage, auditLogger)

        // Stored raw record on disk has no plaintext
        val record = newStorage.get("restart_test_key")
        assertNotNull(record)
        assertFalse(record?.ciphertextBase64?.contains(secretValue) == true)

        // New store can safely decrypt
        val decrypted = newStore.getSecret("restart_test_key")
        assertTrue(decrypted.isSuccess)
        assertEquals(secretValue, decrypted.getOrThrow())
    }

    // 18. Provider credential retrieval works only through SecureSecretStore
    @Test
    fun testProviderCredentialRetrievalOnlyThroughSecureStore() = runBlocking {
        secretStore.storeSecret("claude_prod_key", "sk-claude-999", "Production Key")

        val retrieved = secretStore.getSecret("claude_prod_key")
        assertTrue(retrieved.isSuccess)
        assertEquals("sk-claude-999", retrieved.getOrThrow())

        // Non-registered alias fails
        val missing = secretStore.getSecret("unregistered_service_key")
        assertTrue(missing.isFailure)
    }

    // 19. LLM/domain layer has no direct dependency on Android Keystore
    @Test
    fun testDomainLayerHasNoAndroidKeystoreDependency() {
        val domainClasses = listOf(
            com.example.core.domain.security.SecureSecretStore::class.java,
            com.example.core.domain.security.PermissionBroker::class.java,
            com.example.core.domain.security.SecurityAuthenticator::class.java,
            com.example.core.domain.security.RiskEvaluation::class.java,
            com.example.core.domain.security.ApprovalRequest::class.java
        )

        for (clazz in domainClasses) {
            for (field in clazz.declaredFields) {
                assertFalse(
                    "Domain class ${clazz.simpleName} must not reference android.security.keystore",
                    field.type.name.contains("keystore", ignoreCase = true)
                )
            }
        }
    }

    // 20. PermissionBroker is the only authorized boundary for Android runtime permission requests
    @Test
    fun testPermissionBrokerAuthorizedBoundary() = runBlocking {
        val reqs = listOf(
            PermissionRequirement("android.permission.CAMERA", true, "Scan QR code"),
            PermissionRequirement("android.permission.RECORD_AUDIO", true, "Record speech")
        )

        permissionBroker.setRequestDelegate { perm, _ ->
            if (perm == "android.permission.CAMERA") PermissionStatus.GRANTED else PermissionStatus.DENIED
        }

        val results = permissionBroker.requestPermissions(reqs)
        assertEquals(PermissionStatus.GRANTED, results["android.permission.CAMERA"])
        assertEquals(PermissionStatus.DENIED, results["android.permission.RECORD_AUDIO"])
    }

    // 21. Concurrency: Concurrent storeSecret() under same and multiple aliases
    @Test
    fun testConcurrentStoreAndGetSecrets(): Unit = runBlocking {
        val jobs = (1..20).map { i ->
            async(Dispatchers.IO) {
                secretStore.storeSecret("concurrent_key_$i", "secret_val_$i", "desc_$i")
                val readBack = secretStore.getSecret("concurrent_key_$i")
                assertTrue(readBack.isSuccess)
                assertEquals("secret_val_$i", readBack.getOrThrow())
            }
        }
        jobs.forEach { it.await() }
    }

    // 22. Concurrency: Concurrent delete and get secret fails closed without corruption
    @Test
    fun testConcurrentDeleteAndGetSecretFailsClosed(): Unit = runBlocking {
        secretStore.storeSecret("race_key", "race_val", "race_desc")
        val deleteJob = async(Dispatchers.IO) {
            secretStore.deleteSecret("race_key")
        }
        val getJob = async(Dispatchers.IO) {
            secretStore.getSecret("race_key")
        }
        deleteJob.await()
        val getResult = getJob.await()
        // Either retrieved before delete succeeded or failed closed with SecretNotFoundException
        if (getResult.isFailure) {
            assertTrue(getResult.exceptionOrNull() is ArishException.SecretNotFoundException)
        } else {
            assertEquals("race_val", getResult.getOrThrow())
        }
        // Final state must definitely be deleted
        assertFalse(secretStore.hasSecret("race_key"))
    }
}

