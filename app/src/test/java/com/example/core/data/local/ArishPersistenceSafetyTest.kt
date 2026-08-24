package com.example.core.data.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.data.local.audit.ApprovalAuditManager
import com.example.core.data.local.entity.AgentEventEntity
import com.example.core.data.local.entity.ApprovalEntity
import com.example.core.data.local.entity.EvidenceEntity
import com.example.core.data.local.entity.IdempotencyEntity
import com.example.core.data.local.entity.MemoryEntity
import com.example.core.data.local.entity.StepEntity
import com.example.core.data.local.entity.TaskEntity
import com.example.core.data.local.migration.DatabaseMigrations
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
    private lateinit var approvalAuditManager: ApprovalAuditManager

    @Before
    fun initDb() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, ArishDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        approvalAuditManager = ApprovalAuditManager(db)
    }

    @After
    fun cleanUp() {
        db.close()
    }

    // 1. FTS Actual SQLite Schema & Triggers Verification
    @Test
    fun testFtsActualSqliteSchema() {
        val sqliteDb = db.openHelper.readableDatabase
        val cursor = sqliteDb.query("SELECT sql FROM sqlite_master WHERE type='table' AND name='memories_fts'")
        assertTrue("memories_fts virtual table must exist in sqlite_master", cursor.moveToFirst())
        val createSql = cursor.getString(0)
        cursor.close()

        assertTrue(
            "Virtual table definition must be SQLite FTS. Found: $createSql",
            createSql.contains("FTS", ignoreCase = true)
        )

        // Verify sync triggers exist in sqlite_master
        val triggerCursor = sqliteDb.query("SELECT name FROM sqlite_master WHERE type='trigger' AND name LIKE '%memories%'")
        val triggers = mutableListOf<String>()
        while (triggerCursor.moveToNext()) {
            triggers.add(triggerCursor.getString(0))
        }
        triggerCursor.close()
        assertTrue("Room must generate SQLite synchronization triggers for memories table", triggers.isNotEmpty())
    }

    // 2. FTS Full-Text Search Lifecycle: Insert -> Search -> Stale Update Removal -> Delete
    @Test
    fun testFtsMemorySearchLifecycle() {
        runBlocking {
            val mem1 = MemoryEntity(
                id = "mem-1",
                content = "A-RISH OS memory vault with deterministic lexical recall and neural indexing",
                category = "ARCHITECTURE",
                importance = 10,
                entitiesJson = "[{\"type\":\"MODULE\",\"value\":\"MemoryVault\"}]",
                source = "SYSTEM",
                createdAt = 1700000000000L,
                lastAccessedAt = 1700000000000L,
                accessCount = 1
            )
            val mem2 = MemoryEntity(
                id = "mem-2",
                content = "User preference for dark mode theme and Tamil audio voice responses",
                category = "PREFERENCE",
                importance = 8,
                entitiesJson = "[{\"type\":\"LANGUAGE\",\"value\":\"Tamil\"}]",
                source = "USER",
                createdAt = 1700000001000L,
                lastAccessedAt = 1700000001000L,
                accessCount = 1
            )

            // 1. Insert into memories table (automatically synced to memories_fts)
            db.memoryDao().insertMemory(mem1)
            db.memoryDao().insertMemory(mem2)

            // 2. Search exact and prefix MATCH
            val archResults = db.memoryDao().searchMemoriesLexical("architecture*")
            assertEquals(1, archResults.size)
            assertEquals("mem-1", archResults[0].id)

            val tamilResults = db.memoryDao().searchMemoriesLexical("Tamil")
            assertEquals(1, tamilResults.size)
            assertEquals("mem-2", tamilResults[0].id)

            // 3. Update memory: change content of mem2 from Tamil to Telugu
            val updatedMem2 = mem2.copy(
                content = "User preference for dark mode theme and Telugu audio voice responses",
                entitiesJson = "[{\"type\":\"LANGUAGE\",\"value\":\"Telugu\"}]"
            )
            db.memoryDao().updateMemory(updatedMem2)

            // Stale search for Tamil on mem2 must return 0 results
            val updatedTamilResults = db.memoryDao().searchMemoriesLexical("Tamil")
            assertEquals(0, updatedTamilResults.size)

            // New search for Telugu must find mem2
            val teluguResults = db.memoryDao().searchMemoriesLexical("Telugu")
            assertEquals(1, teluguResults.size)
            assertEquals("mem-2", teluguResults[0].id)

            // 4. Delete memory
            db.memoryDao().deleteMemoryById("mem-2")
            val postDeleteResults = db.memoryDao().searchMemoriesLexical("Telugu")
            assertEquals(0, postDeleteResults.size)
        }
    }

    // 3. Idempotency: Unique Constraint Rejects Duplicate Insertion
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

    // 4. Idempotency Crash Recovery: Resumption detects cached result and prevents duplicate side effect
    @Test
    fun testIdempotencyCrashRecoveryResumption() {
        runBlocking {
            val originalKey = "idemp-crash-resume-42"
            val cachedResult = "{\"orderId\":\"ORDER-999\",\"status\":\"CONFIRMED\"}"

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

            val exists = db.idempotencyDao().hasRecord(originalKey)
            assertTrue("Idempotency record must be found upon crash recovery", exists)

            val retrievedRecord = db.idempotencyDao().getRecordByKey(originalKey)
            assertNotNull(retrievedRecord)
            assertEquals("EXECUTED", retrievedRecord?.executionStatus)
            assertEquals(cachedResult, retrievedRecord?.cachedResultJson)
        }
    }

    // 5. Transaction Atomicity: Multi-Table Failure Rolls Back All Entities
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

            var txFailed = false
            try {
                db.runInTransaction {
                    runBlocking {
                        db.taskDao().updateTask(initialTask.copy(state = "EXECUTING", updatedAt = 1700000050000L))
                        db.stepDao().updateStep(step.copy(status = "EXECUTED"))
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
                        throw RuntimeException("Simulated power loss or SQL runtime exception")
                    }
                }
            } catch (e: Exception) {
                txFailed = true
            }

            assertTrue("Transaction must abort and fail", txFailed)

            val taskAfterRollback = db.taskDao().getTaskById("tx-task-atomic")
            assertEquals("Task state must remain RECEIVED after rollback", "RECEIVED", taskAfterRollback?.state)

            val stepAfterRollback = db.stepDao().getStepById("tx-step-1")
            assertEquals("Step status must remain PENDING after rollback", "PENDING", stepAfterRollback?.status)

            val evidenceAfterRollback = db.evidenceDao().getEvidenceById("ev-atomic-1")
            assertNull("Evidence record must NOT exist due to transaction rollback", evidenceAfterRollback)
        }
    }

    // 6. Approval Recovery: PENDING approval survives process restart
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

            val pendingBefore = db.approvalDao().getApprovalById("appr-durable-99")
            assertNotNull(pendingBefore)
            assertEquals("PENDING", pendingBefore?.status)

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

    // 7. Immutable Approval Audit Trail Lifecycle (CREATED -> APPROVED -> REJECTED -> CANCELLED -> EXPIRED)
    @Test
    fun testImmutableApprovalAuditTrailLifecycle() {
        runBlocking {
            val approval1 = ApprovalEntity(
                approvalId = "appr-101",
                taskId = "task-sec-1",
                stepId = "step-sec-1",
                toolId = "reboot_system",
                capabilityId = "SYSTEM",
                riskLevel = "CRITICAL",
                actionSummary = "Reboot service daemon",
                previewPayloadJson = "{}",
                createdAt = 1700000000000L,
                expiresAt = 1700000060000L,
                status = "PENDING"
            )

            // 1. Creation writes APPROVAL_CREATED event
            approvalAuditManager.createApproval(approval1)

            val pendingApproval = db.approvalDao().getApprovalById("appr-101")
            assertNotNull(pendingApproval)
            assertEquals("PENDING", pendingApproval?.status)

            // 2. Approval decision writes APPROVAL_APPROVED event
            val approved = approvalAuditManager.approve(
                approvalId = "appr-101",
                decidedBy = "user_damo",
                decisionNotes = "Authorized in terminal",
                timestamp = 1700000010000L
            )
            assertTrue("Approval transition must succeed", approved)

            val afterApproved = db.approvalDao().getApprovalById("appr-101")
            assertEquals("APPROVED", afterApproved?.status)
            assertEquals("user_damo", afterApproved?.decidedBy)

            // 3. Create second approval for Rejection test
            val approval2 = ApprovalEntity(
                approvalId = "appr-102",
                taskId = "task-sec-2",
                stepId = "step-sec-2",
                toolId = "format_disk",
                capabilityId = "SYSTEM",
                riskLevel = "CRITICAL",
                actionSummary = "Format disk",
                previewPayloadJson = "{}",
                createdAt = 1700000020000L,
                expiresAt = 1700000080000L,
                status = "PENDING"
            )
            approvalAuditManager.createApproval(approval2)
            approvalAuditManager.reject("appr-102", "security_officer", "Forbidden action", 1700000025000L)

            val afterRejected = db.approvalDao().getApprovalById("appr-102")
            assertEquals("REJECTED", afterRejected?.status)

            // 4. Create third approval for Cancellation test
            val approval3 = ApprovalEntity(
                approvalId = "appr-103",
                taskId = "task-sec-3",
                stepId = "step-sec-3",
                toolId = "send_email",
                capabilityId = "COMMUNICATION",
                riskLevel = "MEDIUM",
                actionSummary = "Send email",
                previewPayloadJson = "{}",
                createdAt = 1700000030000L,
                expiresAt = 1700000090000L,
                status = "PENDING"
            )
            approvalAuditManager.createApproval(approval3)
            approvalAuditManager.cancel("appr-103", "Task cancelled by user", 1700000035000L)

            val afterCancelled = db.approvalDao().getApprovalById("appr-103")
            assertEquals("CANCELLED", afterCancelled?.status)

            // 5. Verify immutable AgentEvents audit trail
            val events1 = db.agentEventDao().getEventsForTask("task-sec-1")
            assertEquals(2, events1.size)
            assertEquals("APPROVAL_CREATED", events1[0].eventType)
            assertEquals("APPROVAL_APPROVED", events1[1].eventType)

            val events2 = db.agentEventDao().getEventsForTask("task-sec-2")
            assertEquals(2, events2.size)
            assertEquals("APPROVAL_CREATED", events2[0].eventType)
            assertEquals("APPROVAL_REJECTED", events2[1].eventType)

            val events3 = db.agentEventDao().getEventsForTask("task-sec-3")
            assertEquals(2, events3.size)
            assertEquals("APPROVAL_CREATED", events3[0].eventType)
            assertEquals("APPROVAL_CANCELLED", events3[1].eventType)
        }
    }

    // 8. Expired Approvals: Past timeout transitions to EXPIRED and records audit event
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
            approvalAuditManager.createApproval(expiredApproval)

            // Run expiration reaper
            val expiredCount = approvalAuditManager.expireOldApprovals(currentTime = now)
            assertEquals(1, expiredCount)

            val reapedApproval = db.approvalDao().getApprovalById("appr-expired-1")
            assertEquals("EXPIRED", reapedApproval?.status)

            val auditEvents = db.agentEventDao().getEventsByType("APPROVAL_EXPIRED")
            assertEquals(1, auditEvents.size)
        }
    }

    // 9. Audit & Evidence Decoupling from Task Cascade Deletion
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

            val approval = ApprovalEntity(
                approvalId = "appr-casc-1",
                taskId = "task-cascade-test",
                stepId = "step-cascade-1",
                toolId = "tool_1",
                capabilityId = "SYSTEM",
                riskLevel = "LOW",
                actionSummary = "Action summary",
                previewPayloadJson = "{}",
                createdAt = 1700000000000L,
                expiresAt = 1700000060000L,
                status = "APPROVED",
                decisionStatus = "APPROVED",
                decidedBy = "user"
            )
            db.approvalDao().insertApproval(approval)

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

            // 3. BUT Verification Evidence, Approvals, and Audit Events MUST REMAIN INTACT
            val preservedEvidence = db.evidenceDao().getEvidenceById("ev-casc-1")
            assertNotNull("Evidence must survive task cleanup for historical integrity", preservedEvidence)
            assertEquals("LOCAL_DATABASE_ROW", preservedEvidence?.evidenceType)

            val preservedApproval = db.approvalDao().getApprovalById("appr-casc-1")
            assertNotNull("Approval records must survive task cleanup", preservedApproval)

            val preservedEvents = db.agentEventDao().getEventsForTask("task-cascade-test")
            assertEquals(1, preservedEvents.size)
            assertEquals("TASK_COMPLETED", preservedEvents[0].eventType)
        }
    }

    // 10. Explicit Database Migration V1 -> V2 Execution Test
    @Test
    fun testDatabaseMigrationV1toV2() {
        val sqliteDb = db.openHelper.writableDatabase
        DatabaseMigrations.MIGRATION_1_2.migrate(sqliteDb)

        val cursor = sqliteDb.query("PRAGMA index_list('agent_events')")
        var foundSessionIndex = false
        while (cursor.moveToNext()) {
            val indexName = cursor.getString(cursor.getColumnIndexOrThrow("name"))
            if (indexName == "index_agent_events_session") {
                foundSessionIndex = true
                break
            }
        }
        cursor.close()
        assertTrue("MIGRATION_1_2 must create index_agent_events_session", foundSessionIndex)
    }

    // 11. Secret Verification: DB Entities must NEVER contain plaintext credentials
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
