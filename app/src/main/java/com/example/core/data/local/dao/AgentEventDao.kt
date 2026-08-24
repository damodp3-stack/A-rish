package com.example.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.core.data.local.entity.AgentEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentEventDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEvent(event: AgentEventEntity)

    @Query("SELECT * FROM agent_events WHERE task_id = :taskId ORDER BY timestamp ASC")
    suspend fun getEventsForTask(taskId: String): List<AgentEventEntity>

    @Query("SELECT * FROM agent_events WHERE event_type = :eventType ORDER BY timestamp ASC")
    suspend fun getEventsByType(eventType: String): List<AgentEventEntity>

    @Query("SELECT * FROM agent_events ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecentEvents(limit: Int = 100): Flow<List<AgentEventEntity>>

    @Query("DELETE FROM agent_events WHERE timestamp < :cutoffTimestamp")
    suspend fun pruneOldEvents(cutoffTimestamp: Long): Int
}
