package com.example.core.tool.builtin

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
 * Deterministic Web Search tool backed by an optional WebSearchProvider.
 * Fails cleanly if provider is unavailable or query fails (no mock data).
 */
class WebSearchTool(
    private val provider: WebSearchProvider? = null,
    override val id: String = "web_search"
) : ToolContract {

    override val name: String = "Web Search"
    override val description: String = "Executes web searches to retrieve verified information and citations."
    override val primaryCapability: CapabilityId = CapabilityId.WEB_SEARCH
    override val baseRiskLevel: RiskLevel = RiskLevel.LOW
    override val sideEffectSemantics: SideEffectSemantics = SideEffectSemantics.NO_SIDE_EFFECT
    override val deliveryGuarantee: DeliveryGuarantee = DeliveryGuarantee.AT_LEAST_ONCE
    override val requiredPermissions: List<PermissionRequirement> = emptyList()

    override val argumentSchema: ToolArgumentSchema = ToolArgumentSchema(
        properties = mapOf(
            "query" to ArgumentProperty(
                type = ArgumentType.STRING,
                description = "Web search query string",
                isRequired = true
            ),
            "maxResults" to ArgumentProperty(
                type = ArgumentType.INTEGER,
                description = "Maximum results count (1-10)",
                isRequired = false
            )
        ),
        requiredKeys = listOf("query")
    )

    override suspend fun execute(args: Map<String, Any?>): ToolOutcome {
        val startTime = System.currentTimeMillis()
        val query = args["query"]?.toString()
            ?: return ToolOutcome.failure(id, "Mandatory argument 'query' is missing")

        val maxResults = when (val max = args["maxResults"]) {
            is Number -> max.toInt().coerceIn(1, 10)
            else -> 5
        }

        if (provider == null) {
            val duration = System.currentTimeMillis() - startTime
            return ToolOutcome.failure(
                toolId = id,
                errorMessage = "WebSearchProvider is not configured or offline",
                durationMs = duration
            )
        }

        return try {
            val result = provider.search(query, maxResults)
            val duration = System.currentTimeMillis() - startTime

            if (result.isSuccess) {
                val itemsData = result.items.map {
                    mapOf(
                        "title" to it.title,
                        "snippet" to it.snippet,
                        "url" to it.url
                    )
                }
                ToolOutcome.success(
                    toolId = id,
                    data = mapOf(
                        "query" to query,
                        "count" to itemsData.size,
                        "results" to itemsData
                    ),
                    summary = "Retrieved ${itemsData.size} web search results for '$query'",
                    semantics = sideEffectSemantics,
                    durationMs = duration
                )
            } else {
                ToolOutcome.failure(
                    toolId = id,
                    errorMessage = result.errorMessage ?: "Web search query failed",
                    durationMs = duration
                )
            }
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            ToolOutcome.failure(
                toolId = id,
                errorMessage = "Web search exception: ${e.message}",
                errorDetails = e.stackTraceToString(),
                durationMs = duration
            )
        }
    }
}
