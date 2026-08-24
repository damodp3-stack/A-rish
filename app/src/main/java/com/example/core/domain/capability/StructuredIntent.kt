package com.example.core.domain.capability

/**
 * Structured intent proposed by the LLM or intent parser.
 * Must be validated before mapping to capability.
 */
data class IntentParameter(
    val key: String,
    val value: Any?,
    val isMandatory: Boolean = false
)

data class StructuredIntent(
    val intentId: String,
    val intentName: String,
    val capabilityId: CapabilityId,
    val parameters: Map<String, Any?>,
    val rawUserPrompt: String,
    val confidence: Float,
    val requiresClarification: Boolean = false,
    val clarificationQuestion: String? = null
) {
    init {
        require(confidence in 0.0f..1.0f) { "Confidence must be between 0.0 and 1.0 (got $confidence)" }
    }
}
