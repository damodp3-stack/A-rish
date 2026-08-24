package com.example.core.domain.memory

/**
 * Memory score weights for hybrid retrieval ranking.
 */
data class MemoryScoreWeights(
    val ftsWeight: Float = 0.35f,
    val entityMatchWeight: Float = 0.25f,
    val importanceWeight: Float = 0.25f,
    val recencyWeight: Float = 0.15f
) {
    init {
        val sum = ftsWeight + entityMatchWeight + importanceWeight + recencyWeight
        require(sum in 0.99f..1.01f) { "MemoryScoreWeights must sum to 1.0 (got $sum)" }
    }
}

/**
 * Scored memory record after hybrid ranking.
 */
data class ScoredMemory(
    val record: MemoryRecord,
    val finalScore: Float,
    val ftsScore: Float,
    val entityScore: Float,
    val importanceScore: Float,
    val recencyScore: Float
)
