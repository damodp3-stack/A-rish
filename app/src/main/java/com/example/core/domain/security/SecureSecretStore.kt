package com.example.core.domain.security

/**
 * Domain boundary contract for secure cryptographic secret storage.
 *
 * Invariants:
 * 1. Plaintext secret values are never persisted or logged.
 * 2. Android Keystore owns the root master key.
 * 3. Retrieval fails closed.
 * 4. Key material is never exposed outside the store.
 */
interface SecureSecretStore {
    /**
     * Encrypts and persists a secret using AES-256-GCM with a unique IV.
     */
    suspend fun storeSecret(alias: String, secret: String, description: String? = null): Result<Unit>

    /**
     * Decrypts and retrieves a secret for the given alias.
     * Fails closed if missing, tampered, or authentication tag fails.
     */
    suspend fun getSecret(alias: String): Result<String>

    /**
     * Deletes a secret from secure storage.
     */
    suspend fun deleteSecret(alias: String): Result<Boolean>

    /**
     * Checks if a secret exists under the given alias without exposing ciphertext.
     */
    suspend fun hasSecret(alias: String): Boolean

    /**
     * Lists all registered secret aliases (metadata only, no secrets).
     */
    suspend fun listAliases(): List<String>
}
