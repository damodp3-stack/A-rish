package com.example.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * SQLite Entity persisting Commitments in A-RISH World Model.
 */
@Entity(
    tableName = "commitments",
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["due_timestamp"]),
        Index(value = ["associated_project_id"]),
        Index(value = ["associated_goal_id"])
    ]
)
data class CommitmentEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "description")
    val description: String,

    @ColumnInfo(name = "due_timestamp")
    val dueTimestamp: Long,

    @ColumnInfo(name = "associated_project_id")
    val associatedProjectId: String?,

    @ColumnInfo(name = "associated_goal_id")
    val associatedGoalId: String?,

    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean,

    @ColumnInfo(name = "provenance_source")
    val provenanceSource: String,

    @ColumnInfo(name = "confidence_score")
    val confidenceScore: Float,

    @ColumnInfo(name = "valid_from")
    val validFrom: Long,

    @ColumnInfo(name = "valid_until")
    val validUntil: Long?,

    @ColumnInfo(name = "version")
    val version: Long = 1L,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "completed_at")
    val completedAt: Long?
)
