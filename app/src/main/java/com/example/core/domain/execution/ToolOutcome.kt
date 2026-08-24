package com.example.core.domain.execution

/**
 * Result produced by a ToolContract execution.
 */
data class ToolOutcome(
    val toolId: String,
    val status: ExecutionStatus,
    val rawResultData: Map<String, Any?> = emptyMap(),
    val summaryText: String,
    val sideEffectSemantics: SideEffectSemantics,
    val executionDurationMs: Long = 0L,
    val errorMessage: String? = null,
    val errorDetails: String? = null
) {
    companion object {
        fun success(
            toolId: String,
            data: Map<String, Any?>,
            summary: String,
            semantics: SideEffectSemantics = SideEffectSemantics.NO_SIDE_EFFECT,
            durationMs: Long = 0L
        ): ToolOutcome = ToolOutcome(
            toolId = toolId,
            status = ExecutionStatus.EXECUTED,
            rawResultData = data,
            summaryText = summary,
            sideEffectSemantics = semantics,
            executionDurationMs = durationMs
        )

        fun failure(
            toolId: String,
            errorMessage: String,
            errorDetails: String? = null,
            durationMs: Long = 0L
        ): ToolOutcome = ToolOutcome(
            toolId = toolId,
            status = ExecutionStatus.FAILED,
            summaryText = "Execution failed: $errorMessage",
            sideEffectSemantics = SideEffectSemantics.NO_SIDE_EFFECT,
            executionDurationMs = durationMs,
            errorMessage = errorMessage,
            errorDetails = errorDetails
        )
    }
}
