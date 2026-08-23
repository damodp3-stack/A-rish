package com.example.core.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    @Json(name = "contents") val contents: List<ContentDto>,
    @Json(name = "generationConfig") val generationConfig: GenerationConfigDto? = null,
    @Json(name = "systemInstruction") val systemInstruction: ContentDto? = null,
    @Json(name = "tools") val tools: List<ToolDeclarationDto>? = null
)

@JsonClass(generateAdapter = true)
data class ContentDto(
    @Json(name = "role") val role: String? = null,
    @Json(name = "parts") val parts: List<PartDto>
)

@JsonClass(generateAdapter = true)
data class PartDto(
    @Json(name = "text") val text: String? = null,
    @Json(name = "inlineData") val inlineData: InlineDataDto? = null,
    @Json(name = "functionCall") val functionCall: FunctionCallDto? = null,
    @Json(name = "functionResponse") val functionResponse: FunctionResponseDto? = null
)

@JsonClass(generateAdapter = true)
data class InlineDataDto(
    @Json(name = "mimeType") val mimeType: String,
    @Json(name = "data") val data: String
)

@JsonClass(generateAdapter = true)
data class FunctionCallDto(
    @Json(name = "name") val name: String,
    @Json(name = "args") val args: Map<String, Any?>? = null
)

@JsonClass(generateAdapter = true)
data class FunctionResponseDto(
    @Json(name = "name") val name: String,
    @Json(name = "response") val response: Map<String, Any?>
)

@JsonClass(generateAdapter = true)
data class ToolDeclarationDto(
    @Json(name = "functionDeclarations") val functionDeclarations: List<FunctionDeclarationDto>
)

@JsonClass(generateAdapter = true)
data class FunctionDeclarationDto(
    @Json(name = "name") val name: String,
    @Json(name = "description") val description: String,
    @Json(name = "parameters") val parameters: Map<String, Any?>? = null
)

@JsonClass(generateAdapter = true)
data class GenerationConfigDto(
    @Json(name = "temperature") val temperature: Float? = 0.7f,
    @Json(name = "topP") val topP: Float? = 0.95f,
    @Json(name = "topK") val topK: Int? = 40,
    @Json(name = "maxOutputTokens") val maxOutputTokens: Int? = 4096
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    @Json(name = "candidates") val candidates: List<CandidateDto>? = null,
    @Json(name = "error") val error: GeminiErrorDto? = null
)

@JsonClass(generateAdapter = true)
data class CandidateDto(
    @Json(name = "content") val content: ContentDto? = null,
    @Json(name = "finishReason") val finishReason: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiErrorDto(
    @Json(name = "code") val code: Int? = null,
    @Json(name = "message") val message: String? = null,
    @Json(name = "status") val status: String? = null
)
