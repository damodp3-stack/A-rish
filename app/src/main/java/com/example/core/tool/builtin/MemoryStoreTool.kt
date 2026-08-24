package com.example.core.tool.builtin

import com.example.core.data.local.dao.MemoryDao
import com.example.core.data.local.entity.MemoryEntity
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
import java.util.UUID

/**
 * Deterministic tool for storing factual knowledge into the local SQLite memory vault.
 * Local transactional database execution.
 */
class MemoryStoreTool(
    private val memoryDao: MemoryDao,
    override val id: String = "memory_store"
) : ToolContract {

    override val name: String = "Memory Matrix Store"
    override val description: String = "Stores knowledge, preferences, and facts in the secure local memory vault."
    override val primaryCapability: CapabilityId = CapabilityId.REMEMBER_FACT
    override val baseRiskLevel: RiskLevel = RiskLevel.LOW
    override val sideEffectSemantics: SideEffectSemantics = SideEffectSemantics.LOCAL_TRANSACTIONAL
    override val deliveryGuarantee: DeliveryGuarantee = DeliveryGuarantee.EXACTLY_ONCE
    override val requiredPermissions: List<PermissionRequirement> = emptyList()

    override val argumentSchema: ToolArgumentSchema = ToolArgumentSchema(
        properties = mapOf(
            "fact" to ArgumentProperty(
                type = ArgumentType.STRING,
                description = "The knowledge or fact to remember",
                isRequired = true
            ),
            "category" to ArgumentProperty(
                type = ArgumentType.STRING,
                description = "Category (e.g. FACT, PREFERENCE, IDENTITY, WORK, SYSTEM)",
                isRequired = false,
                allowedValues = listOf("FACT", "PREFERENCE", "IDENTITY", "WORK", "SYSTEM", "CONVERSATION", "general")
            ),
            "importance" to ArgumentProperty(
                type = ArgumentType.INTEGER,
                description = "Importance score between 1 and 10",
                isRequired = false
            ),
            "source" to ArgumentProperty(
                type = ArgumentType.STRING,
                description = "Source origin (e.g. USER_EXPLICIT, AUTOMATED_EXTRACTION)",
                isRequired = false
            )
        ),
        requiredKeys = listOf("fact")
    )

    override suspend fun execute(args: Map<String, Any?>): ToolOutcome {
        val startTime = System.currentTimeMillis()
        val fact = args["fact"]?.toString()
            ?: return ToolOutcome.failure(id, "Mandatory argument 'fact' is missing")

        return try {
            val category = (args["category"]?.toString() ?: "FACT").uppercase()
            val importance = when (val imp = args["importance"]) {
                is Number -> imp.toInt().coerceIn(1, 10)
                else -> 5
            }
            val source = args["source"]?.toString() ?: "USER_EXPLICIT"

            val memoryId = "mem-${UUID.randomUUID()}"
            val entity = MemoryEntity(
                id = memoryId,
                content = fact,
                category = category,
                importance = importance,
                entitiesJson = "[]",
                source = source,
                createdAt = startTime,
                lastAccessedAt = startTime,
                accessCount = 1
            )

            memoryDao.insertMemory(entity)

            val duration = System.currentTimeMillis() - startTime
            ToolOutcome.success(
                toolId = id,
                data = mapOf(
                    "memoryId" to memoryId,
                    "fact" to fact,
                    "category" to category,
                    "importance" to importance,
                    "storedAt" to startTime
                ),
                summary = "Stored in memory vault [$category]: \"$fact\"",
                semantics = sideEffectSemantics,
                durationMs = duration
            )
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            ToolOutcome.failure(
                toolId = id,
                errorMessage = "Failed to store memory: ${e.message}",
                errorDetails = e.stackTraceToString(),
                durationMs = duration
            )
        }
    }
}
