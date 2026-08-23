package com.example

import com.example.core.model.AssistantLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class PreferencesTest {

    @Test
    fun assistantLanguage_containsExpectedLocales() {
        val tamil = AssistantLanguage.TAMIL
        val english = AssistantLanguage.ENGLISH
        val tanglish = AssistantLanguage.TANGLISH
        
        assertEquals("ta-IN", tamil.code)
        assertEquals("en-US", english.code)
        assertEquals("ta-IN-tanglish", tanglish.code)
    }
}
