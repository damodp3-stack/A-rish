package com.example.core.domain.world.model

import com.example.core.domain.world.identity.UserId

/**
 * Time-bounded obligation involving external entities, deadlines, or deliverables.
 */
data class Commitment(
    val id: String,
    val userId: UserId,
    val title: String,
    val description: String,
    val dueTimestamp: Long,
    val associatedProjectId: String? = null,
    val associatedGoalId: String? = null,
    val isCompleted: Boolean = false,
    val provenance: EpistemicProvenance,
    val version: Long = 1L,
    val createdAt: Long,
    val completedAt: Long? = null
) {
    init {
        require(id.isNotBlank()) { "Commitment id cannot be blank" }
        require(title.isNotBlank()) { "Commitment title cannot be blank" }
        require(version >= 1L) { "Version must be >= 1" }
    }

    fun isOverdue(currentTimeMillis: Long): Boolean = !isCompleted && currentTimeMillis > dueTimestamp
}
