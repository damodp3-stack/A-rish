package com.example.core.domain.capability

import com.example.core.domain.security.AuthenticationRequirement
import com.example.core.domain.security.PermissionRequirement
import com.example.core.domain.security.RiskLevel

/**
 * Top-level capability category grouping.
 */
enum class CapabilityCategory {
    COMMUNICATION,
    PRODUCTIVITY,
    DEVICE,
    INFORMATION,
    MEMORY,
    SYSTEM
}

/**
 * Capability identifiers for deterministic routing.
 */
enum class CapabilityId(val category: CapabilityCategory, val defaultRisk: RiskLevel) {
    // COMMUNICATION
    SEND_MESSAGE(CapabilityCategory.COMMUNICATION, RiskLevel.HIGH),
    SHARE_CONTENT(CapabilityCategory.COMMUNICATION, RiskLevel.MEDIUM),

    // PRODUCTIVITY
    CREATE_NOTE(CapabilityCategory.PRODUCTIVITY, RiskLevel.LOW),
    DELETE_NOTE(CapabilityCategory.PRODUCTIVITY, RiskLevel.HIGH),
    READ_NOTES(CapabilityCategory.PRODUCTIVITY, RiskLevel.LOW),
    CREATE_CALENDAR_EVENT(CapabilityCategory.PRODUCTIVITY, RiskLevel.MEDIUM),
    SET_ALARM_TIMER(CapabilityCategory.PRODUCTIVITY, RiskLevel.MEDIUM),

    // DEVICE
    GET_BATTERY_STATUS(CapabilityCategory.DEVICE, RiskLevel.LOW),
    GET_STORAGE_STATUS(CapabilityCategory.DEVICE, RiskLevel.LOW),
    GET_CONNECTIVITY_STATUS(CapabilityCategory.DEVICE, RiskLevel.LOW),
    OPEN_APPLICATION(CapabilityCategory.DEVICE, RiskLevel.LOW),

    // INFORMATION
    WEB_SEARCH(CapabilityCategory.INFORMATION, RiskLevel.LOW),
    DEEP_RESEARCH(CapabilityCategory.INFORMATION, RiskLevel.LOW),
    CALCULATE_MATH(CapabilityCategory.INFORMATION, RiskLevel.LOW),
    GET_CURRENT_TIME(CapabilityCategory.INFORMATION, RiskLevel.LOW),

    // MEMORY
    REMEMBER_FACT(CapabilityCategory.MEMORY, RiskLevel.LOW),
    RECALL_FACTS(CapabilityCategory.MEMORY, RiskLevel.LOW),
    FORGET_FACT(CapabilityCategory.MEMORY, RiskLevel.HIGH),

    // SYSTEM
    SYSTEM_DIAGNOSTICS(CapabilityCategory.SYSTEM, RiskLevel.LOW),
    SECURITY_AUDIT(CapabilityCategory.SYSTEM, RiskLevel.LOW)
}

/**
 * Formal capability contract definition.
 */
data class CapabilityDefinition(
    val id: CapabilityId,
    val title: String,
    val description: String,
    val requiredPermissions: List<PermissionRequirement> = emptyList(),
    val authRequirement: AuthenticationRequirement = AuthenticationRequirement.NONE,
    val supportedToolIds: List<String>
)
