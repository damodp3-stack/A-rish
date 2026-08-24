package com.example.core.security.keystore

import android.content.Context
import android.content.SharedPreferences

/**
 * Storage repository for encrypted secret payloads.
 *
 * Invariants:
 * 1. Contains ONLY encrypted ciphertext and IV.
 * 2. Plaintext secrets are strictly never persisted.
 * 3. File access is locked to private application sandbox (MODE_PRIVATE).
 */
class EncryptedSecretStorage(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "arish_secure_vault_encrypted_store"
    }

    @Synchronized
    fun save(record: EncryptedSecretRecord): Boolean {
        val success = prefs.edit()
            .putString(record.alias, record.toJson())
            .commit()
        if (!success) {
            throw com.example.core.domain.error.ArishException.StoragePersistenceException(
                operation = "save",
                message = "Failed to atomically persist secret record for alias '${record.alias}'"
            )
        }
        return true
    }

    @Synchronized
    fun get(alias: String): EncryptedSecretRecord? {
        val json = prefs.getString(alias, null) ?: return null
        return try {
            EncryptedSecretRecord.fromJson(json)
        } catch (e: Exception) {
            null
        }
    }

    @Synchronized
    fun delete(alias: String): Boolean {
        if (!prefs.contains(alias)) return false
        val success = prefs.edit().remove(alias).commit()
        if (!success) {
            throw com.example.core.domain.error.ArishException.StoragePersistenceException(
                operation = "delete",
                message = "Failed to atomically remove secret record for alias '$alias'"
            )
        }
        return true
    }

    @Synchronized
    fun exists(alias: String): Boolean {
        return prefs.contains(alias)
    }

    @Synchronized
    fun listAliases(): List<String> {
        return prefs.all.keys.toList()
    }

    @Synchronized
    fun clearAll(): Boolean {
        return prefs.edit().clear().commit()
    }
}
