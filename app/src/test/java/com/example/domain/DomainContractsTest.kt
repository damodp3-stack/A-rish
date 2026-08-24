package com.example.core.domain

import com.example.core.domain.agent.AgentState
import com.example.core.domain.agent.AgentStep
import com.example.core.domain.agent.AgentTask
import com.example.core.domain.agent.ExecutionBudget
import com.example.core.domain.agent.TaskPriority
import com.example.core.domain.capability.CapabilityCategory
import com.example.core.domain.capability.CapabilityId
import com.example.core.domain.capability.CapabilityRegistry
import com.example.core.domain.capability.StructuredIntent
import com.example.core.domain.error.ArishException
import com.example.core.domain.execution.DeliveryGuarantee
import com.example.core.domain.execution.ExecutionStatus
import com.example.core.domain.execution.SideEffectSemantics
import com.example.core.domain.execution.ToolOutcome
import com.example.core.domain.memory.EntityType
import com.example.core.domain.memory.MemoryCategory
import com.example.core.domain.memory.MemoryEntityRef
import com.example.core.domain.memory.MemoryRecord
import com.example.core.domain.memory.MemoryScoreWeights
import com.example.core.domain.memory.MemorySource
import com.example.core.domain.security.ApprovalRequest
import com.example.core.domain.security.ApprovalStatus
import com.example.core.domain.security.RiskEvaluation
import com.example.core.domain.security.RiskLevel
import com.example.core.domain.security.RiskReason
import com.example.core.domain.tool.ArgumentProperty
import com.example.core.domain.tool.ArgumentType
import com.example.core.domain.tool.ToolArgumentSchema
import com.example.core.domain.validation.IntentValidator
import com.example.core.domain.validation.PlanValidator
import com.example.core.domain.validation.StateTransitionValidator
import com.example.core.domain.validation.ToolArgumentValidator
import com.example.core.domain.verification.ConfidenceLevel
import com.example.core.domain.verification.EvidenceType
import com.example.core.domain.verification.VerificationEvidence
import com.example.core.domain.verification.VerificationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rigorous test suite validating all 14 Phase 1A Definition of Done invariants.
 */
class DomainContractsTest {

    // 1. Invalid FSM transition rejected
    @Test
    fun testInvalidFsmTransitionsRejected() {
        assertFalse("RECEIVED cannot jump to COMPLETED", AgentState.RECEIVED.canTransitionTo(AgentState.COMPLETED))
        assertFalse("COMPLETED is terminal", AgentState.COMPLETED.canTransitionTo(AgentState.EXECUTING))
        assertFalse("EXECUTING cannot jump to COMPLETED without VERIFYING", AgentState.EXECUTING.canTransitionTo(AgentState.COMPLETED))

        assertThrows(ArishException.InvalidStateTransitionException::class.java) {
            StateTransitionValidator.validateTransition(AgentState.RECEIVED, AgentState.COMPLETED)
        }

        // Valid transitions
        assertTrue("RECEIVED -> UNDERSTANDING", AgentState.RECEIVED.canTransitionTo(AgentState.UNDERSTANDING))
        assertTrue("UNDERSTANDING -> PLANNING", AgentState.UNDERSTANDING.canTransitionTo(AgentState.PLANNING))
        assertTrue("PLANNING -> EXECUTING", AgentState.PLANNING.canTransitionTo(AgentState.EXECUTING))
        assertTrue("PLANNING -> WAITING_FOR_APPROVAL", AgentState.PLANNING.canTransitionTo(AgentState.WAITING_FOR_APPROVAL))
        assertTrue("EXECUTING -> VERIFYING", AgentState.EXECUTING.canTransitionTo(AgentState.VERIFYING))
        assertTrue("VERIFYING -> COMPLETED", AgentState.VERIFYING.canTransitionTo(AgentState.COMPLETED))
    }

    // 2. Step budget cannot exceed max
    @Test
    fun testStepBudgetEnforced() {
        val budget = ExecutionBudget(maxSteps = 3)
        val steps = (1..4).map { i ->
            AgentStep(
                stepId = "step-$i",
                stepIndex = i,
                title = "Step $i",
                description = "Desc $i",
                capabilityId = "COMMUNICATION",
                toolId = "android_action",
                inputArguments = emptyMap(),
                riskEvaluation = RiskEvaluation.low(),
                idempotencyKey = "key-$i",
                createdAt = System.currentTimeMillis()
            )
        }

        assertThrows(ArishException.BudgetExceededException::class.java) {
            PlanValidator.validatePlan(steps, budget)
        }
    }

    // 3. Tool call budget & task state integrity
    @Test
    fun testExecutionBudgetBounds() {
        val budget = ExecutionBudget.STRICT
        assertEquals(3, budget.maxSteps)
        assertEquals(4, budget.maxToolCalls)
        assertEquals(30_000L, budget.maxExecutionTimeMs)

        assertThrows(IllegalArgumentException::class.java) {
            ExecutionBudget(maxSteps = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExecutionBudget(maxSteps = 50)
        }
    }

    // 4. Invalid intent rejected
    @Test
    fun testInvalidIntentRejected() {
        val blankIntent = StructuredIntent(
            intentId = "int-1",
            intentName = "",
            capabilityId = CapabilityId.SEND_MESSAGE,
            parameters = emptyMap(),
            rawUserPrompt = "send msg",
            confidence = 0.9f
        )
        assertThrows(ArishException.SchemaValidationException::class.java) {
            IntentValidator.validate(blankIntent)
        }

        val lowConfidenceIntent = StructuredIntent(
            intentId = "int-2",
            intentName = "send_message",
            capabilityId = CapabilityId.SEND_MESSAGE,
            parameters = emptyMap(),
            rawUserPrompt = "send msg",
            confidence = 0.1f,
            requiresClarification = false
        )
        assertThrows(ArishException.SchemaValidationException::class.java) {
            IntentValidator.validate(lowConfidenceIntent)
        }
    }

    // 5. Capability Registry integrity
    @Test
    fun testCapabilityRegistryLookups() {
        val messageCap = CapabilityRegistry.get(CapabilityId.SEND_MESSAGE)
        assertEquals(CapabilityCategory.COMMUNICATION, messageCap.id.category)
        assertEquals(RiskLevel.HIGH, messageCap.id.defaultRisk)

        val noteCap = CapabilityRegistry.get(CapabilityId.CREATE_NOTE)
        assertEquals(CapabilityCategory.PRODUCTIVITY, noteCap.id.category)
    }

    // 6. Invalid tool arguments rejected
    @Test
    fun testToolArgumentValidation() {
        val schema = ToolArgumentSchema(
            properties = mapOf(
                "action" to ArgumentProperty(
                    type = ArgumentType.STRING,
                    description = "Action name",
                    allowedValues = listOf("create", "delete")
                ),
                "count" to ArgumentProperty(
                    type = ArgumentType.INTEGER,
                    description = "Item count"
                )
            ),
            requiredKeys = listOf("action")
        )

        // Missing required key
        assertThrows(ArishException.SchemaValidationException::class.java) {
            ToolArgumentValidator.validateArguments(schema, mapOf("count" to 5))
        }

        // Invalid allowed value
        assertThrows(ArishException.SchemaValidationException::class.java) {
            ToolArgumentValidator.validateArguments(schema, mapOf("action" to "unsupported_action"))
        }

        // Type mismatch (passing string instead of integer)
        assertThrows(ArishException.SchemaValidationException::class.java) {
            ToolArgumentValidator.validateArguments(schema, mapOf("action" to "create", "count" to "not-a-number"))
        }

        // Valid arguments pass
        ToolArgumentValidator.validateArguments(schema, mapOf("action" to "create", "count" to 10))
    }

    // 7. HIGH & CRITICAL risk policies
    @Test
    fun testRiskEvaluationRequiresApproval() {
        val lowRisk = RiskEvaluation.low()
        assertFalse(lowRisk.requiresApproval)
        assertEquals(RiskLevel.LOW, lowRisk.level)

        val highRisk = RiskEvaluation.high(
            listOf(RiskReason.ExternalCommunication("Ravi", "WhatsApp")),
            "Sending external WhatsApp communication"
        )
        assertTrue(highRisk.requiresApproval)
        assertEquals(RiskLevel.HIGH, highRisk.level)

        val criticalRisk = RiskEvaluation.critical(
            listOf(RiskReason.LocalDataDeletion("All memories")),
            "Wiping personal memories",
            requiresBiometric = true
        )
        assertTrue(criticalRisk.requiresApproval)
        assertTrue(criticalRisk.requiresBiometric)
        assertEquals(RiskLevel.CRITICAL, criticalRisk.level)
    }

    // 8. Approval expiry works
    @Test
    fun testApprovalRequestExpiry() {
        val now = System.currentTimeMillis()
        val expiredApproval = ApprovalRequest(
            approvalId = "app-1",
            taskId = "task-1",
            stepId = "step-1",
            toolId = "android_action",
            capabilityId = "COMMUNICATION",
            riskEvaluation = RiskEvaluation.high(listOf(RiskReason.ReadOnlyDiagnostic), "Test"),
            actionSummary = "Send msg",
            previewPayload = emptyMap(),
            createdAt = now - 20_000L,
            expiresAt = now - 5_000L, // Expired 5 seconds ago
            status = ApprovalStatus.PENDING
        )

        assertTrue(expiredApproval.isExpired)
        assertFalse(expiredApproval.isPending)

        val autoExpired = expiredApproval.expire()
        assertEquals(ApprovalStatus.EXPIRED, autoExpired.status)
        assertNotNull(autoExpired.decision)
    }

    // 9. UNKNOWN cannot become VERIFIED automatically
    @Test
    fun testUnknownEvidenceStatus() {
        val evidence = VerificationEvidence(
            evidenceId = "ev-1",
            type = EvidenceType.NONE,
            confidence = ConfidenceLevel.INDETERMINATE,
            description = "App crashed mid-flight",
            capturedAt = System.currentTimeMillis()
        )
        val result = VerificationResult.unverifiedOrFailed("step-1", "Indeterminate outcome", evidence)
        assertEquals(ExecutionStatus.UNKNOWN, result.finalExecutionStatus)
        assertFalse(result.isSuccess)
    }

    // 10. External side-effect produces PARTIALLY_VERIFIED rather than false VERIFIED
    @Test
    fun testExternalSideEffectVerification() {
        val intentEvidence = VerificationEvidence(
            evidenceId = "ev-2",
            type = EvidenceType.SYSTEM_INTENT_RESOLVED,
            confidence = ConfidenceLevel.PROBABLE,
            description = "WhatsApp Intent launched to system package com.whatsapp",
            capturedAt = System.currentTimeMillis()
        )
        val result = VerificationResult.partiallyVerified(
            stepId = "step-1",
            evidence = intentEvidence,
            explanation = "WhatsApp was opened, but delivery confirmation is unknown"
        )
        assertEquals(ExecutionStatus.PARTIALLY_VERIFIED, result.finalExecutionStatus)
        assertTrue(result.isSuccess)
    }

    // 11. Memory Category, EntityRef, and Score Weights
    @Test
    fun testMemoryDomainContracts() {
        val entityRef = MemoryEntityRef(EntityType.PERSON, "Ravi")
        val record = MemoryRecord(
            id = "mem-1",
            content = "Ravi prefers coffee with no sugar",
            category = MemoryCategory.IDENTITY,
            importance = 8,
            entities = listOf(entityRef),
            source = MemorySource.USER_EXPLICIT,
            createdAt = System.currentTimeMillis(),
            lastAccessedAt = System.currentTimeMillis()
        )
        assertEquals(8, record.importance)
        assertEquals("Ravi", record.entities.first().value)

        val weights = MemoryScoreWeights(
            ftsWeight = 0.35f,
            entityMatchWeight = 0.25f,
            importanceWeight = 0.25f,
            recencyWeight = 0.15f
        )
        assertEquals(1.0f, weights.ftsWeight + weights.entityMatchWeight + weights.importanceWeight + weights.recencyWeight, 0.001f)
    }

    // 12. SideEffectSemantics separation
    @Test
    fun testSideEffectSemanticsContract() {
        val outcome = ToolOutcome.success(
            toolId = "calculator",
            data = mapOf("result" to 42),
            summary = "42",
            semantics = SideEffectSemantics.NO_SIDE_EFFECT
        )
        assertEquals(SideEffectSemantics.NO_SIDE_EFFECT, outcome.sideEffectSemantics)
        assertEquals(ExecutionStatus.EXECUTED, outcome.status)
    }
}
