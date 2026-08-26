package com.example.core.domain.world.classification

import com.example.core.domain.world.model.ContextCategory

/**
 * Deterministic classifier categorizing conversational context and observations.
 * Ensures observations are appropriately typed rather than dumped as generic memories.
 */
object ContextClassifier {

    data class ClassificationResult(
        val primaryCategory: ContextCategory,
        val secondaryCategories: Set<ContextCategory>,
        val confidence: Float,
        val reasoning: String
    )

    /**
     * Classifies a statement or natural language observation into its canonical domain category.
     */
    fun classify(text: String): ClassificationResult {
        val clean = text.trim().lowercase()

        return when {
            // Preferences: "I prefer", "I like", "Always use dark mode", "Format as markdown"
            clean.contains("i prefer") || clean.contains("i like") || clean.contains("always respond") ||
            clean.contains("my preference") || clean.contains("format as") -> {
                ClassificationResult(
                    primaryCategory = ContextCategory.PREFERENCE,
                    secondaryCategories = setOf(ContextCategory.FACT),
                    confidence = 0.90f,
                    reasoning = "User expressed an ongoing personal or formatting preference"
                )
            }

            // Goals: "My goal is", "I want to build", "I want to achieve", "Aiming to", "Target is"
            clean.contains("my goal") || clean.contains("i want to build") || clean.contains("i want to achieve") ||
            clean.contains("aiming to") || clean.contains("target is") || clean.contains("our objective") -> {
                ClassificationResult(
                    primaryCategory = ContextCategory.GOAL,
                    secondaryCategories = setOf(ContextCategory.FACT),
                    confidence = 0.95f,
                    reasoning = "User expressed a high-level future objective or milestone"
                )
            }

            // Commitments: "Meeting with", "Tomorrow at", "I promised", "Deadline is", "Due on"
            clean.contains("meeting with") || clean.contains("i promised") || clean.contains("scheduled for") ||
            clean.contains("due on") || clean.contains("due at") || clean.contains("deadline is") -> {
                ClassificationResult(
                    primaryCategory = ContextCategory.COMMITMENT,
                    secondaryCategories = setOf(ContextCategory.CONSTRAINT, ContextCategory.TEMPORARY_CONTEXT),
                    confidence = 0.90f,
                    reasoning = "Time-bounded obligation or appointment with an external party"
                )
            }

            // Constraints: "Must be under", "Limit to", "Budget is", "Cannot exceed"
            clean.contains("must be under") || clean.contains("limit to") || clean.contains("budget is") ||
            clean.contains("cannot exceed") || clean.contains("strictly without") -> {
                ClassificationResult(
                    primaryCategory = ContextCategory.CONSTRAINT,
                    secondaryCategories = setOf(ContextCategory.FACT),
                    confidence = 0.85f,
                    reasoning = "Operational boundary, limit, or negative constraint"
                )
            }

            // Tasks: "Please send", "Run test", "Create file", "Execute", "Do this"
            clean.startsWith("please ") || clean.startsWith("send ") || clean.startsWith("run ") ||
            clean.startsWith("create ") || clean.startsWith("fix ") || clean.startsWith("check ") -> {
                ClassificationResult(
                    primaryCategory = ContextCategory.TASK,
                    secondaryCategories = setOf(ContextCategory.OBSERVATION),
                    confidence = 0.90f,
                    reasoning = "Direct immediate action command or tool execution request"
                )
            }

            // Temporary Context: "Right now", "Currently at", "Today's weather", "In the car"
            clean.contains("right now") || clean.contains("currently at") || clean.contains("today is") ||
            clean.contains("for the next hour") -> {
                ClassificationResult(
                    primaryCategory = ContextCategory.TEMPORARY_CONTEXT,
                    secondaryCategories = setOf(ContextCategory.OBSERVATION),
                    confidence = 0.80f,
                    reasoning = "Transient situational context with limited temporal validity"
                )
            }

            // Default: General Fact
            else -> {
                ClassificationResult(
                    primaryCategory = ContextCategory.FACT,
                    secondaryCategories = emptySet(),
                    confidence = 0.70f,
                    reasoning = "General factual or observational statement"
                )
            }
        }
    }
}
