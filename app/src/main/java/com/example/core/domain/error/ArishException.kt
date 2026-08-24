package com.example.core.domain.error

/**
 * Sealed hierarchy of typed domain exceptions for A-RISH.
 * Allows deterministic error classification and recovery.
 */
sealed class ArishException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    class SecurityException(message: String) : ArishException(message)

    class PermissionDeniedException(val permissionKey: String, message: String) :
        ArishException("Permission denied for $permissionKey: $message")

    class BudgetExceededException(message: String) : ArishException(message)

    class InvalidStateTransitionException(message: String) : ArishException(message)

    class VerificationFailedException(val stepId: String, message: String) :
        ArishException("Verification failed for step $stepId: $message")

    class SchemaValidationException(val fieldName: String, message: String) :
        ArishException("Schema validation failed on field '$fieldName': $message")

    class UnknownCapabilityException(message: String) : ArishException(message)

    class UnknownToolException(val toolId: String) : ArishException("Tool '$toolId' is not registered")

    class ExecutionTimeoutException(val timeoutMs: Long, message: String) :
        ArishException("Execution timed out after ${timeoutMs}ms: $message")

    class IdempotencyViolationException(val key: String, message: String) :
        ArishException("Idempotency key '$key' violation: $message")

    class ContextFirewallViolationException(message: String) : ArishException(message)
}
