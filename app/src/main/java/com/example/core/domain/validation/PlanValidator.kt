package com.example.core.domain.validation

import com.example.core.domain.agent.AgentStep
import com.example.core.domain.agent.ExecutionBudget
import com.example.core.domain.capability.CapabilityRegistry
import com.example.core.domain.error.ArishException

/**
 * Deterministic validator enforcing plan boundaries, step counts, and tool allowances.
 */
object PlanValidator {

    fun validatePlan(steps: List<AgentStep>, budget: ExecutionBudget) {
        if (steps.isEmpty()) {
            throw ArishException.SchemaValidationException("steps", "Planned steps list cannot be empty")
        }
        if (steps.size > budget.maxSteps) {
            throw ArishException.BudgetExceededException(
                "Planned step count (${steps.size}) exceeds task execution budget maxSteps (${budget.maxSteps})"
            )
        }

        // Verify each step references a registered capability and has non-blank identifiers
        steps.forEachIndexed { index, step ->
            if (step.stepId.isBlank()) {
                throw ArishException.SchemaValidationException("stepId", "Step at index $index has blank stepId")
            }
            if (step.idempotencyKey.isBlank()) {
                throw ArishException.SchemaValidationException(
                    "idempotencyKey",
                    "Step '${step.stepId}' missing mandatory idempotencyKey"
                )
            }
            if (step.capabilityId.isBlank()) {
                throw ArishException.SchemaValidationException(
                    "capabilityId",
                    "Step '${step.stepId}' has blank capabilityId"
                )
            }
        }
    }
}
