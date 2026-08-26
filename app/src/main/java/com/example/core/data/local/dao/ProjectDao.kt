package com.example.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.core.data.local.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room Data Access Object for Projects.
 */
@Dao
interface ProjectDao {

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProjectById(id: String): ProjectEntity?

    @Query("SELECT * FROM projects WHERE user_id = :userId AND status = :status ORDER BY updated_at DESC")
    fun getProjectsByStatus(userId: String, status: String): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE user_id = :userId ORDER BY updated_at DESC LIMIT :limit")
    suspend fun getRecentProjects(userId: String, limit: Int = 10): List<ProjectEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProject(project: ProjectEntity)

    @Update
    suspend fun updateProject(project: ProjectEntity): Int

    @Query("""
        UPDATE projects 
        SET name = :name, 
            description = :description, 
            status = :status, 
            primary_goal_id = :primaryGoalId, 
            tags_json = :tagsJson, 
            version = :newVersion, 
            updated_at = :updatedAt, 
            completed_at = :completedAt 
        WHERE id = :id AND version = :expectedVersion
    """)
    suspend fun updateProjectWithVersion(
        id: String,
        expectedVersion: Long,
        newVersion: Long,
        name: String,
        description: String,
        status: String,
        primaryGoalId: String?,
        tagsJson: String,
        updatedAt: Long,
        completedAt: Long?
    ): Int

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProject(id: String): Int
}
