package com.example.core.domain.world.validation

import kotlin.math.ln

/**
 * Multi-signal Defense-in-Depth Secret Detector and Sanitizer.
 * Protects World Model & Goal storage from credential leakage and memory poisoning.
 */
object DefenseInDepthSecretDetector {

    private val UUID_REGEX = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    private val HEX_HASH_REGEX = Regex("^[0-9a-fA-F]{32,64}$")

    private val KNOWN_SECRET_PATTERNS = listOf(
        Regex("(?i)\\b(sk-[a-zA-Z0-9]{20,})\\b"),
        Regex("(?i)\\b(AIza[0-9A-Za-z\\-_]{35})\\b"),
        Regex("(?i)\\b(gh[pousr]_[a-zA-Z0-9]{36,})\\b"),
        Regex("(?i)\\b(xox[baprs]-[0-9a-zA-Z]{10,48})\\b"),
        Regex("(?i)\\b(AKIA[0-9A-Z]{16})\\b"),
        Regex("-----BEGIN (?:RSA )?PRIVATE KEY-----"),
        Regex("(?i)Bearer\\s+[a-zA-Z0-9_\\-\\.]{20,}"),
        Regex("(?i)(?:password|secret|api_?key|auth_?token|client_?secret)\\s*[:=]\\s*[\"']?([^\\s\"',;]+)[\"']?")
    )

    data class ScanResult(
        val containsSecret: Boolean,
        val sanitizedText: String,
        val detectedPatternsCount: Int
    )

    /**
     * Scans text for credentials using signature matching, keyword proximity, and entropy heuristics.
     */
    fun scanAndSanitize(input: String): ScanResult {
        if (input.isBlank()) {
            return ScanResult(containsSecret = false, sanitizedText = input, detectedPatternsCount = 0)
        }

        var text = input
        var matchesFound = 0

        // 1. Signature-based redaction
        for (pattern in KNOWN_SECRET_PATTERNS) {
            val matches = pattern.findAll(text).toList()
            if (matches.isNotEmpty()) {
                matchesFound += matches.size
                text = pattern.replace(text) { matchResult ->
                    val fullMatch = matchResult.value
                    if (fullMatch.startsWith("Bearer ", ignoreCase = true)) {
                        "Bearer [REDACTED_SECRET]"
                    } else if (fullMatch.contains("=") || fullMatch.contains(":")) {
                        val keyPart = fullMatch.substringBefore("=").substringBefore(":")
                        val delim = if (fullMatch.contains("=")) "=" else ":"
                        "$keyPart$delim [REDACTED_SECRET]"
                    } else {
                        "[REDACTED_SECRET]"
                    }
                }
            }
        }

        // 2. High-entropy token analysis on individual whitespace-delimited words
        val words = text.split(Regex("\\s+"))
        val sanitizedWords = words.map { word ->
            val cleanWord = word.trim(',', ';', '"', '\'', '(', ')', '[', ']', '{', '}')
            if (shouldRedactForEntropy(cleanWord)) {
                matchesFound++
                "[REDACTED_SECRET]"
            } else {
                word
            }
        }

        val finalText = sanitizedWords.joinToString(" ")
        return ScanResult(
            containsSecret = matchesFound > 0,
            sanitizedText = finalText,
            detectedPatternsCount = matchesFound
        )
    }

    private fun shouldRedactForEntropy(token: String): Boolean {
        // Disregard short tokens
        if (token.length < 24) return false

        // Do not redact legitimate UUIDs or pure hex digests
        if (UUID_REGEX.matches(token)) return false
        if (HEX_HASH_REGEX.matches(token)) return false

        // Calculate Shannon entropy (base 2)
        val entropy = calculateShannonEntropy(token)
        // High entropy threshold for mixed-case base64/alphanumeric strings
        return entropy > 4.5
    }

    private fun calculateShannonEntropy(str: String): Double {
        if (str.isEmpty()) return 0.0
        val frequencyMap = mutableMapOf<Char, Int>()
        for (c in str) {
            frequencyMap[c] = (frequencyMap[c] ?: 0) + 1
        }

        val len = str.length.toDouble()
        var entropy = 0.0
        for (count in frequencyMap.values) {
            val p = count.toDouble() / len
            entropy -= p * (ln(p) / ln(2.0))
        }
        return entropy
    }
}
