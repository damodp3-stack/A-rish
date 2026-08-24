package com.example.core.security.keystore

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.core.domain.error.ArishException
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Low-level cryptographic engine enforcing AES-256-GCM authenticated encryption.
 *
 * Security Invariants:
 * 1. Android KeyStore owns the master encryption key.
 * 2. Every encryption operation generates a unique, non-repeating cryptographic nonce (IV) using SecureRandom.
 * 3. Tag length is 128 bits.
 * 4. Authentication tag mismatches, tampered ciphertext, or tampered IV produce explicit typed security failures.
 * 5. Key material is never exposed or logged.
 * 6. NO SILENT IN-MEMORY FALLBACK: If KeyStore fails, operations fail closed.
 */
class AesGcmCipherEngine(
    private val keyAlias: String = DEFAULT_MASTER_KEY_ALIAS,
    private val keyStoreProvider: String = ANDROID_KEYSTORE_PROVIDER,
    private val injectedKeyStore: KeyStore? = null
) {

    companion object {
        const val DEFAULT_MASTER_KEY_ALIAS = "arish_master_key_aes256"
        const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val IV_LENGTH_BYTES = 12
    }

    private val secureRandom = SecureRandom()

    init {
        ensureMasterKeyExists(keyAlias)
    }

    /**
     * Encrypts plaintext bytes using AES-256-GCM with a fresh, unique IV.
     */
    fun encrypt(plaintext: ByteArray, customKeyAlias: String = keyAlias): Pair<ByteArray, ByteArray> {
        val secretKey = getOrCreateSecretKey(customKeyAlias)
        val iv = ByteArray(IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        val ciphertext = cipher.doFinal(plaintext)
        return Pair(ciphertext, iv)
    }

    /**
     * Decrypts ciphertext bytes using AES-256-GCM and the supplied IV.
     * Fails closed if the ciphertext is corrupted, tampered, or the key is missing/invalid.
     * Does NOT generate a new replacement key for missing keys during decryption.
     */
    fun decrypt(ciphertext: ByteArray, iv: ByteArray, customKeyAlias: String = keyAlias): ByteArray {
        val secretKey = getExistingSecretKey(customKeyAlias)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            cipher.doFinal(ciphertext)
        } catch (e: AEADBadTagException) {
            throw ArishException.AuthenticationTagMismatchException(
                alias = customKeyAlias,
                message = "AEAD authentication tag validation failed. Ciphertext or IV was modified."
            )
        } catch (e: Exception) {
            if (e.javaClass.name.contains("AEADBadTagException") || e.cause is AEADBadTagException) {
                throw ArishException.AuthenticationTagMismatchException(
                    alias = customKeyAlias,
                    message = "AEAD authentication tag validation failed. Ciphertext or IV was modified."
                )
            }
            throw ArishException.KeystoreOperationException(
                operation = "decrypt",
                message = "Decryption failed for alias '$customKeyAlias': ${e.message}",
                cause = e
            )
        }
    }

    private fun loadKeyStore(): KeyStore {
        return try {
            injectedKeyStore ?: KeyStore.getInstance(keyStoreProvider).apply { load(null) }
        } catch (e: Exception) {
            throw ArishException.KeystoreOperationException(
                operation = "loadKeyStore",
                message = "Failed to load KeyStore provider '$keyStoreProvider': ${e.message}",
                cause = e
            )
        }
    }

    private fun ensureMasterKeyExists(alias: String) {
        getOrCreateSecretKey(alias)
    }

    private fun getExistingSecretKey(alias: String): SecretKey {
        val keyStore = loadKeyStore()
        if (!keyStore.containsAlias(alias)) {
            throw ArishException.KeystoreOperationException(
                operation = "getExistingSecretKey",
                message = "Key alias '$alias' does not exist in KeyStore. Cannot decrypt."
            )
        }
        val entry = try {
            val protParam = if (injectedKeyStore != null) KeyStore.PasswordProtection(charArrayOf()) else null
            keyStore.getEntry(alias, protParam) as? KeyStore.SecretKeyEntry
        } catch (e: Exception) {
            throw ArishException.KeystoreOperationException(
                operation = "getEntry",
                message = "Failed to retrieve key entry for alias '$alias': ${e.message}",
                cause = e
            )
        }
        return entry?.secretKey ?: throw ArishException.KeystoreOperationException(
            operation = "getExistingSecretKey",
            message = "KeyStore entry for alias '$alias' is not a valid SecretKeyEntry"
        )
    }

    private fun getOrCreateSecretKey(alias: String): SecretKey {
        val keyStore = loadKeyStore()
        if (keyStore.containsAlias(alias)) {
            val entry = try {
                val protParam = if (injectedKeyStore != null) KeyStore.PasswordProtection(charArrayOf()) else null
                keyStore.getEntry(alias, protParam) as? KeyStore.SecretKeyEntry
            } catch (e: Exception) {
                throw ArishException.KeystoreOperationException(
                    operation = "getEntry",
                    message = "Failed to retrieve existing key entry for alias '$alias': ${e.message}",
                    cause = e
                )
            }
            if (entry != null) {
                return entry.secretKey
            }
        }

        // Generate new AES-256 key
        return try {
            if (injectedKeyStore != null) {
                val keyGen = KeyGenerator.getInstance("AES")
                keyGen.init(256)
                val secretKey = keyGen.generateKey()
                injectedKeyStore.setEntry(
                    alias,
                    KeyStore.SecretKeyEntry(secretKey),
                    KeyStore.PasswordProtection(charArrayOf())
                )
                secretKey
            } else {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, keyStoreProvider)
                val keyGenSpec = KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build()

                keyGenerator.init(keyGenSpec)
                keyGenerator.generateKey()
            }
        } catch (e: Exception) {
            throw ArishException.KeystoreOperationException(
                operation = "generateKey",
                message = "Failed to generate master AES key for alias '$alias': ${e.message}",
                cause = e
            )
        }
    }

    /**
     * Converts raw bytes to base64 string for safe serialization.
     */
    fun toBase64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    /**
     * Converts base64 string back to raw bytes.
     */
    fun fromBase64(base64: String): ByteArray = Base64.decode(base64, Base64.NO_WRAP)
}
