package com.example.core.execution

import com.example.core.capability.CapabilityResolver
import com.example.core.domain.capability.CapabilityId
import com.example.core.domain.capability.StructuredIntent
import com.example.core.domain.error.ArishException
import com.example.core.domain.security.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class CapabilityResolverTest {

    private lateinit var resolver: CapabilityResolver

    @Before
    fun setUp() {
        resolver = CapabilityResolver()
    }

    @Test
    fun `resolve valid structured intent maps to capability definition and preferred tool`() {
        val intent = StructuredIntent(
            intentId = "intent-1",
            intentName = "get_time",
            capabilityId = CapabilityId.GET_CURRENT_TIME,
            parameters = mapOf("timeZone" to "UTC"),
            rawUserPrompt = "what time is it in UTC?",
            confidence = 0.95f,
            requiresClarification = false
        )

        val resolved = resolver.resolve(intent)

        assertEquals(CapabilityId.GET_CURRENT_TIME, resolved.capability.id)
        assertEquals("get_current_time", resolved.selectedToolId)
        assertEquals("UTC", resolved.parameters["timeZone"])
        assertEquals(RiskLevel.LOW, resolved.riskEvaluation.level)
    }

    @Test
    fun `resolve calculator capability maps to calculate tool`() {
        val intent = StructuredIntent(
            intentId = "intent-2",
            intentName = "calculate_expression",
            capabilityId = CapabilityId.CALCULATE_MATH,
            parameters = mapOf("expression" to "25 * 4"),
            rawUserPrompt = "calculate 25 * 4",
            confidence = 0.99f,
            requiresClarification = false
        )

        val resolved = resolver.resolve(intent)

        assertEquals(CapabilityId.CALCULATE_MATH, resolved.capability.id)
        assertEquals("calculate", resolved.selectedToolId)
        assertEquals("25 * 4", resolved.parameters["expression"])
    }

    @Test
    fun `resolve memory store intent maps to memory_store tool`() {
        val intent = StructuredIntent(
            intentId = "intent-3",
            intentName = "remember_fact",
            capabilityId = CapabilityId.REMEMBER_FACT,
            parameters = mapOf("fact" to "User prefers dark mode"),
            rawUserPrompt = "remember that I prefer dark mode",
            confidence = 0.9f,
            requiresClarification = false
        )

        val resolved = resolver.resolve(intent)

        assertEquals(CapabilityId.REMEMBER_FACT, resolved.capability.id)
        assertEquals("memory_store", resolved.selectedToolId)
    }

    @Test
    fun `resolve ambiguous intent requiring clarification throws AmbiguousIntentException`() {
        val intent = StructuredIntent(
            intentId = "intent-ambiguous",
            intentName = "ambiguous_action",
            capabilityId = CapabilityId.GET_CURRENT_TIME,
            parameters = emptyMap(),
            rawUserPrompt = "time or alarm",
            confidence = 0.25f,
            requiresClarification = true,
            clarificationQuestion = "Did you mean current time or set an alarm?"
        )

        val ex = assertThrows(ArishException.AmbiguousIntentException::class.java) {
            resolver.resolve(intent)
        }

        assertEquals("Did you mean current time or set an alarm?", ex.message)
    }

    @Test
    fun `resolve intent with blank intentName throws SchemaValidationException`() {
        val intent = StructuredIntent(
            intentId = "intent-blank",
            intentName = "",
            capabilityId = CapabilityId.GET_CURRENT_TIME,
            parameters = emptyMap(),
            rawUserPrompt = "hello",
            confidence = 0.9f,
            requiresClarification = false
        )

        assertThrows(ArishException.SchemaValidationException::class.java) {
            resolver.resolve(intent)
        }
    }

    @Test
    fun `resolve intent with low confidence without clarification throws SchemaValidationException`() {
        val intent = StructuredIntent(
            intentId = "intent-low-conf",
            intentName = "low_confidence",
            capabilityId = CapabilityId.GET_CURRENT_TIME,
            parameters = emptyMap(),
            rawUserPrompt = "unclear",
            confidence = 0.15f,
            requiresClarification = false
        )

        assertThrows(ArishException.SchemaValidationException::class.java) {
            resolver.resolve(intent)
        }
    }
}
