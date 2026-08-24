package com.example.core.capability

import com.example.core.domain.capability.CapabilityDefinition
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
        val riskEvaluation = RiskEvaluation(
            level = riskLevel,
            reasons = listOf(RiskReason.ReadOnlyDiagnostic),
            requiresApproval = riskLevel.requiresExplicitApproval,
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

