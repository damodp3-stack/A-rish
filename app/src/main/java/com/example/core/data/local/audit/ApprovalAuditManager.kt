package com.example.core.data.local.audit

import androidx.room.withTransaction
import com.example.core.data.local.ArishDatabase
import com.example.core.data.local.entity.AgentEventEntity
import com.example.core.data.local.entity.ApprovalEntity

/**
 * Ensures all Approval state transitions emit append-only, immutable [AgentEventEntity] records.
 *
 * Enforces the A-RISH persistence invariant:
 * "ApprovalEntity status is a mutable projection, while historical audit truth is immutable."
 */
class ApprovalAuditManager(private val database: ArishDatabase) {

    private val approvalDao = database.approvalDao()
    private val agentEventDao = database.agentEventDao()

    /**
     * Creates a new approval request and writes the immutable APPROVAL_CREATED event.
     */
    suspend fun createApproval(approval: ApprovalEntity) {
        database.withTransaction {
            approvalDao.insertApproval(approval)
            agentEventDao.insertEvent(
                AgentEventEntity(
                    taskId = approval.taskId,
                    stepId = approval.stepId,
                    eventType = "APPROVAL_CREATED",
                    payloadJson = """{"approvalId":"${approval.approvalId}","riskLevel":"${approval.riskLevel}","capabilityId":"${approval.capabilityId}","actionSummary":"${approval.actionSummary}"}""",
                    timestamp = approval.createdAt
                )
            )
        }
    }

    /**
     * Approves an existing request and writes the immutable APPROVAL_APPROVED event.
     */
    suspend fun approve(
        approvalId: String,
        decidedBy: String,
        decisionNotes: String? = null,
        timestamp: Long = System.currentTimeMillis()
    ): Boolean {
        return database.withTransaction {
            val existing = approvalDao.getApprovalById(approvalId) ?: return@withTransaction false
            if (existing.status != "PENDING") return@withTransaction false

            val updated = existing.copy(
                status = "APPROVED",
                decisionStatus = "APPROVED",
                decidedBy = decidedBy,
                decidedAt = timestamp,
                decisionNotes = decisionNotes
            )
            approvalDao.updateApproval(updated)
            agentEventDao.insertEvent(
                AgentEventEntity(
                    taskId = existing.taskId,
                    stepId = existing.stepId,
                    eventType = "APPROVAL_APPROVED",
                    payloadJson = """{"approvalId":"$approvalId","decidedBy":"$decidedBy","notes":"${decisionNotes ?: ""}"}""",
                    timestamp = timestamp
                )
            )
            true
        }
    }

    /**
     * Rejects an existing request and writes the immutable APPROVAL_REJECTED event.
     */
    suspend fun reject(
        approvalId: String,
        decidedBy: String,
        decisionNotes: String? = null,
        timestamp: Long = System.currentTimeMillis()
    ): Boolean {
        return database.withTransaction {
            val existing = approvalDao.getApprovalById(approvalId) ?: return@withTransaction false
            if (existing.status != "PENDING") return@withTransaction false

            val updated = existing.copy(
                status = "REJECTED",
                decisionStatus = "REJECTED",
                decidedBy = decidedBy,
                decidedAt = timestamp,
                decisionNotes = decisionNotes
            )
            approvalDao.updateApproval(updated)
            agentEventDao.insertEvent(
                AgentEventEntity(
                    taskId = existing.taskId,
                    stepId = existing.stepId,
                    eventType = "APPROVAL_REJECTED",
                    payloadJson = """{"approvalId":"$approvalId","decidedBy":"$decidedBy","notes":"${decisionNotes ?: ""}"}""",
                    timestamp = timestamp
                )
            )
            true
        }
    }

    /**
     * Cancels an existing approval request and writes the immutable APPROVAL_CANCELLED event.
     */
    suspend fun cancel(
        approvalId: String,
        reason: String,
        timestamp: Long = System.currentTimeMillis()
    ): Boolean {
        return database.withTransaction {
            val existing = approvalDao.getApprovalById(approvalId) ?: return@withTransaction false
            if (existing.status != "PENDING") return@withTransaction false

            val updated = existing.copy(
                status = "CANCELLED",
                decisionStatus = "CANCELLED",
                decisionNotes = reason,
                decidedAt = timestamp
            )
            approvalDao.updateApproval(updated)
            agentEventDao.insertEvent(
                AgentEventEntity(
                    taskId = existing.taskId,
                    stepId = existing.stepId,
                    eventType = "APPROVAL_CANCELLED",
                    payloadJson = """{"approvalId":"$approvalId","reason":"$reason"}""",
                    timestamp = timestamp
                )
            )
            true
        }
    }

    /**
     * Reaps expired pending approvals and writes the immutable APPROVAL_EXPIRED event.
     */
    suspend fun expireOldApprovals(currentTime: Long = System.currentTimeMillis()): Int {
        return database.withTransaction {
            val count = approvalDao.expireOldApprovals(currentTime)
            if (count > 0) {
                agentEventDao.insertEvent(
                    AgentEventEntity(
                        taskId = null,
                        stepId = null,
                        eventType = "APPROVAL_EXPIRED",
                        payloadJson = """{"expiredCount":$count,"reapedAt":$currentTime}""",
                        timestamp = currentTime
                    )
                )
            }
            count
        }
    }
}
