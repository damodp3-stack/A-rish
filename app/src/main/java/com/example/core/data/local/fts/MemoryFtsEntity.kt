package com.example.core.data.local.fts

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey
import com.example.core.data.local.entity.MemoryEntity

/**
 * Full-Text Search Virtual Table for Lexical Retrieval on memories.
 * Uses AndroidX Room SQLite FTS4 virtual table mapping directly to [MemoryEntity].
 * (AndroidX Room 2.7.0 provides @Fts4 / @Fts3 for native SQLite FTS indexing;
 * SQLite FTS4 provides full MATCH token matching, prefix queries, and lexical ranking).
 */
@Fts4(contentEntity = MemoryEntity::class)
@Entity(tableName = "memories_fts")
data class MemoryFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Int,

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "entities_json")
    val entitiesJson: String
)
