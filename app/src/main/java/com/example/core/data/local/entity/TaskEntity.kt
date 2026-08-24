package com.example.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * SQLite Entity persisting autonomous Agent Tasks across process death.
 */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey
    @ColumnInfo(name = "task_id")
    val taskId: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "user_prompt")
    val userPrompt: String,

    @ColumnInfo(name = "structured_intent_id")
    val structuredIntentId: String?,

    @ColumnInfo(name = "priority")
    val priority: String, // LOW, NORMAL, HIGH, CRITICAL

    @ColumnInfo(name = "state")
    val state: String, // RECEIVED, UNDERSTANDING, PLANNING, WAITING_FOR_APPROVAL, EXECUTING, VERIFYING, RECOVERING, COMPLETED, FAILED, ABORTED

    @ColumnInfo(name = "max_steps")
    val maxSteps: Int,

    @ColumnInfo(name = "max_tool_calls")
    val maxToolCalls: Int,

    @ColumnInfo(name = "max_execution_time_ms")
    val maxExecutionTimeMs: Long,

    @ColumnInfo(name = "max_retries_per_step")
    val maxRetriesPerStep: Int,

    @ColumnInfo(name = "current_step_index")
    val currentStepIndex: Int = 0,

    @ColumnInfo(name = "total_tool_calls_count")
    val totalToolCallsCount: Int = 0,

    @ColumnInfo(name = "failure_reason")
    val failureReason: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null
)
