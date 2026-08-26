package com.example.core.domain.world.model

import com.example.core.domain.world.identity.UserId

enum class PreferenceDomain {
    COMMUNICATION,
    LANGUAGE,
    NOTIFICATION,
    WORKFLOW,
    PRIVACY,
    CUSTOM
}

/**
 * Domain-classified user preference.
 * Explicitly separated from Goals and Tasks to prevent memory poisoning.
 */
data class UserPreference(
    val id: String,
    val userId: UserId,
    val domain: PreferenceDomain,
    val preferenceKey: String,
    val preferenceValue: String,
    val provenance: EpistemicProvenance,
    val version: Long = 1L,
    val createdAt: Long,
    val updatedAt: Long
) {
    init {
        require(id.isNotBlank()) { "Preference id cannot be blank" }
        require(preferenceKey.isNotBlank()) { "Preference key cannot be blank" }
        require(preferenceValue.isNotBlank()) { "Preference value cannot be blank" }
        require(version >= 1L) { "Version must be >= 1" }
    }
}
