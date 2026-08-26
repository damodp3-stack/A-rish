package com.example.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * SQLite Entity persisting Projects in A-RISH World Model.
 */
@Entity(
    tableName = "projects",
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["status"])
    ]
)
data class ProjectEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "description")
    val description: String,

    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "primary_goal_id")
    val primaryGoalId: String?,

    @ColumnInfo(name = "tags_json")
    val tagsJson: String,

    @ColumnInfo(name = "version")
    val version: Long = 1L,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "completed_at")
    val completedAt: Long?
)
