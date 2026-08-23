package com.example.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.core.model.MemoryCategory
import com.example.core.model.MessageRole
import com.example.core.model.TaskStatus

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val value: String,
    val category: String = MemoryCategory.GENERAL.name,
    val importance: Int = 3,
    val createdAt: Long = System.currentTimeMillis(),
    val source: String = "Conversation",
    val requiresApproval: Boolean = false
)

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUpdatedAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val imageUri: String? = null,
    val toolName: String? = null,
    val toolInput: String? = null,
    val toolOutput: String? = null,
    val isError: Boolean = false
)

@Entity(tableName = "agent_tasks")
data class AgentTaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val goal: String,
    val status: String = TaskStatus.PENDING.name,
    val progress: Float = 0f,
    val subtasksJson: String = "[]",
    val finalOutput: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

@Entity(tableName = "tool_executions")
data class ToolExecutionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: String? = null,
    val toolId: String,
    val toolName: String,
    val inputArgs: String,
    val outputResult: String,
    val status: String = "SUCCESS",
    val timestamp: Long = System.currentTimeMillis(),
    val executionTimeMs: Long = 0
)

@Entity(tableName = "research_sessions")
data class ResearchSessionEntity(
    @PrimaryKey val id: String,
    val query: String,
    val status: String = TaskStatus.PENDING.name,
    val currentStep: String = "Initialized",
    val progress: Float = 0f,
    val sourcesJson: String = "[]",
    val structuredReport: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
