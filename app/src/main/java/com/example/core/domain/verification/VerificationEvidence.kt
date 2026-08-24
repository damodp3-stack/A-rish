package com.example.core.domain.verification

/**
 * Type of evidence collected during step execution.
 */
enum class EvidenceType {
    LOCAL_DATABASE_ROW,
    SYSTEM_INTENT_RESOLVED,
    HTTP_STATUS_200,
    SYSTEM_PROVIDER_URI,
    OS_SERVICE_STATE,
    USER_MANUAL_VERIFICATION,
    NONE
}

enum class ConfidenceLevel {
    CERTAIN,       // Confirmed via atomic DB insertion / system query
    PROBABLE,      // Confirmed activity launch / 200 HTTP response
    INDETERMINATE, // Process was handed off to third-party app
    UNVERIFIED     // No evidence could be collected
}

/**
 * Concrete evidence record attesting to an execution outcome.
 */
data class VerificationEvidence(
    val evidenceId: String,
    val type: EvidenceType,
    val confidence: ConfidenceLevel,
    val description: String,
    val artifactUri: String? = null,
    val capturedAt: Long
)
