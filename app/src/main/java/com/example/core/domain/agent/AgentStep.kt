package com.example.core.domain.agent

import com.example.core.domain.execution.ExecutionStatus
import com.example.core.domain.execution.ToolOutcome
import com.example.core.domain.security.RiskEvaluation
import com.example.core.domain.verification.VerificationResult

/**
 * Immutable domain model representing a single step within an Agent Task plan.
 */
data class AgentStep(
    val stepId: String,
    val stepIndex: Int,
    val title: String,
    val description: String,
    val capabilityId: String,
    val toolId: String,
    val inputArguments: Map<String, Any?>,
    val riskEvaluation: RiskEvaluation,
    val status: ExecutionStatus = ExecutionStatus.REQUESTED,
    val idempotencyKey: String,
    val retryCount: Int = 0,
    val approvalId: String? = null,
    val executionOutcome: ToolOutcome? = null,
    val verificationResult: VerificationResult? = null,
    val createdAt: Long,
    val startedAt: Long? = null,
    val completedAt: Long? = null
) {
    val isTerminal: Boolean
        get() = status == ExecutionStatus.VERIFIED ||
                status == ExecutionStatus.FAILED ||
                status == ExecutionStatus.ABORTED

    fun withStatus(newStatus: ExecutionStatus): AgentStep = copy(status = newStatus)

    fun withOutcome(outcome: ToolOutcome, verification: VerificationResult?): AgentStep =
        copy(
            executionOutcome = outcome,
            verificationResult = verification,
            status = verification?.finalExecutionStatus ?: outcome.status,
            completedAt = System.currentTimeMillis()
        )
}
