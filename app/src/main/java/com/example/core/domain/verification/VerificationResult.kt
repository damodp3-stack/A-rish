package com.example.core.domain.verification

import com.example.core.domain.execution.ExecutionStatus

/**
 * Result produced by a capability-specific VerificationStrategy.
 */
data class VerificationResult(
    val stepId: String,
    val finalExecutionStatus: ExecutionStatus,
    val evidenceList: List<VerificationEvidence>,
    val explanationText: String,
    val isSuccess: Boolean,
    val verifiedAt: Long
) {
    companion object {
        fun verified(
            stepId: String,
            evidence: VerificationEvidence,
            explanation: String,
            timestamp: Long = System.currentTimeMillis()
        ): VerificationResult = VerificationResult(
            stepId = stepId,
            finalExecutionStatus = ExecutionStatus.VERIFIED,
            evidenceList = listOf(evidence),
            explanationText = explanation,
            isSuccess = true,
            verifiedAt = timestamp
        )

        fun partiallyVerified(
            stepId: String,
            evidence: VerificationEvidence,
            explanation: String,
            timestamp: Long = System.currentTimeMillis()
        ): VerificationResult = VerificationResult(
            stepId = stepId,
            finalExecutionStatus = ExecutionStatus.PARTIALLY_VERIFIED,
            evidenceList = listOf(evidence),
            explanationText = explanation,
            isSuccess = true,
            verifiedAt = timestamp
        )

        fun unverifiedOrFailed(
            stepId: String,
            explanation: String,
            evidence: VerificationEvidence? = null,
            timestamp: Long = System.currentTimeMillis()
        ): VerificationResult = VerificationResult(
            stepId = stepId,
            finalExecutionStatus = if (evidence != null) ExecutionStatus.UNKNOWN else ExecutionStatus.FAILED,
            evidenceList = if (evidence != null) listOf(evidence) else emptyList(),
            explanationText = explanation,
            isSuccess = false,
            verifiedAt = timestamp
        )
    }
}
