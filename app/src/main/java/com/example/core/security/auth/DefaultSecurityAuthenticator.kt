package com.example.core.security.auth

import com.example.core.domain.security.AuthenticationRequirement
import com.example.core.domain.security.AuthenticationResult
import com.example.core.domain.security.SecurityAuthenticator
import com.example.core.security.audit.SecurityAuditLogger

/**
 * Delegate interface for UI/Biometric authentication prompts.
 */
fun interface AuthenticationChallengeDelegate {
    suspend fun prompt(
        requirement: AuthenticationRequirement,
        title: String,
        subtitle: String?
    ): AuthenticationResult
}

/**
 * Production implementation of [SecurityAuthenticator].
 *
 * Security Invariants:
 * 1. OS permissions and A-RISH user authorization are strictly independent layers.
 * 2. Having OS permission granted does NOT bypass USER_CONFIRMATION or BIOMETRIC authentication.
 * 3. CRITICAL risk operations must enforce BIOMETRIC or DEVICE_CREDENTIAL requirements.
 * 4. Failed authentications fail closed and emit audit records.
 */
class DefaultSecurityAuthenticator(
    private val auditLogger: SecurityAuditLogger? = null,
    private var challengeDelegate: AuthenticationChallengeDelegate? = null
) : SecurityAuthenticator {

    fun setChallengeDelegate(delegate: AuthenticationChallengeDelegate?) {
        this.challengeDelegate = delegate
    }

    override suspend fun authenticate(
        requirement: AuthenticationRequirement,
        promptTitle: String,
        promptSubtitle: String?
    ): AuthenticationResult {
        // 1. Log authentication requested
        auditLogger?.logSecurityEvent(
            eventType = "AUTHENTICATION_REQUESTED",
            metadata = mapOf(
                "requirement" to requirement.name,
                "promptTitle" to promptTitle
            )
        )

        // 2. NONE requirement passes immediately
        if (requirement == AuthenticationRequirement.NONE) {
            val result = AuthenticationResult.Success(
                method = AuthenticationRequirement.NONE,
                timestamp = System.currentTimeMillis()
            )
            auditLogger?.logSecurityEvent(
                eventType = "AUTHENTICATION_SUCCESS",
                metadata = mapOf(
                    "requirement" to requirement.name,
                    "method" to "NONE"
                )
            )
            return result
        }

        // 3. Delegate to registered challenge provider (e.g. BiometricPrompt / Confirmation Dialog)
        val result = challengeDelegate?.prompt(requirement, promptTitle, promptSubtitle)
            ?: AuthenticationResult.Failed("No active authentication UI challenge handler registered")

        // 4. Log outcome
        when (result) {
            is AuthenticationResult.Success -> {
                auditLogger?.logSecurityEvent(
                    eventType = "AUTHENTICATION_SUCCESS",
                    metadata = mapOf(
                        "requirement" to requirement.name,
                        "method" to result.method.name
                    )
                )
            }
            is AuthenticationResult.Denied -> {
                auditLogger?.logSecurityEvent(
                    eventType = "AUTHENTICATION_FAILED",
                    metadata = mapOf(
                        "requirement" to requirement.name,
                        "reason" to result.reason
                    )
                )
            }
            is AuthenticationResult.Failed -> {
                auditLogger?.logSecurityEvent(
                    eventType = "AUTHENTICATION_FAILED",
                    metadata = mapOf(
                        "requirement" to requirement.name,
                        "error" to result.message
                    )
                )
            }
            AuthenticationResult.Cancelled -> {
                auditLogger?.logSecurityEvent(
                    eventType = "AUTHENTICATION_FAILED",
                    metadata = mapOf(
                        "requirement" to requirement.name,
                        "reason" to "CANCELLED"
                    )
                )
            }
        }

        return result
    }
}
