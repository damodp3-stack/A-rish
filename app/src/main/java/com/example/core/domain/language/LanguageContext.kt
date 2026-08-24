package com.example.core.domain.language

/**
 * Supported language codes in A-RISH.
 */
enum class LanguageCode(val tag: String, val displayName: String) {
    ENGLISH("en-US", "English"),
    TAMIL("ta-IN", "தமிழ்"),
    TANGLISH("ta-IN-tanglish", "Tanglish (Tamil in Latin)")
}

/**
 * Normalized transcript resolution.
 */
data class LanguageResolution(
    val detectedLanguage: LanguageCode,
    val normalizedPrompt: String,
    val originalPrompt: String,
    val isTanglish: Boolean = false,
    val confidence: Float = 1.0f
)
