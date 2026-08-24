package com.example.core.execution

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.data.local.ArishDatabase
import com.example.core.domain.execution.ExecutionStatus
import com.example.core.domain.execution.SideEffectSemantics
import com.example.core.tool.builtin.CalculateMathTool
import com.example.core.tool.builtin.GetCurrentTimeTool
import com.example.core.tool.builtin.MemorySearchTool
import com.example.core.tool.builtin.MemoryStoreTool
import com.example.core.tool.builtin.WebSearchItem
import com.example.core.tool.builtin.WebSearchProvider
import com.example.core.tool.builtin.WebSearchResult
import com.example.core.tool.builtin.WebSearchTool
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BuiltinToolsTest {

    private lateinit var context: Context
    private lateinit var db: ArishDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, ArishDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // 1. GetCurrentTimeTool Tests
    @Test
    fun `GetCurrentTimeTool returns valid time format and metadata`() = runBlocking {
        val tool = GetCurrentTimeTool()
        val outcome = tool.execute(mapOf("timeZone" to "UTC", "format" to "yyyy-MM-dd"))

        assertEquals(ExecutionStatus.EXECUTED, outcome.status)
        assertEquals(SideEffectSemantics.NO_SIDE_EFFECT, outcome.sideEffectSemantics)
        assertNotNull(outcome.rawResultData["formattedTime"])
        assertEquals("UTC", outcome.rawResultData["timeZone"])
        assertTrue(outcome.executionDurationMs >= 0)
    }

    // 2. CalculateMathTool Tests
    @Test
    fun `CalculateMathTool evaluates arithmetic operations accurately`() = runBlocking {
        val tool = CalculateMathTool()

        val addOutcome = tool.execute(mapOf("expression" to "12 + 34"))
        assertEquals(ExecutionStatus.EXECUTED, addOutcome.status)
        assertEquals(46.0, addOutcome.rawResultData["result"] as Double, 0.0001)

        val precedenceOutcome = tool.execute(mapOf("expression" to "2 + 3 * 4"))
        assertEquals(14.0, precedenceOutcome.rawResultData["result"] as Double, 0.0001)

        val parenthesesOutcome = tool.execute(mapOf("expression" to "(2 + 3) * 4"))
        assertEquals(20.0, parenthesesOutcome.rawResultData["result"] as Double, 0.0001)

        val sqrtOutcome = tool.execute(mapOf("expression" to "sqrt(144) + 8"))
        assertEquals(20.0, sqrtOutcome.rawResultData["result"] as Double, 0.0001)

        val powerOutcome = tool.execute(mapOf("expression" to "2 ^ 8"))
        assertEquals(256.0, powerOutcome.rawResultData["result"] as Double, 0.0001)
    }

    @Test
    fun `CalculateMathTool handles division by zero safely without crashing`() = runBlocking {
        val tool = CalculateMathTool()
        val outcome = tool.execute(mapOf("expression" to "100 / 0"))

        assertEquals(ExecutionStatus.FAILED, outcome.status)
        assertTrue(outcome.errorMessage?.contains("Division by zero") == true)
    }

    @Test
    fun `CalculateMathTool handles syntax errors safely`() = runBlocking {
        val tool = CalculateMathTool()
        val outcome = tool.execute(mapOf("expression" to "10 + * 5"))

        assertEquals(ExecutionStatus.FAILED, outcome.status)
        assertNotNull(outcome.errorMessage)
    }

    // 3. MemoryStoreTool & MemorySearchTool Tests
    @Test
    fun `MemoryStoreTool inserts facts into Room database and MemorySearchTool retrieves them`() = runBlocking {
        val storeTool = MemoryStoreTool(db.memoryDao())
        val searchTool = MemorySearchTool(db.memoryDao())

        val storeOutcome = storeTool.execute(
            mapOf(
                "fact" to "User's favorite color is Cobalt Blue",
                "category" to "PREFERENCE",
                "importance" to 8
            )
        )

        assertEquals(ExecutionStatus.EXECUTED, storeOutcome.status)
        assertEquals(SideEffectSemantics.LOCAL_TRANSACTIONAL, storeOutcome.sideEffectSemantics)
        val memoryId = storeOutcome.rawResultData["memoryId"] as String
        assertNotNull(memoryId)

        // Verify entity persisted in SQLite
        val entity = db.memoryDao().getMemoryById(memoryId)
        assertNotNull(entity)
        assertEquals("User's favorite color is Cobalt Blue", entity?.content)
        assertEquals("PREFERENCE", entity?.category)
        assertEquals(8, entity?.importance)

        // Search for the memory
        val searchOutcome = searchTool.execute(mapOf("query" to "favorite color"))
        assertEquals(ExecutionStatus.EXECUTED, searchOutcome.status)
        val count = searchOutcome.rawResultData["count"] as Int
        assertTrue("Expected at least 1 memory found", count >= 1)
    }

    // 4. WebSearchTool Tests
    @Test
    fun `WebSearchTool with provider executes search and formats results`() = runBlocking {
        val provider = object : WebSearchProvider {
            override suspend fun search(query: String, maxResults: Int): WebSearchResult {
                return WebSearchResult(
                    query = query,
                    items = listOf(
                        WebSearchItem(
                            title = "Kotlin Documentation",
                            snippet = "Kotlin is a modern statically typed programming language.",
                            url = "https://kotlinlang.org"
                        )
                    ),
                    isSuccess = true
                )
            }
        }

        val tool = WebSearchTool(provider = provider)
        val outcome = tool.execute(mapOf("query" to "Kotlin programming language"))

        assertEquals(ExecutionStatus.EXECUTED, outcome.status)
        assertEquals(1, outcome.rawResultData["count"])
        assertTrue(outcome.summaryText.contains("Retrieved 1 web search results"))
    }

    @Test
    fun `WebSearchTool without provider fails cleanly without fake data`() = runBlocking {
        val tool = WebSearchTool(provider = null)
        val outcome = tool.execute(mapOf("query" to "test query"))

        assertEquals(ExecutionStatus.FAILED, outcome.status)
        assertTrue(outcome.errorMessage?.contains("not configured") == true)
    }
}
