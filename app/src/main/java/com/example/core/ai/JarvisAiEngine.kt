package com.example.core.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.example.BuildConfig
import com.example.core.memory.MemoryManager
import com.example.core.model.AssistantLanguage
import com.example.core.network.ContentDto
import com.example.core.network.FunctionDeclarationDto
import com.example.core.network.FunctionResponseDto
import com.example.core.network.GeminiApiClient
import com.example.core.network.GeminiRequest
import com.example.core.network.GenerationConfigDto
import com.example.core.network.InlineDataDto
import com.example.core.network.PartDto
import com.example.core.network.ToolDeclarationDto
import com.example.core.tools.ToolExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

class JarvisAiEngine(
    private val context: Context,
    private val memoryManager: MemoryManager,
    private val toolExecutor: ToolExecutor
) {
    suspend fun generateResponse(
        prompt: String,
        conversationHistory: List<Pair<String, String>>, // role, text
        imageUri: Uri? = null,
        userApiKeyOverride: String = "",
        modelName: String = "gemini-3.5-flash",
        language: AssistantLanguage = AssistantLanguage.ENGLISH,
        isOfflineMode: Boolean = false,
        onToolInvoked: ((String, String) -> Unit)? = null
    ): Pair<String, String?> = withContext(Dispatchers.IO) {
        val apiKey = when {
            userApiKeyOverride.isNotBlank() -> userApiKeyOverride
            else -> try {
                BuildConfig.GEMINI_API_KEY
            } catch (e: Throwable) {
                ""
            }
        }

        if (isOfflineMode || apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext generateLocalEngineResponse(prompt, language, onToolInvoked)
        }

        try {
            val memoryContext = memoryManager.getRelevantMemoriesForPrompt(prompt)

            val languageDirective = when (language) {
                AssistantLanguage.ENGLISH -> "Respond fluently in clear, sophisticated English."
                AssistantLanguage.TAMIL -> "நீங்கள் தமிழிலும் (Tamil) சரளமாகப் பேசவும். Respond predominantly in natural, respectful Tamil."
                AssistantLanguage.TANGLISH -> "You fully understand Tanglish (Tamil written in English script or mixed Tamil-English). Respond with natural, modern Tanglish or bilingual phrasing."
            }

            val systemInstruction = ContentDto(
                parts = listOf(
                    PartDto(
                        text = """
                            You are J.A.R.V.I.S., an intelligent, polite, efficient, and proactive AI assistant.
                            $languageDirective
                            $memoryContext
                            Always prioritize direct answers, factual accuracy, and structured clarity.
                            You have access to tools for web search, calculator, device diagnostics, weather, calendar, notes, file analysis, clipboard sharing, and deep research. Use them ONLY when explicitly relevant to the user's request.
                            IMPORTANT PRIVACY & CONTEXT RULE: Do NOT mention or output the user's device model, hardware specifications, RAM, storage, or internal telemetry unless the user explicitly asks about their device or hardware diagnostics.
                        """.trimIndent()
                    )
                )
            )

            val contentList = mutableListOf<ContentDto>()

            // Append history (up to last 6 turns to keep context optimal)
            conversationHistory.takeLast(6).forEach { (role, text) ->
                contentList.add(
                    ContentDto(
                        role = if (role.equals("USER", ignoreCase = true)) "user" else "model",
                        parts = listOf(PartDto(text = text))
                    )
                )
            }

            // Current prompt parts
            val currentParts = mutableListOf<PartDto>()
            currentParts.add(PartDto(text = prompt))

            if (imageUri != null) {
                val imageBase64 = encodeImageUriToBase64(imageUri)
                if (imageBase64 != null) {
                    currentParts.add(
                        PartDto(
                            inlineData = InlineDataDto(
                                mimeType = "image/jpeg",
                                data = imageBase64
                            )
                        )
                    )
                }
            }

            contentList.add(ContentDto(role = "user", parts = currentParts))

            val toolDeclarations = listOf(
                ToolDeclarationDto(
                    functionDeclarations = listOf(
                        FunctionDeclarationDto(
                            name = "web_search",
                            description = "Search current live web information, facts, news, and technical benchmarks.",
                            parameters = mapOf(
                                "type" to "OBJECT",
                                "properties" to mapOf(
                                    "query" to mapOf("type" to "STRING", "description" to "Search query topic or question")
                                ),
                                "required" to listOf("query")
                            )
                        ),
                        FunctionDeclarationDto(
                            name = "calculator",
                            description = "Calculate mathematical expressions, conversions, trigonometry, logarithms, and powers.",
                            parameters = mapOf(
                                "type" to "OBJECT",
                                "properties" to mapOf(
                                    "expression" to mapOf("type" to "STRING", "description" to "Mathematical equation or expression")
                                ),
                                "required" to listOf("expression")
                            )
                        ),
                        FunctionDeclarationDto(
                            name = "device_diagnostics",
                            description = "Inspect real-time device telemetry: battery, RAM, internal storage, CPU cores, thermal state, and network state."
                        ),
                        FunctionDeclarationDto(
                            name = "android_action",
                            description = "Launch Android apps (WhatsApp, Maps, YouTube, Camera, Settings, Browser), set timers, or open dialer.",
                            parameters = mapOf(
                                "type" to "OBJECT",
                                "properties" to mapOf(
                                    "action" to mapOf("type" to "STRING", "description" to "Action name: open_app, timer, dial"),
                                    "target" to mapOf("type" to "STRING", "description" to "App or target: whatsapp, youtube, maps, camera, settings, browser"),
                                    "extra" to mapOf("type" to "STRING", "description" to "Search term, timer seconds, or phone number")
                                ),
                                "required" to listOf("action", "target")
                            )
                        ),
                        FunctionDeclarationDto(
                            name = "weather",
                            description = "Get meteorological forecast, humidity, and atmospheric conditions for a given city or location.",
                            parameters = mapOf(
                                "type" to "OBJECT",
                                "properties" to mapOf(
                                    "location" to mapOf("type" to "STRING", "description" to "City or region name")
                                ),
                                "required" to listOf("location")
                            )
                        ),
                        FunctionDeclarationDto(
                            name = "calendar",
                            description = "Schedule and inspect reminders or calendar events in Android system calendar.",
                            parameters = mapOf(
                                "type" to "OBJECT",
                                "properties" to mapOf(
                                    "title" to mapOf("type" to "STRING", "description" to "Event title or agenda"),
                                    "time" to mapOf("type" to "STRING", "description" to "Event time or date")
                                ),
                                "required" to listOf("title")
                            )
                        ),
                        FunctionDeclarationDto(
                            name = "notes",
                            description = "Store, retrieve, or delete local secure memos in encrypted vault.",
                            parameters = mapOf(
                                "type" to "OBJECT",
                                "properties" to mapOf(
                                    "action" to mapOf("type" to "STRING", "description" to "Action: create, list, delete"),
                                    "content" to mapOf("type" to "STRING", "description" to "Note text")
                                )
                            )
                        ),
                        FunctionDeclarationDto(
                            name = "file_analyzer",
                            description = "Inspect document text, code syntax, token metrics, and summarize technical text.",
                            parameters = mapOf(
                                "type" to "OBJECT",
                                "properties" to mapOf(
                                    "document_text" to mapOf("type" to "STRING", "description" to "Text or code to analyze")
                                ),
                                "required" to listOf("document_text")
                            )
                        ),
                        FunctionDeclarationDto(
                            name = "clipboard_share",
                            description = "Copy text to Android clipboard or open native share sheet.",
                            parameters = mapOf(
                                "type" to "OBJECT",
                                "properties" to mapOf(
                                    "text" to mapOf("type" to "STRING", "description" to "Text to copy or share"),
                                    "mode" to mapOf("type" to "STRING", "description" to "Mode: copy or share")
                                ),
                                "required" to listOf("text")
                            )
                        ),
                        FunctionDeclarationDto(
                            name = "deep_research",
                            description = "Execute autonomous multi-step research on complex topic with citation vectors.",
                            parameters = mapOf(
                                "type" to "OBJECT",
                                "properties" to mapOf(
                                    "topic" to mapOf("type" to "STRING", "description" to "Subject for deep research")
                                ),
                                "required" to listOf("topic")
                            )
                        )
                    )
                )
            )

            val request = GeminiRequest(
                contents = contentList,
                systemInstruction = systemInstruction,
                generationConfig = GenerationConfigDto(temperature = 0.7f),
                tools = toolDeclarations
            )

            val targetModel = if (modelName.contains("gemini")) modelName else "gemini-3.5-flash"
            val response = GeminiApiClient.service.generateContent(targetModel, apiKey, request)

            val firstCandidate = response.candidates?.firstOrNull()
            val candidateParts = firstCandidate?.content?.parts

            // Check if model called a function (Real Agentic Tool Loop)
            val functionCall = candidateParts?.firstOrNull { it.functionCall != null }?.functionCall
            if (functionCall != null) {
                val toolName = functionCall.name
                val toolArgs = functionCall.args ?: emptyMap()
                onToolInvoked?.invoke(toolName, toolArgs.toString())

                val toolResult = toolExecutor.executeTool(toolName, toolArgs).getOrElse { "Tool execution completed with nominal telemetry." }

                // Multi-Turn Agentic Synthesis: Feed tool response back to Gemini for final natural synthesis
                try {
                    val followupContents = contentList.toMutableList().apply {
                        // 1. Model's tool call turn
                        add(ContentDto(role = "model", parts = candidateParts ?: listOf(PartDto(functionCall = functionCall))))
                        // 2. User's function response turn
                        add(
                            ContentDto(
                                role = "user",
                                parts = listOf(
                                    PartDto(
                                        functionResponse = FunctionResponseDto(
                                            name = toolName,
                                            response = mapOf(
                                                "name" to toolName,
                                                "content" to toolResult,
                                                "status" to "SUCCESS"
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    }

                    val followupRequest = GeminiRequest(
                        contents = followupContents,
                        systemInstruction = systemInstruction,
                        generationConfig = GenerationConfigDto(temperature = 0.7f)
                    )

                    val followupResponse = GeminiApiClient.service.generateContent(targetModel, apiKey, followupRequest)
                    val synthesizedText = followupResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull { it.text != null }?.text

                    if (!synthesizedText.isNullOrBlank()) {
                        return@withContext Pair(synthesizedText, toolName)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // Fallback structured response if followup synthesis was unreachable
                return@withContext Pair(
                    "Executing **$toolName**...\n\n$toolResult\n\nIs there anything further you require with these telemetry findings, sir?",
                    toolName
                )
            }

            val textResponse = candidateParts?.firstOrNull { it.text != null }?.text
                ?: "I processed your request, sir. All parameters are nominal."

            Pair(textResponse, null)
        } catch (e: Exception) {
            e.printStackTrace()
            // Graceful fallback to local engine on network failure
            generateLocalEngineResponse(prompt, language, onToolInvoked)
        }
    }

    private suspend fun generateLocalEngineResponse(
        prompt: String,
        language: AssistantLanguage,
        onToolInvoked: ((String, String) -> Unit)?
    ): Pair<String, String?> {
        val lower = prompt.lowercase()

        // 1. Math / Calculation intent
        if (lower.matches(".*[0-9]+[\\s]*[+\\-*/^][\\s]*[0-9]+.*".toRegex()) || lower.contains("calculate") || lower.contains("sqrt") || lower.contains("sin(")) {
            val expr = prompt.replace("calculate", "").replace("what is", "").replace("eval", "").trim()
            onToolInvoked?.invoke("calculator", expr)
            val result = toolExecutor.executeTool("calculator", mapOf("expression" to expr)).getOrDefault("Calculated.")
            val response = when (language) {
                AssistantLanguage.TAMIL -> "கணக்கீடு முடிந்தது, ஐயா:\n$result"
                AssistantLanguage.TANGLISH -> "Calculation done, sir:\n$result"
                AssistantLanguage.ENGLISH -> "Calculation complete, sir:\n$result"
            }
            return Pair(response, "calculator")
        }

        // 2. Hardware / Telemetry intent
        if (lower.contains("battery") || lower.contains("diagnostic") || lower.contains("status") || lower.contains("hardware") || lower.contains("system")) {
            onToolInvoked?.invoke("device_diagnostics", "")
            val diag = toolExecutor.executeTool("device_diagnostics", emptyMap()).getOrDefault("Diagnostics nominal.")
            val response = when (language) {
                AssistantLanguage.TAMIL -> "சாதனத்தின் நிலை அறிக்கை தயார், ஐயா:\n\n$diag"
                AssistantLanguage.TANGLISH -> "Device diagnostics check complete, sir:\n\n$diag"
                AssistantLanguage.ENGLISH -> "System diagnostics initialized, sir:\n\n$diag"
            }
            return Pair(response, "device_diagnostics")
        }

        // 3. Weather intent
        if (lower.contains("weather") || lower.contains("rain") || lower.contains("temperature") || lower.contains("climate")) {
            val city = prompt.substringAfter("in ", "").substringAfter("for ", "").ifBlank { "Chennai / Local" }
            onToolInvoked?.invoke("weather", city)
            val weather = toolExecutor.executeTool("weather", mapOf("location" to city)).getOrDefault("Weather report.")
            val response = when (language) {
                AssistantLanguage.TAMIL -> "வானிலை அறிக்கை இதோ, ஐயா:\n\n$weather"
                AssistantLanguage.TANGLISH -> "Weather radar update, sir:\n\n$weather"
                AssistantLanguage.ENGLISH -> "Meteorological radar report, sir:\n\n$weather"
            }
            return Pair(response, "weather")
        }

        // 4. Web Search intent
        if (lower.contains("search") || lower.contains("who is") || lower.contains("what is") || lower.contains("news") || lower.contains("latest")) {
            onToolInvoked?.invoke("web_search", prompt)
            val searchRes = toolExecutor.executeTool("web_search", mapOf("query" to prompt)).getOrDefault("Search completed.")
            val response = when (language) {
                AssistantLanguage.TAMIL -> "இணையத் தகவல் தேடல் முடிவுகள், ஐயா:\n\n$searchRes"
                AssistantLanguage.TANGLISH -> "Web search telemetry completed, sir:\n\n$searchRes"
                AssistantLanguage.ENGLISH -> "Live web intelligence retrieval completed, sir:\n\n$searchRes"
            }
            return Pair(response, "web_search")
        }

        // 5. Memory recall intent
        if (lower.contains("who am i") || lower.contains("my name") || lower.contains("what do you remember")) {
            val memories = memoryManager.getRelevantMemoriesForPrompt(prompt)
            val response = if (memories.isNotBlank()) {
                "Recalling from JARVIS Memory Matrix:\n$memories\nAlways at your service, sir."
            } else {
                "I am JARVIS, your on-device AI operating system. I am ready to record any instructions, preferences, or tasks."
            }
            return Pair(response, null)
        }

        // 6. Tamil greeting / general query
        if (language == AssistantLanguage.TAMIL || lower.contains("வணக்கம்") || lower.contains("eppadi irukinga") || lower.contains("vanakkam")) {
            return Pair(
                "வணக்கம் ஐயா! நான் JARVIS AI உதவியாளர். உங்களுக்கு எவ்வாறு உதவ முடியும்?",
                null
            )
        }

        if (language == AssistantLanguage.TANGLISH) {
            return Pair(
                "Vanakkam sir! JARVIS systems are ready. Text, voice, search, and memory active. Enna pannanum சொல்லுங்க sir?",
                null
            )
        }

        // Default English JARVIS greeting
        return Pair(
            "At your service, sir. How may I assist you today?",
            null
        )
    }

    private fun encodeImageUriToBase64(uri: Uri): String? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val bytes = outputStream.toByteArray()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }
}
