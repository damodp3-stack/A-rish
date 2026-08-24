package com.example.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * SQLite Entity enforcing database-level unique constraint on idempotency keys.
 * Prevents duplicated side effects across threads or post-crash resumptions.
 */
@Entity(
    tableName = "idempotency_records",
    indices = [
        Index(value = ["idempotency_key"], unique = true),
        Index(value = ["task_id", "step_id"])
    ]
)
data class IdempotencyEntity(
    @PrimaryKey
    @ColumnInfo(name = "idempotency_key")
    val idempotencyKey: String,

    @ColumnInfo(name = "task_id")
    val taskId: String,

    @ColumnInfo(name = "step_id")
    val stepId: String,

    @ColumnInfo(name = "tool_id")
    val toolId: String,

    @ColumnInfo(name = "arguments_hash")
    val argumentsHash: String,

    @ColumnInfo(name = "execution_status")
    val executionStatus: String,

    @ColumnInfo(name = "cached_result_json")
    val cachedResultJson: String,

    @ColumnInfo(name = "executed_at")
    val executedAt: Long
)
