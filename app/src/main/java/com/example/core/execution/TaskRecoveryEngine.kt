package com.example.core.execution

import com.example.core.data.local.dao.IdempotencyDao
import com.example.core.data.local.dao.StepDao
import com.example.core.data.local.dao.TaskDao
import com.example.core.data.local.entity.StepEntity
import com.example.core.data.local.entity.TaskEntity
import com.example.core.domain.execution.ExecutionStatus
import com.example.core.domain.execution.SideEffectSemantics

sealed class RecoveryAction {
    data class ResumeStep(val step: StepEntity, val stepIndex: Int) : RecoveryAction()
    data class FlagAmbiguousState(val step: StepEntity, val reason: String) : RecoveryAction()
    data object CompleteTask : RecoveryAction()
    data class AbortTask(val reason: String) : RecoveryAction()
}

data class TaskRecoveryPlan(
    val taskId: String,
    val canResume: Boolean,
    val task: TaskEntity?,
    val action: RecoveryAction,
    val explanation: String
)

/**
 * Deterministic engine inspecting post-crash task and step states.
 * Enforces safe resumption without duplicating non-idempotent external actions.
 */
class TaskRecoveryEngine(
    private val taskDao: TaskDao,
    private val stepDao: StepDao,
    private val idempotencyDao: IdempotencyDao
) {

    suspend fun inspectAndPlanRecovery(taskId: String): TaskRecoveryPlan {
        val task = taskDao.getTaskById(taskId)
            ?: return TaskRecoveryPlan(
                taskId = taskId,
                canResume = false,
                task = null,
                action = RecoveryAction.AbortTask("Task not found in local database"),
                explanation = "Task '$taskId' does not exist"
            )

        // If task is already in terminal state, no recovery needed
        if (task.state in listOf("COMPLETED", "FAILED", "ABORTED")) {
            return TaskRecoveryPlan(
                taskId = taskId,
                canResume = false,
                task = task,
                action = RecoveryAction.AbortTask("Task is in terminal state: ${task.state}"),
                explanation = "Task already finalized with state ${task.state}"
            )
        }

        val steps = stepDao.getStepsForTask(taskId)
        if (steps.isEmpty()) {
            return TaskRecoveryPlan(
                taskId = taskId,
                canResume = false,
                task = task,
                action = RecoveryAction.AbortTask("Task has no planned steps"),
                explanation = "No steps found for task $taskId"
            )
        }

        for ((index, step) in steps.withIndex()) {
            val stepStatus = try {
                ExecutionStatus.valueOf(step.status)
            } catch (_: Exception) {
                ExecutionStatus.UNKNOWN
            }

            if (stepStatus == ExecutionStatus.VERIFIED || stepStatus == ExecutionStatus.EXECUTED) {
                // Step was finished, proceed to check next step
                continue
            }

            val hasIdempotencyRecord = idempotencyDao.hasRecord(step.idempotencyKey)
            if (hasIdempotencyRecord) {
                // Idempotency record already exists, treat step as completed or check its status
                val record = idempotencyDao.getRecordByKey(step.idempotencyKey)
                if (record?.executionStatus == ExecutionStatus.VERIFIED.name) {
                    continue
                }
            }

            // If step was DISPATCHED when process died
            if (stepStatus == ExecutionStatus.DISPATCHED) {
                val semantics = try {
                    step.sideEffectSemantics?.let { SideEffectSemantics.valueOf(it) }
                } catch (_: Exception) {
                    null
                }

                if (semantics == SideEffectSemantics.EXTERNAL_SIDE_EFFECT) {
                    // Ambiguous external side effect: must not blindly re-dispatch
                    return TaskRecoveryPlan(
                        taskId = taskId,
                        canResume = false,
                        task = task,
                        action = RecoveryAction.FlagAmbiguousState(
                            step = step,
                            reason = "Step was in DISPATCHED state with EXTERNAL_SIDE_EFFECT during process death"
                        ),
                        explanation = "Interrupted external side effect requires probe/confirmation before retry"
                    )
                }
            }

            // Safe to resume this step
            return TaskRecoveryPlan(
                taskId = taskId,
                canResume = true,
                task = task,
                action = RecoveryAction.ResumeStep(step, index),
                explanation = "Resuming execution from step index $index (${step.stepId})"
            )
        }

        // All steps verified
        return TaskRecoveryPlan(
            taskId = taskId,
            canResume = true,
            task = task,
            action = RecoveryAction.CompleteTask,
            explanation = "All planned steps verified, task can be marked COMPLETED"
        )
    }
}
