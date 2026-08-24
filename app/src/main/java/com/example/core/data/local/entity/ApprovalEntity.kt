package com.example.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Durable ApprovalRequest entity persisting approval prompts and decisions across process death.
 */
@Entity(
    tableName = "approvals",
    indices = [
        Index(value = ["task_id"]),
        Index(value = ["step_id"])
    ]
)
data class ApprovalEntity(
    @PrimaryKey
    @ColumnInfo(name = "approval_id")
    val approvalId: String,

    @ColumnInfo(name = "task_id")
    val taskId: String,

    @ColumnInfo(name = "step_id")
    val stepId: String,

    @ColumnInfo(name = "tool_id")
    val toolId: String,

    @ColumnInfo(name = "capability_id")
    val capabilityId: String,

    @ColumnInfo(name = "risk_level")
    val riskLevel: String,

    @ColumnInfo(name = "action_summary")
    val actionSummary: String,

    @ColumnInfo(name = "preview_payload_json")
    val previewPayloadJson: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "expires_at")
    val expiresAt: Long,

    @ColumnInfo(name = "status")
    val status: String, // PENDING, APPROVED, REJECTED, EXPIRED, CANCELLED

    @ColumnInfo(name = "decision_status")
    val decisionStatus: String? = null,

    @ColumnInfo(name = "decided_by")
    val decidedBy: String? = null,

    @ColumnInfo(name = "decided_at")
    val decidedAt: Long? = null,

    @ColumnInfo(name = "decision_notes")
    val decisionNotes: String? = null
)
