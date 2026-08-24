package com.example.core.domain.security

/**
 * Result of an explicit user or hardware authentication challenge.
 */
sealed class AuthenticationResult {
    data class Success(val method: AuthenticationRequirement, val timestamp: Long) : AuthenticationResult()
    data class Denied(val reason: String) : AuthenticationResult()
    data class Failed(val message: String) : AuthenticationResult()
    data object Cancelled : AuthenticationResult()

    val isSuccess: Boolean get() = this is Success
}

/**
 * Domain boundary contract for authenticating user actions according to risk and authentication requirements.
 *
 * Invariants:
 * 1. HIGH risk actions require explicit user approval (USER_CONFIRMATION or higher) even if OS permission is granted.
 * 2. CRITICAL risk actions require stronger authentication (BIOMETRIC or DEVICE_CREDENTIAL).
 * 3. Authentication is completely decoupled from Android OS runtime permissions.
 */
interface SecurityAuthenticator {
    suspend fun authenticate(
        requirement: AuthenticationRequirement,
        promptTitle: String,
        promptSubtitle: String? = null
    ): AuthenticationResult
}
