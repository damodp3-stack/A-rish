package com.example.core.domain.world.validation

import com.example.core.domain.world.model.Goal
import com.example.core.domain.world.model.GoalProgress
import com.example.core.domain.world.model.GoalStatus

/**
 * Deterministic engine for deriving objective Goal Progress.
 * Never blindly trusts AI-generated floating point numbers.
 */
object GoalProgressCalculator {

    /**
     * Calculates the normalized progress fraction (0.0 to 1.0) and recommended status.
     */
    fun computeProgress(goal: Goal): Pair<Float, GoalStatus> {
        val fraction = when (val p = goal.progress) {
            is GoalProgress.NotStarted -> 0.0f
            is GoalProgress.DiscreteMilestones -> p.fraction
            is GoalProgress.TaskDerived -> p.fraction
            is GoalProgress.ManualAssessment -> p.fraction
        }

        val updatedStatus = when {
            goal.status.isTerminal -> goal.status
            fraction >= 1.0f -> GoalStatus.COMPLETED
            fraction > 0.0f && goal.status == GoalStatus.ACTIVE -> GoalStatus.ACTIVE
            else -> goal.status
        }

        return Pair(fraction, updatedStatus)
    }

    /**
     * Creates a verified Milestone-based progress.
     */
    fun fromMilestones(total: Int, completed: Int): GoalProgress.DiscreteMilestones {
        require(total >= 0) { "Total milestones cannot be negative" }
        val safeCompleted = completed.coerceIn(0, total)
        return GoalProgress.DiscreteMilestones(totalMilestones = total, completedMilestones = safeCompleted)
    }

    /**
     * Creates a verified Task-derived progress.
     */
    fun fromTasks(total: Int, completed: Int, failed: Int): GoalProgress.TaskDerived {
        require(total >= 0) { "Total tasks cannot be negative" }
        val safeCompleted = completed.coerceIn(0, total)
        val safeFailed = failed.coerceIn(0, total - safeCompleted)
        return GoalProgress.TaskDerived(
            totalLinkedTasks = total,
            completedTasks = safeCompleted,
            failedTasks = safeFailed
        )
    }
}
