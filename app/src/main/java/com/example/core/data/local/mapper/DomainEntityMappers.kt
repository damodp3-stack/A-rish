package com.example.core.data.local.mapper

import com.example.core.data.local.entity.AgentEventEntity
import com.example.core.data.local.entity.ApprovalEntity
import com.example.core.data.local.entity.EvidenceEntity
import com.example.core.data.local.entity.IdempotencyEntity
import com.example.core.data.local.entity.MemoryEntity
import com.example.core.data.local.entity.StepEntity
import com.example.core.data.local.entity.TaskEntity
import com.example.core.domain.agent.AgentState
import com.example.core.domain.agent.AgentStep
import com.example.core.domain.agent.AgentTask
import com.example.core.domain.agent.ExecutionBudget
import com.example.core.domain.agent.TaskPriority
import com.example.core.domain.execution.ExecutionStatus
import com.example.core.domain.execution.SideEffectSemantics
import com.example.core.domain.execution.ToolOutcome
import com.example.core.domain.memory.EntityType
import com.example.core.domain.memory.MemoryCategory
import com.example.core.domain.memory.MemoryEntityRef
import com.example.core.domain.memory.MemoryRecord
import com.example.core.domain.memory.MemorySource
import com.example.core.domain.security.ApprovalDecision
import com.example.core.domain.security.ApprovalRequest
import com.example.core.domain.security.ApprovalStatus
import com.example.core.domain.security.RiskEvaluation
import com.example.core.domain.security.RiskLevel
import com.example.core.domain.security.RiskReason
import com.example.core.domain.verification.ConfidenceLevel
import com.example.core.domain.verification.EvidenceType
import com.example.core.domain.verification.VerificationEvidence
import com.example.core.domain.verification.VerificationResult
import org.json.JSONArray
import org.json.JSONObject

/**
 * Deterministic mappers translating between pure Phase 1A Domain models and Room SQLite Entities.
 */
object DomainEntityMappers {

    // --- Task Mapping ---
    fun TaskEntity.toDomain(steps: List<AgentStep> = emptyList()): AgentTask = AgentTask(
        taskId = taskId,
        title = title,
        userPrompt = userPrompt,
        structuredIntentId = structuredIntentId,
        priority = runCatching { TaskPriority.valueOf(priority) }.getOrDefault(TaskPriority.NORMAL),
        state = runCatching { AgentState.valueOf(state) }.getOrDefault(AgentState.RECEIVED),
        budget = ExecutionBudget(
            maxSteps = maxSteps,
            maxToolCalls = maxToolCalls,
            maxExecutionTimeMs = maxExecutionTimeMs,
            maxRetriesPerStep = maxRetriesPerStep
        ),
        steps = steps,
        currentStepIndex = currentStepIndex,
        totalToolCallsCount = totalToolCallsCount,
        failureReason = failureReason,
        createdAt = createdAt,
        updatedAt = updatedAt,
        completedAt = completedAt
    )

    fun AgentTask.toEntity(): TaskEntity = TaskEntity(
        taskId = taskId,
        title = title,
        userPrompt = userPrompt,
        structuredIntentId = structuredIntentId,
        priority = priority.name,
        state = state.name,
        maxSteps = budget.maxSteps,
        maxToolCalls = budget.maxToolCalls,
        maxExecutionTimeMs = budget.maxExecutionTimeMs,
        maxRetriesPerStep = budget.maxRetriesPerStep,
        currentStepIndex = currentStepIndex,
        totalToolCallsCount = totalToolCallsCount,
        failureReason = failureReason,
        createdAt = createdAt,
        updatedAt = updatedAt,
        completedAt = completedAt
    )

    // --- Step Mapping ---
    fun StepEntity.toDomain(evidenceList: List<VerificationEvidence> = emptyList()): AgentStep {
        val inputArgs = parseJsonToMap(inputArgumentsJson)
        val riskLvl = runCatching { RiskLevel.valueOf(riskLevel) }.getOrDefault(RiskLevel.LOW)
        val execStatus = runCatching { ExecutionStatus.valueOf(status) }.getOrDefault(ExecutionStatus.REQUESTED)
        val semantics = sideEffectSemantics?.let { runCatching { SideEffectSemantics.valueOf(it) }.getOrNull() }
            ?: SideEffectSemantics.NO_SIDE_EFFECT

        val outcome = if (outcomeSummary != null) {
            ToolOutcome(
                toolId = toolId,
                status = execStatus,
                rawResultData = parseJsonToMap(outcomeDataJson ?: "{}"),
                summaryText = outcomeSummary,
                sideEffectSemantics = semantics
            )
        } else null

        val verification = if (evidenceList.isNotEmpty()) {
            VerificationResult(
                stepId = stepId,
                finalExecutionStatus = execStatus,
                evidenceList = evidenceList,
                explanationText = outcomeSummary ?: "Verified with evidence",
                isSuccess = execStatus.isSuccessful,
                verifiedAt = completedAt ?: System.currentTimeMillis()
            )
        } else null

        return AgentStep(
            stepId = stepId,
            stepIndex = stepIndex,
            title = title,
            description = description,
            capabilityId = capabilityId,
            toolId = toolId,
            inputArguments = inputArgs,
            riskEvaluation = RiskEvaluation(
                level = riskLvl,
                reasons = listOf(RiskReason.ReadOnlyDiagnostic),
                requiresApproval = riskLvl.requiresExplicitApproval,
                explanationText = "Persisted risk evaluation"
            ),
            status = execStatus,
            idempotencyKey = idempotencyKey,
            retryCount = retryCount,
            approvalId = approvalId,
            executionOutcome = outcome,
            verificationResult = verification,
            createdAt = createdAt,
            startedAt = startedAt,
            completedAt = completedAt
        )
    }

    fun AgentStep.toEntity(taskId: String): StepEntity = StepEntity(
        stepId = stepId,
        taskId = taskId,
        stepIndex = stepIndex,
        title = title,
        description = description,
        capabilityId = capabilityId,
        toolId = toolId,
        inputArgumentsJson = mapToJson(inputArguments),
        riskLevel = riskEvaluation.level.name,
        riskReasonsJson = "[]",
        status = status.name,
        idempotencyKey = idempotencyKey,
        retryCount = retryCount,
        approvalId = approvalId,
        outcomeSummary = executionOutcome?.summaryText,
        outcomeDataJson = executionOutcome?.let { mapToJson(it.rawResultData) },
        sideEffectSemantics = executionOutcome?.sideEffectSemantics?.name,
        createdAt = createdAt,
        startedAt = startedAt,
        completedAt = completedAt
    )

    // --- Approval Mapping ---
    fun ApprovalEntity.toDomain(): ApprovalRequest {
        val appStatus = runCatching { ApprovalStatus.valueOf(status) }.getOrDefault(ApprovalStatus.PENDING)
        val statusStr = decisionStatus
        val decision = if (!statusStr.isNullOrBlank()) {
            val validStatus = runCatching { ApprovalStatus.valueOf(statusStr) }.getOrDefault(ApprovalStatus.PENDING)
            ApprovalDecision(
                status = validStatus,
                decidedBy = decidedBy ?: "USER",
                decidedAt = decidedAt ?: System.currentTimeMillis(),
                notes = decisionNotes
            )
        } else null

        val riskLvl = runCatching { RiskLevel.valueOf(riskLevel) }.getOrDefault(RiskLevel.MEDIUM)

        return ApprovalRequest(
            approvalId = approvalId,
            taskId = taskId,
            stepId = stepId,
            toolId = toolId,
            capabilityId = capabilityId,
            riskEvaluation = RiskEvaluation(
                level = riskLvl,
                reasons = listOf(RiskReason.HighValueAction(actionSummary)),
                requiresApproval = true,
                explanationText = actionSummary
            ),
            actionSummary = actionSummary,
            previewPayload = parseJsonToMap(previewPayloadJson),
            createdAt = createdAt,
            expiresAt = expiresAt,
            status = appStatus,
            decision = decision
        )
    }

    fun ApprovalRequest.toEntity(): ApprovalEntity = ApprovalEntity(
        approvalId = approvalId,
        taskId = taskId,
        stepId = stepId,
        toolId = toolId,
        capabilityId = capabilityId,
        riskLevel = riskEvaluation.level.name,
        actionSummary = actionSummary,
        previewPayloadJson = mapToJson(previewPayload),
        createdAt = createdAt,
        expiresAt = expiresAt,
        status = status.name,
        decisionStatus = decision?.status?.name,
        decidedBy = decision?.decidedBy,
        decidedAt = decision?.decidedAt,
        decisionNotes = decision?.notes
    )

    // --- Evidence Mapping ---
    fun EvidenceEntity.toDomain(): VerificationEvidence = VerificationEvidence(
        evidenceId = evidenceId,
        type = runCatching { EvidenceType.valueOf(evidenceType) }.getOrDefault(EvidenceType.NONE),
        confidence = runCatching { ConfidenceLevel.valueOf(confidence) }.getOrDefault(ConfidenceLevel.INDETERMINATE),
        description = description,
        artifactUri = artifactUri,
        capturedAt = capturedAt
    )

    fun VerificationEvidence.toEntity(stepId: String): EvidenceEntity = EvidenceEntity(
        evidenceId = evidenceId,
        stepId = stepId,
        evidenceType = type.name,
        confidence = confidence.name,
        description = description,
        artifactUri = artifactUri,
        capturedAt = capturedAt
    )

    // --- Memory Mapping ---
    fun MemoryEntity.toDomain(): MemoryRecord {
        val cat = runCatching { MemoryCategory.valueOf(category) }.getOrDefault(MemoryCategory.FACT)
        val src = runCatching { MemorySource.valueOf(source) }.getOrDefault(MemorySource.USER_EXPLICIT)
        val entitiesList = parseEntitiesJson(entitiesJson)

        return MemoryRecord(
            id = id,
            content = content,
            category = cat,
            importance = importance,
            entities = entitiesList,
            source = src,
            createdAt = createdAt,
            lastAccessedAt = lastAccessedAt,
            accessCount = accessCount
        )
    }

    fun MemoryRecord.toEntity(): MemoryEntity = MemoryEntity(
        id = id,
        content = content,
        category = category.name,
        importance = importance,
        entitiesJson = serializeEntitiesJson(entities),
        source = source.name,
        createdAt = createdAt,
        lastAccessedAt = lastAccessedAt,
        accessCount = accessCount
    )

    // --- Helper JSON Functions (Pure Standard Library / Cross-platform) ---
    private fun mapToJson(map: Map<String, Any?>): String {
        val pairs = map.entries.map { (k, v) ->
            val serializedVal = when (v) {
                null -> "null"
                is Number, is Boolean -> v.toString()
                else -> "\"${escapeJson(v.toString())}\""
            }
            "\"${escapeJson(k)}\":$serializedVal"
        }
        return "{${pairs.joinToString(",")}}"
    }

    private fun parseJsonToMap(jsonStr: String): Map<String, Any?> {
        val trimmed = jsonStr.trim()
        val result = mutableMapOf<String, Any?>()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return result
        val inner = trimmed.substring(1, trimmed.length - 1).trim()
        if (inner.isEmpty()) return result

        val tokens = splitJsonTokens(inner)
        for (token in tokens) {
            val colonIdx = token.indexOf(':')
            if (colonIdx != -1) {
                val key = unescapeJson(token.substring(0, colonIdx).trim().removeSurrounding("\""))
                val rawVal = token.substring(colonIdx + 1).trim()
                val parsedVal: Any? = when {
                    rawVal == "null" -> null
                    rawVal == "true" -> true
                    rawVal == "false" -> false
                    rawVal.startsWith("\"") && rawVal.endsWith("\"") -> unescapeJson(rawVal.substring(1, rawVal.length - 1))
                    rawVal.toIntOrNull() != null -> rawVal.toInt()
                    rawVal.toLongOrNull() != null -> rawVal.toLong()
                    rawVal.toDoubleOrNull() != null -> rawVal.toDouble()
                    else -> rawVal
                }
                result[key] = parsedVal
            }
        }
        return result
    }

    private fun serializeEntitiesJson(entities: List<MemoryEntityRef>): String {
        val items = entities.map { "{\"type\":\"${it.type.name}\",\"value\":\"${escapeJson(it.value)}\"}" }
        return "[${items.joinToString(",")}]"
    }

    private fun parseEntitiesJson(jsonStr: String): List<MemoryEntityRef> {
        val trimmed = jsonStr.trim()
        val list = mutableListOf<MemoryEntityRef>()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return list
        val inner = trimmed.substring(1, trimmed.length - 1).trim()
        if (inner.isEmpty()) return list

        val objects = splitJsonTokens(inner)
        for (objStr in objects) {
            val map = parseJsonToMap(objStr)
            val typeStr = map["type"]?.toString() ?: EntityType.CUSTOM.name
            val valStr = map["value"]?.toString() ?: ""
            val type = runCatching { EntityType.valueOf(typeStr) }.getOrDefault(EntityType.CUSTOM)
            list.add(MemoryEntityRef(type, valStr))
        }
        return list
    }

    private fun splitJsonTokens(input: String): List<String> {
        val tokens = mutableListOf<String>()
        var inQuotes = false
        var depth = 0
        var start = 0
        for (i in input.indices) {
            val c = input[i]
            if (c == '\"' && (i == 0 || input[i - 1] != '\\')) {
                inQuotes = !inQuotes
            } else if (!inQuotes) {
                if (c == '{' || c == '[') depth++
                else if (c == '}' || c == ']') depth--
                else if (c == ',' && depth == 0) {
                    tokens.add(input.substring(start, i).trim())
                    start = i + 1
                }
            }
        }
        if (start < input.length) {
            val last = input.substring(start).trim()
            if (last.isNotEmpty()) tokens.add(last)
        }
        return tokens
    }

    private fun escapeJson(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")

    private fun unescapeJson(s: String): String =
        s.replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t").replace("\\\"", "\"").replace("\\\\", "\\")
}
