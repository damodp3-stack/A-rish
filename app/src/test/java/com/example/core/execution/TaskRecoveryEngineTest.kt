package com.example.core.execution

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.data.local.ArishDatabase
import com.example.core.data.local.entity.StepEntity
import com.example.core.data.local.entity.TaskEntity
import com.example.core.domain.capability.CapabilityId
import com.example.core.domain.execution.ExecutionStatus
import com.example.core.domain.execution.SideEffectSemantics
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TaskRecoveryEngineTest {

    private lateinit var context: Context
    private lateinit var db: ArishDatabase
    private lateinit var recoveryEngine: TaskRecoveryEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, ArishDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        recoveryEngine = TaskRecoveryEngine(
            taskDao = db.taskDao(),
            stepDao = db.stepDao(),
            idempotencyDao = db.idempotencyDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `inspectAndPlanRecovery returns ResumeStep for first incomplete step`() = runBlocking {
        val taskId = "task-rec-1"
        db.taskDao().insertTask(
            TaskEntity(
                taskId = taskId,
                title = "Multi-step Task",
                userPrompt = "Run steps",
                structuredIntentId = null,
                priority = "NORMAL",
                state = "EXECUTING",
                maxSteps = 10,
                maxToolCalls = 10,
                maxExecutionTimeMs = 30000,
                maxRetriesPerStep = 3,
                currentStepIndex = 1,
                totalToolCallsCount = 1,
                failureReason = null,
                createdAt = 1000L,
                updatedAt = 2000L
            )
        )

        // Step 0: Verified
        db.stepDao().insertStep(
            StepEntity(
                stepId = "step-0",
                taskId = taskId,
                stepIndex = 0,
                title = "Step 0",
                description = "Step 0",
                capabilityId = CapabilityId.GET_CURRENT_TIME.name,
                toolId = "get_current_time",
                inputArgumentsJson = "{}",
                riskLevel = "LOW",
                riskReasonsJson = "[]",
                status = ExecutionStatus.VERIFIED.name,
                idempotencyKey = "idemp-0",
                createdAt = 1000L
            )
        )

        // Step 1: Requested (not executed yet when process crashed)
        db.stepDao().insertStep(
            StepEntity(
                stepId = "step-1",
                taskId = taskId,
                stepIndex = 1,
                title = "Step 1",
                description = "Step 1",
                capabilityId = CapabilityId.CALCULATE_MATH.name,
                toolId = "calculate",
                inputArgumentsJson = """{"expression":"5+5"}""",
                riskLevel = "LOW",
                riskReasonsJson = "[]",
                status = ExecutionStatus.REQUESTED.name,
                idempotencyKey = "idemp-1",
                createdAt = 1000L
            )
        )

        val plan = recoveryEngine.inspectAndPlanRecovery(taskId)

        assertTrue(plan.canResume)
        assertTrue(plan.action is RecoveryAction.ResumeStep)
        val resumeAction = plan.action as RecoveryAction.ResumeStep
        assertEquals("step-1", resumeAction.step.stepId)
        assertEquals(1, resumeAction.stepIndex)
    }

    @Test
    fun `inspectAndPlanRecovery returns CompleteTask when all steps are verified`() = runBlocking {
        val taskId = "task-complete"
        db.taskDao().insertTask(
            TaskEntity(
                taskId = taskId,
                title = "Finished Task",
                userPrompt = "Run steps",
                structuredIntentId = null,
                priority = "NORMAL",
                state = "EXECUTING",
                maxSteps = 10,
                maxToolCalls = 10,
                maxExecutionTimeMs = 30000,
                maxRetriesPerStep = 3,
                currentStepIndex = 1,
                totalToolCallsCount = 1,
                failureReason = null,
                createdAt = 1000L,
                updatedAt = 2000L
            )
        )

        db.stepDao().insertStep(
            StepEntity(
                stepId = "step-comp-0",
                taskId = taskId,
                stepIndex = 0,
                title = "Step 0",
                description = "Step 0",
                capabilityId = CapabilityId.GET_CURRENT_TIME.name,
                toolId = "get_current_time",
                inputArgumentsJson = "{}",
                riskLevel = "LOW",
                riskReasonsJson = "[]",
                status = ExecutionStatus.VERIFIED.name,
                idempotencyKey = "idemp-comp-0",
                createdAt = 1000L
            )
        )

        val plan = recoveryEngine.inspectAndPlanRecovery(taskId)

        assertTrue(plan.canResume)
        assertTrue(plan.action is RecoveryAction.CompleteTask)
    }

    @Test
    fun `inspectAndPlanRecovery flags ambiguous state on in-flight EXTERNAL_SIDE_EFFECT`() = runBlocking {
        val taskId = "task-ambig"
        db.taskDao().insertTask(
            TaskEntity(
                taskId = taskId,
                title = "External task",
                userPrompt = "Send SMS",
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
                createdAt = 1000L,
                updatedAt = 2000L
            )
        )

        db.stepDao().insertStep(
            StepEntity(
                stepId = "step-ambig-0",
                taskId = taskId,
                stepIndex = 0,
                title = "Send Message",
                description = "External dispatch",
                capabilityId = CapabilityId.SEND_MESSAGE.name,
                toolId = "send_message",
                inputArgumentsJson = "{}",
                riskLevel = "HIGH",
                riskReasonsJson = "[]",
                status = ExecutionStatus.DISPATCHED.name,
                idempotencyKey = "idemp-ambig-0",
                sideEffectSemantics = SideEffectSemantics.EXTERNAL_SIDE_EFFECT.name,
                createdAt = 1000L
            )
        )

        val plan = recoveryEngine.inspectAndPlanRecovery(taskId)

        assertFalse("Ambiguous external side effect cannot be blindly resumed", plan.canResume)
        assertTrue(plan.action is RecoveryAction.FlagAmbiguousState)
    }

    @Test
    fun `inspectAndPlanRecovery returns canResume false for terminal task`() = runBlocking {
        val taskId = "task-term"
        db.taskDao().insertTask(
            TaskEntity(
                taskId = taskId,
                title = "Terminal Task",
                userPrompt = "Prompt",
                structuredIntentId = null,
                priority = "NORMAL",
                state = "COMPLETED",
                maxSteps = 10,
                maxToolCalls = 10,
                maxExecutionTimeMs = 30000,
                maxRetriesPerStep = 3,
                currentStepIndex = 0,
                totalToolCallsCount = 0,
                failureReason = null,
                createdAt = 1000L,
                updatedAt = 2000L
            )
        )

        val plan = recoveryEngine.inspectAndPlanRecovery(taskId)

        assertFalse(plan.canResume)
    }
}
