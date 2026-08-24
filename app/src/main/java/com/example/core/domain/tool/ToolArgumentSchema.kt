package com.example.core.domain.tool

/**
 * Data type for schema parameter definitions.
 */
enum class ArgumentType {
    STRING,
    INTEGER,
    NUMBER,
    BOOLEAN,
    OBJECT,
    ARRAY
}

/**
 * Property definition inside a tool argument schema.
 */
data class ArgumentProperty(
    val type: ArgumentType,
    val description: String,
    val isRequired: Boolean = true,
    val allowedValues: List<String>? = null,
    val defaultValue: Any? = null
)

/**
 * Formal schema contract defining expected arguments for a Tool.
 */
data class ToolArgumentSchema(
    val properties: Map<String, ArgumentProperty>,
    val requiredKeys: List<String>
)
