package com.example.core.domain.security

/**
 * Risk classification levels for capability actions and tools.
 */
enum class RiskLevel(val severityScore: Int) {
    /** Safe, read-only operations with no privacy or system modification (e.g. calculator, time) */
    LOW(severityScore = 1),

    /** Operations that access system settings, query read-only storage, or set alarms */
    MEDIUM(severityScore = 2),

    /** Operations modifying personal data, sending messages, or creating persistent events */
    HIGH(severityScore = 3),

    /** Destructive operations (delete memory, wipe database, modify system security) */
    CRITICAL(severityScore = 4);

    val requiresExplicitApproval: Boolean
        get() = this == HIGH || this == CRITICAL
}
