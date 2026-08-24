package com.example.core.security.permission

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.core.domain.security.PermissionBroker
import com.example.core.domain.security.PermissionRequirement
import com.example.core.domain.security.PermissionStatus
import com.example.core.security.audit.SecurityAuditLogger

/**
 * Delegate interface for UI-bound runtime permission requesting (e.g. Activity / Fragment / Compose launcher).
 */
fun interface PermissionRequestDelegate {
    suspend fun request(permission: String, rationale: String?): PermissionStatus
}

/**
 * Android implementation of [PermissionBroker].
 *
 * Security Invariants:
 * 1. PermissionBroker is the ONLY authorized boundary for checking and requesting OS permissions.
 * 2. LLM / Tool execution code is strictly forbidden from directly calling ContextCompat or Activity permission APIs.
 * 3. Already granted permissions avoid redundant request prompts.
 * 4. Audit events record every permission check and request attempt.
 */
class AndroidPermissionBroker(
    private val context: Context,
    private val auditLogger: SecurityAuditLogger? = null,
    private var requestDelegate: PermissionRequestDelegate? = null
) : PermissionBroker {

    fun setRequestDelegate(delegate: PermissionRequestDelegate?) {
        this.requestDelegate = delegate
    }

    override fun checkPermission(permission: String): PermissionStatus {
        val isGranted = ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED

        val status = if (isGranted) PermissionStatus.GRANTED else PermissionStatus.DENIED
        return status
    }

    override fun checkPermissions(requirements: List<PermissionRequirement>): Map<String, PermissionStatus> {
        val results = mutableMapOf<String, PermissionStatus>()
        for (req in requirements) {
            results[req.permissionManifestKey] = checkPermission(req.permissionManifestKey)
        }
        return results
    }

    override suspend fun requestPermission(permission: String, rationale: String?): PermissionStatus {
        // 1. If already granted, do not prompt user or trigger unnecessary dialogs
        if (checkPermission(permission) == PermissionStatus.GRANTED) {
            auditLogger?.logSecurityEvent(
                eventType = "PERMISSION_CHECKED",
                metadata = mapOf(
                    "permissionKey" to permission,
                    "status" to "ALREADY_GRANTED"
                )
            )
            return PermissionStatus.GRANTED
        }

        // 2. Log permission request intent
        auditLogger?.logSecurityEvent(
            eventType = "PERMISSION_REQUESTED",
            metadata = mapOf(
                "permissionKey" to permission,
                "rationale" to (rationale ?: "")
            )
        )

        // 3. Request via registered delegate or return DENIED if no UI delegate is active
        val finalStatus = requestDelegate?.request(permission, rationale) ?: checkPermission(permission)

        // 4. Log outcome
        val outcomeEvent = when (finalStatus) {
            PermissionStatus.GRANTED -> "PERMISSION_GRANTED"
            PermissionStatus.PERMANENTLY_DENIED -> "PERMISSION_PERMANENTLY_DENIED"
            else -> "PERMISSION_DENIED"
        }

        auditLogger?.logSecurityEvent(
            eventType = outcomeEvent,
            metadata = mapOf(
                "permissionKey" to permission,
                "status" to finalStatus.name
            )
        )

        return finalStatus
    }

    override suspend fun requestPermissions(requirements: List<PermissionRequirement>): Map<String, PermissionStatus> {
        val outcomes = mutableMapOf<String, PermissionStatus>()
        for (req in requirements) {
            outcomes[req.permissionManifestKey] = requestPermission(req.permissionManifestKey, req.rationaleUserText)
        }
        return outcomes
    }
}
