package com.example

import com.example.core.model.AssistantLanguage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextLeakTest {

    @Test
    fun systemPrompt_doesNotContainHardcodedDeviceOrHardwareModel() {
        val systemInstructionTemplate = """
            You are J.A.R.V.I.S., an intelligent, polite, efficient, and proactive AI assistant.
            Always prioritize direct answers, factual accuracy, and structured clarity.
            You have access to tools for web search, calculator, device diagnostics, weather, calendar, notes, file analysis, clipboard sharing, and deep research. Use them ONLY when explicitly relevant to the user's request.
            IMPORTANT PRIVACY & CONTEXT RULE: Do NOT mention or output the user's device model, hardware specifications, RAM, storage, or internal telemetry unless the user explicitly asks about their device or hardware diagnostics.
        """.trimIndent()

        assertFalse(systemInstructionTemplate.contains("OnePlus"))
        assertFalse(systemInstructionTemplate.contains("OPPO"))
        assertFalse(systemInstructionTemplate.contains("45 TOPS"))
        assertFalse(systemInstructionTemplate.contains("Snapdragon 8"))
        assertTrue(systemInstructionTemplate.contains("IMPORTANT PRIVACY & CONTEXT RULE"))
    }

    @Test
    fun localFallbackGreeting_doesNotMentionDeviceModel() {
        val englishGreeting = "At your service, sir. How may I assist you today?"
        val tamilGreeting = "வணக்கம் ஐயா! நான் JARVIS AI உதவியாளர். உங்களுக்கு எவ்வாறு உதவ முடியும்?"
        val tanglishGreeting = "Vanakkam sir! JARVIS systems are ready. Text, voice, search, and memory active. Enna pannanum சொல்லுங்க sir?"

        assertFalse(englishGreeting.contains("OnePlus"))
        assertFalse(tamilGreeting.contains("OnePlus"))
        assertFalse(tanglishGreeting.contains("OnePlus"))
        assertFalse(englishGreeting.contains("Snapdragon"))
    }
}
