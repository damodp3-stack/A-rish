package com.example.core.domain.world.model

import com.example.core.domain.world.identity.UserId

enum class GoalStatus {
    ACTIVE,
    PAUSED,
    COMPLETED,
    CANCELLED,
    ARCHIVED;

    val isTerminal: Boolean
        get() = this == COMPLETED || this == CANCELLED || this == ARCHIVED
}

enum class GoalPriority {
    LOW,
    NORMAL,
    HIGH,
    CRITICAL
}

/**
 * Objective, verified representation of progress towards a Goal.
 * Progress is a derived state, NOT an arbitrary AI-set float.
 */
sealed class GoalProgress {
    abstract val fraction: Float

    object NotStarted : GoalProgress() {
        override val fraction: Float = 0.0f
    }

    data class DiscreteMilestones(
        val totalMilestones: Int,
        val completedMilestones: Int
    ) : GoalProgress() {
        init {
            require(totalMilestones >= 0) { "totalMilestones cannot be negative" }
            require(completedMilestones in 0..totalMilestones) {
                "completedMilestones ($completedMilestones) must be in 0..$totalMilestones"
            }
        }

        override val fraction: Float
            get() = if (totalMilestones == 0) 0.0f else (completedMilestones.toFloat() / totalMilestones.toFloat()).coerceIn(0.0f, 1.0f)
    }

    data class TaskDerived(
        val totalLinkedTasks: Int,
        val completedTasks: Int,
        val failedTasks: Int
    ) : GoalProgress() {
        init {
            require(totalLinkedTasks >= 0) { "totalLinkedTasks cannot be negative" }
            require(completedTasks >= 0) { "completedTasks cannot be negative" }
            require(failedTasks >= 0) { "failedTasks cannot be negative" }
            require(completedTasks + failedTasks <= totalLinkedTasks) {
                "completed ($completedTasks) + failed ($failedTasks) cannot exceed total ($totalLinkedTasks)"
            }
        }

        override val fraction: Float
            get() = if (totalLinkedTasks == 0) 0.0f else (completedTasks.toFloat() / totalLinkedTasks.toFloat()).coerceIn(0.0f, 1.0f)
    }

    data class ManualAssessment(
        val percentage: Int, // 0 to 100
        val reasoning: String,
        val assessedAt: Long,
        val assessorProvenance: EpistemicProvenance
    ) : GoalProgress() {
        init {
            require(percentage in 0..100) { "percentage must be between 0 and 100" }
            require(reasoning.isNotBlank()) { "reasoning must be provided for manual assessment" }
        }

        override val fraction: Float
            get() = (percentage.toFloat() / 100.0f).coerceIn(0.0f, 1.0f)
    }
}

/**
 * Version-safe structured Goal Constraints.
 * System-enforceable constraints vs Informational Context.
 * Informational context is explicitly non-executable and cannot bypass security.
 */
sealed class GoalConstraint {
    abstract val typeName: String

    data class AbsoluteDeadline(val epochMillis: Long) : GoalConstraint() {
        override val typeName: String = "DEADLINE"
    }

    data class RequiredDeviceCapability(val capabilityId: String) : GoalConstraint() {
        override val typeName: String = "CAPABILITY"
    }

    data class TimeWindow(val startHourUtc: Int, val endHourUtc: Int) : GoalConstraint() {
        override val typeName: String = "TIME_WINDOW"
        init {
            require(startHourUtc in 0..23) { "startHourUtc must be 0..23" }
            require(endHourUtc in 0..23) { "endHourUtc must be 0..23" }
        }
    }

    data class EntityDependency(val canonicalEntityId: String) : GoalConstraint() {
        override val typeName: String = "ENTITY_DEPENDENCY"
    }

    /**
     * Non-executable descriptive user context.
     * Guaranteed never to be interpreted as system policy or security bypass.
     */
    data class InformationalContext(
        val key: String,
        val description: String
    ) : GoalConstraint() {
        override val typeName: String = "INFORMATIONAL"
        val isExecutablePolicy: Boolean = false
    }

    /**
     * Fallback for forward compatibility when newer constraint types are encountered.
     */
    data class UnknownConstraint(
        override val typeName: String,
        val rawData: String
    ) : GoalConstraint()
}

/**
 * Authoritative Goal Domain Model in A-RISH World Model.
 */
data class Goal(
    val id: String,
    val userId: UserId,
    val title: String,
    val description: String,
    val status: GoalStatus = GoalStatus.ACTIVE,
    val priority: GoalPriority = GoalPriority.NORMAL,
    val parentGoalId: String? = null,
    val targetDeadline: Long? = null,
    val progress: GoalProgress = GoalProgress.NotStarted,
    val constraints: List<GoalConstraint> = emptyList(),
    val provenance: EpistemicProvenance,
    val version: Long = 1L,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long? = null
) {
    init {
        require(id.isNotBlank()) { "Goal id cannot be blank" }
        require(title.isNotBlank()) { "Goal title cannot be blank" }
        require(parentGoalId != id) { "Goal cannot be its own parent (self-parenting detected for id: $id)" }
        require(version >= 1L) { "Version must be >= 1" }
    }

    fun isCompleted(): Boolean = status == GoalStatus.COMPLETED

    fun isExpired(currentTimeMillis: Long): Boolean {
        val deadline = targetDeadline ?: return false
        return currentTimeMillis > deadline && !status.isTerminal
    }
}
