package com.example.core.tool

import com.example.core.domain.capability.CapabilityId
import com.example.core.domain.error.ArishException
import com.example.core.domain.tool.ToolContract
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe registry maintaining the catalog of available executable ToolContracts.
 */
class ToolRegistry {

    private val tools = ConcurrentHashMap<String, ToolContract>()

    fun register(tool: ToolContract) {
        tools[tool.id] = tool
    }

    fun registerAll(toolList: Collection<ToolContract>) {
        toolList.forEach { register(it) }
    }

    fun get(toolId: String): ToolContract {
        return tools[toolId] ?: throw ArishException.ToolNotFoundException(toolId)
    }

    fun find(toolId: String): ToolContract? = tools[toolId]

    fun getToolsForCapability(capabilityId: CapabilityId): List<ToolContract> {
        return tools.values.filter { it.primaryCapability == capabilityId }
    }

    fun all(): List<ToolContract> = tools.values.toList()

    fun contains(toolId: String): Boolean = tools.containsKey(toolId)

    fun clear() {
        tools.clear()
    }
}
