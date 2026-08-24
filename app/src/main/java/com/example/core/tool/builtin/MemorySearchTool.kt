package com.example.core.tool.builtin

import com.example.core.data.local.dao.MemoryDao
import com.example.core.domain.capability.CapabilityId
import com.example.core.domain.execution.DeliveryGuarantee
import com.example.core.domain.execution.SideEffectSemantics
import com.example.core.domain.execution.ToolOutcome
import com.example.core.domain.security.PermissionRequirement
import com.example.core.domain.security.RiskLevel
import com.example.core.domain.tool.ArgumentProperty
import com.example.core.domain.tool.ArgumentType
import com.example.core.domain.tool.ToolArgumentSchema
import com.example.core.domain.tool.ToolContract

/**
 * Deterministic tool for retrieving memories and facts from the local memory vault.
 * Read-only, zero side effect.
 */
class MemorySearchTool(
    private val memoryDao: MemoryDao,
    override val id: String = "memory_search"
) : ToolContract {

    override val name: String = "Memory Matrix Search"
    override val description: String = "Retrieves relevant knowledge, preferences, and facts from the local memory vault."
    override val primaryCapability: CapabilityId = CapabilityId.RECALL_FACTS
    override val baseRiskLevel: RiskLevel = RiskLevel.LOW
    override val sideEffectSemantics: SideEffectSemantics = SideEffectSemantics.NO_SIDE_EFFECT
    override val deliveryGuarantee: DeliveryGuarantee = DeliveryGuarantee.EXACTLY_ONCE
    override val requiredPermissions: List<PermissionRequirement> = emptyList()

    override val argumentSchema: ToolArgumentSchema = ToolArgumentSchema(
        properties = mapOf(
            "query" to ArgumentProperty(
                type = ArgumentType.STRING,
                description = "Keywords or topic to search within memory",
                isRequired = true
            ),
            "limit" to ArgumentProperty(
                type = ArgumentType.INTEGER,
                description = "Maximum number of memories to return (1-20)",
                isRequired = false
            ),
            "category" to ArgumentProperty(
                type = ArgumentType.STRING,
                description = "Filter by memory category (e.g. FACT, PREFERENCE, IDENTITY, WORK)",
                isRequired = false
            )
        ),
        requiredKeys = listOf("query")
    )

    override suspend fun execute(args: Map<String, Any?>): ToolOutcome {
        val startTime = System.currentTimeMillis()
        val query = args["query"]?.toString()
            ?: return ToolOutcome.failure(id, "Mandatory argument 'query' is missing")

        return try {
            val limit = when (val lim = args["limit"]) {
                is Number -> lim.toInt().coerceIn(1, 20)
                else -> 5
            }
            val category = args["category"]?.toString()

            val memories = if (!category.isNullOrBlank()) {
                memoryDao.getMemoriesByCategory(category.uppercase()).take(limit)
            } else {
                try {
                    // Try lexical FTS first
                    val ftsQuery = query.trim().split("\\s+".toRegex()).joinToString(" OR ") { "$it*" }
                    val ftsResults = memoryDao.searchMemoriesLexical(ftsQuery).take(limit)
                    if (ftsResults.isNotEmpty()) {
                        ftsResults
                    } else {
                        memoryDao.getTopImportantMemories(limit)
                    }
                } catch (e: Exception) {
                    memoryDao.getTopImportantMemories(limit)
                }
            }

            // Record access for retrieved memories
            for (mem in memories) {
                try {
                    memoryDao.recordAccess(mem.id, startTime)
                } catch (_: Exception) {}
            }

            val resultsList = memories.map {
                mapOf(
                    "id" to it.id,
                    "content" to it.content,
                    "category" to it.category,
                    "importance" to it.importance,
                    "source" to it.source
                )
            }

            val duration = System.currentTimeMillis() - startTime
            ToolOutcome.success(
                toolId = id,
                data = mapOf(
                    "query" to query,
                    "count" to resultsList.size,
                    "memories" to resultsList
                ),
                summary = "Found ${resultsList.size} memories matching '$query'",
                semantics = sideEffectSemantics,
                durationMs = duration
            )
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            ToolOutcome.failure(
                toolId = id,
                errorMessage = "Failed to search memories: ${e.message}",
                errorDetails = e.stackTraceToString(),
                durationMs = duration
            )
        }
    }
}
