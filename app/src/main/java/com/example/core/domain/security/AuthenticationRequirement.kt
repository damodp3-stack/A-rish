package com.example.core.domain.security

/**
 * Explicit authentication level required for an action or tool execution.
 */
enum class AuthenticationRequirement {
    /** No authentication required beyond application execution */
    NONE,

    /** Explicit user confirmation required (UI button tap / voice confirmation) */
    USER_CONFIRMATION,

    /** Device lock screen credential required (PIN/Pattern/Password) */
    DEVICE_CREDENTIAL,

    /** Hardware-backed biometric authentication required (Fingerprint / Face Unlock) */
    BIOMETRIC
}

/**
 * Declarative Android OS permission requirement for a capability/tool.
 */
data class PermissionRequirement(
    val permissionManifestKey: String,
    val isMandatory: Boolean = true,
    val rationaleUserText: String
)

enum class PermissionStatus {
    GRANTED,
    DENIED,
    PERMANENTLY_DENIED,
    NOT_REQUESTED
}
