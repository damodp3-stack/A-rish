package com.example.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.core.data.local.entity.MemoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Memory Vault Data Access Object with SQLite FTS lexical retrieval and token matching.
 */
@Dao
interface MemoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemories(memories: List<MemoryEntity>)

    @Update
    suspend fun updateMemory(memory: MemoryEntity)

    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun getMemoryById(id: String): MemoryEntity?

    @Query("SELECT * FROM memories ORDER BY last_accessed_at DESC")
    fun observeAllMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories ORDER BY importance DESC, last_accessed_at DESC LIMIT :limit")
    suspend fun getTopImportantMemories(limit: Int): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE category = :category ORDER BY importance DESC")
    suspend fun getMemoriesByCategory(category: String): List<MemoryEntity>

    /**
     * Lexical Full-Text Search query joining the FTS virtual table with the base memories table.
     */
    @Query("""
        SELECT memories.* FROM memories
        JOIN memories_fts ON memories.rowid = memories_fts.rowid
        WHERE memories_fts MATCH :query
    """)
    suspend fun searchMemoriesLexical(query: String): List<MemoryEntity>

    @Query("UPDATE memories SET last_accessed_at = :timestamp, access_count = access_count + 1 WHERE id = :id")
    suspend fun recordAccess(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteMemoryById(id: String)

    @Query("DELETE FROM memories")
    suspend fun deleteAllMemories()
}
