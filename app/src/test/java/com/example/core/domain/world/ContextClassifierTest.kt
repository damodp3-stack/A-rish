package com.example.core.domain.world

import com.example.core.domain.world.classification.ContextClassifier
import com.example.core.domain.world.model.ContextCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class ContextClassifierTest {

    @Test
    fun testClassifyPreference() {
        val text = "I prefer concise responses formatted as markdown lists"
        val result = ContextClassifier.classify(text)
        assertEquals(ContextCategory.PREFERENCE, result.primaryCategory)
    }

    @Test
    fun testClassifyGoal() {
        val text = "My goal is to complete the Phase 2A release before Friday"
        val result = ContextClassifier.classify(text)
        assertEquals(ContextCategory.GOAL, result.primaryCategory)
    }

    @Test
    fun testClassifyCommitment() {
        val text = "Meeting with product team tomorrow at 10am"
        val result = ContextClassifier.classify(text)
        assertEquals(ContextCategory.COMMITMENT, result.primaryCategory)
    }

    @Test
    fun testClassifyConstraint() {
        val text = "Execution must be under 30 seconds and cannot exceed memory limits"
        val result = ContextClassifier.classify(text)
        assertEquals(ContextCategory.CONSTRAINT, result.primaryCategory)
    }

    @Test
    fun testClassifyTask() {
        val text = "Please send the report to the team"
        val result = ContextClassifier.classify(text)
        assertEquals(ContextCategory.TASK, result.primaryCategory)
    }

    @Test
    fun testClassifyTemporaryContext() {
        val text = "Right now I am driving in the car"
        val result = ContextClassifier.classify(text)
        assertEquals(ContextCategory.TEMPORARY_CONTEXT, result.primaryCategory)
    }
}
