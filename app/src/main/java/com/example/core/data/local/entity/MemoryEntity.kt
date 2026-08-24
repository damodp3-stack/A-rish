package com.example.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * SQLite Entity persisting memory records for hybrid retrieval ranking.
 */
@Entity(
    tableName = "memories",
    indices = [
        Index(value = ["category"]),
        Index(value = ["importance"])
    ]
)
data class MemoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "category")
    val category: String, // IDENTITY, PREFERENCE, WORK, SYSTEM, FACT, CONVERSATION

    @ColumnInfo(name = "importance")
    val importance: Int,

    @ColumnInfo(name = "entities_json")
    val entitiesJson: String, // Serialized List<MemoryEntityRef>

    @ColumnInfo(name = "source")
    val source: String, // USER_EXPLICIT, AUTOMATED_EXTRACTION, SYSTEM_INFERRED

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "last_accessed_at")
    val lastAccessedAt: Long,

    @ColumnInfo(name = "access_count")
    val accessCount: Int = 1
)
