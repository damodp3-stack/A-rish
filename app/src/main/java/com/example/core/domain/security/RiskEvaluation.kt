package com.example.core.domain.security

/**
 * Structured risk reasons explaining why an operation was assigned a given risk level.
 */
sealed class RiskReason {
    data object ReadOnlyDiagnostic : RiskReason()
    data object LocalDataRead : RiskReason()
    data object LocalDataWrite : RiskReason()
    data class LocalDataDeletion(val targetDescription: String) : RiskReason()
    data class ExternalCommunication(val recipient: String, val channel: String) : RiskReason()
    data class SystemModification(val settingName: String) : RiskReason()
    data class UntrustedTarget(val target: String) : RiskReason()
    data class HighValueAction(val actionDescription: String) : RiskReason()
    data class CustomReason(val description: String) : RiskReason()
}

/**
 * Deterministic outcome of evaluating risk for a proposed capability action.
 */
data class RiskEvaluation(
    val level: RiskLevel,
    val reasons: List<RiskReason>,
    val requiresApproval: Boolean,
    val requiresBiometric: Boolean = false,
    val explanationText: String
) {
    companion object {
        fun low(reason: RiskReason = RiskReason.ReadOnlyDiagnostic, explanation: String = "Read-only safe operation"): RiskEvaluation =
            RiskEvaluation(
                level = RiskLevel.LOW,
                reasons = listOf(reason),
                requiresApproval = false,
                requiresBiometric = false,
                explanationText = explanation
            )

        fun medium(reason: RiskReason, explanation: String): RiskEvaluation =
            RiskEvaluation(
                level = RiskLevel.MEDIUM,
                reasons = listOf(reason),
                requiresApproval = false,
                requiresBiometric = false,
                explanationText = explanation
            )

        fun high(reasons: List<RiskReason>, explanation: String): RiskEvaluation =
            RiskEvaluation(
                level = RiskLevel.HIGH,
                reasons = reasons,
                requiresApproval = true,
                requiresBiometric = false,
                explanationText = explanation
            )

        fun critical(reasons: List<RiskReason>, explanation: String, requiresBiometric: Boolean = true): RiskEvaluation =
            RiskEvaluation(
                level = RiskLevel.CRITICAL,
                reasons = reasons,
                requiresApproval = true,
                requiresBiometric = requiresBiometric,
                explanationText = explanation
            )
    }
}
