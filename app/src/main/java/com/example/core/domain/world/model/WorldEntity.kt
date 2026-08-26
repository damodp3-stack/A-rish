package com.example.core.domain.world.model

import com.example.core.domain.world.identity.UserId

enum class WorldEntityType {
    PERSON,
    ORGANIZATION,
    PROJECT_NAME,
    DEVICE_ASSET,
    LOCATION,
    TOOL,
    CUSTOM
}

/**
 * First-class World Entity model with alias resolution support.
 */
data class WorldEntity(
    val canonicalId: String,
    val userId: UserId,
    val type: WorldEntityType,
    val primaryDisplayName: String,
    val aliases: Set<String> = emptySet(),
    val externalIdentifiers: Map<String, String> = emptyMap(),
    val metadata: Map<String, String> = emptyMap(),
    val provenance: EpistemicProvenance,
    val version: Long = 1L,
    val createdAt: Long,
    val updatedAt: Long
) {
    init {
        require(canonicalId.isNotBlank()) { "canonicalId cannot be blank" }
        require(primaryDisplayName.isNotBlank()) { "primaryDisplayName cannot be blank" }
        require(version >= 1L) { "Version must be >= 1" }
    }

    fun matchesQuery(query: String): Boolean {
        val q = query.trim().lowercase()
        if (canonicalId.lowercase() == q) return true
        if (primaryDisplayName.lowercase() == q) return true
        return aliases.any { it.lowercase() == q }
    }
}
