package com.example.core.domain.agent

import com.example.core.domain.execution.ExecutionStatus

enum class TaskPriority {
    LOW,
    NORMAL,
    HIGH,
    CRITICAL
}

/**
 * Immutable domain model for an autonomous Agent Task.
 */
data class AgentTask(
    val taskId: String,
    val title: String,
    val userPrompt: String,
    val structuredIntentId: String?,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val state: AgentState = AgentState.RECEIVED,
    val budget: ExecutionBudget = ExecutionBudget.STANDARD,
    val steps: List<AgentStep> = emptyList(),
    val currentStepIndex: Int = 0,
    val totalToolCallsCount: Int = 0,
    val failureReason: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long? = null
) {
    val isTerminal: Boolean
        get() = state.isTerminal

    val activeStep: AgentStep?
        get() = steps.getOrNull(currentStepIndex)

    val isBudgetExhausted: Boolean
        get() = steps.size > budget.maxSteps || totalToolCallsCount > budget.maxToolCalls

    fun transitionTo(newState: AgentState, reason: String? = null): AgentTask {
        require(state.canTransitionTo(newState)) {
            "Illegal FSM transition from $state to $newState for task $taskId"
        }
        return copy(
            state = newState,
            failureReason = reason ?: failureReason,
            updatedAt = System.currentTimeMillis(),
            completedAt = if (newState.isTerminal) System.currentTimeMillis() else completedAt
        )
    }

    fun withSteps(newSteps: List<AgentStep>): AgentTask {
        require(newSteps.size <= budget.maxSteps) {
            "Planned steps count (${newSteps.size}) exceeds budget maxSteps (${budget.maxSteps})"
        }
        return copy(steps = newSteps, updatedAt = System.currentTimeMillis())
    }

    fun updateStep(stepIndex: Int, transform: (AgentStep) -> AgentStep): AgentTask {
        val updatedSteps = steps.toMutableList()
        if (stepIndex in updatedSteps.indices) {
            updatedSteps[stepIndex] = transform(updatedSteps[stepIndex])
        }
        return copy(steps = updatedSteps, updatedAt = System.currentTimeMillis())
    }
}
