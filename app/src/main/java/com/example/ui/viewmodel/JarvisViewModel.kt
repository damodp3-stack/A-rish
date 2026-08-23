package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.agent.JarvisAgentOrchestrator
import com.example.core.ai.JarvisAiEngine
import com.example.core.database.ConversationEntity
import com.example.core.database.JarvisDatabase
import com.example.core.database.MessageEntity
import com.example.core.memory.MemoryManager
import com.example.core.model.AgentTask
import com.example.core.model.AiState
import com.example.core.model.AssistantLanguage
import com.example.core.model.ChatMessage
import com.example.core.model.MemoryCategory
import com.example.core.model.MemoryItem
import com.example.core.model.MessageRole
import com.example.core.model.ResearchSession
import com.example.core.model.ToolDefinition
import com.example.core.model.ToolExecutionRecord
import com.example.core.research.ResearchEngine
import com.example.core.settings.JarvisPreferences
import com.example.core.tools.ToolCatalog
import com.example.core.tools.ToolExecutor
import com.example.core.voice.JarvisVoiceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class ConfirmationAction(
    val title: String,
    val description: String,
    val toolId: String,
    val args: Map<String, Any?>,
    val onConfirm: () -> Unit
)

data class JarvisUiState(
    val aiState: AiState = AiState.IDLE,
    val currentConversationId: String = "default_session",
    val messages: List<ChatMessage> = emptyList(),
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val speechAmplitude: Float = 0f,
    val recognizedSpeechText: String = "",
    val activeToolName: String? = null,
    val language: AssistantLanguage = AssistantLanguage.ENGLISH,
    val selectedModel: String = "gemini-3.5-flash",
    val isOfflineMode: Boolean = false,
    val isDeveloperMode: Boolean = true,
    val customApiKey: String = "",
    val pendingConfirmation: ConfirmationAction? = null,
    val availableTools: List<ToolDefinition> = ToolCatalog.allTools,
    val speechRate: Float = 1.0f,
    val speechPitch: Float = 1.0f,
    val isAutoSpeak: Boolean = true
)

class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    private val db = JarvisDatabase.getInstance(application)
    private val preferences = JarvisPreferences(application)
    private val toolExecutor = ToolExecutor(application)
    private val memoryManager = MemoryManager(db.memoryDao())
    private val agentOrchestrator = JarvisAgentOrchestrator(db.agentTaskDao(), db.toolExecutionDao(), toolExecutor)
    private val researchEngine = ResearchEngine(db.researchDao())
    private val aiEngine = JarvisAiEngine(application, memoryManager, toolExecutor)
    private val voiceManager = JarvisVoiceManager(application)

    private val _aiState = MutableStateFlow(AiState.IDLE)
    private val _activeToolName = MutableStateFlow<String?>(null)
    private val _pendingConfirmation = MutableStateFlow<ConfirmationAction?>(null)
    private val _currentConversationId = MutableStateFlow("default_session")

    val memories: StateFlow<List<MemoryItem>> = memoryManager.allMemories.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val agentTasks: StateFlow<List<AgentTask>> = agentOrchestrator.allTasks.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val researchSessions: StateFlow<List<ResearchSession>> = researchEngine.allSessions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val toolLogs: StateFlow<List<ToolExecutionRecord>> = db.toolExecutionDao().getRecentExecutions().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    ).combine(MutableStateFlow(Unit)) { entities, _ ->
        entities.map {
            ToolExecutionRecord(
                id = it.id,
                taskId = it.taskId,
                toolId = it.toolId,
                toolName = it.toolName,
                inputArgs = it.inputArgs,
                outputResult = it.outputResult,
                status = it.status,
                timestamp = it.timestamp,
                executionTimeMs = it.executionTimeMs
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())

    val uiState: StateFlow<JarvisUiState> = combine(
        _aiState,
        _chatMessages,
        voiceManager.isListening,
        voiceManager.isSpeaking,
        voiceManager.speechAmplitude,
        voiceManager.recognizedText,
        _activeToolName,
        preferences.selectedLanguage,
        preferences.selectedModel,
        preferences.isOfflineMode,
        preferences.isDeveloperMode,
        preferences.customApiKey,
        _pendingConfirmation
    ) { values ->
        JarvisUiState(
            aiState = values[0] as AiState,
            currentConversationId = _currentConversationId.value,
            messages = values[1] as List<ChatMessage>,
            isListening = values[2] as Boolean,
            isSpeaking = values[3] as Boolean,
            speechAmplitude = values[4] as Float,
            recognizedSpeechText = values[5] as String,
            activeToolName = values[6] as String?,
            language = values[7] as AssistantLanguage,
            selectedModel = values[8] as String,
            isOfflineMode = values[9] as Boolean,
            isDeveloperMode = values[10] as Boolean,
            customApiKey = values[11] as String,
            pendingConfirmation = values[12] as ConfirmationAction?
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = JarvisUiState()
    )

    init {
        viewModelScope.launch {
            memoryManager.seedInitialDefaults()
            initDefaultConversation()
            loadConversationMessages(_currentConversationId.value)
        }
    }

    private suspend fun initDefaultConversation() {
        val conv = db.conversationDao().getConversationById("default_session")
        if (conv == null) {
            db.conversationDao().insertConversation(
                ConversationEntity(
                    id = "default_session",
                    title = "Primary Command Stream",
                    createdAt = System.currentTimeMillis(),
                    lastUpdatedAt = System.currentTimeMillis()
                )
            )

            // Seed initial greeting message
            val welcomeMsg = MessageEntity(
                id = UUID.randomUUID().toString(),
                conversationId = "default_session",
                role = MessageRole.ASSISTANT.name,
                content = "Good day, sir. J.A.R.V.I.S. initialized and ready. How may I facilitate your operations today?",
                timestamp = System.currentTimeMillis()
            )
            db.messageDao().insertMessage(welcomeMsg)
        }
    }

    private fun loadConversationMessages(conversationId: String) {
        viewModelScope.launch {
            db.messageDao().getMessagesForConversation(conversationId).collect { entities ->
                _chatMessages.value = entities.map {
                    ChatMessage(
                        id = it.id,
                        role = try { MessageRole.valueOf(it.role) } catch (e: Exception) { MessageRole.ASSISTANT },
                        content = it.content,
                        timestamp = it.timestamp,
                        imageUri = it.imageUri,
                        toolName = it.toolName,
                        toolInput = it.toolInput,
                        toolOutput = it.toolOutput,
                        isError = it.isError
                    )
                }
            }
        }
    }

    fun sendMessage(userText: String, imageUri: Uri? = null) {
        if (userText.isBlank() && imageUri == null) return

        val userMessageId = UUID.randomUUID().toString()
        val userMsg = ChatMessage(
            id = userMessageId,
            role = MessageRole.USER,
            content = userText,
            imageUri = imageUri?.toString(),
            timestamp = System.currentTimeMillis()
        )

        // Optimistically add to UI & DB
        _chatMessages.value = _chatMessages.value + userMsg

        viewModelScope.launch {
            db.messageDao().insertMessage(
                MessageEntity(
                    id = userMessageId,
                    conversationId = _currentConversationId.value,
                    role = MessageRole.USER.name,
                    content = userText,
                    imageUri = imageUri?.toString(),
                    timestamp = System.currentTimeMillis()
                )
            )
            db.conversationDao().updateLastTimestamp(_currentConversationId.value, System.currentTimeMillis())

            // Memory extraction in background
            memoryManager.autoExtractAndStore(userText)

            _aiState.value = AiState.THINKING

            val history = _chatMessages.value.dropLast(1).map { it.role.name to it.content }
            val state = uiState.value

            val (responseText, invokedTool) = aiEngine.generateResponse(
                prompt = userText,
                conversationHistory = history,
                imageUri = imageUri,
                userApiKeyOverride = state.customApiKey,
                modelName = state.selectedModel,
                language = state.language,
                isOfflineMode = state.isOfflineMode,
                onToolInvoked = { toolName, args ->
                    _aiState.value = AiState.EXECUTING_TOOL
                    _activeToolName.value = toolName
                }
            )

            val assistantMsgId = UUID.randomUUID().toString()
            val assistantMsg = ChatMessage(
                id = assistantMsgId,
                role = MessageRole.ASSISTANT,
                content = responseText,
                toolName = invokedTool,
                timestamp = System.currentTimeMillis()
            )

            _chatMessages.value = _chatMessages.value + assistantMsg

            db.messageDao().insertMessage(
                MessageEntity(
                    id = assistantMsgId,
                    conversationId = _currentConversationId.value,
                    role = MessageRole.ASSISTANT.name,
                    content = responseText,
                    toolName = invokedTool,
                    timestamp = System.currentTimeMillis()
                )
            )

            _activeToolName.value = null
            _aiState.value = AiState.SPEAKING

            if (state.isAutoSpeak) {
                voiceManager.speak(responseText)
            } else {
                _aiState.value = AiState.IDLE
            }
        }
    }

    fun startVoiceListening() {
        _aiState.value = AiState.LISTENING
        voiceManager.setLanguage(uiState.value.language)
        voiceManager.startListening { spokenText ->
            _aiState.value = AiState.THINKING
            sendMessage(spokenText)
        }
    }

    fun stopVoiceListening() {
        voiceManager.stopListening()
        if (_aiState.value == AiState.LISTENING) {
            _aiState.value = AiState.IDLE
        }
    }

    fun stopSpeaking() {
        voiceManager.stopSpeaking()
        if (_aiState.value == AiState.SPEAKING) {
            _aiState.value = AiState.IDLE
        }
    }

    fun createAgentTask(goal: String) {
        viewModelScope.launch {
            val key = uiState.value.customApiKey
            val task = agentOrchestrator.createAndPlanTask(goal, key)
            agentOrchestrator.executeTask(task.id, key)
        }
    }

    fun cancelAgentTask(taskId: String) {
        viewModelScope.launch {
            agentOrchestrator.cancelTask(taskId)
        }
    }

    fun deleteAgentTask(taskId: String) {
        viewModelScope.launch {
            agentOrchestrator.deleteTask(taskId)
        }
    }

    fun startDeepResearch(topic: String) {
        viewModelScope.launch {
            researchEngine.startDeepResearch(topic)
        }
    }

    fun deleteResearchSession(id: String) {
        viewModelScope.launch {
            researchEngine.deleteSession(id)
        }
    }

    fun saveMemory(key: String, value: String, category: MemoryCategory, importance: Int) {
        viewModelScope.launch {
            memoryManager.saveMemory(key, value, category, importance)
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            memoryManager.deleteMemory(id)
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            memoryManager.clearAll()
        }
    }

    fun requestConfirmation(action: ConfirmationAction) {
        _pendingConfirmation.value = action
        _aiState.value = AiState.AWAITING_CONFIRMATION
    }

    fun resolveConfirmation(approved: Boolean) {
        val action = _pendingConfirmation.value
        _pendingConfirmation.value = null
        _aiState.value = AiState.IDLE
        if (approved && action != null) {
            action.onConfirm.invoke()
        }
    }

    fun setLanguage(language: AssistantLanguage) {
        viewModelScope.launch {
            preferences.setSelectedLanguage(language)
            voiceManager.setLanguage(language)
        }
    }

    fun setSelectedModel(model: String) {
        viewModelScope.launch {
            preferences.setSelectedModel(model)
        }
    }

    fun setCustomApiKey(key: String) {
        viewModelScope.launch {
            preferences.setCustomApiKey(key)
        }
    }

    fun setOfflineMode(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setOfflineMode(enabled)
        }
    }

    fun setDeveloperMode(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setDeveloperMode(enabled)
        }
    }

    fun setAutoSpeak(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setAutoSpeak(enabled)
        }
    }

    fun setSpeechRate(rate: Float) {
        viewModelScope.launch {
            preferences.setSpeechRate(rate)
            voiceManager.setSpeechRate(rate)
        }
    }

    fun setSpeechPitch(pitch: Float) {
        viewModelScope.launch {
            preferences.setSpeechPitch(pitch)
            voiceManager.setPitch(pitch)
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            db.messageDao().deleteMessagesForConversation(_currentConversationId.value)
            _chatMessages.value = emptyList()
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceManager.release()
    }
}
