package com.example.core.security.keystore

import org.json.JSONObject

/**
 * Encrypted persistent representation of a secret.
 * Contains ONLY ciphertext bytes, initialization vector (nonce), and non-sensitive metadata.
 * Plaintext secrets are strictly never stored.
 */
data class EncryptedSecretRecord(
    val alias: String,
    val ciphertextBase64: String,
    val ivBase64: String,
    val description: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toJson(): String {
        val obj = JSONObject()
        obj.put("alias", alias)
        obj.put("ciphertextBase64", ciphertextBase64)
        obj.put("ivBase64", ivBase64)
        obj.put("description", description ?: JSONObject.NULL)
        obj.put("createdAt", createdAt)
        return obj.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): EncryptedSecretRecord {
            val obj = JSONObject(jsonStr)
            return EncryptedSecretRecord(
                alias = obj.getString("alias"),
                ciphertextBase64 = obj.getString("ciphertextBase64"),
                ivBase64 = obj.getString("ivBase64"),
                description = if (obj.isNull("description")) null else obj.getString("description"),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }
}
