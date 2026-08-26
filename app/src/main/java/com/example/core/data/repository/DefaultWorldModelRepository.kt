package com.example.core.data.repository

import com.example.core.data.local.dao.CommitmentDao
import com.example.core.data.local.dao.GoalDao
import com.example.core.data.local.dao.ProjectDao
import com.example.core.data.local.dao.UserPreferenceDao
import com.example.core.data.local.dao.WorldEntityDao
import com.example.core.data.local.mapper.WorldModelMappers.toDomain
import com.example.core.data.local.mapper.WorldModelMappers.toEntity
import com.example.core.domain.time.TimeProvider
import com.example.core.domain.world.identity.UserId
import com.example.core.domain.world.model.Commitment
import com.example.core.domain.world.model.GoalStatus
import com.example.core.domain.world.model.UserPreference
import com.example.core.domain.world.model.WorldEntity
import com.example.core.domain.world.repository.WorldModelRepository
import com.example.core.domain.world.repository.WorldPlanningContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DefaultWorldModelRepository(
    private val goalDao: GoalDao,
    private val projectDao: ProjectDao,
    private val commitmentDao: CommitmentDao,
    private val preferenceDao: UserPreferenceDao,
    private val entityDao: WorldEntityDao,
    private val timeProvider: TimeProvider
) : WorldModelRepository {

    override suspend fun getPlanningContext(
        userId: UserId,
        maxActiveGoals: Int,
        maxCommitments: Int,
        maxPreferences: Int
    ): WorldPlanningContext {
        val now = timeProvider.currentTimeMillis()

        val activeGoals = goalDao.getGoalsByStatus(userId.value, GoalStatus.ACTIVE.name)
            .map { it.toDomain() }
            .take(maxActiveGoals)

        val recentProjects = projectDao.getRecentProjects(userId.value, limit = 5)
            .map { it.toDomain() }

        val upcomingCommitments = commitmentDao.getUpcomingCommitmentsList(userId.value, fromTimestamp = now, limit = maxCommitments)
            .map { it.toDomain() }

        val preferences = preferenceDao.getAllPreferencesList(userId.value)
            .map { it.toDomain() }
            .take(maxPreferences)

        return WorldPlanningContext(
            activeGoals = activeGoals,
            relevantProjects = recentProjects,
            upcomingCommitments = upcomingCommitments,
            userPreferences = preferences,
            knownEntities = emptyList(),
            capturedAt = now
        )
    }

    override suspend fun savePreference(preference: UserPreference): Result<Unit> = runCatching {
        preferenceDao.insertPreference(preference.toEntity())
    }

    override fun observePreferences(userId: UserId): Flow<List<UserPreference>> {
        return preferenceDao.getAllPreferences(userId.value).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getPreference(userId: UserId, domain: String, key: String): UserPreference? {
        return preferenceDao.getPreference(userId.value, domain, key)?.toDomain()
    }

    override suspend fun deletePreference(userId: UserId, domain: String, key: String): Result<Unit> = runCatching {
        preferenceDao.deletePreferenceByKey(userId.value, domain, key)
    }

    override suspend fun saveCommitment(commitment: Commitment): Result<Unit> = runCatching {
        val existing = commitmentDao.getCommitmentById(commitment.id)
        if (existing == null) {
            commitmentDao.insertCommitment(commitment.toEntity())
        } else {
            commitmentDao.updateCommitment(commitment.toEntity())
        }
    }

    override fun observeUpcomingCommitments(userId: UserId, fromTimestamp: Long, limit: Int): Flow<List<Commitment>> {
        return commitmentDao.getUpcomingCommitments(userId.value, fromTimestamp, limit).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun deleteCommitment(id: String): Result<Unit> = runCatching {
        commitmentDao.deleteCommitment(id)
    }

    override suspend fun saveWorldEntity(entity: WorldEntity): Result<Unit> = runCatching {
        entityDao.saveEntityWithAliases(entity.toEntity(), entity.aliases.toList())
    }

    override suspend fun getEntity(canonicalId: String): WorldEntity? {
        val entityRecord = entityDao.getEntityById(canonicalId) ?: return null
        val aliases = entityDao.getAliasesForEntity(canonicalId).map { it.alias }.toSet()
        return entityRecord.toDomain(aliases)
    }

    override suspend fun resolveEntity(userId: UserId, query: String): WorldEntity? {
        val results = entityDao.searchEntities(userId.value, query)
        if (results.isEmpty()) return null
        val first = results.first()
        val aliases = entityDao.getAliasesForEntity(first.canonicalId).map { it.alias }.toSet()
        return first.toDomain(aliases)
    }

    override suspend fun deleteEntity(canonicalId: String): Result<Unit> = runCatching {
        entityDao.deleteEntity(canonicalId)
    }
}
