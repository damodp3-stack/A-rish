package com.example.core.domain.world

import com.example.core.domain.world.identity.UserId
import com.example.core.domain.world.model.EpistemicProvenance
import com.example.core.domain.world.model.Goal
import com.example.core.domain.world.model.GoalPriority
import com.example.core.domain.world.model.GoalProgress
import com.example.core.domain.world.model.GoalStatus
import com.example.core.domain.world.validation.GoalProgressCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

class GoalProgressCalculatorTest {

    private val userId = UserId("user_progress_test")
    private val prov = EpistemicProvenance.userExplicit(1700000000000L)

    private fun buildGoal(progress: GoalProgress, status: GoalStatus = GoalStatus.ACTIVE): Goal = Goal(
        id = "goal_p1",
        userId = userId,
        title = "Test Progress Goal",
        description = "Testing derived progress",
        status = status,
        priority = GoalPriority.NORMAL,
        progress = progress,
        provenance = prov,
        createdAt = 1000L,
        updatedAt = 1000L
    )

    @Test
    fun testNotStartedProgress() {
        val goal = buildGoal(GoalProgress.NotStarted)
        val (fraction, status) = GoalProgressCalculator.computeProgress(goal)
        assertEquals(0.0f, fraction, 0.001f)
        assertEquals(GoalStatus.ACTIVE, status)
    }

    @Test
    fun testMilestoneProgress_Partial() {
        val progress = GoalProgressCalculator.fromMilestones(total = 4, completed = 2)
        val goal = buildGoal(progress)
        val (fraction, status) = GoalProgressCalculator.computeProgress(goal)
        assertEquals(0.5f, fraction, 0.001f)
        assertEquals(GoalStatus.ACTIVE, status)
    }

    @Test
    fun testMilestoneProgress_Completed() {
        val progress = GoalProgressCalculator.fromMilestones(total = 5, completed = 5)
        val goal = buildGoal(progress)
        val (fraction, status) = GoalProgressCalculator.computeProgress(goal)
        assertEquals(1.0f, fraction, 0.001f)
        assertEquals(GoalStatus.COMPLETED, status)
    }

    @Test
    fun testTaskDerivedProgress() {
        val progress = GoalProgressCalculator.fromTasks(total = 10, completed = 7, failed = 1)
        val goal = buildGoal(progress)
        val (fraction, status) = GoalProgressCalculator.computeProgress(goal)
        assertEquals(0.7f, fraction, 0.001f)
        assertEquals(GoalStatus.ACTIVE, status)
    }

    @Test
    fun testManualAssessmentProgress() {
        val progress = GoalProgress.ManualAssessment(
            percentage = 85,
            reasoning = "User verified manual milestone completion",
            assessedAt = 1700000000000L,
            assessorProvenance = prov
        )
        val goal = buildGoal(progress)
        val (fraction, status) = GoalProgressCalculator.computeProgress(goal)
        assertEquals(0.85f, fraction, 0.001f)
        assertEquals(GoalStatus.ACTIVE, status)
    }

    @Test
    fun testTerminalStatusPreserved() {
        val progress = GoalProgressCalculator.fromMilestones(total = 10, completed = 2)
        val goal = buildGoal(progress, status = GoalStatus.CANCELLED)
        val (fraction, status) = GoalProgressCalculator.computeProgress(goal)
        assertEquals(0.2f, fraction, 0.001f)
        assertEquals(GoalStatus.CANCELLED, status)
    }
}
