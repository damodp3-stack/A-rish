package com.example.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.core.data.local.entity.CommitmentEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room Data Access Object for Commitments.
 */
@Dao
interface CommitmentDao {

    @Query("SELECT * FROM commitments WHERE id = :id")
    suspend fun getCommitmentById(id: String): CommitmentEntity?

    @Query("SELECT * FROM commitments WHERE user_id = :userId AND is_completed = 0 AND due_timestamp >= :fromTimestamp ORDER BY due_timestamp ASC LIMIT :limit")
    fun getUpcomingCommitments(userId: String, fromTimestamp: Long, limit: Int = 10): Flow<List<CommitmentEntity>>

    @Query("SELECT * FROM commitments WHERE user_id = :userId AND is_completed = 0 AND due_timestamp >= :fromTimestamp ORDER BY due_timestamp ASC LIMIT :limit")
    suspend fun getUpcomingCommitmentsList(userId: String, fromTimestamp: Long, limit: Int = 10): List<CommitmentEntity>

    @Query("SELECT * FROM commitments WHERE associated_goal_id = :goalId")
    suspend fun getCommitmentsForGoal(goalId: String): List<CommitmentEntity>

    @Query("SELECT * FROM commitments WHERE associated_project_id = :projectId")
    suspend fun getCommitmentsForProject(projectId: String): List<CommitmentEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCommitment(commitment: CommitmentEntity)

    @Update
    suspend fun updateCommitment(commitment: CommitmentEntity): Int

    @Query("DELETE FROM commitments WHERE id = :id")
    suspend fun deleteCommitment(id: String): Int
}
