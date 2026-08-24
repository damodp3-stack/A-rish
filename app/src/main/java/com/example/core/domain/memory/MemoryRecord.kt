package com.example.core.domain.memory

/**
 * Origin source of a memory entry.
 */
enum class MemorySource {
    USER_EXPLICIT,      // User told A-RISH "Remember that..."
    AUTOMATED_EXTRACTION, // Extracted by MemoryDistiller from conversation
    SYSTEM_INFERRED     // Inferred from repeated interactions
}

/**
 * Pure domain model for a single knowledge record in the Memory Vault.
 */
data class MemoryRecord(
    val id: String,
    val content: String,
    val category: MemoryCategory,
    val importance: Int, // 1 to 10 scale
    val entities: List<MemoryEntityRef> = emptyList(),
    val source: MemorySource = MemorySource.USER_EXPLICIT,
    val createdAt: Long,
    val lastAccessedAt: Long,
    val accessCount: Int = 1
) {
    init {
        require(importance in 1..10) { "Importance must be between 1 and 10 (got $importance)" }
        require(content.isNotBlank()) { "Memory content cannot be blank" }
    }
}
