package com.example.core.data.repository

import com.example.core.data.local.dao.GoalDao
import com.example.core.data.local.mapper.WorldModelMappers.toDomain
import com.example.core.data.local.mapper.WorldModelMappers.toEntity
import com.example.core.domain.time.TimeProvider
import com.example.core.domain.world.identity.UserId
import com.example.core.domain.world.model.Goal
import com.example.core.domain.world.model.GoalProgress
import com.example.core.domain.world.model.GoalStatus
import com.example.core.domain.world.repository.GoalRepository
import com.example.core.domain.world.validation.GoalHierarchyValidator
import com.example.core.domain.world.validation.GoalProgressCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DefaultGoalRepository(
    private val goalDao: GoalDao,
    private val timeProvider: TimeProvider,
    private val hierarchyValidator: GoalHierarchyValidator = GoalHierarchyValidator()
) : GoalRepository {

    override suspend fun getGoal(id: String): Goal? {
        return goalDao.getGoalById(id)?.toDomain()
    }

    override fun observeActiveGoals(userId: UserId, limit: Int): Flow<List<Goal>> {
        return goalDao.getActiveGoals(userId.value, limit).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getGoalsByStatus(userId: UserId, status: GoalStatus): List<Goal> {
        return goalDao.getGoalsByStatus(userId.value, status.name).map { it.toDomain() }
    }

    override suspend fun getAllGoals(userId: UserId): List<Goal> {
        return goalDao.getAllGoals(userId.value).map { it.toDomain() }
    }

    override suspend fun createGoal(goal: Goal): Result<Unit> = runCatching {
        // 1. Hierarchy validation
        if (goal.parentGoalId != null) {
            val existingGoals = goalDao.getAllGoals(goal.userId.value).associate { it.id to it.toDomain() }
            when (val validation = hierarchyValidator.validateHierarchy(goal, existingGoals)) {
                is GoalHierarchyValidator.ValidationResult.Valid -> Unit
                is GoalHierarchyValidator.ValidationResult.SelfParenting ->
                    throw IllegalArgumentException("Goal cannot be its own parent: ${validation.goalId}")
                is GoalHierarchyValidator.ValidationResult.CircularReference ->
                    throw IllegalStateException("Circular hierarchy detected: ${validation.cyclePath.joinToString(" -> ")}")
                is GoalHierarchyValidator.ValidationResult.MaxDepthExceeded ->
                    throw IllegalStateException("Goal hierarchy max depth (${validation.maxDepth}) exceeded by ${validation.goalId}")
                is GoalHierarchyValidator.ValidationResult.ParentNotFound ->
                    throw IllegalArgumentException("Parent goal ${validation.missingParentId} not found for goal ${validation.goalId}")
            }
        }

        val entity = goal.toEntity()
        goalDao.insertGoal(entity)
    }

    override suspend fun updateGoal(goal: Goal): Result<Unit> = runCatching {
        // 1. Hierarchy validation
        if (goal.parentGoalId != null) {
            val existingGoals = goalDao.getAllGoals(goal.userId.value).associate { it.id to it.toDomain() }
            when (val validation = hierarchyValidator.validateHierarchy(goal, existingGoals)) {
                is GoalHierarchyValidator.ValidationResult.Valid -> Unit
                is GoalHierarchyValidator.ValidationResult.SelfParenting ->
                    throw IllegalArgumentException("Goal cannot be its own parent: ${validation.goalId}")
                is GoalHierarchyValidator.ValidationResult.CircularReference ->
                    throw IllegalStateException("Circular hierarchy detected: ${validation.cyclePath.joinToString(" -> ")}")
                is GoalHierarchyValidator.ValidationResult.MaxDepthExceeded ->
                    throw IllegalStateException("Goal hierarchy max depth (${validation.maxDepth}) exceeded by ${validation.goalId}")
                is GoalHierarchyValidator.ValidationResult.ParentNotFound ->
                    throw IllegalArgumentException("Parent goal ${validation.missingParentId} not found for goal ${validation.goalId}")
            }
        }

        val updatedEntity = goal.toEntity()
        val expectedVersion = goal.version
        val newVersion = expectedVersion + 1L

        val rowsUpdated = goalDao.updateGoalWithVersion(
            id = goal.id,
            expectedVersion = expectedVersion,
            newVersion = newVersion,
            title = updatedEntity.title,
            description = updatedEntity.description,
            status = updatedEntity.status,
            priority = updatedEntity.priority,
            parentGoalId = updatedEntity.parentGoalId,
            targetDeadline = updatedEntity.targetDeadline,
            progressType = updatedEntity.progressType,
            milestonesTotal = updatedEntity.progressMilestonesTotal,
            milestonesCompleted = updatedEntity.progressMilestonesCompleted,
            manualPercentage = updatedEntity.progressManualPercentage,
            manualReasoning = updatedEntity.progressManualReasoning,
            constraintsJson = updatedEntity.constraintsJson,
            confidenceScore = updatedEntity.confidenceScore,
            updatedAt = timeProvider.currentTimeMillis(),
            completedAt = updatedEntity.completedAt
        )

        if (rowsUpdated == 0) {
            throw IllegalStateException("Optimistic lock conflict on Goal '${goal.id}'. Expected version $expectedVersion")
        }
    }

    override suspend fun updateGoalProgress(goalId: String, progress: GoalProgress): Result<Goal> = runCatching {
        val existing = goalDao.getGoalById(goalId)?.toDomain()
            ?: throw NoSuchElementException("Goal not found: $goalId")

        val updatedGoal = existing.copy(
            progress = progress,
            updatedAt = timeProvider.currentTimeMillis()
        )

        val (_, derivedStatus) = GoalProgressCalculator.computeProgress(updatedGoal)
        val finalGoal = updatedGoal.copy(
            status = derivedStatus,
            completedAt = if (derivedStatus == GoalStatus.COMPLETED) timeProvider.currentTimeMillis() else null
        )

        updateGoal(finalGoal).getOrThrow()
        finalGoal.copy(version = finalGoal.version + 1L)
    }

    override suspend fun deleteGoal(id: String): Result<Unit> = runCatching {
        val deleted = goalDao.deleteGoal(id)
        if (deleted == 0) {
            throw NoSuchElementException("Goal not found for deletion: $id")
        }
    }
}
