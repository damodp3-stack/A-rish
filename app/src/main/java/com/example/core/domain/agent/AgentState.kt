package com.example.core.domain.agent

/**
 * Deterministic, Bounded Finite State Machine (FSM) states for A-RISH agent tasks.
 * No recursive or open-ended transitions are permitted.
 */
enum class AgentState(val isTerminal: Boolean) {
    /** Task received from user or external trigger; awaiting intake processing */
    RECEIVED(isTerminal = false),

    /** Context Engine assembling environment, memories, and sanitized user intent */
    UNDERSTANDING(isTerminal = false),

    /** Intent mapped to Capability and Step Graph decomposition generated */
    PLANNING(isTerminal = false),

    /** Action contains medium/high risk or requires explicit user authorization */
    WAITING_FOR_APPROVAL(isTerminal = false),

    /** Step currently executing within deterministic runtime bounds */
    EXECUTING(isTerminal = false),

    /** Step execution completed; verification engine evaluating evidence */
    VERIFYING(isTerminal = false),

    /** Verification failed, recovering via alternate step or bounded retry */
    RECOVERING(isTerminal = false),

    /** All planned steps successfully executed and evidence-verified */
    COMPLETED(isTerminal = true),

    /** Task failed due to security rejection, budget exhaustion, or unrecoverable error */
    FAILED(isTerminal = true),

    /** Explicitly cancelled by user or timeout */
    ABORTED(isTerminal = true);

    fun canTransitionTo(next: AgentState): Boolean {
        if (this.isTerminal) return false
        return when (this) {
            RECEIVED -> next == UNDERSTANDING || next == ABORTED || next == FAILED
            UNDERSTANDING -> next == PLANNING || next == FAILED || next == ABORTED
            PLANNING -> next == WAITING_FOR_APPROVAL || next == EXECUTING || next == FAILED || next == ABORTED
            WAITING_FOR_APPROVAL -> next == EXECUTING || next == ABORTED || next == FAILED
            EXECUTING -> next == VERIFYING || next == FAILED || next == ABORTED
            VERIFYING -> next == EXECUTING || next == RECOVERING || next == COMPLETED || next == FAILED || next == ABORTED
            RECOVERING -> next == EXECUTING || next == WAITING_FOR_APPROVAL || next == FAILED || next == ABORTED
            COMPLETED, FAILED, ABORTED -> false
        }
    }
}
