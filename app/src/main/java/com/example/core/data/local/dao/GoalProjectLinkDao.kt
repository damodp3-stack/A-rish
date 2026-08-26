package com.example.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.core.data.local.entity.GoalEntity
import com.example.core.data.local.entity.GoalProjectLinkEntity
import com.example.core.data.local.entity.ProjectEntity

/**
 * Room Data Access Object for Goal-Project relational links.
 */
@Dao
interface GoalProjectLinkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLink(link: GoalProjectLinkEntity)

    @Query("DELETE FROM goal_project_links WHERE goal_id = :goalId AND project_id = :projectId")
    suspend fun removeLink(goalId: String, projectId: String): Int

    @Query("""
        SELECT p.* FROM projects p
        INNER JOIN goal_project_links l ON p.id = l.project_id
        WHERE l.goal_id = :goalId
    """)
    suspend fun getProjectsForGoal(goalId: String): List<ProjectEntity>

    @Query("""
        SELECT g.* FROM goals g
        INNER JOIN goal_project_links l ON g.id = l.goal_id
        WHERE l.project_id = :projectId
    """)
    suspend fun getGoalsForProject(projectId: String): List<GoalEntity>

    @Query("SELECT COUNT(*) FROM goal_project_links WHERE goal_id = :goalId AND project_id = :projectId")
    suspend fun isLinked(goalId: String, projectId: String): Int
}
