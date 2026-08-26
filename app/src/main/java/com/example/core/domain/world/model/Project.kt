package com.example.core.domain.world.model

import com.example.core.domain.world.identity.UserId

enum class ProjectStatus {
    ACTIVE,
    ON_HOLD,
    COMPLETED,
    ARCHIVED;

    val isTerminal: Boolean
        get() = this == COMPLETED || this == ARCHIVED
}

/**
 * Authoritative Project Domain Model linking high-level Goals to concrete Tasks.
 */
data class Project(
    val id: String,
    val userId: UserId,
    val name: String,
    val description: String,
    val status: ProjectStatus = ProjectStatus.ACTIVE,
    val primaryGoalId: String? = null,
    val tags: Set<String> = emptySet(),
    val version: Long = 1L,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long? = null
) {
    init {
        require(id.isNotBlank()) { "Project id cannot be blank" }
        require(name.isNotBlank()) { "Project name cannot be blank" }
        require(version >= 1L) { "Version must be >= 1" }
    }
}
