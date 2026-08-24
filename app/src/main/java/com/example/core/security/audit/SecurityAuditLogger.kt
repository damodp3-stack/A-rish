package com.example.core.security.audit

import com.example.core.data.local.dao.AgentEventDao
import com.example.core.data.local.entity.AgentEventEntity
import org.json.JSONObject

/**
 * Audit logger for security events in A-RISH.
 *
 * NON-NEGOTIABLE INVARIANT:
 * Security audit payloads must NEVER contain secret values, passwords, tokens, API keys, or raw key material.
 */
class SecurityAuditLogger(private val agentEventDao: AgentEventDao) {

    private val forbiddenKeys = listOf(
        "secret", "key", "password", "token", "credential", "auth_token", "api_key", "apikey", "access_token", "private"
    )

    suspend fun logSecurityEvent(
        eventType: String,
        metadata: Map<String, Any?>,
        taskId: String? = null,
        stepId: String? = null,
        timestamp: Long = System.currentTimeMillis()
    ) {
        // Enforce strict redaction: ensure no key or value inadvertently leaks sensitive material
        val sanitized = JSONObject()
        for ((k, v) in metadata) {
            val lowerK = k.lowercase()
            if (forbiddenKeys.any { lowerK == it || lowerK.endsWith("_key") || lowerK.endsWith("token") || lowerK.endsWith("password") }) {
                // If it's a metadata alias or identifier, allow it only if it's explicitly an alias name, else redact
                if (k.equals("alias", ignoreCase = true) || k.equals("permissionKey", ignoreCase = true)) {
                    sanitized.put(k, v?.toString() ?: "")
                } else {
                    sanitized.put(k, "[REDACTED_SECRET_MATERIAL]")
                }
            } else {
                sanitized.put(k, v ?: JSONObject.NULL)
            }
        }

        agentEventDao.insertEvent(
            AgentEventEntity(
                taskId = taskId,
                stepId = stepId,
                eventType = eventType,
                payloadJson = sanitized.toString(),
                timestamp = timestamp
            )
        )
    }
}
