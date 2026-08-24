package com.example.core.domain.security

/**
 * Lifecycle status of an ApprovalRequest.
 */
enum class ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED,
    EXPIRED,
    CANCELLED
}

/**
 * Immutable decision object recorded when a user acts on an approval request.
 */
data class ApprovalDecision(
    val status: ApprovalStatus,
    val decidedBy: String = "USER",
    val decidedAt: Long = System.currentTimeMillis(),
    val notes: String? = null
)

/**
 * Durable ApprovalRequest contract that survives process death and restarts.
 */
data class ApprovalRequest(
    val approvalId: String,
    val taskId: String,
    val stepId: String,
    val toolId: String,
    val capabilityId: String,
    val riskEvaluation: RiskEvaluation,
    val actionSummary: String,
    val previewPayload: Map<String, Any?>,
    val createdAt: Long,
    val expiresAt: Long,
    val status: ApprovalStatus = ApprovalStatus.PENDING,
    val decision: ApprovalDecision? = null
) {
    val isPending: Boolean
        get() = status == ApprovalStatus.PENDING && System.currentTimeMillis() < expiresAt

    val isExpired: Boolean
        get() = status == ApprovalStatus.PENDING && System.currentTimeMillis() >= expiresAt

    fun approve(decidedBy: String = "USER"): ApprovalRequest = copy(
        status = ApprovalStatus.APPROVED,
        decision = ApprovalDecision(status = ApprovalStatus.APPROVED, decidedBy = decidedBy)
    )

    fun reject(reason: String? = null): ApprovalRequest = copy(
        status = ApprovalStatus.REJECTED,
        decision = ApprovalDecision(status = ApprovalStatus.REJECTED, notes = reason)
    )

    fun expire(): ApprovalRequest = copy(
        status = ApprovalStatus.EXPIRED,
        decision = ApprovalDecision(status = ApprovalStatus.EXPIRED, notes = "Approval request timed out")
    )
}
