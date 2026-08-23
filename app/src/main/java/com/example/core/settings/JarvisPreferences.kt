package com.example.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.model.AiModelInfo
import com.example.core.model.AssistantLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "jarvis_preferences")

class JarvisPreferences(private val context: Context) {

    companion object {
        val KEY_API_KEY = stringPreferencesKey("custom_api_key")
        val KEY_SELECTED_MODEL = stringPreferencesKey("selected_model")
        val KEY_LANGUAGE = stringPreferencesKey("selected_language")
        val KEY_AUTO_SPEAK = booleanPreferencesKey("auto_speak")
        val KEY_SPEECH_RATE = floatPreferencesKey("speech_rate")
        val KEY_SPEECH_PITCH = floatPreferencesKey("speech_pitch")
        val KEY_OFFLINE_MODE = booleanPreferencesKey("offline_mode")
        val KEY_DEVELOPER_MODE = booleanPreferencesKey("developer_mode")
        val KEY_LOCAL_MODEL_INSTALLED = booleanPreferencesKey("local_model_installed")
    }

    val customApiKey: Flow<String> = context.dataStore.data.map { it[KEY_API_KEY] ?: "" }
    val selectedModel: Flow<String> = context.dataStore.data.map { it[KEY_SELECTED_MODEL] ?: "gemini-3.5-flash" }
    val selectedLanguage: Flow<AssistantLanguage> = context.dataStore.data.map {
        val name = it[KEY_LANGUAGE] ?: AssistantLanguage.ENGLISH.name
        try { AssistantLanguage.valueOf(name) } catch (e: Exception) { AssistantLanguage.ENGLISH }
    }
    val isAutoSpeakEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_SPEAK] ?: true }
    val speechRate: Flow<Float> = context.dataStore.data.map { it[KEY_SPEECH_RATE] ?: 1.0f }
    val speechPitch: Flow<Float> = context.dataStore.data.map { it[KEY_SPEECH_PITCH] ?: 1.0f }
    val isOfflineMode: Flow<Boolean> = context.dataStore.data.map { it[KEY_OFFLINE_MODE] ?: false }
    val isDeveloperMode: Flow<Boolean> = context.dataStore.data.map { it[KEY_DEVELOPER_MODE] ?: true }
    val isLocalModelInstalled: Flow<Boolean> = context.dataStore.data.map { it[KEY_LOCAL_MODEL_INSTALLED] ?: false }

    suspend fun setCustomApiKey(key: String) {
        context.dataStore.edit { it[KEY_API_KEY] = key.trim() }
    }

    suspend fun setSelectedModel(model: String) {
        context.dataStore.edit { it[KEY_SELECTED_MODEL] = model }
    }

    suspend fun setSelectedLanguage(language: AssistantLanguage) {
        context.dataStore.edit { it[KEY_LANGUAGE] = language.name }
    }

    suspend fun setAutoSpeak(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_SPEAK] = enabled }
    }

    suspend fun setSpeechRate(rate: Float) {
        context.dataStore.edit { it[KEY_SPEECH_RATE] = rate }
    }

    suspend fun setSpeechPitch(pitch: Float) {
        context.dataStore.edit { it[KEY_SPEECH_PITCH] = pitch }
    }

    suspend fun setOfflineMode(enabled: Boolean) {
        context.dataStore.edit { it[KEY_OFFLINE_MODE] = enabled }
    }

    suspend fun setDeveloperMode(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DEVELOPER_MODE] = enabled }
    }

    suspend fun setLocalModelInstalled(installed: Boolean) {
        context.dataStore.edit { it[KEY_LOCAL_MODEL_INSTALLED] = installed }
    }

    fun getModelCatalog(): List<AiModelInfo> {
        return listOf(
            AiModelInfo(
                id = "gemini-3.5-flash",
                name = "Gemini 3.5 Flash (Default Master AI)",
                provider = "Google Cloud AI",
                description = "Ultra-fast multimodal reasoning, streaming tool execution, long-context memory, and agent orchestration.",
                isLocal = false,
                sizeOnDisk = "Cloud Hosted",
                contextWindow = "1,048,576 tokens",
                isDefault = true
            ),
            AiModelInfo(
                id = "gemini-3.1-pro-preview",
                name = "Gemini 3.1 Pro Preview (Complex Reasoning)",
                provider = "Google Cloud AI",
                description = "Maximum cognitive depth for complex STEM coding, math deduction, and elaborate multi-step research.",
                isLocal = false,
                sizeOnDisk = "Cloud Hosted",
                contextWindow = "2,097,152 tokens",
                isDefault = false
            ),
            AiModelInfo(
                id = "gemini-2.5-flash-image",
                name = "Gemini 2.5 Flash Image (Multimodal Vision)",
                provider = "Google Cloud AI",
                description = "High resolution computer vision, document OCR, layout decomposition, and camera stream analysis.",
                isLocal = false,
                sizeOnDisk = "Cloud Hosted",
                contextWindow = "512,000 tokens",
                isDefault = false
            ),
            AiModelInfo(
                id = "jarvis-mobile-edge-2b",
                name = "JARVIS Mobile Edge 2B (On-Device Local)",
                provider = "OnePlus 15R NPU Kernel",
                description = "Quantized 4-bit edge model for offline device commands, local calculations, note lookups, and private offline synthesis.",
                isLocal = true,
                sizeOnDisk = "1.4 GB",
                contextWindow = "8,192 tokens",
                isDefault = false
            )
        )
    }
}
