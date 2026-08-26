package com.example.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * SQLite Junction Entity establishing relational many-to-many links between Goals and Projects.
 */
@Entity(
    tableName = "goal_project_links",
    primaryKeys = ["goal_id", "project_id"],
    foreignKeys = [
        ForeignKey(
            entity = GoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goal_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION
        ),
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION
        )
    ],
    indices = [
        Index(value = ["goal_id"]),
        Index(value = ["project_id"])
    ]
)
data class GoalProjectLinkEntity(
    @ColumnInfo(name = "goal_id")
    val goalId: String,

    @ColumnInfo(name = "project_id")
    val projectId: String,

    @ColumnInfo(name = "linked_at")
    val linkedAt: Long
)
