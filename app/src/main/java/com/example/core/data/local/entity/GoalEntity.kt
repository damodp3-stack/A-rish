package com.example.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * SQLite Entity persisting Goal models in A-RISH World Model.
 */
@Entity(
    tableName = "goals",
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["status"]),
        Index(value = ["parent_goal_id"])
    ]
)
data class GoalEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "description")
    val description: String,

    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "priority")
    val priority: String,

    @ColumnInfo(name = "parent_goal_id")
    val parentGoalId: String?,

    @ColumnInfo(name = "target_deadline")
    val targetDeadline: Long?,

    @ColumnInfo(name = "progress_type")
    val progressType: String,

    @ColumnInfo(name = "progress_milestones_total")
    val progressMilestonesTotal: Int,

    @ColumnInfo(name = "progress_milestones_completed")
    val progressMilestonesCompleted: Int,

    @ColumnInfo(name = "progress_manual_percentage")
    val progressManualPercentage: Int = 0,

    @ColumnInfo(name = "progress_manual_reasoning")
    val progressManualReasoning: String? = null,

    @ColumnInfo(name = "constraints_json")
    val constraintsJson: String,

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

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "completed_at")
    val completedAt: Long?
)
