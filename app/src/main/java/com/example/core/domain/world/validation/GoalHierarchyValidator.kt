package com.example.core.domain.world.validation

import com.example.core.domain.world.model.Goal

/**
 * Validates Goal parent-child hierarchy to guarantee:
 * 1. No self-parenting (Goal.parentGoalId != Goal.id)
 * 2. No circular hierarchy loops (A -> B -> C -> A)
 * 3. Max hierarchy depth enforcement (depth <= 5)
 * 4. Safe relationship structure
 */
class GoalHierarchyValidator(
    private val maxAllowedDepth: Int = 5
) {
    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class SelfParenting(val goalId: String) : ValidationResult()
        data class CircularReference(val cyclePath: List<String>) : ValidationResult()
        data class MaxDepthExceeded(val goalId: String, val depth: Int, val maxDepth: Int) : ValidationResult()
        data class ParentNotFound(val goalId: String, val missingParentId: String) : ValidationResult()
    }

    /**
     * Validates a candidate goal update or creation against the existing goals lookup table.
     */
    fun validateHierarchy(
        candidateGoal: Goal,
        existingGoals: Map<String, Goal>
    ): ValidationResult {
        val parentId = candidateGoal.parentGoalId ?: return ValidationResult.Valid

        // Invariant 1: Self-parenting
        if (parentId == candidateGoal.id) {
            return ValidationResult.SelfParenting(candidateGoal.id)
        }

        // Invariant 2: Cycle and depth detection
        val visited = mutableSetOf<String>()
        val path = mutableListOf(candidateGoal.id)
        var currentParentId: String? = parentId
        var depth = 1

        while (currentParentId != null) {
            if (currentParentId == candidateGoal.id) {
                path.add(currentParentId)
                return ValidationResult.CircularReference(path)
            }

            if (visited.contains(currentParentId)) {
                path.add(currentParentId)
                return ValidationResult.CircularReference(path)
            }

            visited.add(currentParentId)
            path.add(currentParentId)
            depth++

            if (depth > maxAllowedDepth) {
                return ValidationResult.MaxDepthExceeded(candidateGoal.id, depth, maxAllowedDepth)
            }

            val parentGoal = existingGoals[currentParentId]
            if (parentGoal == null) {
                // If parent is not in the active lookup, check if it's expected
                return ValidationResult.ParentNotFound(candidateGoal.id, currentParentId)
            }

            currentParentId = parentGoal.parentGoalId
        }

        return ValidationResult.Valid
    }
}
