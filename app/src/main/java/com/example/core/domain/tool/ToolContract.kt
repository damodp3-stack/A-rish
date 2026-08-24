package com.example.core.domain.tool

import com.example.core.domain.capability.CapabilityId
import com.example.core.domain.execution.DeliveryGuarantee
import com.example.core.domain.execution.SideEffectSemantics
import com.example.core.domain.execution.ToolOutcome
import com.example.core.domain.security.PermissionRequirement
import com.example.core.domain.security.RiskLevel

/**
 * Pure domain contract for any A-RISH tool implementation.
 * Independent of Android OS runtime, HTTP client, or UI.
 */
interface ToolContract {
    val id: String
    val name: String
    val description: String
    val primaryCapability: CapabilityId
    val baseRiskLevel: RiskLevel
    val sideEffectSemantics: SideEffectSemantics
    val deliveryGuarantee: DeliveryGuarantee
    val argumentSchema: ToolArgumentSchema
    val requiredPermissions: List<PermissionRequirement>

    suspend fun execute(args: Map<String, Any?>): ToolOutcome
}
