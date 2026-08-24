package com.example.core.domain.validation

import com.example.core.domain.agent.AgentState
import com.example.core.domain.error.ArishException

/**
 * Validates FSM transitions against bounded state rules.
 */
object StateTransitionValidator {

    fun validateTransition(current: AgentState, target: AgentState) {
        if (!current.canTransitionTo(target)) {
            throw ArishException.InvalidStateTransitionException(
                "Illegal state transition from $current to $target. Valid targets from $current: ${getValidTargets(current)}"
            )
        }
    }

    private fun getValidTargets(current: AgentState): List<AgentState> {
        return AgentState.entries.filter { current.canTransitionTo(it) }
    }
}
