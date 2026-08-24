package com.example.core.execution

import com.example.core.domain.agent.ExecutionBudget
import com.example.core.domain.error.ArishException

/**
 * Deterministic runtime context for executing a task step.
 * Enforces execution budgets and operational tracking.
 */
data class ExecutionContext(
    val taskId: String,
    val stepId: String,
    val stepIndex: Int,
    val idempotencyKey: String,
    val budget: ExecutionBudget = ExecutionBudget.STANDARD,
    val currentToolCallCount: Int = 0,
    val startTimeMs: Long = System.currentTimeMillis(),
    val environment: Map<String, Any?> = emptyMap()
) {

    fun checkBudget() {
        if (currentToolCallCount >= budget.maxToolCalls) {
            throw ArishException.BudgetExceededException(
                "Execution budget exceeded: Tool calls count ($currentToolCallCount) reached limit (${budget.maxToolCalls})"
            )
        }
        val elapsed = System.currentTimeMillis() - startTimeMs
        if (elapsed > budget.maxExecutionTimeMs) {
            throw ArishException.BudgetExceededException(
                "Execution budget exceeded: Elapsed time (${elapsed}ms) exceeded maximum (${budget.maxExecutionTimeMs}ms)"
            )
        }
    }

    fun incrementToolCall(): ExecutionContext = copy(
        currentToolCallCount = currentToolCallCount + 1
    )
}
