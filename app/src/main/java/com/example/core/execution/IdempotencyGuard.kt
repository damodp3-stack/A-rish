package com.example.core.execution

import com.example.core.data.local.dao.IdempotencyDao
import com.example.core.data.local.entity.IdempotencyEntity
import com.example.core.domain.execution.ExecutionStatus
import java.security.MessageDigest

sealed class IdempotencyCheckResult {
    data object NewKey : IdempotencyCheckResult()
    data class AlreadyExecuted(val record: IdempotencyEntity) : IdempotencyCheckResult()
}

/**
 * Enforces exactly-once / at-most-once execution using database idempotency records.
 */
class IdempotencyGuard(
    private val idempotencyDao: IdempotencyDao
) {

    suspend fun checkKey(key: String): IdempotencyCheckResult {
        val record = idempotencyDao.getRecordByKey(key)
        return if (record != null) {
            IdempotencyCheckResult.AlreadyExecuted(record)
        } else {
            IdempotencyCheckResult.NewKey
        }
    }

    suspend fun recordExecution(
        key: String,
        taskId: String,
        stepId: String,
        toolId: String,
        args: Map<String, Any?>,
        status: ExecutionStatus,
        resultSummary: String,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val argsHash = computeHash(args.toString())
        val entity = IdempotencyEntity(
            idempotencyKey = key,
            taskId = taskId,
            stepId = stepId,
            toolId = toolId,
            argumentsHash = argsHash,
            executionStatus = status.name,
            cachedResultJson = resultSummary,
            executedAt = timestamp
        )
        try {
            idempotencyDao.insertRecord(entity)
        } catch (_: Exception) {
            // Already recorded or conflict; ignore in non-fatal path
        }
    }

    private fun computeHash(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
