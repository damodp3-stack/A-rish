package com.example.core.model

enum class AiState {
    IDLE,
    LISTENING,
    THINKING,
    EXECUTING_TOOL,
    SPEAKING,
    AWAITING_CONFIRMATION,
    ERROR
}

enum class AssistantLanguage(val code: String, val displayName: String, val ttsLocale: String) {
    ENGLISH("en-US", "English (US/Global)", "en_US"),
    TAMIL("ta-IN", "தமிழ் (Tamil)", "ta_IN"),
    TANGLISH("ta-IN-tanglish", "Tanglish (Tamil+English Mix)", "ta_IN")
}

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL
}

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val imageUri: String? = null,
    val toolName: String? = null,
    val toolInput: String? = null,
    val toolOutput: String? = null,
    val isStreaming: Boolean = false,
    val isError: Boolean = false
)

enum class MemoryCategory(val displayName: String, val iconName: String) {
    USER_PREFERENCE("User Preferences", "tune"),
    FACT("Personal Facts", "person"),
    PROJECT("Projects & Goals", "folder"),
    TASK("Routines & Tasks", "check_circle"),
    IDENTITY("Identity & Lore", "smart_toy"),
    GENERAL("General Knowledge", "psychology")
}

data class MemoryItem(
    val id: Long = 0,
    val key: String,
    val value: String,
    val category: MemoryCategory = MemoryCategory.GENERAL,
    val importance: Int = 3, // 1 to 5
    val createdAt: Long = System.currentTimeMillis(),
    val source: String = "Conversation",
    val requiresApproval: Boolean = false
)

enum class ToolRiskLevel(val label: String) {
    LOW("Safe / Auto-Run"),
    MEDIUM("Caution / Moderate"),
    HIGH("Sensitive / Needs Approval")
}

data class ToolDefinition(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val riskLevel: ToolRiskLevel,
    val requiresConfirmation: Boolean = riskLevel == ToolRiskLevel.HIGH,
    val isEnabled: Boolean = true,
    val iconName: String = "build"
)

enum class TaskStatus {
    PENDING,
    PLANNING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class SubTask(
    val id: String,
    val title: String,
    val description: String = "",
    val toolRequired: String? = null,
    val status: TaskStatus = TaskStatus.PENDING,
    val output: String? = null,
    val error: String? = null
)

data class AgentTask(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val goal: String,
    val status: TaskStatus = TaskStatus.PENDING,
    val progress: Float = 0f,
    val subtasks: List<SubTask> = emptyList(),
    val finalOutput: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

data class ToolExecutionRecord(
    val id: Long = 0,
    val taskId: String? = null,
    val toolId: String,
    val toolName: String,
    val inputArgs: String,
    val outputResult: String,
    val status: String = "SUCCESS",
    val timestamp: Long = System.currentTimeMillis(),
    val executionTimeMs: Long = 0
)

data class ResearchSource(
    val title: String,
    val url: String,
    val snippet: String,
    val sourceName: String = "Web"
)

data class ResearchSession(
    val id: String = java.util.UUID.randomUUID().toString(),
    val query: String,
    val status: TaskStatus = TaskStatus.PENDING,
    val currentStep: String = "Initialized",
    val progress: Float = 0f,
    val sources: List<ResearchSource> = emptyList(),
    val structuredReport: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class AiModelInfo(
    val id: String,
    val name: String,
    val provider: String,
    val description: String,
    val isLocal: Boolean,
    val isAvailable: Boolean = true,
    val sizeOnDisk: String = "Cloud",
    val contextWindow: String = "1M tokens",
    val isDefault: Boolean = false
)
