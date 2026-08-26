package com.example.core.data.repository

import com.example.core.data.local.dao.GoalProjectLinkDao
import com.example.core.data.local.dao.ProjectDao
import com.example.core.data.local.entity.GoalProjectLinkEntity
import com.example.core.data.local.mapper.WorldModelMappers.toDomain
import com.example.core.data.local.mapper.WorldModelMappers.toEntity
import com.example.core.domain.time.TimeProvider
import com.example.core.domain.world.identity.UserId
import com.example.core.domain.world.model.Goal
import com.example.core.domain.world.model.Project
import com.example.core.domain.world.model.ProjectStatus
import com.example.core.domain.world.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DefaultProjectRepository(
    private val projectDao: ProjectDao,
    private val linkDao: GoalProjectLinkDao,
    private val timeProvider: TimeProvider
) : ProjectRepository {

    override suspend fun getProject(userId: UserId, id: String): Project? {
        return projectDao.getProjectById(id, userId.value)?.toDomain()
    }

    override fun observeProjects(userId: UserId): Flow<List<Project>> {
        return projectDao.getProjectsByStatus(userId.value, ProjectStatus.ACTIVE.name).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun saveProject(project: Project): Result<Unit> = runCatching {
        val existing = projectDao.getProjectById(project.id, project.userId.value)
        if (existing == null) {
            projectDao.insertProject(project.toEntity())
        } else {
            val updated = project.toEntity()
            val expectedVersion = project.version
            val newVersion = expectedVersion + 1L
            val rows = projectDao.updateProjectWithVersion(
                id = project.id, userId = project.userId.value,
                expectedVersion = expectedVersion,
                newVersion = newVersion,
                name = updated.name,
                description = updated.description,
                status = updated.status,
                primaryGoalId = updated.primaryGoalId,
                tagsJson = updated.tagsJson,
                updatedAt = timeProvider.currentTimeMillis(),
                completedAt = updated.completedAt
            )
            if (rows == 0) {
                throw IllegalStateException("Optimistic lock conflict on Project '${project.id}'")
            }
        }
    }

    override suspend fun linkGoalAndProject(userId: UserId, goalId: String, projectId: String): Result<Unit> = runCatching {
        linkDao.insertLink(
            GoalProjectLinkEntity(
                goalId = goalId,
                projectId = projectId,
                linkedAt = timeProvider.currentTimeMillis()
            )
        )
    }

    override suspend fun unlinkGoalAndProject(userId: UserId, goalId: String, projectId: String): Result<Unit> = runCatching {
        linkDao.removeLink(goalId, projectId)
    }

    override suspend fun getProjectsForGoal(userId: UserId, goalId: String): List<Project> {
        return linkDao.getProjectsForGoal(goalId).map { it.toDomain() }
    }

    override suspend fun getGoalsForProject(userId: UserId, projectId: String): List<Goal> {
        return linkDao.getGoalsForProject(projectId).map { it.toDomain() }
    }

    override suspend fun deleteProject(userId: UserId, id: String): Result<Unit> = runCatching {
        val deleted = projectDao.deleteProject(id, userId.value)
        if (deleted == 0) {
            throw NoSuchElementException("Project not found for deletion: $id")
        }
    }
}
