package com.example.core.security.audit

import com.example.core.data.local.dao.AgentEventDao
import com.example.core.data.local.entity.AgentEventEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * Audit logger for security events in A-RISH.
 *
 * NON-NEGOTIABLE INVARIANT:
 * Security audit payloads must NEVER contain secret values, passwords, tokens, API keys,
 * raw key material, unredacted prompts, or arbitrary exception messages.
 * Uses a strict ALLOWLIST for keys and scans all values for high-risk secret patterns.
 */
class SecurityAuditLogger(private val agentEventDao: AgentEventDao) {

    companion object {
        // Strict allowlist of safe metadata keys
        private val ALLOWED_METADATA_KEYS = setOf(
            "eventType",
            "operation",
            "status",
            "result",
            "resultType",
            "alias",
            "permissionKey",
            "permissionStatus",
            "requirement",
            "riskLevel",
            "capabilityId",
            "toolId",
            "algorithm",
            "cipherAlgorithm",
            "timestamp",
            "success",
            "isSuccess",
            "errorCategory",
            "stepIndex",
            "count",
            "method"
        )

        private val SAFE_ALIAS_REGEX = Regex("^[a-zA-Z0-9_.-]{1,64}$")
        private val FORBIDDEN_SUBSTRINGS = listOf("bearer", "sk-", "aizasy", "password", "secret", "api_key", "token=")

        // Patterns that represent credentials or sensitive material
        private val SENSITIVE_PATTERNS = listOf(
            Regex("(?i)AIza[0-9A-Za-z-_]{35}"),
            Regex("(?i)sk-[0-9A-Za-z-_]{20,}"),
            Regex("(?i)Bearer\\s+[A-Za-z0-9\\-_.~+/]+=*"),
            Regex("(?i)(?:password|secret|token|api[_-]?key)\\s*[:=]\\s*\\S+"),
            Regex("(?i)ghp_[0-9A-Za-z]{36}"),
            Regex("(?i)xox[baprs]-[0-9A-Za-z-]{10,}")
        )
    }

    suspend fun logSecurityEvent(
        eventType: String,
        metadata: Map<String, Any?>,
        taskId: String? = null,
        stepId: String? = null,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val sanitizedJson = sanitizeMetadata(metadata)

        agentEventDao.insertEvent(
            AgentEventEntity(
                taskId = taskId,
                stepId = stepId,
                eventType = sanitizeString(eventType),
                payloadJson = sanitizedJson.toString(),
                timestamp = timestamp
            )
        )
    }

    private fun sanitizeMetadata(map: Map<String, Any?>): JSONObject {
        val json = JSONObject()
        for ((key, value) in map) {
            if (!ALLOWED_METADATA_KEYS.contains(key)) {
                // Drop any unallowed key (e.g. description, rationale, promptTitle, error, etc.)
                continue
            }

            val sanitizedValue = sanitizeValue(key, value)
            if (sanitizedValue != null) {
                json.put(key, sanitizedValue)
            }
        }
        return json
    }

    private fun sanitizeValue(key: String, value: Any?): Any? {
        if (value == null) return JSONObject.NULL

        if (key == "alias" && value is String) {
            return validateAndSanitizeAlias(value)
        }

        return when (value) {
            is String -> sanitizeString(value)
            is Number, is Boolean -> value
            is Map<*, *> -> {
                val nestedObj = JSONObject()
                for ((k, v) in value) {
                    val kStr = k?.toString() ?: continue
                    if (ALLOWED_METADATA_KEYS.contains(kStr)) {
                        val nestedSanitized = sanitizeValue(kStr, v)
                        if (nestedSanitized != null) {
                            nestedObj.put(kStr, nestedSanitized)
                        }
                    }
                }
                nestedObj
            }
            is Collection<*> -> {
                val array = JSONArray()
                for (item in value) {
                    val sanitizedItem = when (item) {
                        is String -> sanitizeString(item)
                        is Number, is Boolean -> item
                        else -> null
                    }
                    if (sanitizedItem != null) {
                        array.put(sanitizedItem)
                    }
                }
                array
            }
            else -> sanitizeString(value.toString())
        }
    }

    private fun validateAndSanitizeAlias(alias: String): String {
        if (!SAFE_ALIAS_REGEX.matches(alias)) {
            return "[INVALID_OR_UNSAFE_ALIAS]"
        }
        val lower = alias.lowercase()
        if (FORBIDDEN_SUBSTRINGS.any { lower.contains(it) && !lower.endsWith("_key") && !lower.endsWith("_token") }) {
            // If it literally looks like a credential rather than an identifier
            if (lower.startsWith("sk-") || lower.startsWith("bearer") || lower.startsWith("aizasy")) {
                return "[REDACTED_SECRET_MATERIAL]"
            }
        }
        return sanitizeString(alias)
    }

    private fun sanitizeString(str: String): String {
        var result = str
        for (pattern in SENSITIVE_PATTERNS) {
            if (pattern.containsMatchIn(result)) {
                result = pattern.replace(result, "[REDACTED_SECRET_MATERIAL]")
            }
        }
        return result
    }
}
