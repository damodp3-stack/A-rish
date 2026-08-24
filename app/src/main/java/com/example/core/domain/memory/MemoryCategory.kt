package com.example.core.domain.memory

/**
 * Structured categories for memory classification.
 */
enum class MemoryCategory {
    IDENTITY,     // User's name, preferences, personal profile
    PREFERENCE,   // App settings, UI themes, interaction mode
    WORK,         // Projects, tasks, deadlines, professional context
    SYSTEM,       // Tool configurations, device associations
    FACT,         // World facts, learned information
    CONVERSATION  // Episodic summary from recent sessions
}

/**
 * Categorized entity types extracted from memories.
 */
enum class EntityType {
    PERSON,
    ORGANIZATION,
    LOCATION,
    DATE_TIME,
    TOPIC,
    PROJECT,
    PHONE_NUMBER,
    URL,
    CUSTOM
}

/**
 * Typed reference to an entity inside memory (replaces comma-separated strings).
 */
data class MemoryEntityRef(
    val type: EntityType,
    val value: String
)
