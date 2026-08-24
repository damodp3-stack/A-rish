package com.example.core.domain.security

/**
 * Domain boundary contract for Android runtime permission orchestration.
 *
 * Invariants:
 * 1. LLM/Agent code NEVER directly calls Android framework permission APIs.
 * 2. Permission requests must originate strictly through PermissionBroker.
 * 3. Denied states produce explicit typed failures.
 * 4. Runtime OS permissions and A-RISH user approvals remain strictly separate layers.
 */
interface PermissionBroker {
    /**
     * Checks current permission status without requesting.
     */
    fun checkPermission(permission: String): PermissionStatus

    /**
     * Checks multiple permission requirements.
     */
    fun checkPermissions(requirements: List<PermissionRequirement>): Map<String, PermissionStatus>

    /**
     * Requests a specific runtime permission if not already granted.
     */
    suspend fun requestPermission(permission: String, rationale: String? = null): PermissionStatus

    /**
     * Requests multiple runtime permissions.
     */
    suspend fun requestPermissions(requirements: List<PermissionRequirement>): Map<String, PermissionStatus>
}
