package com.example.core.domain.execution

/**
 * Explicit delivery and side-effect guarantees for tools and system actions.
 * Prevents false assumptions of external exactly-once execution.
 */
enum class SideEffectSemantics {
    /** Read-only operation; pure query with zero state modification (e.g., calculator, search, clock) */
    NO_SIDE_EFFECT,

    /** Local database transaction with ACID guarantees (e.g., Room insert/update/delete) */
    LOCAL_TRANSACTIONAL,

    /** External system side effect dispatched (e.g., WhatsApp message, System Alarm, SMS) */
    EXTERNAL_SIDE_EFFECT,

    /** Interrupted or crashed execution where external state cannot be determined without probe */
    UNKNOWN_EXTERNAL_STATE
}

enum class DeliveryGuarantee {
    /** Guaranteed exactly once via local transactional rollback / primary key constraint */
    EXACTLY_ONCE,

    /** At most once: will not re-dispatch on ambiguity to prevent duplicate messaging */
    AT_MOST_ONCE,

    /** At least once: idempotent retry is completely safe */
    AT_LEAST_ONCE,

    /** Unknown after process death; requires explicit user/probe verification before retry */
    UNKNOWN_AFTER_CRASH
}
