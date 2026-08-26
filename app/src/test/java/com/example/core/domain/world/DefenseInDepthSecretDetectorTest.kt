package com.example.core.domain.world

import com.example.core.domain.world.validation.DefenseInDepthSecretDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefenseInDepthSecretDetectorTest {

    @Test
    fun testOpenAiKeyRedacted() {
        val input = "Please configure OpenAI with key sk-1234567890abcdef1234567890abcdef for extraction"
        val result = DefenseInDepthSecretDetector.scanAndSanitize(input)
        assertTrue(result.containsSecret)
        assertFalse(result.sanitizedText.contains("sk-1234567890"))
        assertTrue(result.sanitizedText.contains("[REDACTED_SECRET]"))
    }

    @Test
    fun testGitHubTokenRedacted() {
        val input = "Deploy repo using token ghp_1234567890abcdef1234567890abcdef1234"
        val result = DefenseInDepthSecretDetector.scanAndSanitize(input)
        assertTrue(result.containsSecret)
        assertFalse(result.sanitizedText.contains("ghp_"))
        assertTrue(result.sanitizedText.contains("[REDACTED_SECRET]"))
    }

    @Test
    fun testPasswordAssignmentRedacted() {
        val input = "User preference: password = mySuperSecretP@ssw0rd! and host = localhost"
        val result = DefenseInDepthSecretDetector.scanAndSanitize(input)
        assertTrue(result.containsSecret)
        assertFalse(result.sanitizedText.contains("mySuperSecretP@ssw0rd!"))
        assertTrue(result.sanitizedText.contains("[REDACTED_SECRET]"))
        assertTrue(result.sanitizedText.contains("password"))
    }

    @Test
    fun testBearerTokenRedacted() {
        val input = "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.e30.t-IDcSemACt8x4iTmc6Y5uvRtErqYRxX0XYc27zq4WU"
        val result = DefenseInDepthSecretDetector.scanAndSanitize(input)
        assertTrue(result.containsSecret)
        assertFalse(result.sanitizedText.contains("eyJhbGciOiJIUzI1NiI"))
        assertTrue(result.sanitizedText.contains("Bearer [REDACTED_SECRET]"))
    }

    @Test
    fun testUuidNotRedacted() {
        val uuid = "123e4567-e89b-12d3-a456-426614174000"
        val input = "Task correlation ID is $uuid in system"
        val result = DefenseInDepthSecretDetector.scanAndSanitize(input)
        assertFalse(result.containsSecret)
        assertEquals(input, result.sanitizedText)
    }

    @Test
    fun testHexHashNotRedacted() {
        val hash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val input = "Verification hash is $hash"
        val result = DefenseInDepthSecretDetector.scanAndSanitize(input)
        assertFalse(result.containsSecret)
        assertEquals(input, result.sanitizedText)
    }

    @Test
    fun testHighEntropyRandomStringRedacted() {
        val highEntropyRandom = "k9ZbPmQ8WvL2QxP4TjR7VwT1ScY3RsN54"
        val input = "Secret entropy token $highEntropyRandom provided"
        val result = DefenseInDepthSecretDetector.scanAndSanitize(input)
        assertTrue(result.containsSecret)
        assertTrue(result.sanitizedText.contains("[REDACTED_SECRET]"))
    }
}
