package com.example.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.core.data.local.entity.StepEntity
import com.example.core.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTask(task: TaskEntity)

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Query("SELECT * FROM tasks WHERE task_id = :taskId")
    suspend fun getTaskById(taskId: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE task_id = :taskId")
    fun observeTaskById(taskId: String): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks ORDER BY created_at DESC")
    fun observeAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE state NOT IN ('COMPLETED', 'FAILED', 'ABORTED') ORDER BY created_at ASC")
    suspend fun getIncompleteTasks(): List<TaskEntity>

    @Query("DELETE FROM tasks WHERE task_id = :taskId")
    suspend fun deleteTaskById(taskId: String)

    @Query("DELETE FROM tasks")
    suspend fun deleteAllTasks()
}

@Dao
interface StepDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStep(step: StepEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSteps(steps: List<StepEntity>)

    @Update
    suspend fun updateStep(step: StepEntity)

    @Query("SELECT * FROM steps WHERE step_id = :stepId")
    suspend fun getStepById(stepId: String): StepEntity?

    @Query("SELECT * FROM steps WHERE task_id = :taskId ORDER BY step_index ASC")
    suspend fun getStepsForTask(taskId: String): List<StepEntity>

    @Query("SELECT * FROM steps WHERE task_id = :taskId ORDER BY step_index ASC")
    fun observeStepsForTask(taskId: String): Flow<List<StepEntity>>

    @Query("DELETE FROM steps WHERE task_id = :taskId")
    suspend fun deleteStepsForTask(taskId: String)
}
