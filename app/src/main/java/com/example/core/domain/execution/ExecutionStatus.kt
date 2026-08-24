package com.example.core.domain.execution

/**
 * Explicit execution lifecycle status for agent steps and side-effects.
 * Distinguishes dispatched vs executed vs evidence-verified states.
 */
enum class ExecutionStatus(val isTerminal: Boolean) {
    /** Step request received and placed in queue */
    REQUESTED(isTerminal = false),

    /** Step dispatched to tool executor or OS intent dispatcher */
    DISPATCHED(isTerminal = false),

    /** Tool executed without runtime exception, awaiting verification evidence */
    EXECUTED(isTerminal = false),

    /** Strong, verifiable positive evidence collected (e.g. Room row inserted, URI resolved) */
    VERIFIED(isTerminal = true),

    /** Dispatched to external app/subsystem (e.g. WhatsApp opened), but final delivery unknown */
    PARTIALLY_VERIFIED(isTerminal = true),

    /** Verification indeterminate or process crashed mid-execution with unknown outcome */
    UNKNOWN(isTerminal = true),

    /** Step failed due to exception, permission denial, or validation error */
    FAILED(isTerminal = true),

    /** Step explicitly aborted */
    ABORTED(isTerminal = true);

    val isSuccessful: Boolean
        get() = this == VERIFIED || this == PARTIALLY_VERIFIED
}
