package com.example.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.core.data.local.entity.GoalEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room Data Access Object for Goals with Optimistic Concurrency Control.
 */
@Dao
interface GoalDao {

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun getGoalById(id: String): GoalEntity?

    @Query("SELECT * FROM goals WHERE user_id = :userId AND status = 'ACTIVE' ORDER BY priority DESC, created_at DESC LIMIT :limit")
    fun getActiveGoals(userId: String, limit: Int = 10): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE user_id = :userId AND status = :status ORDER BY created_at DESC")
    suspend fun getGoalsByStatus(userId: String, status: String): List<GoalEntity>

    @Query("SELECT * FROM goals WHERE user_id = :userId ORDER BY created_at DESC")
    suspend fun getAllGoals(userId: String): List<GoalEntity>

    @Query("SELECT * FROM goals WHERE parent_goal_id = :parentGoalId")
    suspend fun getSubGoals(parentGoalId: String): List<GoalEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGoal(goal: GoalEntity)

    @Update
    suspend fun updateGoal(goal: GoalEntity): Int

    @Query("""
        UPDATE goals 
        SET title = :title, 
            description = :description, 
            status = :status, 
            priority = :priority, 
            parent_goal_id = :parentGoalId, 
            target_deadline = :targetDeadline, 
            progress_type = :progressType, 
            progress_milestones_total = :milestonesTotal, 
            progress_milestones_completed = :milestonesCompleted, 
            progress_manual_percentage = :manualPercentage,
            progress_manual_reasoning = :manualReasoning,
            constraints_json = :constraintsJson, 
            confidence_score = :confidenceScore, 
            version = :newVersion, 
            updated_at = :updatedAt, 
            completed_at = :completedAt 
        WHERE id = :id AND version = :expectedVersion
    """)
    suspend fun updateGoalWithVersion(
        id: String,
        expectedVersion: Long,
        newVersion: Long,
        title: String,
        description: String,
        status: String,
        priority: String,
        parentGoalId: String?,
        targetDeadline: Long?,
        progressType: String,
        milestonesTotal: Int,
        milestonesCompleted: Int,
        manualPercentage: Int,
        manualReasoning: String?,
        constraintsJson: String,
        confidenceScore: Float,
        updatedAt: Long,
        completedAt: Long?
    ): Int

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun deleteGoal(id: String): Int

    @Query("DELETE FROM goals WHERE user_id = :userId")
    suspend fun deleteAllGoalsForUser(userId: String): Int
}
