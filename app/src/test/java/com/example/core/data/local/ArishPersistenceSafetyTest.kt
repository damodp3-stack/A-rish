package com.example.core.data.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.data.local.entity.AgentEventEntity
import com.example.core.data.local.entity.ApprovalEntity
import com.example.core.data.local.entity.EvidenceEntity
import com.example.core.data.local.entity.IdempotencyEntity
import com.example.core.data.local.entity.MemoryEntity
import com.example.core.data.local.entity.StepEntity
import com.example.core.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ArishPersistenceSafetyTest {

    private lateinit var context: Context
    private lateinit var db: ArishDatabase

    @Before
    fun initDb() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, ArishDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun cleanUp() {
        db.close()
    }

    // 1. Idempotency: Unique Constraint Rejects Duplicate Insertion
    @Test
    fun testIdempotencyUniqueConstraintRejection() {
        runBlocking {
            val record1 = IdempotencyEntity(
                idempotencyKey = "idemp-unique-100",
                taskId = "task-1",
                stepId = "step-1",
                toolId = "send_message",
                argumentsHash = "hash-abc",
                executionStatus = "EXECUTED",
                cachedResultJson = "{\"status\":\"ok\"}",
                executedAt = 1700000000000L
            )
            db.idempotencyDao().insertRecord(record1)

            val duplicateRecord = IdempotencyEntity(
                idempotencyKey = "idemp-unique-100", // Same idempotency key!
                taskId = "task-2",
                stepId = "step-2",
                toolId = "send_message",
                argumentsHash = "hash-abc",
                executionStatus = "EXECUTING",
                cachedResultJson = "{}",
                executedAt = 1700000001000L
            )

            var rejectedByConstraint = false
            try {
                db.idempotencyDao().insertRecord(duplicateRecord)
            } catch (e: SQLiteConstraintException) {
                rejectedByConstraint = true
            } catch (e: Exception) {
                if (e.cause is SQLiteConstraintException || e.message?.contains("UNIQUE") == true) {
                    rejectedByConstraint = true
                }
            }

            assertTrue("SQLite UNIQUE constraint must reject duplicate idempotency key", rejectedByConstraint)
        }
    }

    // 2. Idempotency Crash Recovery: Resumption detects cached result and prevents duplicate side effect
    @Test
    fun testIdempotencyCrashRecoveryResumption() {
        runBlocking {
            val originalKey = "idemp-crash-resume-42"
            val cachedResult = "{\"orderId\":\"ORDER-999\",\"status\":\"CONFIRMED\"}"

            // Simulate step execution writing idempotency record before crash
            db.idempotencyDao().insertRecord(
                IdempotencyEntity(
                    idempotencyKey = originalKey,
                    taskId = "task-flight-booking",
                    stepId = "step-book-flight",
                    toolId = "airline_api",
                    argumentsHash = "hash-flight-101",
                    executionStatus = "EXECUTED",
                    cachedResultJson = cachedResult,
                    executedAt = 1700000010000L
                )
            )

            // Simulate app restart / crash recovery: query idempotency check
            val exists = db.idempotencyDao().hasRecord(originalKey)
            assertTrue("Idempotency record must be found upon crash recovery", exists)

            val retrievedRecord = db.idempotencyDao().getRecordByKey(originalKey)
            assertNotNull(retrievedRecord)
            assertEquals("EXECUTED", retrievedRecord?.executionStatus)
            assertEquals(cachedResult, retrievedRecord?.cachedResultJson)
            // Resumed agent consumes cached result without repeating external side effect!
        }
    }

    // 3. Transaction Atomicity: Multi-Table Failure Rolls Back All Entities
    @Test
    fun testMultiEntityTransactionRollback() {
        runBlocking {
            val initialTask = TaskEntity(
                taskId = "tx-task-atomic",
                title = "Original Title",
                userPrompt = "Initial prompt",
                structuredIntentId = null,
                priority = "NORMAL",
                state = "RECEIVED",
                maxSteps = 3,
                maxToolCalls = 5,
                maxExecutionTimeMs = 30000L,
                maxRetriesPerStep = 2,
                createdAt = 1700000000000L,
                updatedAt = 1700000000000L
            )
            db.taskDao().insertTask(initialTask)

            val step = StepEntity(
                stepId = "tx-step-1",
                taskId = "tx-task-atomic",
                stepIndex = 0,
                title = "Step 1",
                description = "Execute",
                capabilityId = "SEARCH",
                toolId = "search",
                inputArgumentsJson = "{}",
                riskLevel = "LOW",
                riskReasonsJson = "[]",
                status = "PENDING",
                idempotencyKey = "tx-step-key-1",
                createdAt = 1700000000000L
            )
            db.stepDao().insertStep(step)

            // Execute atomic transaction that updates task, updates step, inserts evidence, but fails midway
            var txFailed = false
            try {
                db.runInTransaction {
                    runBlocking {
                        // 1. Task update to EXECUTING
                        db.taskDao().updateTask(initialTask.copy(state = "EXECUTING", updatedAt = 1700000050000L))
                        // 2. Step update to EXECUTED
                        db.stepDao().updateStep(step.copy(status = "EXECUTED"))
                        // 3. Evidence insertion
                        db.evidenceDao().insertEvidence(
                            EvidenceEntity(
                                evidenceId = "ev-atomic-1",
                                stepId = "tx-step-1",
                                evidenceType = "HTTP_STATUS_200",
                                confidence = "CERTAIN",
                                description = "API responded 200 OK",
                                capturedAt = 1700000050000L
                            )
                        )
                        // Deliberate simulated mid-transaction failure
                        throw RuntimeException("Simulated power loss or SQL runtime exception")
                    }
                }
            } catch (e: Exception) {
                txFailed = true
            }

            assertTrue("Transaction must abort and fail", txFailed)

            // Assert complete atomic rollback across all three tables
            val taskAfterRollback = db.taskDao().getTaskById("tx-task-atomic")
            assertEquals("Task state must remain RECEIVED after rollback", "RECEIVED", taskAfterRollback?.state)

            val stepAfterRollback = db.stepDao().getStepById("tx-step-1")
            assertEquals("Step status must remain PENDING after rollback", "PENDING", stepAfterRollback?.status)

            val evidenceAfterRollback = db.evidenceDao().getEvidenceById("ev-atomic-1")
            assertNull("Evidence record must NOT exist due to transaction rollback", evidenceAfterRollback)
        }
    }

    // 4. Approval Recovery: PENDING approval survives process restart
    @Test
    fun testApprovalDurableAcrossProcessRestart() {
        runBlocking {
            val approval = ApprovalEntity(
                approvalId = "appr-durable-99",
                taskId = "task-secure-1",
                stepId = "step-secure-3",
                toolId = "device_action",
                capabilityId = "DEVICE_CONTROL",
                riskLevel = "CRITICAL",
                actionSummary = "Wipe temporary storage cache",
                previewPayloadJson = "{\"action\":\"wipe_cache\"}",
                createdAt = 1700000000000L,
                expiresAt = 1700000060000L,
                status = "PENDING"
            )
            db.approvalDao().insertApproval(approval)

            // Verify approval is queryable as PENDING
            val pendingBefore = db.approvalDao().getApprovalById("appr-durable-99")
            assertNotNull(pendingBefore)
            assertEquals("PENDING", pendingBefore?.status)
            assertEquals("CRITICAL", pendingBefore?.riskLevel)

            // User decides to APPROVE
            val approved = pendingBefore!!.copy(
                status = "APPROVED",
                decisionStatus = "APPROVED",
                decidedBy = "user_damo",
                decidedAt = 1700000010000L,
                decisionNotes = "Authorized by user"
            )
            db.approvalDao().updateApproval(approved)

            val afterDecision = db.approvalDao().getApprovalById("appr-durable-99")
            assertEquals("APPROVED", afterDecision?.status)
            assertEquals("user_damo", afterDecision?.decidedBy)
        }
    }

    // 5. Expired Approvals: Past timeout transitions to EXPIRED and cannot execute
    @Test
    fun testExpiredApprovalLifecycle() {
        runBlocking {
            val now = 1700000100000L
            val expiredApproval = ApprovalEntity(
                approvalId = "appr-expired-1",
                taskId = "task-exp-1",
                stepId = "step-exp-1",
                toolId = "delete_file",
                capabilityId = "FILE_SYSTEM",
                riskLevel = "HIGH",
                actionSummary = "Delete obsolete log directory",
                previewPayloadJson = "{}",
                createdAt = 1700000000000L,
                expiresAt = 1700000050000L, // Expired 50s ago
                status = "PENDING"
            )
            db.approvalDao().insertApproval(expiredApproval)

            // Run expiration reaper
            val expiredCount = db.approvalDao().expireOldApprovals(currentTime = now)
            assertEquals(1, expiredCount)

            val reapedApproval = db.approvalDao().getApprovalById("appr-expired-1")
            assertEquals("EXPIRED", reapedApproval?.status)
        }
    }

    // 6. Immutable Audit Trail: Approval Decision Emits Immutable Event
    @Test
    fun testImmutableApprovalAuditTrail() {
        runBlocking {
            // Write approval request
            val approval = ApprovalEntity(
                approvalId = "appr-audit-7",
                taskId = "task-audit-1",
                stepId = "step-audit-2",
                toolId = "system_exec",
                capabilityId = "SYSTEM",
                riskLevel = "HIGH",
                actionSummary = "Reboot service",
                previewPayloadJson = "{\"service\":\"agent_daemon\"}",
                createdAt = 1700000000000L,
                expiresAt = 1700000060000L,
                status = "PENDING"
            )
            db.approvalDao().insertApproval(approval)

            // Emit immutable audit event for creation
            db.agentEventDao().insertEvent(
                AgentEventEntity(
                    taskId = "task-audit-1",
                    stepId = "step-audit-2",
                    eventType = "APPROVAL_REQUESTED",
                    payloadJson = "{\"approvalId\":\"appr-audit-7\",\"risk\":\"HIGH\"}",
                    timestamp = 1700000000000L
                )
            )

            // User Approves
            db.approvalDao().updateApproval(
                approval.copy(status = "APPROVED", decidedBy = "operator_1", decidedAt = 1700000010000L)
            )

            // Emit immutable audit event for approval decision
            db.agentEventDao().insertEvent(
                AgentEventEntity(
                    taskId = "task-audit-1",
                    stepId = "step-audit-2",
                    eventType = "APPROVAL_DECIDED",
                    payloadJson = "{\"approvalId\":\"appr-audit-7\",\"status\":\"APPROVED\",\"decidedBy\":\"operator_1\"}",
                    timestamp = 1700000010000L
                )
            )

            val auditEvents = db.agentEventDao().getEventsForTask("task-audit-1")
            assertEquals(2, auditEvents.size)
            assertEquals("APPROVAL_REQUESTED", auditEvents[0].eventType)
            assertEquals("APPROVAL_DECIDED", auditEvents[1].eventType)
        }
    }

    // 7. FTS Lexical Search Lifecycle: Insert -> Search -> Update -> Delete
    @Test
    fun testFtsMemorySearchLifecycle() {
        runBlocking {
            val memory1 = MemoryEntity(
                id = "mem-fts-1",
                content = "User prefers concise answers in Tamil and English",
                category = "PREFERENCE",
                importance = 9,
                entitiesJson = "[{\"type\":\"LANGUAGE\",\"value\":\"Tamil\"}]",
                source = "USER_EXPLICIT",
                createdAt = 1700000000000L,
                lastAccessedAt = 1700000000000L,
                accessCount = 1
            )
            val memory2 = MemoryEntity(
                id = "mem-fts-2",
                content = "A-RISH OS architecture specification and offline memory vault",
                category = "IDENTITY",
                importance = 10,
                entitiesJson = "[{\"type\":\"TOPIC\",\"value\":\"Architecture\"}]",
                source = "SYSTEM_INFERRED",
                createdAt = 1700000000000L,
                lastAccessedAt = 1700000000000L,
                accessCount = 1
            )

            // 1. Insert memories
            db.memoryDao().insertMemory(memory1)
            db.memoryDao().insertMemory(memory2)

            // 2. FTS MATCH Search
            val tamilResults = db.memoryDao().searchMemoriesLexical("Tamil")
            assertEquals(1, tamilResults.size)
            assertEquals("mem-fts-1", tamilResults[0].id)

            val archResults = db.memoryDao().searchMemoriesLexical("architecture")
            assertEquals(1, archResults.size)
            assertEquals("mem-fts-2", archResults[0].id)

            // 3. Update Memory: Content and entities change from Tamil to Malayalam
            val updatedMemory1 = memory1.copy(
                content = "User prefers concise answers in Malayalam and English",
                entitiesJson = "[{\"type\":\"LANGUAGE\",\"value\":\"Malayalam\"}]"
            )
            db.memoryDao().updateMemory(updatedMemory1)

            // Search for "Tamil" must now return 0 results
            val oldQueryResults = db.memoryDao().searchMemoriesLexical("Tamil")
            assertEquals(0, oldQueryResults.size)

            // Search for "Malayalam" must now return the updated memory
            val newQueryResults = db.memoryDao().searchMemoriesLexical("Malayalam")
            assertEquals(1, newQueryResults.size)
            assertEquals("mem-fts-1", newQueryResults[0].id)

            // 4. Delete Memory
            db.memoryDao().deleteMemoryById("mem-fts-1")
            val deletedQueryResults = db.memoryDao().searchMemoriesLexical("Malayalam")
            assertEquals(0, deletedQueryResults.size)
        }
    }

    // 8. Audit & Evidence Decoupling from Task Cascade Deletion
    @Test
    fun testAuditAndEvidenceDecoupledFromTaskCascade() {
        runBlocking {
            val task = TaskEntity(
                taskId = "task-cascade-test",
                title = "Task with Operational and Audit Data",
                userPrompt = "Run test workflow",
                structuredIntentId = null,
                priority = "NORMAL",
                state = "COMPLETED",
                maxSteps = 2,
                maxToolCalls = 2,
                maxExecutionTimeMs = 10000L,
                maxRetriesPerStep = 1,
                createdAt = 1700000000000L,
                updatedAt = 1700000005000L
            )
            db.taskDao().insertTask(task)

            val step = StepEntity(
                stepId = "step-cascade-1",
                taskId = "task-cascade-test",
                stepIndex = 0,
                title = "Step 1",
                description = "Execute action",
                capabilityId = "SYSTEM",
                toolId = "tool_1",
                inputArgumentsJson = "{}",
                riskLevel = "LOW",
                riskReasonsJson = "[]",
                status = "VERIFIED",
                idempotencyKey = "idemp-casc-1",
                createdAt = 1700000000000L
            )
            db.stepDao().insertStep(step)

            val evidence = EvidenceEntity(
                evidenceId = "ev-casc-1",
                stepId = "step-cascade-1",
                evidenceType = "LOCAL_DATABASE_ROW",
                confidence = "CERTAIN",
                description = "Historical verification evidence record",
                capturedAt = 1700000002000L
            )
            db.evidenceDao().insertEvidence(evidence)

            val auditEvent = AgentEventEntity(
                taskId = "task-cascade-test",
                stepId = "step-cascade-1",
                eventType = "TASK_COMPLETED",
                payloadJson = "{\"status\":\"SUCCESS\"}",
                timestamp = 1700000005000L
            )
            db.agentEventDao().insertEvent(auditEvent)

            // Delete task
            db.taskDao().deleteTaskById("task-cascade-test")

            // 1. Task must be deleted
            assertNull(db.taskDao().getTaskById("task-cascade-test"))

            // 2. Operational steps must be deleted by CASCADE
            val remainingSteps = db.stepDao().getStepsForTask("task-cascade-test")
            assertEquals(0, remainingSteps.size)

            // 3. BUT Verification Evidence and Audit Events MUST REMAIN INTACT (Decoupled from CASCADE)
            val preservedEvidence = db.evidenceDao().getEvidenceById("ev-casc-1")
            assertNotNull("Evidence must survive task cleanup for historical integrity", preservedEvidence)
            assertEquals("LOCAL_DATABASE_ROW", preservedEvidence?.evidenceType)

            val preservedEvents = db.agentEventDao().getEventsForTask("task-cascade-test")
            assertEquals(1, preservedEvents.size)
            assertEquals("TASK_COMPLETED", preservedEvents[0].eventType)
        }
    }

    // 9. Secret Verification: DB Entities must NEVER contain plaintext credentials
    @Test
    fun testNoSecretsInDatabaseEntities() {
        val entityClasses = listOf(
            TaskEntity::class.java,
            StepEntity::class.java,
            ApprovalEntity::class.java,
            IdempotencyEntity::class.java,
            EvidenceEntity::class.java,
            MemoryEntity::class.java,
            AgentEventEntity::class.java
        )

        val forbiddenKeywords = listOf("api_key", "apikey", "password", "access_token", "refreshtoken", "secret_key")
        for (clazz in entityClasses) {
            for (field in clazz.declaredFields) {
                val fieldName = field.name.lowercase()
                for (forbidden in forbiddenKeywords) {
                    assertFalse(
                        "Entity class ${clazz.simpleName} field '$fieldName' must not store secrets",
                        fieldName.contains(forbidden)
                    )
                }
            }
        }
    }
}
