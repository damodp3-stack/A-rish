package com.example.core.tool.builtin

import com.example.core.domain.capability.CapabilityId
import com.example.core.domain.execution.DeliveryGuarantee
import com.example.core.domain.execution.SideEffectSemantics
import com.example.core.domain.execution.ToolOutcome
import com.example.core.domain.security.PermissionRequirement
import com.example.core.domain.security.RiskLevel
import com.example.core.domain.tool.ArgumentProperty
import com.example.core.domain.tool.ArgumentType
import com.example.core.domain.tool.ToolArgumentSchema
import com.example.core.domain.tool.ToolContract
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Deterministic tool for reading current system date, time, and timestamp.
 * Read-only, zero side-effect.
 */
class GetCurrentTimeTool(
    override val id: String = "get_current_time"
) : ToolContract {

    override val name: String = "Get Current Time"
    override val description: String = "Reads system date, time, ISO timestamp, and time zone information."
    override val primaryCapability: CapabilityId = CapabilityId.GET_CURRENT_TIME
    override val baseRiskLevel: RiskLevel = RiskLevel.LOW
    override val sideEffectSemantics: SideEffectSemantics = SideEffectSemantics.NO_SIDE_EFFECT
    override val deliveryGuarantee: DeliveryGuarantee = DeliveryGuarantee.EXACTLY_ONCE
    override val requiredPermissions: List<PermissionRequirement> = emptyList()

    override val argumentSchema: ToolArgumentSchema = ToolArgumentSchema(
        properties = mapOf(
            "timeZone" to ArgumentProperty(
                type = ArgumentType.STRING,
                description = "Optional time zone ID (e.g. UTC, America/New_York, Asia/Kolkata)",
                isRequired = false
            ),
            "format" to ArgumentProperty(
                type = ArgumentType.STRING,
                description = "Optional date format pattern (e.g. yyyy-MM-dd HH:mm:ss)",
                isRequired = false
            )
        ),
        requiredKeys = emptyList()
    )

    override suspend fun execute(args: Map<String, Any?>): ToolOutcome {
        val startTime = System.currentTimeMillis()
        return try {
            val tzId = args["timeZone"] as? String
            val formatPattern = (args["format"] as? String) ?: "yyyy-MM-dd HH:mm:ss z"

            val timeZone = if (!tzId.isNullOrBlank()) {
                TimeZone.getTimeZone(tzId)
            } else {
                TimeZone.getDefault()
            }

            val now = Date(startTime)
            val sdf = SimpleDateFormat(formatPattern, Locale.US).apply {
                this.timeZone = timeZone
            }
            val formattedTime = sdf.format(now)

            val data = mapOf(
                "formattedTime" to formattedTime,
                "timestampMs" to startTime,
                "timeZone" to timeZone.id,
                "timeZoneDisplayName" to timeZone.getDisplayName(false, TimeZone.SHORT, Locale.US)
            )

            val duration = System.currentTimeMillis() - startTime
            ToolOutcome.success(
                toolId = id,
                data = data,
                summary = "Current time: $formattedTime (${timeZone.id})",
                semantics = sideEffectSemantics,
                durationMs = duration
            )
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            ToolOutcome.failure(
                toolId = id,
                errorMessage = "Failed to format time: ${e.message}",
                errorDetails = e.stackTraceToString(),
                durationMs = duration
            )
        }
    }
}
