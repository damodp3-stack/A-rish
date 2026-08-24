package com.example.core.domain.agent

/**
 * Immutable execution envelope for deterministic, bounded agent execution.
 * Enforces strict upper bounds on steps, tool calls, time, retries, and token estimates.
 */
data class ExecutionBudget(
    val maxSteps: Int = DEFAULT_MAX_STEPS,
    val maxToolCalls: Int = DEFAULT_MAX_TOOL_CALLS,
    val maxExecutionTimeMs: Long = DEFAULT_MAX_TIME_MS,
    val maxRetriesPerStep: Int = DEFAULT_MAX_RETRIES,
    val maxInputTokensEstimate: Int = DEFAULT_MAX_INPUT_TOKENS,
    val maxOutputTokensEstimate: Int = DEFAULT_MAX_OUTPUT_TOKENS,
    val requiresExplicitCheckpointing: Boolean = true
) {
    companion object {
        const val DEFAULT_MAX_STEPS = 5
        const val DEFAULT_MAX_TOOL_CALLS = 8
        const val DEFAULT_MAX_TIME_MS = 60_000L // 60 seconds max
        const val DEFAULT_MAX_RETRIES = 2
        const val DEFAULT_MAX_INPUT_TOKENS = 8_192
        const val DEFAULT_MAX_OUTPUT_TOKENS = 2_048

        val STRICT = ExecutionBudget(
            maxSteps = 3,
            maxToolCalls = 4,
            maxExecutionTimeMs = 30_000L,
            maxRetriesPerStep = 1,
            maxInputTokensEstimate = 4_096,
            maxOutputTokensEstimate = 1_024
        )

        val STANDARD = ExecutionBudget()
    }

    init {
        require(maxSteps in 1..20) { "maxSteps must be between 1 and 20 (got $maxSteps)" }
        require(maxToolCalls in 1..30) { "maxToolCalls must be between 1 and 30 (got $maxToolCalls)" }
        require(maxExecutionTimeMs in 1_000L..300_000L) { "maxExecutionTimeMs must be between 1s and 300s (got $maxExecutionTimeMs)" }
        require(maxRetriesPerStep in 0..5) { "maxRetriesPerStep must be between 0 and 5 (got $maxRetriesPerStep)" }
    }
}
