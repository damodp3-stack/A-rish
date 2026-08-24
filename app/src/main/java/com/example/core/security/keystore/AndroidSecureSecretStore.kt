package com.example.core.security.keystore

import com.example.core.domain.error.ArishException
import com.example.core.domain.security.SecureSecretStore
import com.example.core.security.audit.SecurityAuditLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android Keystore-backed implementation of [SecureSecretStore] using AES-256-GCM.
 *
 * Security Invariants:
 * 1. Plaintext secrets are decrypted in-memory only when requested by authorized components.
 * 2. Plaintext secrets are NEVER written to logs, Room database, or persistent storage.
 * 3. Retrieval fails closed on any cryptographic failure or missing alias.
 * 4. Audit events track lifecycle operations without recording secret content.
 */
class AndroidSecureSecretStore(
    private val cipherEngine: AesGcmCipherEngine,
    private val storage: EncryptedSecretStorage,
    private val auditLogger: SecurityAuditLogger? = null
) : SecureSecretStore {

    override suspend fun storeSecret(
        alias: String,
        secret: String,
        description: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(alias.isNotBlank()) { "Secret alias must not be blank" }
            require(secret.isNotBlank()) { "Secret value must not be blank" }

            val (ciphertext, iv) = cipherEngine.encrypt(secret.toByteArray(Charsets.UTF_8))
            val record = EncryptedSecretRecord(
                alias = alias,
                ciphertextBase64 = cipherEngine.toBase64(ciphertext),
                ivBase64 = cipherEngine.toBase64(iv),
                description = description,
                createdAt = System.currentTimeMillis()
            )

            storage.save(record)

            auditLogger?.logSecurityEvent(
                eventType = "SECRET_CREATED",
                metadata = mapOf(
                    "alias" to alias,
                    "description" to (description ?: ""),
                    "cipherAlgorithm" to "AES-256-GCM"
                )
            )
            Unit
        }
    }

    override suspend fun getSecret(alias: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(alias.isNotBlank()) { "Secret alias must not be blank" }

            val record = storage.get(alias)
                ?: throw ArishException.SecretNotFoundException(alias)

            val ciphertext = cipherEngine.fromBase64(record.ciphertextBase64)
            val iv = cipherEngine.fromBase64(record.ivBase64)

            val decryptedBytes = cipherEngine.decrypt(ciphertext, iv)
            val plaintext = String(decryptedBytes, Charsets.UTF_8)

            auditLogger?.logSecurityEvent(
                eventType = "SECRET_RETRIEVED",
                metadata = mapOf(
                    "alias" to alias,
                    "status" to "SUCCESS"
                )
            )

            plaintext
        }
    }

    override suspend fun deleteSecret(alias: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val existed = storage.delete(alias)
            if (existed) {
                auditLogger?.logSecurityEvent(
                    eventType = "SECRET_DELETED",
                    metadata = mapOf(
                        "alias" to alias,
                        "status" to "DELETED"
                    )
                )
            }
            existed
        }
    }

    override suspend fun hasSecret(alias: String): Boolean = withContext(Dispatchers.IO) {
        storage.exists(alias)
    }

    override suspend fun listAliases(): List<String> = withContext(Dispatchers.IO) {
        storage.listAliases()
    }
}
