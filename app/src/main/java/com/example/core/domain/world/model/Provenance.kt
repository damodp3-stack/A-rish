package com.example.core.domain.world.model

/**
 * Epistemic source taxonomy for all World Model facts, goals, preferences, and commitments.
 * Distinguishes explicit user intent from automated inference.
 */
enum class EpistemicSource {
    /** Stated directly by the user via typing or voice. Highest human authority. */
    USER_EXPLICIT,

    /** Proposed by AI and explicitly accepted/confirmed by the user via interactive UI. */
    USER_CONFIRMED,

    /** Directly sampled from Android OS hardware sensors or system services (battery, network, GPS). */
    DEVICE_TELEMETRY,

    /** Purely derived via deterministic arithmetic or logical deduction. */
    DERIVED_LOGIC,

    /** Inferred by an LLM or statistical extractor from conversation. Low trust; MUST NOT be treated as user authority. */
    AI_INFERRED
}

/**
 * Semantic classification of incoming conversational context or observations.
 */
enum class ContextCategory {
    FACT,
    PREFERENCE,
    GOAL,
    TASK,
    COMMITMENT,
    CONSTRAINT,
    TEMPORARY_CONTEXT,
    OBSERVATION,
    INFERENCE
}

/**
 * Provenance and epistemic validity metadata attached to every world model entry.
 */
data class EpistemicProvenance(
    val source: EpistemicSource,
    val confidenceScore: Float, // 0.0f to 1.0f: statistical epistemic likelihood
    val validFrom: Long,
    val validUntil: Long? = null // Null indicates unbounded until updated
) {
    init {
        require(confidenceScore in 0.0f..1.0f) {
            "Confidence score must be in range [0.0, 1.0], found: $confidenceScore"
        }
        if (validUntil != null) {
            require(validUntil >= validFrom) {
                "validUntil ($validUntil) must be >= validFrom ($validFrom)"
            }
        }
    }

    /**
     * Checks if this record is currently temporally valid.
     */
    fun isTemporallyValid(currentTimeMillis: Long): Boolean {
        if (currentTimeMillis < validFrom) return false
        val until = validUntil ?: return true
        return currentTimeMillis <= until
    }

    /**
     * Critical Security Invariant:
     * High confidence (e.g. 0.99) NEVER equates to user authorization.
     * Only explicit or confirmed user actions carry human authority.
     */
    val hasUserAuthorization: Boolean
        get() = source == EpistemicSource.USER_EXPLICIT || source == EpistemicSource.USER_CONFIRMED

    companion object {
        fun userExplicit(currentTimeMillis: Long): EpistemicProvenance = EpistemicProvenance(
            source = EpistemicSource.USER_EXPLICIT,
            confidenceScore = 1.0f,
            validFrom = currentTimeMillis
        )

        fun aiInferred(
            confidence: Float,
            currentTimeMillis: Long,
            ttlMillis: Long? = null
        ): EpistemicProvenance = EpistemicProvenance(
            source = EpistemicSource.AI_INFERRED,
            confidenceScore = confidence.coerceIn(0.0f, 1.0f),
            validFrom = currentTimeMillis,
            validUntil = ttlMillis?.let { currentTimeMillis + it }
        )

        fun deviceTelemetry(currentTimeMillis: Long): EpistemicProvenance = EpistemicProvenance(
            source = EpistemicSource.DEVICE_TELEMETRY,
            confidenceScore = 1.0f,
            validFrom = currentTimeMillis
        )
    }
}
