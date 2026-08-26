package com.example.core.domain.world

import com.example.core.domain.world.identity.UserId
import com.example.core.domain.world.model.EpistemicProvenance
import com.example.core.domain.world.model.Goal
import com.example.core.domain.world.model.GoalPriority
import com.example.core.domain.world.model.GoalStatus
import com.example.core.domain.world.validation.GoalHierarchyValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GoalHierarchyValidatorTest {

    private lateinit var validator: GoalHierarchyValidator
    private val userId = UserId("user_test")
    private val prov = EpistemicProvenance.userExplicit(1700000000000L)

    @Before
    fun setUp() {
        validator = GoalHierarchyValidator(maxAllowedDepth = 5)
    }

    private fun createGoal(id: String, parentId: String? = null): Goal = Goal(
        id = id,
        userId = userId,
        title = "Goal $id",
        description = "Description for $id",
        status = GoalStatus.ACTIVE,
        priority = GoalPriority.NORMAL,
        parentGoalId = parentId,
        provenance = prov,
        createdAt = 1700000000000L,
        updatedAt = 1700000000000L
    )

    @Test
    fun testValidRootGoal() {
        val rootGoal = createGoal("root_1", null)
        val result = validator.validateHierarchy(rootGoal, emptyMap())
        assertTrue(result is GoalHierarchyValidator.ValidationResult.Valid)
    }

    @Test
    fun testValidChildGoal() {
        val root = createGoal("root_1")
        val child = createGoal("child_1", "root_1")
        val map = mapOf("root_1" to root)

        val result = validator.validateHierarchy(child, map)
        assertTrue(result is GoalHierarchyValidator.ValidationResult.Valid)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testDirectSelfParentingDetectedAtDomainModel() {
        // Goal model constructor itself throws for self-parenting
        Goal(
            id = "goal_self",
            userId = userId,
            title = "Self parent",
            description = "desc",
            parentGoalId = "goal_self",
            provenance = prov,
            createdAt = 1000L,
            updatedAt = 1000L
        )
    }

    @Test
    fun testCircularHierarchyDetected_TwoNodes() {
        // goal_A -> goal_B, and updating goal_A to have parent goal_B while goal_B has parent goal_A
        val goalA = createGoal("goal_A", "goal_B")
        val goalB = createGoal("goal_B", "goal_A")
        val lookup = mapOf("goal_B" to goalB)

        val result = validator.validateHierarchy(goalA, lookup)
        assertTrue(result is GoalHierarchyValidator.ValidationResult.CircularReference)
    }

    @Test
    fun testCircularHierarchyDetected_ThreeNodes() {
        // A -> B -> C -> A
        val goalC = createGoal("goal_C", "goal_A")
        val goalB = createGoal("goal_B", "goal_C")
        val goalA = createGoal("goal_A", "goal_B")
        val lookup = mapOf("goal_B" to goalB, "goal_C" to goalC)

        val result = validator.validateHierarchy(goalA, lookup)
        assertTrue(result is GoalHierarchyValidator.ValidationResult.CircularReference)
    }

    @Test
    fun testMaxDepthExceeded() {
        // 1 -> 2 -> 3 -> 4 -> 5 -> 6 (depth 6 exceeds max 5)
        val g1 = createGoal("g1", null)
        val g2 = createGoal("g2", "g1")
        val g3 = createGoal("g3", "g2")
        val g4 = createGoal("g4", "g3")
        val g5 = createGoal("g5", "g4")
        val g6 = createGoal("g6", "g5")
        val lookup = mapOf("g1" to g1, "g2" to g2, "g3" to g3, "g4" to g4, "g5" to g5)

        val result = validator.validateHierarchy(g6, lookup)
        assertTrue(result is GoalHierarchyValidator.ValidationResult.MaxDepthExceeded)
        val maxExceeded = result as GoalHierarchyValidator.ValidationResult.MaxDepthExceeded
        assertEquals(6, maxExceeded.depth)
        assertEquals(5, maxExceeded.maxDepth)
    }

    @Test
    fun testParentNotFound() {
        val child = createGoal("child_orphan", "missing_parent_999")
        val result = validator.validateHierarchy(child, emptyMap())
        assertTrue(result is GoalHierarchyValidator.ValidationResult.ParentNotFound)
    }
}
