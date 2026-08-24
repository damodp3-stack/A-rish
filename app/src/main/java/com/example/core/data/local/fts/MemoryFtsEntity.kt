package com.example.core.data.local.fts

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey
import com.example.core.data.local.entity.MemoryEntity

/**
 * Full-Text Search Virtual Table for Lexical Retrieval on memories.
 *
 * Uses AndroidX Room SQLite FTS4 virtual table mapping directly to [MemoryEntity].
 *
 * Technical Note on Android SQLite Engine:
 * Standard Android AOSP SQLite (`android.database.sqlite.*`) compiles FTS3 and FTS4 natively.
 * AndroidX Room 2.7.0 uses @Fts4 / @Fts3 for native SQLite FTS indexing, providing full MATCH
 * token searching, prefix queries, and lexical ranking across all Android OS versions without
 * custom NDK SQLite binaries.
 */
@Fts4(contentEntity = MemoryEntity::class)
@Entity(tableName = "memories_fts")
data class MemoryFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Int,

    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "category")
    val category: String,

    @ColumnInfo(name = "importance")
    val importance: Int,

    @ColumnInfo(name = "entities_json")
    val entitiesJson: String,

    @ColumnInfo(name = "source")
    val source: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "last_accessed_at")
    val lastAccessedAt: Long,

    @ColumnInfo(name = "access_count")
    val accessCount: Int
)
