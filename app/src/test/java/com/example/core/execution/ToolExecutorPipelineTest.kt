package com.example.core.execution

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.data.local.ArishDatabase
import com.example.core.data.local.entity.StepEntity
import com.example.core.data.local.entity.TaskEntity
import com.example.core.domain.agent.AgentStep
import com.example.core.domain.agent.ExecutionBudget
import com.example.core.domain.capability.CapabilityId
import com.example.core.domain.execution.DeliveryGuarantee
import com.example.core.domain.execution.ExecutionStatus
import com.example.core.domain.execution.SideEffectSemantics
import com.example.core.domain.execution.ToolOutcome
import com.example.core.domain.security.ApprovalRequest
import com.example.core.domain.security.ApprovalStatus
import com.example.core.domain.security.AuthenticationRequirement
import com.example.core.domain.security.AuthenticationResult
import com.example.core.domain.security.PermissionBroker
import com.example.core.domain.security.PermissionRequirement
import com.example.core.domain.security.PermissionStatus
import com.example.core.domain.security.RiskEvaluation
import com.example.core.domain.security.RiskLevel
import com.example.core.domain.security.RiskReason
import com.example.core.domain.security.SecurityAuthenticator
import com.example.core.domain.tool.ArgumentProperty
import com.example.core.domain.tool.ArgumentType
import com.example.core.domain.tool.ToolArgumentSchema
import com.example.core.domain.tool.ToolContract
import com.example.core.domain.verification.ConfidenceLevel
import com.example.core.domain.verification.EvidenceType
import com.example.core.security.SecurityGate
import com.example.core.security.audit.SecurityAuditLogger
import com.example.core.tool.ToolRegistry
import com.example.core.tool.builtin.CalculateMathTool
import com.example.core.tool.builtin.GetCurrentTimeTool
import com.example.core.tool.builtin.MemorySearchTool
import com.example.core.tool.builtin.MemoryStoreTool
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ToolExecutorPipelineTest {

    private lateinit var context: Context
    private lateinit var db: ArishDatabase
    private lateinit var toolRegistry: ToolRegistry
    private lateinit var idempotencyGuard: IdempotencyGuard
    private lateinit var verificationEngine: VerificationEngine
    private lateinit var securityAuditLogger: SecurityAuditLogger
    private lateinit var permissionBroker: FakePermissionBroker
    private lateinit var authenticator: FakeAuthenticator
    private lateinit var securityGate: SecurityGate
    private lateinit var toolExecutor: ToolExecutor

    class FakePermissionBroker : PermissionBroker {
        var defaultStatus: PermissionStatus = PermissionStatus.GRANTED
        override fun checkPermission(permission: String): PermissionStatus = defaultStatus
        override fun checkPermissions(requirements: List<PermissionRequirement>): Map<String, PermissionStatus> =
            requirements.associate { it.permissionManifestKey to defaultStatus }
        override suspend fun requestPermission(permission: String, rationale: String?): PermissionStatus = defaultStatus
        override suspend fun requestPermissions(requirements: List<PermissionRequirement>): Map<String, PermissionStatus> =
            requirements.associate { it.permissionManifestKey to defaultStatus }
    }

    class FakeAuthenticator : SecurityAuthenticator {
        var isSuccess: Boolean = true
        override suspend fun authenticate(requirement: AuthenticationRequirement, promptTitle: String, promptSubtitle: String?): AuthenticationResult {
            return if (isSuccess) AuthenticationResult.Success(requirement, System.currentTimeMillis()) else AuthenticationResult.Failed("Authentication cancelled")
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, ArishDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        toolRegistry = ToolRegistry().apply {
            register(GetCurrentTimeTool())
            register(CalculateMathTool())
            register(MemoryStoreTool(db.memoryDao()))
            register(MemorySearchTool(db.memoryDao()))
        }

        idempotencyGuard = IdempotencyGuard(db.idempotencyDao())
        verificationEngine = VerificationEngine(db.memoryDao())
        securityAuditLogger = SecurityAuditLogger(db.agentEventDao())
        permissionBroker = FakePermissionBroker()
        authenticator = FakeAuthenticator()
        securityGate = SecurityGate(permissionBroker, authenticator)

        toolExecutor = ToolExecutor(
            toolRegistry = toolRegistry,
            securityGate = securityGate,
            idempotencyGuard = idempotencyGuard,
            verificationEngine = verificationEngine,
            stepDao = db.stepDao(),
            taskDao = db.taskDao(),
            evidenceDao = db.evidenceDao(),
            agentEventDao = db.agentEventDao(),
            securityAuditLogger = securityAuditLogger
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insertParentTask(taskId: String) {
        db.taskDao().insertTask(
            TaskEntity(
                taskId = taskId,
                title = "Test Task",
                userPrompt = "Run test step",
                structuredIntentId = null,
                priority = "NORMAL",
                state = "EXECUTING",
                maxSteps = 10,
                maxToolCalls = 10,
                maxExecutionTimeMs = 30000,
                maxRetriesPerStep = 3,
                currentStepIndex = 0,
                totalToolCallsCount = 0,
                failureReason = null,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    @Test
    fun `full pipeline executes calculate step and records verified status and idempotency`() = runBlocking {
        val taskId = "task-calc-1"
        val stepId = "step-calc-1"
        insertParentTask(taskId)

        db.stepDao().insertStep(
            StepEntity(
                stepId = stepId,
                taskId = taskId,
                stepIndex = 0,
                title = "Evaluate Math",
                description = "25 * 4",
                capabilityId = CapabilityId.CALCULATE_MATH.name,
                toolId = "calculate",
                inputArgumentsJson = """{"expression":"25 * 4"}""",
                riskLevel = "LOW",
                riskReasonsJson = "[]",
                status = "REQUESTED",
                idempotencyKey = "idemp-calc-1",
                createdAt = System.currentTimeMillis()
            )
        )

        val step = AgentStep(
            stepId = stepId,
            stepIndex = 0,
            title = "Evaluate Math",
            description = "25 * 4",
            capabilityId = CapabilityId.CALCULATE_MATH.name,
            toolId = "calculate",
            inputArguments = mapOf("expression" to "25 * 4"),
            riskEvaluation = RiskEvaluation.low(),
            status = ExecutionStatus.REQUESTED,
            idempotencyKey = "idemp-calc-1",
            createdAt = System.currentTimeMillis()
        )

        val execContext = ExecutionContext(
            taskId = taskId,
            stepId = stepId,
            stepIndex = 0,
            idempotencyKey = "idemp-calc-1",
            budget = ExecutionBudget.STANDARD
        )

        val result = toolExecutor.executeStep(execContext, step)

        assertTrue(result.isSuccess)
        assertEquals(ExecutionStatus.VERIFIED, result.status)
        assertEquals("25 * 4 = 100", result.outcome.summaryText)

        // Verify SQLite updates
        val stepInDb = db.stepDao().getStepById(stepId)
        assertEquals(ExecutionStatus.VERIFIED.name, stepInDb?.status)

        // Verify Idempotency record
        val idempRecord = db.idempotencyDao().getRecordByKey("idemp-calc-1")
        assertNotNull(idempRecord)
        assertEquals(ExecutionStatus.VERIFIED.name, idempRecord?.executionStatus)

        // Verify Evidence was recorded
        val evidenceList = db.evidenceDao().getEvidenceForStep(stepId)
        assertTrue(evidenceList.isNotEmpty())
        assertEquals(EvidenceType.OS_SERVICE_STATE.name, evidenceList.first().evidenceType)
        assertEquals(ConfidenceLevel.CERTAIN.name, evidenceList.first().confidence)
    }

    @Test
    fun `duplicate step execution returns cached outcome without re-invoking tool`() = runBlocking {
        val taskId = "task-dup-1"
        val stepId = "step-dup-1"
        val idempotencyKey = "idemp-dup-key"
        insertParentTask(taskId)

        val callCounter = AtomicInteger(0)
        val customTool = object : ToolContract {
            override val id: String = "counting_tool"
            override val name: String = "Counting Tool"
            override val description: String = "Counts invocations"
            override val primaryCapability: CapabilityId = CapabilityId.GET_CURRENT_TIME
            override val baseRiskLevel: RiskLevel = RiskLevel.LOW
            override val sideEffectSemantics: SideEffectSemantics = SideEffectSemantics.NO_SIDE_EFFECT
            override val deliveryGuarantee: DeliveryGuarantee = DeliveryGuarantee.EXACTLY_ONCE
            override val requiredPermissions: List<PermissionRequirement> = emptyList()
            override val argumentSchema: ToolArgumentSchema = ToolArgumentSchema(emptyMap(), emptyList())
            override suspend fun execute(args: Map<String, Any?>): ToolOutcome {
                val count = callCounter.incrementAndGet()
                return ToolOutcome.success(id, mapOf("count" to count), "Invocation count: $count")
            }
        }
        toolRegistry.register(customTool)

        val step = AgentStep(
            stepId = stepId,
            stepIndex = 0,
            title = "Counting",
            description = "Counting",
            capabilityId = CapabilityId.GET_CURRENT_TIME.name,
            toolId = "counting_tool",
            inputArguments = emptyMap(),
            riskEvaluation = RiskEvaluation.low(),
            status = ExecutionStatus.REQUESTED,
            idempotencyKey = idempotencyKey,
            createdAt = System.currentTimeMillis()
        )

        val execContext = ExecutionContext(
            taskId = taskId,
            stepId = stepId,
            stepIndex = 0,
            idempotencyKey = idempotencyKey
        )

        // 1st Execution
        val result1 = toolExecutor.executeStep(execContext, step)
        assertEquals(1, callCounter.get())
        assertTrue(result1.isSuccess)

        // 2nd Execution with same idempotency key
        val result2 = toolExecutor.executeStep(execContext, step)
        assertEquals("Tool must NOT be invoked again on idempotency hit", 1, callCounter.get())
        assertTrue(result2.isSuccess)
        assertEquals(result1.status, result2.status)
    }

    @Test
    fun `budget exhaustion blocks execution before tool is dispatched`() = runBlocking {
        val taskId = "task-budget-1"
        val stepId = "step-budget-1"
        insertParentTask(taskId)

        val step = AgentStep(
            stepId = stepId,
            stepIndex = 0,
            title = "Math",
            description = "Math",
            capabilityId = CapabilityId.CALCULATE_MATH.name,
            toolId = "calculate",
            inputArguments = mapOf("expression" to "1 + 1"),
            riskEvaluation = RiskEvaluation.low(),
            status = ExecutionStatus.REQUESTED,
            idempotencyKey = "idemp-budget-1",
            createdAt = System.currentTimeMillis()
        )

        // Context with tool calls already at maximum
        val exhaustedContext = ExecutionContext(
            taskId = taskId,
            stepId = stepId,
            stepIndex = 0,
            idempotencyKey = "idemp-budget-1",
            budget = ExecutionBudget(maxSteps = 5, maxToolCalls = 2, maxExecutionTimeMs = 30000),
            currentToolCallCount = 2 // at limit
        )

        val result = toolExecutor.executeStep(exhaustedContext, step)

        assertFalse(result.isSuccess)
        assertEquals(ExecutionStatus.FAILED, result.status)
        assertTrue(result.outcome.errorMessage?.contains("budget exceeded", ignoreCase = true) == true)
    }

    @Test
    fun `security gate blocks high-risk action without approval`() = runBlocking {
        val taskId = "task-highrisk-1"
        val stepId = "step-highrisk-1"
        insertParentTask(taskId)

        val highRiskStep = AgentStep(
            stepId = stepId,
            stepIndex = 0,
            title = "Delete Data",
            description = "High risk deletion",
            capabilityId = CapabilityId.DELETE_NOTE.name,
            toolId = "calculate", // use registered tool for test
            inputArguments = mapOf("expression" to "1 + 1"),
            riskEvaluation = RiskEvaluation.high(
                reasons = listOf(RiskReason.LocalDataDeletion("All user memos")),
                explanation = "Requires explicit user confirmation"
            ),
            status = ExecutionStatus.REQUESTED,
            idempotencyKey = "idemp-risk-1",
            createdAt = System.currentTimeMillis()
        )

        val execContext = ExecutionContext(
            taskId = taskId,
            stepId = stepId,
            stepIndex = 0,
            idempotencyKey = "idemp-risk-1"
        )

        // Without existing approval -> should require approval
        val result = toolExecutor.executeStep(execContext, highRiskStep, existingApproval = null)

        assertFalse(result.isSuccess)
        assertTrue(result.outcome.errorMessage?.contains("requires user approval") == true)

        // With approved ApprovalRequest -> should execute successfully
        val approvedRequest = ApprovalRequest(
            approvalId = "appr-123",
            taskId = taskId,
            stepId = stepId,
            toolId = "calculate",
            capabilityId = CapabilityId.DELETE_NOTE.name,
            riskEvaluation = highRiskStep.riskEvaluation,
            actionSummary = "Approved",
            previewPayload = emptyMap(),
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 60000,
            status = ApprovalStatus.APPROVED
        )

        val approvedResult = toolExecutor.executeStep(execContext, highRiskStep, existingApproval = approvedRequest)
        assertTrue("Approved high-risk step must execute", approvedResult.isSuccess)
    }

    @Test
    fun `transactional memory_store produces verified LOCAL_DATABASE_ROW evidence`() = runBlocking {
        val taskId = "task-mem-1"
        val stepId = "step-mem-1"
        val idempKey = "idemp-mem-store"
        insertParentTask(taskId)

        val step = AgentStep(
            stepId = stepId,
            stepIndex = 0,
            title = "Store Memory",
            description = "Remember user preference",
            capabilityId = CapabilityId.REMEMBER_FACT.name,
            toolId = "memory_store",
            inputArguments = mapOf(
                "fact" to "User works in Quantum Computing",
                "category" to "WORK",
                "importance" to 9
            ),
            riskEvaluation = RiskEvaluation.low(),
            status = ExecutionStatus.REQUESTED,
            idempotencyKey = idempKey,
            createdAt = System.currentTimeMillis()
        )

        val execContext = ExecutionContext(
            taskId = taskId,
            stepId = stepId,
            stepIndex = 0,
            idempotencyKey = idempKey
        )

        val result = toolExecutor.executeStep(execContext, step)

        assertTrue(result.isSuccess)
        assertEquals(ExecutionStatus.VERIFIED, result.status)

        // Check SQLite evidence table
        val evidence = db.evidenceDao().getEvidenceForStep(stepId)
        assertTrue(evidence.isNotEmpty())
        assertEquals(EvidenceType.LOCAL_DATABASE_ROW.name, evidence.first().evidenceType)
        assertEquals(ConfidenceLevel.CERTAIN.name, evidence.first().confidence)
    }

    @Test
    fun `external side effect produces INDETERMINATE confidence and PARTIALLY_VERIFIED status`() = runBlocking {
        val taskId = "task-ext-1"
        val stepId = "step-ext-1"
        val idempKey = "idemp-ext-1"
        insertParentTask(taskId)

        val externalTool = object : ToolContract {
            override val id: String = "external_dispatch_tool"
            override val name: String = "External Dispatch Tool"
            override val description: String = "Sends external message"
            override val primaryCapability: CapabilityId = CapabilityId.SEND_MESSAGE
            override val baseRiskLevel: RiskLevel = RiskLevel.LOW
            override val sideEffectSemantics: SideEffectSemantics = SideEffectSemantics.EXTERNAL_SIDE_EFFECT
            override val deliveryGuarantee: DeliveryGuarantee = DeliveryGuarantee.AT_MOST_ONCE
            override val requiredPermissions: List<PermissionRequirement> = emptyList()
            override val argumentSchema: ToolArgumentSchema = ToolArgumentSchema(emptyMap(), emptyList())
            override suspend fun execute(args: Map<String, Any?>): ToolOutcome {
                return ToolOutcome.success(id, emptyMap(), "Dispatched message to external receiver", SideEffectSemantics.EXTERNAL_SIDE_EFFECT)
            }
        }
        toolRegistry.register(externalTool)

        val step = AgentStep(
            stepId = stepId,
            stepIndex = 0,
            title = "External Dispatch",
            description = "External dispatch",
            capabilityId = CapabilityId.SEND_MESSAGE.name,
            toolId = "external_dispatch_tool",
            inputArguments = emptyMap(),
            riskEvaluation = RiskEvaluation.low(),
            status = ExecutionStatus.REQUESTED,
            idempotencyKey = idempKey,
            createdAt = System.currentTimeMillis()
        )

        val execContext = ExecutionContext(
            taskId = taskId,
            stepId = stepId,
            stepIndex = 0,
            idempotencyKey = idempKey
        )

        val result = toolExecutor.executeStep(execContext, step)

        // Invariant: external side effect without confirmation is PARTIALLY_VERIFIED, never VERIFIED
        assertEquals(ExecutionStatus.PARTIALLY_VERIFIED, result.status)
        val evidence = db.evidenceDao().getEvidenceForStep(stepId)
        assertTrue(evidence.isNotEmpty())
        assertEquals(ConfidenceLevel.INDETERMINATE.name, evidence.first().confidence)
    }
}
