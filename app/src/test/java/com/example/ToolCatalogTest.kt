package com.example

import com.example.core.model.ToolRiskLevel
import com.example.core.tools.ToolCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCatalogTest {

    @Test
    fun toolCatalog_containsAllCoreTools() {
        val tools = ToolCatalog.allTools
        assertTrue(tools.isNotEmpty())
        
        val searchTool = tools.find { it.id == "web_search" }
        assertNotNull(searchTool)
        assertEquals("Live Web Intelligence", searchTool?.name)
        
        val calcTool = tools.find { it.id == "calculator" }
        assertNotNull(calcTool)
        
        val diagTool = tools.find { it.id == "device_diagnostics" }
        assertNotNull(diagTool)
        
        val weatherTool = tools.find { it.id == "weather" }
        assertNotNull(weatherTool)
    }

    @Test
    fun toolCatalog_checksConfirmationCorrectly() {
        // High/Medium risk tools must require confirmation
        val calendarTool = ToolCatalog.allTools.find { it.id == "calendar" }
        assertNotNull(calendarTool)
        assertTrue(calendarTool!!.requiresConfirmation)
        assertEquals(ToolRiskLevel.MEDIUM, calendarTool.riskLevel)
        
        // Low risk tools should not require confirmation
        val searchTool = ToolCatalog.allTools.find { it.id == "web_search" }
        assertNotNull(searchTool)
        assertEquals(false, searchTool!!.requiresConfirmation)
        assertEquals(ToolRiskLevel.LOW, searchTool.riskLevel)
    }
}

