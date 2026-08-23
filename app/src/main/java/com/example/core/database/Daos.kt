package com.example.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY importance DESC, createdAt DESC")
    fun getAllMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE category = :category ORDER BY importance DESC")
    fun getMemoriesByCategory(category: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE `key` LIKE '%' || :query || '%' OR `value` LIKE '%' || :query || '%'")
    suspend fun searchMemories(query: String): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity): Long

    @Update
    suspend fun updateMemory(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteMemoryById(id: Long)

    @Query("DELETE FROM memories")
    suspend fun deleteAllMemories()
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY lastUpdatedAt DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversationById(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity)

    @Query("UPDATE conversations SET lastUpdatedAt = :timestamp WHERE id = :id")
    suspend fun updateLastTimestamp(id: String, timestamp: Long)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)

    @Query("DELETE FROM conversations")
    suspend fun deleteAll()
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getMessagesList(conversationId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: String)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessageById(id: String)
}

@Dao
interface AgentTaskDao {
    @Query("SELECT * FROM agent_tasks ORDER BY createdAt DESC")
    fun getAllTasks(): Flow<List<AgentTaskEntity>>

    @Query("SELECT * FROM agent_tasks WHERE id = :id")
    suspend fun getTaskById(id: String): AgentTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: AgentTaskEntity)

    @Update
    suspend fun updateTask(task: AgentTaskEntity)

    @Query("DELETE FROM agent_tasks WHERE id = :id")
    suspend fun deleteTask(id: String)

    @Query("DELETE FROM agent_tasks")
    suspend fun deleteAllTasks()
}

@Dao
interface ToolExecutionDao {
    @Query("SELECT * FROM tool_executions ORDER BY timestamp DESC LIMIT 100")
    fun getRecentExecutions(): Flow<List<ToolExecutionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExecution(execution: ToolExecutionEntity): Long

    @Query("DELETE FROM tool_executions")
    suspend fun clearLogs()
}

@Dao
interface ResearchDao {
    @Query("SELECT * FROM research_sessions ORDER BY createdAt DESC")
    fun getAllSessions(): Flow<List<ResearchSessionEntity>>

    @Query("SELECT * FROM research_sessions WHERE id = :id")
    suspend fun getSessionById(id: String): ResearchSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ResearchSessionEntity)

    @Update
    suspend fun updateSession(session: ResearchSessionEntity)

    @Query("DELETE FROM research_sessions WHERE id = :id")
    suspend fun deleteSession(id: String)
}
