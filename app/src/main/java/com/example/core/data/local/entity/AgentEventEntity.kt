package com.example.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Append-only immutable event audit log for observability, recovery, and diagnostics.
 */
@Entity(
    tableName = "agent_events",
    indices = [
        Index(value = ["task_id"]),
        Index(value = ["step_id"]),
        Index(value = ["event_type"]),
        Index(value = ["timestamp"])
    ]
)
data class AgentEventEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "event_id")
    val eventId: Long = 0,

    @ColumnInfo(name = "task_id")
    val taskId: String?,

    @ColumnInfo(name = "step_id")
    val stepId: String?,

    @ColumnInfo(name = "event_type")
    val eventType: String,

    @ColumnInfo(name = "payload_json")
    val payloadJson: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long
)
