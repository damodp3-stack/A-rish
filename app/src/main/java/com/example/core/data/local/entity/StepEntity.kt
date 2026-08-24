package com.example.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * SQLite Entity persisting individual planned steps of a task with Foreign Key to tasks table.
 */
@Entity(
    tableName = "steps",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["task_id"],
            childColumns = ["task_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["task_id"]),
        Index(value = ["idempotency_key"], unique = true)
    ]
)
data class StepEntity(
    @PrimaryKey
    @ColumnInfo(name = "step_id")
    val stepId: String,

    @ColumnInfo(name = "task_id")
    val taskId: String,

    @ColumnInfo(name = "step_index")
    val stepIndex: Int,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "description")
    val description: String,

    @ColumnInfo(name = "capability_id")
    val capabilityId: String,

    @ColumnInfo(name = "tool_id")
    val toolId: String,

    @ColumnInfo(name = "input_arguments_json")
    val inputArgumentsJson: String,

    @ColumnInfo(name = "risk_level")
    val riskLevel: String,

    @ColumnInfo(name = "risk_reasons_json")
    val riskReasonsJson: String,

    @ColumnInfo(name = "status")
    val status: String, // REQUESTED, DISPATCHED, EXECUTED, VERIFIED, PARTIALLY_VERIFIED, UNKNOWN, FAILED, ABORTED

    @ColumnInfo(name = "idempotency_key")
    val idempotencyKey: String,

    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,

    @ColumnInfo(name = "approval_id")
    val approvalId: String? = null,

    @ColumnInfo(name = "outcome_summary")
    val outcomeSummary: String? = null,

    @ColumnInfo(name = "outcome_data_json")
    val outcomeDataJson: String? = null,

    @ColumnInfo(name = "side_effect_semantics")
    val sideEffectSemantics: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "started_at")
    val startedAt: Long? = null,

    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null
)
