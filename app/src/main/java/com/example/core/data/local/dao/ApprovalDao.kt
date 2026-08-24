package com.example.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.core.data.local.entity.ApprovalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ApprovalDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApproval(approval: ApprovalEntity)

    @Update
    suspend fun updateApproval(approval: ApprovalEntity)

    @Query("SELECT * FROM approvals WHERE approval_id = :approvalId")
    suspend fun getApprovalById(approvalId: String): ApprovalEntity?

    @Query("SELECT * FROM approvals WHERE step_id = :stepId LIMIT 1")
    suspend fun getApprovalForStep(stepId: String): ApprovalEntity?

    @Query("SELECT * FROM approvals WHERE status = 'PENDING' AND expires_at > :currentTime ORDER BY created_at ASC")
    fun observePendingApprovals(currentTime: Long = System.currentTimeMillis()): Flow<List<ApprovalEntity>>

    @Query("UPDATE approvals SET status = 'EXPIRED' WHERE status = 'PENDING' AND expires_at <= :currentTime")
    suspend fun expireOldApprovals(currentTime: Long = System.currentTimeMillis()): Int
}
