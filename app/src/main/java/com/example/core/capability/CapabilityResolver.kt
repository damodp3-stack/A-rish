package com.example.core.capability

import com.example.core.domain.capability.CapabilityDefinition
import com.example.core.domain.capability.CapabilityId
import com.example.core.domain.capability.CapabilityRegistry
import com.example.core.domain.capability.StructuredIntent
import com.example.core.domain.error.ArishException
import com.example.core.domain.security.RiskEvaluation
import com.example.core.domain.security.RiskReason
import com.example.core.domain.validation.IntentValidator

data class ResolvedCapability(
    val capability: CapabilityDefinition,
    val selectedToolId: String,
    val parameters: Map<String, Any?>,
    val riskEvaluation: RiskEvaluation
)

/**
 * Deterministic capability resolver translating validated StructuredIntent into
 * concrete CapabilityDefinition and allowable ToolContract targets.
 *
 * Invariant: LLM cannot bypass this resolver or invoke arbitrary tool IDs.
 */
class CapabilityResolver(
    private val registry: CapabilityRegistry = CapabilityRegistry
) {

    fun resolve(intent: StructuredIntent): ResolvedCapability {
        // 1. Validate intent structural boundaries
        IntentValidator.validate(intent)

        if (intent.requiresClarification) {
            throw ArishException.AmbiguousIntentException(
                intent.clarificationQuestion ?: "Intent requires explicit user clarification"
            )
        }

        // 2. Lookup registered capability
        val capability = registry.find(intent.capabilityId)
            ?: throw ArishException.UnknownCapabilityException(
                "Capability '${intent.capabilityId}' is not registered in CapabilityRegistry"
            )

        // 3. Resolve preferred supported tool
        val preferredToolId = capability.supportedToolIds.firstOrNull()
            ?: throw ArishException.UnknownCapabilityException(
                "Capability '${capability.id}' has no registered supported tools"
            )

        val riskLevel = capability.id.defaultRisk
        val riskReasons: List<RiskReason> = when (capability.id) {
            CapabilityId.SEND_MESSAGE -> listOf(
                RiskReason.ExternalCommunication(
                    recipient = intent.parameters["recipient"]?.toString() ?: "ExternalRecipient",
                    channel = "Messaging"
                )
            )
            CapabilityId.SHARE_CONTENT -> listOf(
                RiskReason.ExternalCommunication(
                    recipient = intent.parameters["target"]?.toString() ?: "ExternalApp",
                    channel = "ShareSheet"
                )
            )
            CapabilityId.CREATE_NOTE -> listOf(RiskReason.LocalDataWrite)
            CapabilityId.READ_NOTES -> listOf(RiskReason.LocalDataRead)
            CapabilityId.DELETE_NOTE -> listOf(
                RiskReason.LocalDataDeletion(
                    targetDescription = intent.parameters["noteId"]?.toString() ?: "Note"
                )
            )
            CapabilityId.CREATE_CALENDAR_EVENT -> listOf(RiskReason.LocalDataWrite)
            CapabilityId.SET_ALARM_TIMER -> listOf(RiskReason.SystemModification("Alarm/Timer"))
            CapabilityId.GET_BATTERY_STATUS,
            CapabilityId.GET_STORAGE_STATUS,
            CapabilityId.GET_CONNECTIVITY_STATUS -> listOf(RiskReason.ReadOnlyDiagnostic)
            CapabilityId.OPEN_APPLICATION -> listOf(
                RiskReason.HighValueAction(
                    actionDescription = "Launch Application: ${intent.parameters["packageName"] ?: "App"}"
                )
            )
            CapabilityId.WEB_SEARCH,
            CapabilityId.DEEP_RESEARCH -> listOf(RiskReason.ReadOnlyDiagnostic)
            CapabilityId.CALCULATE_MATH,
            CapabilityId.GET_CURRENT_TIME -> listOf(RiskReason.ReadOnlyDiagnostic)
            CapabilityId.REMEMBER_FACT -> listOf(RiskReason.LocalDataWrite)
            CapabilityId.RECALL_FACTS -> listOf(RiskReason.LocalDataRead)
            CapabilityId.FORGET_FACT -> listOf(
                RiskReason.LocalDataDeletion(
                    targetDescription = intent.parameters["factId"]?.toString() ?: "MemoryFact"
                )
            )
            CapabilityId.SYSTEM_DIAGNOSTICS,
            CapabilityId.SECURITY_AUDIT -> listOf(RiskReason.ReadOnlyDiagnostic)
        }

        val requiresBiometric = capability.authRequirement == com.example.core.domain.security.AuthenticationRequirement.BIOMETRIC ||
            riskLevel == com.example.core.domain.security.RiskLevel.CRITICAL

        val riskEvaluation = RiskEvaluation(
            level = riskLevel,
            reasons = riskReasons,
            requiresApproval = riskLevel.requiresExplicitApproval,
            requiresBiometric = requiresBiometric,
            explanationText = "Capability: ${capability.title} (${capability.id.name})"
        )

        return ResolvedCapability(
            capability = capability,
            selectedToolId = preferredToolId,
            parameters = intent.parameters,
            riskEvaluation = riskEvaluation
        )
    }
}

