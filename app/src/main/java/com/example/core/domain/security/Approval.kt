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
    fun isExpiredAt(currentTime: Long = System.currentTimeMillis()): Boolean =
        status == ApprovalStatus.EXPIRED || currentTime >= expiresAt

    fun isPendingValid(currentTime: Long = System.currentTimeMillis()): Boolean =
        status == ApprovalStatus.PENDING && currentTime < expiresAt

    fun isValidForExecution(currentTime: Long = System.currentTimeMillis()): Boolean =
        status == ApprovalStatus.APPROVED && currentTime < expiresAt

    val isPending: Boolean
        get() = isPendingValid()

    val isExpired: Boolean
        get() = isExpiredAt()

    val isValidApproved: Boolean
        get() = isValidForExecution()

    fun approve(decidedBy: String = "USER", timestamp: Long = System.currentTimeMillis()): ApprovalRequest = copy(
        status = ApprovalStatus.APPROVED,
        decision = ApprovalDecision(status = ApprovalStatus.APPROVED, decidedBy = decidedBy, decidedAt = timestamp)
    )

    fun reject(reason: String? = null, timestamp: Long = System.currentTimeMillis()): ApprovalRequest = copy(
        status = ApprovalStatus.REJECTED,
        decision = ApprovalDecision(status = ApprovalStatus.REJECTED, decidedAt = timestamp, notes = reason)
    )

    fun expire(timestamp: Long = System.currentTimeMillis()): ApprovalRequest = copy(
        status = ApprovalStatus.EXPIRED,
        decision = ApprovalDecision(status = ApprovalStatus.EXPIRED, decidedAt = timestamp, notes = "Approval request timed out")
    )
}
