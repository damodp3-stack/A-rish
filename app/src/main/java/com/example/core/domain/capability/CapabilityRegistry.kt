package com.example.core.domain.capability

import com.example.core.domain.error.ArishException

/**
 * Deterministic capability registry.
 * Maps intent to concrete capabilities and allowed tools without dynamic LLM injection.
 */
object CapabilityRegistry {

    private val capabilities: Map<CapabilityId, CapabilityDefinition> = listOf(
        CapabilityDefinition(
            id = CapabilityId.SEND_MESSAGE,
            title = "Send External Message",
            description = "Dispatches communication via WhatsApp or external messaging apps",
            supportedToolIds = listOf("android_action")
        ),
        CapabilityDefinition(
            id = CapabilityId.SHARE_CONTENT,
            title = "Share Content",
            description = "Shares text, links, or media via Android ShareSheet",
            supportedToolIds = listOf("android_action")
        ),
        CapabilityDefinition(
            id = CapabilityId.CREATE_NOTE,
            title = "Create Note",
            description = "Persists a note in local storage",
            supportedToolIds = listOf("notes")
        ),
        CapabilityDefinition(
            id = CapabilityId.READ_NOTES,
            title = "Read Notes",
            description = "Retrieves notes from local storage",
            supportedToolIds = listOf("notes")
        ),
        CapabilityDefinition(
            id = CapabilityId.DELETE_NOTE,
            title = "Delete Note",
            description = "Deletes a stored note",
            supportedToolIds = listOf("notes")
        ),
        CapabilityDefinition(
            id = CapabilityId.CREATE_CALENDAR_EVENT,
            title = "Create Calendar Event",
            description = "Schedules an event in system calendar",
            supportedToolIds = listOf("calendar")
        ),
        CapabilityDefinition(
            id = CapabilityId.SET_ALARM_TIMER,
            title = "Set Alarm / Timer",
            description = "Dispatches an alarm or countdown timer",
            supportedToolIds = listOf("android_action")
        ),
        CapabilityDefinition(
            id = CapabilityId.GET_BATTERY_STATUS,
            title = "Battery Telemetry",
            description = "Reads battery level and charging state",
            supportedToolIds = listOf("device_diagnostics")
        ),
        CapabilityDefinition(
            id = CapabilityId.GET_STORAGE_STATUS,
            title = "Storage Telemetry",
            description = "Reads internal storage free space",
            supportedToolIds = listOf("device_diagnostics")
        ),
        CapabilityDefinition(
            id = CapabilityId.GET_CONNECTIVITY_STATUS,
            title = "Network Telemetry",
            description = "Checks network connectivity status",
            supportedToolIds = listOf("device_diagnostics")
        ),
        CapabilityDefinition(
            id = CapabilityId.OPEN_APPLICATION,
            title = "Open App",
            description = "Launches an installed Android application",
            supportedToolIds = listOf("android_action")
        ),
        CapabilityDefinition(
            id = CapabilityId.WEB_SEARCH,
            title = "Web Search",
            description = "Executes internet search via search provider",
            supportedToolIds = listOf("web_search")
        ),
        CapabilityDefinition(
            id = CapabilityId.DEEP_RESEARCH,
            title = "Deep Research",
            description = "Conducts multi-query synthesis and structured research",
            supportedToolIds = listOf("web_search", "deep_research")
        ),
        CapabilityDefinition(
            id = CapabilityId.CALCULATE_MATH,
            title = "Calculator",
            description = "Evaluates mathematical expressions deterministically",
            supportedToolIds = listOf("calculate", "calculator")
        ),
        CapabilityDefinition(
            id = CapabilityId.GET_CURRENT_TIME,
            title = "Current Time",
            description = "Reads current date and time",
            supportedToolIds = listOf("get_current_time", "calculator", "device_diagnostics")
        ),
        CapabilityDefinition(
            id = CapabilityId.REMEMBER_FACT,
            title = "Remember Fact",
            description = "Stores knowledge in Memory Matrix",
            supportedToolIds = listOf("memory_store", "memory")
        ),
        CapabilityDefinition(
            id = CapabilityId.RECALL_FACTS,
            title = "Recall Memory",
            description = "Retrieves relevant knowledge from Memory Matrix",
            supportedToolIds = listOf("memory_search", "memory")
        ),
        CapabilityDefinition(
            id = CapabilityId.FORGET_FACT,
            title = "Forget Fact",
            description = "Deletes memory from Memory Matrix",
            supportedToolIds = listOf("memory")
        ),
        CapabilityDefinition(
            id = CapabilityId.SYSTEM_DIAGNOSTICS,
            title = "System Diagnostics",
            description = "Reads holistic device health and status",
            supportedToolIds = listOf("device_diagnostics")
        ),
        CapabilityDefinition(
            id = CapabilityId.SECURITY_AUDIT,
            title = "Security Audit",
            description = "Evaluates permissions and security status",
            supportedToolIds = listOf("device_diagnostics")
        )
    ).associateBy { it.id }

    fun get(id: CapabilityId): CapabilityDefinition =
        capabilities[id] ?: throw ArishException.UnknownCapabilityException("Capability not registered: $id")

    fun find(id: CapabilityId): CapabilityDefinition? = capabilities[id]

    fun all(): List<CapabilityDefinition> = capabilities.values.toList()
}
