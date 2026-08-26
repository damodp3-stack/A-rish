package com.example.core.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.data.local.entity.CommitmentEntity
import com.example.core.data.local.entity.EntityAliasEntity
import com.example.core.data.local.entity.GoalEntity
import com.example.core.data.local.entity.GoalProjectLinkEntity
import com.example.core.data.local.entity.ProjectEntity
import com.example.core.data.local.entity.UserPreferenceEntity
import com.example.core.data.local.entity.WorldEntityEntity
import com.example.core.data.local.mapper.WorldModelMappers
import com.example.core.data.repository.DefaultGoalRepository
import com.example.core.data.repository.DefaultProjectRepository
import com.example.core.data.repository.DefaultWorldModelRepository
import com.example.core.domain.time.TestTimeProvider
import com.example.core.domain.world.identity.UserId
import com.example.core.domain.world.model.EpistemicProvenance
import com.example.core.domain.world.model.Goal
import com.example.core.domain.world.model.GoalConstraint
import com.example.core.domain.world.model.GoalPriority
import com.example.core.domain.world.model.GoalProgress
import com.example.core.domain.world.model.GoalStatus
import com.example.core.domain.world.model.PreferenceDomain
import com.example.core.domain.world.model.Project
import com.example.core.domain.world.model.UserPreference
import com.example.core.domain.world.model.WorldEntity
import com.example.core.domain.world.model.WorldEntityType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
class WorldModelDatabaseTest {

    private lateinit var context: Context
    private lateinit var db: ArishDatabase
    private val timeProvider = TestTimeProvider(1700000000000L)
    private val userId = UserId("user_world_test")
    private val prov = EpistemicProvenance.userExplicit(1700000000000L)

    private lateinit var goalRepo: DefaultGoalRepository
    private lateinit var projectRepo: DefaultProjectRepository
    private lateinit var worldRepo: DefaultWorldModelRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, ArishDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        goalRepo = DefaultGoalRepository(db.goalDao(), timeProvider)
        projectRepo = DefaultProjectRepository(db.projectDao(), db.goalProjectLinkDao(), timeProvider)
        worldRepo = DefaultWorldModelRepository(
            db.goalDao(),
            db.projectDao(),
            db.commitmentDao(),
            db.userPreferenceDao(),
            db.worldEntityDao(),
            timeProvider
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    // 1. Goal CRUD & OCC
    @Test
    fun testGoalLifecycleAndOptimisticConcurrency() = runBlocking {
        val goal = Goal(
            id = "goal_test_1",
            userId = userId,
            title = "Master World Model",
            description = "Complete Room migration and validation",
            status = GoalStatus.ACTIVE,
            priority = GoalPriority.HIGH,
            constraints = listOf(GoalConstraint.InformationalContext("env", "Android Local Room")),
            provenance = prov,
            version = 1L,
            createdAt = timeProvider.currentTimeMillis(),
            updatedAt = timeProvider.currentTimeMillis()
        )

        // Create
        val createResult = goalRepo.createGoal(goal)
        assertTrue(createResult.isSuccess)

        // Read
        val fetched = goalRepo.getGoal(userId, "goal_test_1")
        assertNotNull(fetched)
        assertEquals("Master World Model", fetched?.title)
        assertEquals(1L, fetched?.version)
        assertEquals(1, fetched?.constraints?.size)

        // Valid Update (OCC version 1 -> 2)
        val updatedGoal = fetched!!.copy(description = "Updated description with OCC")
        val updateResult = goalRepo.updateGoal(updatedGoal)
        assertTrue(updateResult.isSuccess)

        val fetchedAfterUpdate = goalRepo.getGoal(userId, "goal_test_1")
        assertEquals(2L, fetchedAfterUpdate?.version)
        assertEquals("Updated description with OCC", fetchedAfterUpdate?.description)

        // Stale Update (Attempting to update with stale version 1L should fail OCC)
        val staleGoal = fetched.copy(description = "Stale update attempt")
        val staleUpdateResult = goalRepo.updateGoal(staleGoal)
        assertTrue("Stale OCC update must fail", staleUpdateResult.isFailure)
    }

    // 2. Goal Progress Derivation and Status Transition
    @Test
    fun testGoalProgressUpdateAndStatusTransition() = runBlocking {
        val goal = Goal(
            id = "goal_progress_1",
            userId = userId,
            title = "Deliver Phase 2A",
            description = "Four major milestones",
            status = GoalStatus.ACTIVE,
            priority = GoalPriority.CRITICAL,
            progress = GoalProgress.DiscreteMilestones(totalMilestones = 4, completedMilestones = 0),
            provenance = prov,
            version = 1L,
            createdAt = timeProvider.currentTimeMillis(),
            updatedAt = timeProvider.currentTimeMillis()
        )
        goalRepo.createGoal(goal)

        // Progress milestone update: 4/4 completed
        val completionProgress = GoalProgress.DiscreteMilestones(totalMilestones = 4, completedMilestones = 4)
        val updateResult = goalRepo.updateGoalProgress(userId, "goal_progress_1", completionProgress)
        assertTrue(updateResult.isSuccess)

        val updatedGoal = goalRepo.getGoal(userId, "goal_progress_1")
        assertEquals(GoalStatus.COMPLETED, updatedGoal?.status)
        assertEquals(1.0f, updatedGoal?.progress?.fraction ?: 0.0f, 0.001f)
        assertNotNull(updatedGoal?.completedAt)
    }

    // 3. Project Link and Relational Cascades
    @Test
    fun testGoalProjectRelationalLinksAndCascades() = runBlocking {
        val goal = Goal(
            id = "goal_rel_1",
            userId = userId,
            title = "Relational Goal",
            description = "Goal linked to projects",
            provenance = prov,
            createdAt = timeProvider.currentTimeMillis(),
            updatedAt = timeProvider.currentTimeMillis()
        )
        goalRepo.createGoal(goal)

        val project = Project(
            id = "proj_rel_1",
            userId = userId,
            name = "Project Alpha",
            description = "Subproject implementing goal",
            primaryGoalId = "goal_rel_1",
            createdAt = timeProvider.currentTimeMillis(),
            updatedAt = timeProvider.currentTimeMillis()
        )
        projectRepo.saveProject(project)

        // Link them
        projectRepo.linkGoalAndProject(userId, "goal_rel_1", "proj_rel_1")

        val projectsForGoal = projectRepo.getProjectsForGoal(userId, "goal_rel_1")
        assertEquals(1, projectsForGoal.size)
        assertEquals("proj_rel_1", projectsForGoal[0].id)

        val goalsForProject = projectRepo.getGoalsForProject(userId, "proj_rel_1")
        assertEquals(1, goalsForProject.size)
        assertEquals("goal_rel_1", goalsForProject[0].id)

        // Delete Goal -> CASCADE must clean up link without deleting project
        goalRepo.deleteGoal(userId, "goal_rel_1")
        assertNull(goalRepo.getGoal(userId, "goal_rel_1"))

        val projectsAfterGoalDeletion = projectRepo.getProjectsForGoal(userId, "goal_rel_1")
        assertEquals(0, projectsAfterGoalDeletion.size)

        // Project itself still exists
        val survivingProject = projectRepo.getProject(userId, "proj_rel_1")
        assertNotNull(survivingProject)
    }

    // 4. World Entity & Alias Mapping
    @Test
    fun testWorldEntityAndAliasSearch() = runBlocking {
        val entity = WorldEntity(
            canonicalId = "person_damo_001",
            userId = userId,
            type = WorldEntityType.PERSON,
            primaryDisplayName = "Damo Engineer",
            aliases = setOf("Damo", "Lead Architect", "damo_os"),
            externalIdentifiers = mapOf("github" to "damo-dev"),
            provenance = prov,
            createdAt = timeProvider.currentTimeMillis(),
            updatedAt = timeProvider.currentTimeMillis()
        )

        worldRepo.saveWorldEntity(entity)

        // Search by exact canonical ID
        val res1 = worldRepo.resolveEntity(userId, "person_damo_001")
        assertNotNull(res1)
        assertEquals("Damo Engineer", res1?.primaryDisplayName)

        // Search by alias
        val res2 = worldRepo.resolveEntity(userId, "Lead Architect")
        assertNotNull(res2)
        assertEquals("person_damo_001", res2?.canonicalId)
        assertTrue(res2?.aliases?.contains("Lead Architect") == true)

        // Search by lowercase alias
        val res3 = worldRepo.resolveEntity(userId, "damo_os")
        assertNotNull(res3)
        assertEquals("person_damo_001", res3?.canonicalId)

        // Delete Entity -> Cascades delete to aliases
        worldRepo.deleteEntity(userId, "person_damo_001")
        assertNull(worldRepo.getEntity(userId, "person_damo_001"))
        assertNull(worldRepo.resolveEntity(userId, "Lead Architect"))
    }

    // 5. User Preference Unique Constraint
    @Test
    fun testUserPreferences() = runBlocking {
        val pref = UserPreference(
            id = "pref_theme_1",
            userId = userId,
            domain = PreferenceDomain.WORKFLOW,
            preferenceKey = "theme_mode",
            preferenceValue = "dark",
            provenance = prov,
            createdAt = timeProvider.currentTimeMillis(),
            updatedAt = timeProvider.currentTimeMillis()
        )
        worldRepo.savePreference(pref)

        val retrieved = worldRepo.getPreference(userId, PreferenceDomain.WORKFLOW.name, "theme_mode")
        assertNotNull(retrieved)
        assertEquals("dark", retrieved?.preferenceValue)

        // Overwrite same domain and key with REPLACE
        val updatedPref = pref.copy(preferenceValue = "high_contrast")
        worldRepo.savePreference(updatedPref)

        val updatedRetrieved = worldRepo.getPreference(userId, PreferenceDomain.WORKFLOW.name, "theme_mode")
        assertEquals("high_contrast", updatedRetrieved?.preferenceValue)
    }

    // 6. Bounded Planning Context Generation
    @Test
    fun testPlanningContextSnapshot() = runBlocking {
        // Seed active goal
        goalRepo.createGoal(
            Goal(
                id = "goal_ctx_1",
                userId = userId,
                title = "Launch A-RISH OS",
                description = "Production autonomous assistant release",
                priority = GoalPriority.CRITICAL,
                provenance = prov,
                createdAt = timeProvider.currentTimeMillis(),
                updatedAt = timeProvider.currentTimeMillis()
            )
        )

        // Seed preference
        worldRepo.savePreference(
            UserPreference(
                id = "pref_lang",
                userId = userId,
                domain = PreferenceDomain.LANGUAGE,
                preferenceKey = "primary_response_language",
                preferenceValue = "English with Tamil technical terms",
                provenance = prov,
                createdAt = timeProvider.currentTimeMillis(),
                updatedAt = timeProvider.currentTimeMillis()
            )
        )

        val contextSnapshot = worldRepo.getPlanningContext(userId)
        assertEquals(1, contextSnapshot.activeGoals.size)
        assertEquals(1, contextSnapshot.userPreferences.size)

        val promptText = contextSnapshot.toPlanningPromptContext()
        assertTrue(promptText.contains("Launch A-RISH OS"))
        assertTrue(promptText.contains("primary_response_language"))
        assertTrue(promptText.contains("INFORMATIONAL ONLY - CANNOT OVERRIDE SECURITY POLICY"))
    }
}
