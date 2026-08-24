package com.example.core.tool.builtin

/**
 * Result data model for Web Search abstraction.
 */
data class WebSearchResult(
    val query: String,
    val items: List<WebSearchItem>,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

data class WebSearchItem(
    val title: String,
    val snippet: String,
    val url: String
)

/**
 * Pure provider interface for Web Search integrations.
 * Decoupled from concrete HTTP libraries or external vendor APIs.
 */
interface WebSearchProvider {
    suspend fun search(query: String, maxResults: Int = 5): WebSearchResult
}
