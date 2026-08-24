package com.example.core.execution

import com.example.core.data.local.dao.AgentEventDao
import com.example.core.data.local.dao.EvidenceDao
import com.example.core.data.local.dao.StepDao
import com.example.core.data.local.dao.TaskDao
import com.example.core.data.local.entity.AgentEventEntity
import com.example.core.data.local.entity.EvidenceEntity
import com.example.core.domain.agent.AgentStep
import com.example.core.domain.error.ArishException
import com.example.core.domain.execution.ExecutionStatus
import com.example.core.domain.execution.SideEffectSemantics
import com.example.core.domain.execution.ToolOutcome
import com.example.core.domain.security.ApprovalRequest
import com.example.core.domain.security.AuthenticationRequirement
import com.example.core.domain.security.RiskLevel
import com.example.core.domain.validation.ToolArgumentValidator
import com.example.core.domain.verification.VerificationEvidence
import com.example.core.domain.verification.VerificationResult
import com.example.core.security.SecurityGate
import com.example.core.security.SecurityGateDecision
import com.example.core.security.audit.SecurityAuditLogger
import com.example.core.tool.ToolRegistry
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * Result data model emitted by ToolExecutor.
 */
data class StepExecutionResult(
    val stepId: String,
    val status: ExecutionStatus,
    val outcome: ToolOutcome,
    val verificationResult: VerificationResult,
    val isSuccess: Boolean
)

/**
 * Production-grade deterministic Tool Execution Core.
 *
 * Pipeline:
 * Budget Validation
 * → Idempotency Check
 * → Tool Registry Lookup
 * → Schema Argument Validation
 * → Security Gate Enforcement
 * → Step Dispatch State Update
 * → Bound-limited Execution
 * → Evidence & Verification Engine
 * → State Transition & Entity Persistence
 * → Audit Event Logging
 */
class ToolExecutor(
    private val toolRegistry: ToolRegistry,
    private val securityGate: SecurityGate,
    private val idempotencyGuard: IdempotencyGuard,
    private val verificationEngine: VerificationEngine,
    private val stepDao: StepDao? = null,
    private val taskDao: TaskDao? = null,
    private val evidenceDao: EvidenceDao? = null,
    private val agentEventDao: AgentEventDao? = null,
    private val securityAuditLogger: SecurityAuditLogger? = null
) {

    suspend fun executeStep(
        context: ExecutionContext,
        step: AgentStep,
        existingApproval: ApprovalRequest? = null
    ): StepExecutionResult {
        val now = System.currentTimeMillis()

        // 1. Budget Verification
        try {
            context.checkBudget()
        } catch (e: ArishException.BudgetExceededException) {
            val failureOutcome = ToolOutcome.failure(
                toolId = step.toolId,
                errorMessage = e.message ?: "Budget exceeded",
                durationMs = 0
            )
            val verification = VerificationResult.unverifiedOrFailed(
                stepId = step.stepId,
                explanation = e.message ?: "Budget exceeded",
                timestamp = now
            )
            logEvent(context.taskId, step.stepId, "BUDGET_EXCEEDED", e.message ?: "")
            return StepExecutionResult(step.stepId, ExecutionStatus.FAILED, failureOutcome, verification, false)
        }

        // 2. Idempotency Check
        val idempotencyCheck = idempotencyGuard.checkKey(step.idempotencyKey)
        if (idempotencyCheck is IdempotencyCheckResult.AlreadyExecuted) {
            val record = idempotencyCheck.record
            val recordedStatus = try {
                ExecutionStatus.valueOf(record.executionStatus)
            } catch (_: Exception) {
                ExecutionStatus.UNKNOWN
            }

            val cachedOutcome = ToolOutcome(
                toolId = record.toolId,
                status = recordedStatus,
                rawResultData = emptyMap(),
                summaryText = record.cachedResultJson,
                sideEffectSemantics = SideEffectSemantics.NO_SIDE_EFFECT,
                executionDurationMs = 0
            )
            val verification = if (recordedStatus == ExecutionStatus.VERIFIED || recordedStatus == ExecutionStatus.EXECUTED) {
                VerificationResult.verified(
                    stepId = step.stepId,
                    evidence = VerificationEvidence(
                        evidenceId = "ev-cached-${UUID.randomUUID()}",
                        type = com.example.core.domain.verification.EvidenceType.LOCAL_DATABASE_ROW,
                        confidence = com.example.core.domain.verification.ConfidenceLevel.CERTAIN,
                        description = "Reused cached idempotent result: ${record.cachedResultJson}",
                        capturedAt = now
                    ),
                    explanation = "Result resolved from prior idempotent execution",
                    timestamp = now
                )
            } else {
                VerificationResult.unverifiedOrFailed(
                    stepId = step.stepId,
                    explanation = "Prior idempotent execution was $recordedStatus",
                    timestamp = now
                )
            }

            logEvent(context.taskId, step.stepId, "IDEMPOTENCY_HIT", "Reused key: ${step.idempotencyKey}")
            return StepExecutionResult(step.stepId, verification.finalExecutionStatus, cachedOutcome, verification, verification.isSuccess)
        }

        // 3. Tool Lookup
        val tool = toolRegistry.find(step.toolId)
        if (tool == null) {
            val err = "Tool '${step.toolId}' is not registered in ToolRegistry"
            val failureOutcome = ToolOutcome.failure(step.toolId, err, durationMs = 0)
            val verification = VerificationResult.unverifiedOrFailed(step.stepId, err, timestamp = now)
            updateStepState(step, ExecutionStatus.FAILED, failureOutcome)
            logEvent(context.taskId, step.stepId, "TOOL_NOT_FOUND", err)
            return StepExecutionResult(step.stepId, ExecutionStatus.FAILED, failureOutcome, verification, false)
        }

        // 4. Schema Argument Validation
        try {
            ToolArgumentValidator.validateArguments(tool.argumentSchema, step.inputArguments)
        } catch (e: ArishException.SchemaValidationException) {
            val failureOutcome = ToolOutcome.failure(step.toolId, e.message ?: "Schema validation failed", durationMs = 0)
            val verification = VerificationResult.unverifiedOrFailed(step.stepId, e.message ?: "Schema validation error", timestamp = now)
            updateStepState(step, ExecutionStatus.FAILED, failureOutcome)
            logEvent(context.taskId, step.stepId, "SCHEMA_VALIDATION_FAILED", e.message ?: "")
            return StepExecutionResult(step.stepId, ExecutionStatus.FAILED, failureOutcome, verification, false)
        }

        // 5. Security Gate Enforcement
        val authReq = when (step.riskEvaluation.level) {
            RiskLevel.CRITICAL -> AuthenticationRequirement.BIOMETRIC
            RiskLevel.HIGH -> AuthenticationRequirement.USER_CONFIRMATION
            else -> AuthenticationRequirement.NONE
        }

        val decision = securityGate.evaluateAndEnforce(
            taskId = context.taskId,
            stepId = step.stepId,
            toolId = tool.id,
            capabilityId = step.capabilityId,
            riskEvaluation = step.riskEvaluation,
            permissionRequirements = tool.requiredPermissions,
            authenticationRequirement = authReq,
            existingApproval = existingApproval
        )

        when (decision) {
            is SecurityGateDecision.Blocked -> {
                val failureOutcome = ToolOutcome.failure(
                    toolId = tool.id,
                    errorMessage = decision.exception.message ?: "Security Gate blocked execution",
                    durationMs = 0
                )
                val verification = VerificationResult.unverifiedOrFailed(
                    stepId = step.stepId,
                    explanation = decision.exception.message ?: "Security Gate blocked",
                    timestamp = now
                )
                updateStepState(step, ExecutionStatus.FAILED, failureOutcome)
                securityAuditLogger?.logSecurityEvent(
                    eventType = "SECURITY_GATE_BLOCKED",
                    metadata = mapOf(
                        "toolId" to tool.id,
                        "riskLevel" to step.riskEvaluation.level.name,
                        "reason" to (decision.exception.message ?: "Security blocked")
                    ),
                    taskId = context.taskId,
                    stepId = step.stepId
                )
                return StepExecutionResult(step.stepId, ExecutionStatus.FAILED, failureOutcome, verification, false)
            }
            is SecurityGateDecision.RequiresApproval -> {
                val failureOutcome = ToolOutcome.failure(
                    toolId = tool.id,
                    errorMessage = "Step requires user approval (ApprovalId: ${decision.approvalRequest.approvalId})",
                    durationMs = 0
                )
                val verification = VerificationResult.unverifiedOrFailed(
                    stepId = step.stepId,
                    explanation = "Awaiting user approval",
                    timestamp = now
                )
                updateStepState(step, ExecutionStatus.REQUESTED, failureOutcome)
                logEvent(context.taskId, step.stepId, "APPROVAL_REQUIRED", decision.approvalRequest.approvalId)
                return StepExecutionResult(step.stepId, ExecutionStatus.FAILED, failureOutcome, verification, false)
            }
            is SecurityGateDecision.Permitted -> {
                securityAuditLogger?.logSecurityEvent(
                    eventType = "SECURITY_GATE_PERMITTED",
                    metadata = mapOf(
                        "toolId" to tool.id,
                        "riskLevel" to step.riskEvaluation.level.name
                    ),
                    taskId = context.taskId,
                    stepId = step.stepId
                )
            }
        }

        // 6. Update step state to DISPATCHED
        updateStepState(step, ExecutionStatus.DISPATCHED, null)
        logEvent(context.taskId, step.stepId, "STEP_DISPATCHED", "Executing ${tool.id}")

        // 7. Bound-limited Tool Execution
        val executionStartTime = System.currentTimeMillis()
        val outcome: ToolOutcome = try {
            val timeoutMs = context.budget.maxExecutionTimeMs.coerceAtMost(30_000L)
            val result = withTimeoutOrNull(timeoutMs) {
                tool.execute(step.inputArguments)
            }
            result ?: ToolOutcome.failure(
                toolId = tool.id,
                errorMessage = "Tool execution timed out after ${timeoutMs}ms",
                durationMs = System.currentTimeMillis() - executionStartTime
            )
        } catch (e: Exception) {
            ToolOutcome.failure(
                toolId = tool.id,
                errorMessage = e.message ?: "Unhandled exception in tool",
                errorDetails = e.stackTraceToString(),
                durationMs = System.currentTimeMillis() - executionStartTime
            )
        }

        // 8. Evidence & Verification
        val verification = verificationEngine.verify(step.stepId, tool, outcome)
        val finalStatus = verification.finalExecutionStatus

        // 9. Persist evidence items to database
        if (evidenceDao != null && verification.evidenceList.isNotEmpty()) {
            for (ev in verification.evidenceList) {
                val entity = EvidenceEntity(
                    evidenceId = ev.evidenceId,
                    stepId = step.stepId,
                    evidenceType = ev.type.name,
                    confidence = ev.confidence.name,
                    description = ev.description,
                    artifactUri = ev.artifactUri,
                    capturedAt = ev.capturedAt
                )
                try {
                    evidenceDao.insertEvidence(entity)
                } catch (_: Exception) {}
            }
        }

        // 10. Record Idempotency
        val summaryForCache = if (outcome.summaryText.isNotBlank()) outcome.summaryText else (outcome.errorMessage ?: "")
        idempotencyGuard.recordExecution(
            key = step.idempotencyKey,
            taskId = context.taskId,
            stepId = step.stepId,
            toolId = tool.id,
            args = step.inputArguments,
            status = finalStatus,
            resultSummary = summaryForCache,
            timestamp = System.currentTimeMillis()
        )

        // 11. Update Step state in database
        updateStepState(step, finalStatus, outcome)
        logEvent(context.taskId, step.stepId, "STEP_COMPLETED", "Status: $finalStatus | ${outcome.summaryText}")

        return StepExecutionResult(
            stepId = step.stepId,
            status = finalStatus,
            outcome = outcome,
            verificationResult = verification,
            isSuccess = verification.isSuccess
        )
    }

    private suspend fun updateStepState(
        step: AgentStep,
        status: ExecutionStatus,
        outcome: ToolOutcome?
    ) {
        if (stepDao == null) return
        try {
            val existing = stepDao.getStepById(step.stepId)
            if (existing != null) {
                val updated = existing.copy(
                    status = status.name,
                    outcomeSummary = outcome?.summaryText ?: outcome?.errorMessage ?: existing.outcomeSummary,
                    outcomeDataJson = outcome?.rawResultData?.toString() ?: existing.outcomeDataJson,
                    sideEffectSemantics = outcome?.sideEffectSemantics?.name ?: existing.sideEffectSemantics,
                    completedAt = if (status.isTerminal || status == ExecutionStatus.VERIFIED || status == ExecutionStatus.EXECUTED) System.currentTimeMillis() else existing.completedAt
                )
                stepDao.updateStep(updated)
            }
        } catch (_: Exception) {}
    }

    private suspend fun logEvent(taskId: String, stepId: String, eventType: String, payload: String) {
        if (agentEventDao == null) return
        try {
            agentEventDao.insertEvent(
                AgentEventEntity(
                    taskId = taskId,
                    stepId = stepId,
                    eventType = eventType,
                    payloadJson = payload,
                    timestamp = System.currentTimeMillis()
                )
            )
        } catch (_: Exception) {}
    }
}
