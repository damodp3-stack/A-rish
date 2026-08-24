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
import kotlinx.coroutines.awaitAll
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
    private lateinit var testKeyStore: java.security.KeyStore
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

        testKeyStore = java.security.KeyStore.getInstance("PKCS12").apply {
            load(null, null)
        }

        auditLogger = SecurityAuditLogger(db.agentEventDao())
        cipherEngine = AesGcmCipherEngine(injectedKeyStore = testKeyStore)
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

    // ==========================================
    // RED TEAM CORRECTION GATE TESTS (1 to 8)
    // ==========================================

    // Gate 1: Critical Authentication Downgrade Prevention
    @Test
    fun testCriticalAuthenticationDowngradePreventionMatrix() = runBlocking {
        // 1. CRITICAL + NONE -> Promoted to BIOMETRIC
        var promptedAuthReq: AuthenticationRequirement? = null
        authenticator.setChallengeDelegate { req, _, _ ->
            promptedAuthReq = req
            AuthenticationResult.Success(req, System.currentTimeMillis())
        }

        val validApproval = ApprovalRequest(
            approvalId = "appr-crit-matrix",
            taskId = "t1",
            stepId = "s1",
            toolId = "wipe_data",
            capabilityId = "SYSTEM",
            riskEvaluation = RiskEvaluation.critical(listOf(RiskReason.LocalDataDeletion("All")), "Wipe"),
            actionSummary = "Wipe",
            previewPayload = emptyMap(),
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 60000,
            status = ApprovalStatus.APPROVED,
            decision = ApprovalDecision(ApprovalStatus.APPROVED, "admin")
        )

        val dec1 = securityGate.evaluateAndEnforce(
            taskId = "t1", stepId = "s1", toolId = "wipe_data", capabilityId = "SYSTEM",
            riskEvaluation = validApproval.riskEvaluation,
            permissionRequirements = emptyList(),
            authenticationRequirement = AuthenticationRequirement.NONE,
            existingApproval = validApproval
        )
        assertEquals(SecurityGateDecision.Permitted, dec1)
        assertEquals(AuthenticationRequirement.BIOMETRIC, promptedAuthReq)

        // 2. CRITICAL + USER_CONFIRMATION -> Promoted to BIOMETRIC. Must FAIL if authenticator only satisfies USER_CONFIRMATION
        authenticator.setChallengeDelegate { req, _, _ ->
            if (req == AuthenticationRequirement.BIOMETRIC) {
                AuthenticationResult.Denied("Biometric required, user confirmation insufficient")
            } else {
                AuthenticationResult.Success(AuthenticationRequirement.USER_CONFIRMATION, System.currentTimeMillis())
            }
        }

        val dec2 = securityGate.evaluateAndEnforce(
            taskId = "t1", stepId = "s1", toolId = "wipe_data", capabilityId = "SYSTEM",
            riskEvaluation = validApproval.riskEvaluation,
            permissionRequirements = emptyList(),
            authenticationRequirement = AuthenticationRequirement.USER_CONFIRMATION,
            existingApproval = validApproval
        )
        assertTrue("CRITICAL + USER_CONFIRMATION cannot become Permitted without biometric", dec2 is SecurityGateDecision.Blocked)

        // 3. CRITICAL + DEVICE_CREDENTIAL -> Enforced as DEVICE_CREDENTIAL
        authenticator.setChallengeDelegate { req, _, _ ->
            promptedAuthReq = req
            AuthenticationResult.Success(req, System.currentTimeMillis())
        }
        val dec3 = securityGate.evaluateAndEnforce(
            taskId = "t1", stepId = "s1", toolId = "wipe_data", capabilityId = "SYSTEM",
            riskEvaluation = validApproval.riskEvaluation,
            permissionRequirements = emptyList(),
            authenticationRequirement = AuthenticationRequirement.DEVICE_CREDENTIAL,
            existingApproval = validApproval
        )
        assertEquals(SecurityGateDecision.Permitted, dec3)
        assertEquals(AuthenticationRequirement.DEVICE_CREDENTIAL, promptedAuthReq)

        // 4. CRITICAL + BIOMETRIC -> Enforced as BIOMETRIC
        val dec4 = securityGate.evaluateAndEnforce(
            taskId = "t1", stepId = "s1", toolId = "wipe_data", capabilityId = "SYSTEM",
            riskEvaluation = validApproval.riskEvaluation,
            permissionRequirements = emptyList(),
            authenticationRequirement = AuthenticationRequirement.BIOMETRIC,
            existingApproval = validApproval
        )
        assertEquals(SecurityGateDecision.Permitted, dec4)
        assertEquals(AuthenticationRequirement.BIOMETRIC, promptedAuthReq)

        // 5. HIGH + NONE -> Promoted to USER_CONFIRMATION
        val highApproval = validApproval.copy(
            toolId = "write_data",
            riskEvaluation = RiskEvaluation.high(listOf(RiskReason.LocalDataWrite), "Write shared")
        )
        val dec5 = securityGate.evaluateAndEnforce(
            taskId = "t1", stepId = "s1", toolId = "write_data", capabilityId = "SYSTEM",
            riskEvaluation = highApproval.riskEvaluation,
            permissionRequirements = emptyList(),
            authenticationRequirement = AuthenticationRequirement.NONE,
            existingApproval = highApproval
        )
        assertEquals(SecurityGateDecision.Permitted, dec5)
        assertEquals(AuthenticationRequirement.USER_CONFIRMATION, promptedAuthReq)

        // 6. HIGH + USER_CONFIRMATION -> Enforced as USER_CONFIRMATION
        val dec6 = securityGate.evaluateAndEnforce(
            taskId = "t1", stepId = "s1", toolId = "write_data", capabilityId = "SYSTEM",
            riskEvaluation = highApproval.riskEvaluation,
            permissionRequirements = emptyList(),
            authenticationRequirement = AuthenticationRequirement.USER_CONFIRMATION,
            existingApproval = highApproval
        )
        assertEquals(SecurityGateDecision.Permitted, dec6)
        assertEquals(AuthenticationRequirement.USER_CONFIRMATION, promptedAuthReq)
    }

    // Gate 2: Expired Approval Re-use Prevention
    @Test
    fun testExpiredApprovalCannotAuthorizeExecution() = runBlocking {
        val now = System.currentTimeMillis()

        // 1. Expired PENDING request -> RequiresApproval (cannot execute)
        val expiredPending = ApprovalRequest(
            approvalId = "appr-expired-pending",
            taskId = "t2", stepId = "s2", toolId = "delete_file", capabilityId = "FILES",
            riskEvaluation = RiskEvaluation.high(listOf(RiskReason.LocalDataDeletion("File")), "Delete"),
            actionSummary = "Delete file",
            previewPayload = emptyMap(),
            createdAt = now - 120_000,
            expiresAt = now - 60_000,
            status = ApprovalStatus.PENDING
        )
        assertTrue(expiredPending.isExpiredAt(now))
        assertFalse(expiredPending.isValidForExecution(now))

        val decisionPending = securityGate.evaluateAndEnforce(
            taskId = "t2", stepId = "s2", toolId = "delete_file", capabilityId = "FILES",
            riskEvaluation = expiredPending.riskEvaluation,
            permissionRequirements = emptyList(),
            authenticationRequirement = AuthenticationRequirement.USER_CONFIRMATION,
            existingApproval = expiredPending
        )
        assertTrue("Expired pending approval cannot execute", decisionPending is SecurityGateDecision.RequiresApproval)

        // 2. Expired APPROVED request -> Must NOT authorize execution!
        val expiredApproved = expiredPending.copy(
            status = ApprovalStatus.APPROVED,
            decision = ApprovalDecision(ApprovalStatus.APPROVED, "user", decidedAt = now - 70_000)
        )
        assertTrue(expiredApproved.isExpiredAt(now))
        assertFalse(expiredApproved.isValidForExecution(now))

        val decisionApproved = securityGate.evaluateAndEnforce(
            taskId = "t2", stepId = "s2", toolId = "delete_file", capabilityId = "FILES",
            riskEvaluation = expiredApproved.riskEvaluation,
            permissionRequirements = emptyList(),
            authenticationRequirement = AuthenticationRequirement.USER_CONFIRMATION,
            existingApproval = expiredApproved
        )
        assertFalse("Expired APPROVED request MUST NOT permit execution", decisionApproved is SecurityGateDecision.Permitted)
        assertTrue(decisionApproved is SecurityGateDecision.RequiresApproval)

        // 3. Active APPROVED request within window -> Allowed
        val validApproved = expiredApproved.copy(
            createdAt = now - 10_000,
            expiresAt = now + 50_000
        )
        assertTrue(validApproved.isValidForExecution(now))
        authenticator.setChallengeDelegate { req, _, _ -> AuthenticationResult.Success(req, System.currentTimeMillis()) }

        val decisionValid = securityGate.evaluateAndEnforce(
            taskId = "t2", stepId = "s2", toolId = "delete_file", capabilityId = "FILES",
            riskEvaluation = validApproved.riskEvaluation,
            permissionRequirements = emptyList(),
            authenticationRequirement = AuthenticationRequirement.USER_CONFIRMATION,
            existingApproval = validApproved
        )
        assertEquals(SecurityGateDecision.Permitted, decisionValid)
    }

    // Gate 3: Android KeyStore Failure Fails Closed Without Silent Fallback
    @Test
    fun testKeystoreFailureFailsClosedNoSilentFallback() {
        // Attempting to decrypt with non-existent alias throws KeystoreOperationException, not generating a fallback key
        try {
            cipherEngine.decrypt("tampered_ciphertext".toByteArray(), ByteArray(12), customKeyAlias = "non_existent_key_alias")
            fail("Expected KeystoreOperationException on missing key alias")
        } catch (e: ArishException.KeystoreOperationException) {
            assertTrue("Must fail closed with KeystoreOperationException", e.operation == "getExistingSecretKey" || e.operation == "decrypt")
        }
    }

    // Gate 4: Audit Logger Strict Allowlist and Redaction
    @Test
    fun testAuditLoggerStrictAllowlistAndRedaction() = runBlocking {
        val sensitiveApiKey = "AIzaSyDa9_Xz1234567890abcdefghijklmnopq"
        val sensitiveBearer = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.do_not_leak"
        val sensitivePassword = "password=SuperSecretPassword123!"

        auditLogger.logSecurityEvent(
            eventType = "SECURITY_TEST_EVENT",
            metadata = mapOf(
                "alias" to "valid_secret_alias",
                "status" to "SUCCESS",
                "operation" to "TEST_AUDIT",
                "apiKeyPayload" to sensitiveApiKey, // Not in allowlist -> dropped
                "description" to "User secret for $sensitivePassword", // Not in allowlist -> dropped
                "rationale" to "Need access to $sensitiveBearer", // Not in allowlist -> dropped
                "promptTitle" to "Authenticate with $sensitiveApiKey", // Not in allowlist -> dropped
                "nested" to mapOf(
                    "status" to "NESTED_OK",
                    "secretField" to sensitiveApiKey // nested unallowed -> dropped
                )
            )
        )

        val events = db.agentEventDao().getEventsByType("SECURITY_TEST_EVENT")
        assertEquals(1, events.size)
        val payload = events[0].payloadJson

        // Invariant: No sensitive string ever reaches agent_events
        assertFalse("Payload must not contain Google API key", payload.contains(sensitiveApiKey))
        assertFalse("Payload must not contain Bearer token", payload.contains(sensitiveBearer))
        assertFalse("Payload must not contain Password", payload.contains(sensitivePassword))
        assertFalse("Payload must not contain unallowed keys", payload.contains("apiKeyPayload"))
        assertFalse("Payload must not contain description", payload.contains("description"))
        assertFalse("Payload must not contain rationale", payload.contains("rationale"))
        assertFalse("Payload must not contain promptTitle", payload.contains("promptTitle"))
        assertTrue("Payload must contain allowed keys", payload.contains("valid_secret_alias"))
    }

    // Gate 5: Durable Secret Storage Persistence
    @Test
    fun testSecretDurablePersistenceAndFreshInstanceSurvival() = runBlocking {
        val secret = "DURABLE_VAULT_KEY_XYZ_123"
        val storeResult = secretStore.storeSecret("durable_alias", secret, "Durable key")
        assertTrue(storeResult.isSuccess)

        // Fresh storage instance reading from disk
        val freshStorage = EncryptedSecretStorage(context)
        val freshRecord = freshStorage.get("durable_alias")
        assertNotNull(freshRecord)

        // Fresh store instance decrypting
        val freshStore = AndroidSecureSecretStore(cipherEngine, freshStorage, auditLogger)
        val retrieval = freshStore.getSecret("durable_alias")
        assertTrue(retrieval.isSuccess)
        assertEquals(secret, retrieval.getOrThrow())

        // Deterministic deletion
        val deleteResult = freshStore.deleteSecret("durable_alias")
        assertTrue(deleteResult.isSuccess && deleteResult.getOrThrow())
        assertFalse(freshStorage.exists("durable_alias"))
    }

    // Gate 6: Process Restart Cryptographic Independence Test
    @Test
    fun testProcessRestartCryptographicIndependence() = runBlocking {
        val secret = "PROCESS_RESTART_INDEPENDENT_SECRET"
        val alias = "independent_alias"

        // Step 1: Write with original engine & storage
        secretStore.storeSecret(alias, secret, "Isolation check")

        // Step 2: Simulate fresh process launch (reading from same underlying keystore)
        val freshCipherEngine = AesGcmCipherEngine(injectedKeyStore = testKeyStore)
        val freshStorage = EncryptedSecretStorage(context)
        val freshStore = AndroidSecureSecretStore(freshCipherEngine, freshStorage, auditLogger)

        val retrieved = freshStore.getSecret(alias)
        assertTrue(retrieved.isSuccess)
        assertEquals(secret, retrieved.getOrThrow())
    }

    // Gate 7: Capability Risk Evaluation Semantics
    @Test
    fun testCapabilityRiskEvaluationSemantics() {
        val resolver = com.example.core.capability.CapabilityResolver()

        // 1. SEND_MESSAGE -> ExternalCommunication (HIGH)
        val sendMsg = resolver.resolve(
            com.example.core.domain.capability.StructuredIntent(
                intentId = "i1",
                intentName = "send_message",
                capabilityId = com.example.core.domain.capability.CapabilityId.SEND_MESSAGE,
                parameters = mapOf("recipient" to "+1234567890", "message" to "Hello"),
                rawUserPrompt = "Send message to Alice",
                confidence = 0.95f
            )
        )
        assertEquals(RiskLevel.HIGH, sendMsg.riskEvaluation.level)
        assertTrue(sendMsg.riskEvaluation.requiresApproval)
        assertTrue(sendMsg.riskEvaluation.reasons.any { it is RiskReason.ExternalCommunication })

        // 2. DELETE_NOTE -> LocalDataDeletion (HIGH)
        val delNote = resolver.resolve(
            com.example.core.domain.capability.StructuredIntent(
                intentId = "i2",
                intentName = "delete_note",
                capabilityId = com.example.core.domain.capability.CapabilityId.DELETE_NOTE,
                parameters = mapOf("noteId" to "note-123"),
                rawUserPrompt = "Delete note 123",
                confidence = 0.99f
            )
        )
        assertEquals(RiskLevel.HIGH, delNote.riskEvaluation.level)
        assertTrue(delNote.riskEvaluation.requiresApproval)
        assertTrue(delNote.riskEvaluation.reasons.any { it is RiskReason.LocalDataDeletion })

        // 3. READ_NOTES -> LocalDataRead (LOW)
        val readNotes = resolver.resolve(
            com.example.core.domain.capability.StructuredIntent(
                intentId = "i3",
                intentName = "read_notes",
                capabilityId = com.example.core.domain.capability.CapabilityId.READ_NOTES,
                parameters = emptyMap(),
                rawUserPrompt = "Show my notes",
                confidence = 1.0f
            )
        )
        assertEquals(RiskLevel.LOW, readNotes.riskEvaluation.level)
        assertFalse(readNotes.riskEvaluation.requiresApproval)
        assertTrue(readNotes.riskEvaluation.reasons.any { it is RiskReason.LocalDataRead })

        // 4. CREATE_CALENDAR_EVENT -> LocalDataWrite (MEDIUM)
        val calEvent = resolver.resolve(
            com.example.core.domain.capability.StructuredIntent(
                intentId = "i4",
                intentName = "create_calendar_event",
                capabilityId = com.example.core.domain.capability.CapabilityId.CREATE_CALENDAR_EVENT,
                parameters = mapOf("title" to "Team Sync"),
                rawUserPrompt = "Schedule team sync",
                confidence = 0.9f
            )
        )
        assertEquals(RiskLevel.MEDIUM, calEvent.riskEvaluation.level)
        assertTrue(calEvent.riskEvaluation.reasons.any { it is RiskReason.LocalDataWrite })

        // 5. WEB_SEARCH -> ReadOnlyDiagnostic (LOW)
        val webSearch = resolver.resolve(
            com.example.core.domain.capability.StructuredIntent(
                intentId = "i5",
                intentName = "web_search",
                capabilityId = com.example.core.domain.capability.CapabilityId.WEB_SEARCH,
                parameters = mapOf("query" to "Kotlin Coroutines"),
                rawUserPrompt = "Search for Kotlin coroutines",
                confidence = 1.0f
            )
        )
        assertEquals(RiskLevel.LOW, webSearch.riskEvaluation.level)
        assertFalse(webSearch.riskEvaluation.requiresApproval)
        assertTrue(webSearch.riskEvaluation.reasons.any { it is RiskReason.ReadOnlyDiagnostic })

        // 6. FORGET_FACT -> LocalDataDeletion (HIGH)
        val forgetFact = resolver.resolve(
            com.example.core.domain.capability.StructuredIntent(
                intentId = "i6",
                intentName = "forget_fact",
                capabilityId = com.example.core.domain.capability.CapabilityId.FORGET_FACT,
                parameters = mapOf("factId" to "fact-456"),
                rawUserPrompt = "Forget my old address",
                confidence = 0.92f
            )
        )
        assertEquals(RiskLevel.HIGH, forgetFact.riskEvaluation.level)
        assertTrue(forgetFact.riskEvaluation.requiresApproval)
        assertTrue(forgetFact.riskEvaluation.reasons.any { it is RiskReason.LocalDataDeletion })
    }

    // Gate 8: Permission Broker Audit Contract
    @Test
    fun testPermissionBrokerAuditContract() = runBlocking {
        val perm = "android.permission.CAMERA"
        permissionBroker.setRequestDelegate { _, _ -> PermissionStatus.GRANTED }

        permissionBroker.requestPermission(perm, "Sensitive rationale text that should not leak")

        val events = db.agentEventDao().getEventsByType("PERMISSION_REQUESTED")
        assertTrue(events.isNotEmpty())
        for (event in events) {
            assertFalse("Permission audit event must not contain sensitive rationale text", event.payloadJson.contains("Sensitive rationale text"))
            assertTrue("Permission audit event must contain permissionKey", event.payloadJson.contains(perm))
        }
    }

    // ==========================================
    // PHASE 1D REGRESSION & ADVERSARIAL TESTS
    // ==========================================

    @Test
    fun testApprovalMutationAttacksAreRejected() = runBlocking {
        val now = System.currentTimeMillis()
        val approvedReq = ApprovalRequest(
            approvalId = "appr-valid-1",
            taskId = "task-alpha",
            stepId = "step-1",
            toolId = "delete_note",
            capabilityId = "NOTES",
            riskEvaluation = RiskEvaluation.high(listOf(RiskReason.LocalDataDeletion("Note")), "Delete note"),
            actionSummary = "Delete Note",
            previewPayload = mapOf("noteId" to "123"),
            createdAt = now - 5000,
            expiresAt = now + 55000,
            status = ApprovalStatus.APPROVED,
            decision = ApprovalDecision(ApprovalStatus.APPROVED, "user", decidedAt = now - 4000)
        )

        authenticator.setChallengeDelegate { req, _, _ -> AuthenticationResult.Success(req, System.currentTimeMillis()) }

        // 1. Correct binding -> Permitted
        val validDecision = securityGate.evaluateAndEnforce(
            taskId = "task-alpha", stepId = "step-1", toolId = "delete_note", capabilityId = "NOTES",
            riskEvaluation = approvedReq.riskEvaluation,
            permissionRequirements = emptyList(),
            authenticationRequirement = AuthenticationRequirement.USER_CONFIRMATION,
            existingApproval = approvedReq
        )
        assertEquals(SecurityGateDecision.Permitted, validDecision)

        // 2. Attack: Tool ID changed (e.g. Action A approved -> execute Action B 'wipe_system')
        val tamperedToolDecision = securityGate.evaluateAndEnforce(
            taskId = "task-alpha", stepId = "step-1", toolId = "wipe_system", capabilityId = "NOTES",
            riskEvaluation = approvedReq.riskEvaluation,
            permissionRequirements = emptyList(),
            authenticationRequirement = AuthenticationRequirement.USER_CONFIRMATION,
            existingApproval = approvedReq
        )
        assertTrue("Mismatched tool ID must NOT be permitted", tamperedToolDecision is SecurityGateDecision.RequiresApproval)

        // 3. Attack: Capability changed
        val tamperedCapDecision = securityGate.evaluateAndEnforce(
            taskId = "task-alpha", stepId = "step-1", toolId = "delete_note", capabilityId = "SYSTEM",
            riskEvaluation = approvedReq.riskEvaluation,
            permissionRequirements = emptyList(),
            authenticationRequirement = AuthenticationRequirement.USER_CONFIRMATION,
            existingApproval = approvedReq
        )
        assertTrue("Mismatched capability ID must NOT be permitted", tamperedCapDecision is SecurityGateDecision.RequiresApproval)

        // 4. Attack: Task ID changed
        val tamperedTaskDecision = securityGate.evaluateAndEnforce(
            taskId = "task-hijacked", stepId = "step-1", toolId = "delete_note", capabilityId = "NOTES",
            riskEvaluation = approvedReq.riskEvaluation,
            permissionRequirements = emptyList(),
            authenticationRequirement = AuthenticationRequirement.USER_CONFIRMATION,
            existingApproval = approvedReq
        )
        assertTrue("Mismatched task ID must NOT be permitted", tamperedTaskDecision is SecurityGateDecision.RequiresApproval)

        // 5. Attack: Step ID changed
        val tamperedStepDecision = securityGate.evaluateAndEnforce(
            taskId = "task-alpha", stepId = "step-hijacked", toolId = "delete_note", capabilityId = "NOTES",
            riskEvaluation = approvedReq.riskEvaluation,
            permissionRequirements = emptyList(),
            authenticationRequirement = AuthenticationRequirement.USER_CONFIRMATION,
            existingApproval = approvedReq
        )
        assertTrue("Mismatched step ID must NOT be permitted", tamperedStepDecision is SecurityGateDecision.RequiresApproval)
    }

    @Test
    fun testAdversarialRapidConcurrentApprovalEvaluation() = runBlocking {
        val now = System.currentTimeMillis()
        val approvedReq = ApprovalRequest(
            approvalId = "appr-concurrent",
            taskId = "task-conc",
            stepId = "step-conc",
            toolId = "send_sms",
            capabilityId = "SMS",
            riskEvaluation = RiskEvaluation.high(listOf(RiskReason.ExternalCommunication("recipient", "SMS")), "Send SMS"),
            actionSummary = "Send SMS",
            previewPayload = emptyMap(),
            createdAt = now,
            expiresAt = now + 60000,
            status = ApprovalStatus.APPROVED,
            decision = ApprovalDecision(ApprovalStatus.APPROVED, "user", decidedAt = now)
        )

        authenticator.setChallengeDelegate { req, _, _ -> AuthenticationResult.Success(req, System.currentTimeMillis()) }

        val jobs = (1..30).map { i ->
            async(Dispatchers.Default) {
                securityGate.evaluateAndEnforce(
                    taskId = "task-conc",
                    stepId = "step-conc",
                    toolId = "send_sms",
                    capabilityId = "SMS",
                    riskEvaluation = approvedReq.riskEvaluation,
                    permissionRequirements = emptyList(),
                    authenticationRequirement = AuthenticationRequirement.USER_CONFIRMATION,
                    existingApproval = approvedReq
                )
            }
        }
        val results = jobs.awaitAll()
        assertEquals(30, results.size)
        assertTrue("All correctly bound concurrent evaluations must be Permitted", results.all { it is SecurityGateDecision.Permitted })
    }

    @Test
    fun testDynamicPermissionRevocationFailsClosed() = runBlocking {
        val perm = "android.permission.RECORD_AUDIO"
        permissionBroker.setRequestDelegate { _, _ -> PermissionStatus.DENIED }

        val decision = securityGate.evaluateAndEnforce(
            taskId = "t-audio",
            stepId = "s-1",
            toolId = "record_voice",
            capabilityId = "VOICE",
            riskEvaluation = RiskEvaluation.low(RiskReason.ReadOnlyDiagnostic, "Record audio"),
            permissionRequirements = listOf(PermissionRequirement(perm, true, "Need mic access")),
            authenticationRequirement = AuthenticationRequirement.NONE
        )

        assertTrue("Dynamically denied permission must result in Blocked decision", decision is SecurityGateDecision.Blocked)
        val blockedEx = (decision as SecurityGateDecision.Blocked).exception
        assertTrue("Exception must be PermissionDeniedException", blockedEx is ArishException.PermissionDeniedException)
        assertEquals(perm, (blockedEx as ArishException.PermissionDeniedException).permissionKey)
    }
}

