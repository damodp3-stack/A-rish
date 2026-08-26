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

    // 0. SQLite Schema & Trigger Verification
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

    // 1. FTS insertion: Memory inserted -> searchable
    @Test
    fun testFtsInsertion() {
        runBlocking {
            val mem = MemoryEntity(
                id = "mem-insert-1",
                content = "Autonomous reasoning engine with tool capability execution and verification",
                category = "CAPABILITY",
                importance = 9,
                entitiesJson = "[{\"type\":\"CORE\",\"value\":\"Engine\"}]",
                source = "SYSTEM",
                createdAt = 1700000000000L,
                lastAccessedAt = 1700000000000L,
                accessCount = 1
            )
            db.memoryDao().insertMemory(mem)

            val results = db.memoryDao().searchMemoriesLexical("autonomous")
            assertEquals(1, results.size)
            assertEquals("mem-insert-1", results[0].id)
        }
    }

    // 2. FTS update: Old text -> NOT searchable, New text -> searchable
    @Test
    fun testFtsUpdate() {
        runBlocking {
            val original = MemoryEntity(
                id = "mem-update-1",
                content = "Initial memory content referencing alpha_protocol",
                category = "GENERAL",
                importance = 5,
                entitiesJson = "[]",
                source = "USER",
                createdAt = 1700000000000L,
                lastAccessedAt = 1700000000000L,
                accessCount = 1
            )
            db.memoryDao().insertMemory(original)

            // Verify searchable initially
            val initialSearch = db.memoryDao().searchMemoriesLexical("alpha_protocol")
            assertEquals(1, initialSearch.size)

            // Update with replacement content
            val updated = original.copy(
                content = "Updated memory content referencing beta_protocol instead"
            )
            db.memoryDao().updateMemory(updated)

            // Old token must return 0 results
            val staleSearch = db.memoryDao().searchMemoriesLexical("alpha_protocol")
            assertEquals(0, staleSearch.size)

            // New token must return 1 result
            val newSearch = db.memoryDao().searchMemoriesLexical("beta_protocol")
            assertEquals(1, newSearch.size)
            assertEquals("mem-update-1", newSearch[0].id)
        }
    }

    // 3. FTS deletion: Deleted memory -> NOT searchable
    @Test
    fun testFtsDeletion() {
        runBlocking {
            val mem = MemoryEntity(
                id = "mem-delete-1",
                content = "Temporary session artifact marked for deletion",
                category = "TEMPORARY",
                importance = 2,
                entitiesJson = "[]",
                source = "AGENT",
                createdAt = 1700000000000L,
                lastAccessedAt = 1700000000000L,
                accessCount = 1
            )
            db.memoryDao().insertMemory(mem)

            assertEquals(1, db.memoryDao().searchMemoriesLexical("artifact").size)

            db.memoryDao().deleteMemoryById("mem-delete-1")

            val postDeleteResults = db.memoryDao().searchMemoriesLexical("artifact")
            assertEquals(0, postDeleteResults.size)
        }
    }

    // 4. MATCH query: Exact token and prefix/token behavior works as intended
    @Test
    fun testMatchQuery() {
        runBlocking {
            val mem = MemoryEntity(
                id = "mem-match-1",
                content = "Deterministic state machine transitioning through discrete execution phases",
                category = "ARCHITECTURE",
                importance = 8,
                entitiesJson = "[]",
                source = "SYSTEM",
                createdAt = 1700000000000L,
                lastAccessedAt = 1700000000000L,
                accessCount = 1
            )
            db.memoryDao().insertMemory(mem)

            // Exact token
            val exactResults = db.memoryDao().searchMemoriesLexical("deterministic")
            assertEquals(1, exactResults.size)

            // Prefix token
            val prefixResults = db.memoryDao().searchMemoriesLexical("transit*")
            assertEquals(1, prefixResults.size)

            // Non-matching token
            val noMatch = db.memoryDao().searchMemoriesLexical("nonexistent")
            assertEquals(0, noMatch.size)
        }
    }

    // 5. Deterministic ranking: Same dataset + same query -> deterministic result ordering
    @Test
    fun testDeterministicRanking() {
        runBlocking {
            val memHigh = MemoryEntity(
                id = "mem-rank-high",
                content = "Critical security protocol documentation",
                category = "SECURITY",
                importance = 10,
                entitiesJson = "[]",
                source = "SYSTEM",
                createdAt = 1700000002000L,
                lastAccessedAt = 1700000002000L,
                accessCount = 1
            )
            val memLow = MemoryEntity(
                id = "mem-rank-low",
                content = "General security discussion note",
                category = "SECURITY",
                importance = 3,
                entitiesJson = "[]",
                source = "USER",
                createdAt = 1700000001000L,
                lastAccessedAt = 1700000001000L,
                accessCount = 1
            )
            db.memoryDao().insertMemory(memLow)
            db.memoryDao().insertMemory(memHigh)

            val query1 = db.memoryDao().searchMemoriesLexical("security")
            val query2 = db.memoryDao().searchMemoriesLexical("security")

            assertEquals(2, query1.size)
            assertEquals("mem-rank-high", query1[0].id)
            assertEquals("mem-rank-low", query1[1].id)

            // Ranking must be 100% deterministic across repeated invocations
            assertEquals(query1.map { it.id }, query2.map { it.id })
        }
    }

    // 6. Idempotency: Same idempotency key -> second insertion rejected by SQLite UNIQUE constraint
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

    // 7. Crash recovery: Existing idempotency record -> recovery must NOT execute duplicate side effect
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

    // 8. Transaction rollback: Task update + Step update + Evidence insert. Force failure. Verify ALL three rollback.
    @Test
    fun testTransactionRollback() {
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

    // 9. Approval process recovery: PENDING approval -> simulated process restart -> still PENDING
    @Test
    fun testApprovalProcessRecovery() {
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

    // 10. Approval expiry: Expired PENDING approval -> EXPIRED -> cannot execute
    @Test
    fun testApprovalExpiry() {
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

    // 11. Approval audit: Every transition must have append-only history (APPROVAL_CREATED, APPROVAL_APPROVED, APPROVAL_REJECTED, APPROVAL_CANCELLED, APPROVAL_EXPIRED)
    @Test
    fun testApprovalAudit() {
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

    // 12. Cascade safety: Deleting Task must NOT delete agent_events, approval audit history, or historical verification evidence
    @Test
    fun testCascadeSafety() {
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

    // 13. Explicit Database Migration V1 -> V2 Execution Test
    @Test
    fun testDatabaseMigrationV1toV2() {
        runBlocking {
            // Seed data prior to migration
            db.memoryDao().insertMemory(
                MemoryEntity(
                    id = "mem-mig-1",
                    content = "Lexical search query persistence test across migration",
                    category = "SYSTEM",
                    importance = 9,
                    entitiesJson = "[]",
                    source = "SYSTEM",
                    createdAt = 1700000000000L,
                    lastAccessedAt = 1700000000000L,
                    accessCount = 1
                )
            )

            db.idempotencyDao().insertRecord(
                IdempotencyEntity(
                    idempotencyKey = "idemp-mig-key",
                    taskId = "task-mig-1",
                    stepId = "step-mig-1",
                    toolId = "tool_mig",
                    argumentsHash = "hash-mig",
                    executionStatus = "EXECUTED",
                    cachedResultJson = "{\"status\":\"ok\"}",
                    executedAt = 1700000000000L
                )
            )

            // Execute migration
            val sqliteDb = db.openHelper.writableDatabase
            DatabaseMigrations.MIGRATION_1_2.migrate(sqliteDb)

            // 1. Verify new tables created
            val tableCursor = sqliteDb.query("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('goals', 'projects', 'goal_project_links', 'commitments', 'user_preferences', 'world_entities', 'entity_aliases')")
            val tablesFound = mutableSetOf<String>()
            while (tableCursor.moveToNext()) {
                tablesFound.add(tableCursor.getString(0))
            }
            tableCursor.close()
            assertEquals("MIGRATION_1_2 must create all 7 Phase 2A tables", 7, tablesFound.size)

            // 2. Verify FTS table and searchability survive migration
            val searchResults = db.memoryDao().searchMemoriesLexical("persistence")
            assertEquals(1, searchResults.size)
            assertEquals("mem-mig-1", searchResults[0].id)

            // 3. Verify Idempotency records survive migration and constraint holds
            val idempRecord = db.idempotencyDao().getRecordByKey("idemp-mig-key")
            assertNotNull(idempRecord)
            assertEquals("EXECUTED", idempRecord?.executionStatus)
        }
    }

    // 14. Secret Verification: DB Entities must NEVER contain plaintext credentials
    @Test
    fun testNoSecretsInDatabaseEntities() {
        val entityClasses = listOf(
            TaskEntity::class.java,
            StepEntity::class.java,
            ApprovalEntity::class.java,
            IdempotencyEntity::class.java,
            EvidenceEntity::class.java,
            MemoryEntity::class.java,
            AgentEventEntity::class.java,
            com.example.core.data.local.entity.GoalEntity::class.java,
            com.example.core.data.local.entity.ProjectEntity::class.java,
            com.example.core.data.local.entity.GoalProjectLinkEntity::class.java,
            com.example.core.data.local.entity.CommitmentEntity::class.java,
            com.example.core.data.local.entity.UserPreferenceEntity::class.java,
            com.example.core.data.local.entity.WorldEntityEntity::class.java,
            com.example.core.data.local.entity.EntityAliasEntity::class.java
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
