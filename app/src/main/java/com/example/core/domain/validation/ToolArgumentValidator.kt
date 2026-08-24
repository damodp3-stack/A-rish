package com.example.core.domain.validation

import com.example.core.domain.error.ArishException
import com.example.core.domain.tool.ArgumentType
import com.example.core.domain.tool.ToolArgumentSchema

/**
 * Validates tool arguments against the declared ToolArgumentSchema.
 */
object ToolArgumentValidator {

    fun validateArguments(schema: ToolArgumentSchema, args: Map<String, Any?>) {
        // 1. Verify all required keys are present and non-null
        for (requiredKey in schema.requiredKeys) {
            val value = args[requiredKey]
            if (value == null || (value is String && value.isBlank())) {
                throw ArishException.SchemaValidationException(
                    requiredKey,
                    "Mandatory argument '$requiredKey' is missing or empty"
                )
            }
        }

        // 2. Type validation and allowed-value checks
        for ((key, value) in args) {
            val property = schema.properties[key] ?: continue // Ignore undeclared extra parameters or reject if strict
            if (value == null) continue

            // Type check
            when (property.type) {
                ArgumentType.STRING -> {
                    if (value !is String) {
                        throw ArishException.SchemaValidationException(key, "Expected String for '$key', but got ${value::class.simpleName}")
                    }
                    if (property.allowedValues != null && value !in property.allowedValues) {
                        throw ArishException.SchemaValidationException(
                            key,
                            "Value '$value' is not in allowed values: ${property.allowedValues}"
                        )
                    }
                }
                ArgumentType.INTEGER -> {
                    if (value !is Int && value !is Long) {
                        throw ArishException.SchemaValidationException(key, "Expected Integer for '$key', but got ${value::class.simpleName}")
                    }
                }
                ArgumentType.NUMBER -> {
                    if (value !is Number) {
                        throw ArishException.SchemaValidationException(key, "Expected Number for '$key', but got ${value::class.simpleName}")
                    }
                }
                ArgumentType.BOOLEAN -> {
                    if (value !is Boolean) {
                        throw ArishException.SchemaValidationException(key, "Expected Boolean for '$key', but got ${value::class.simpleName}")
                    }
                }
                ArgumentType.OBJECT -> {
                    if (value !is Map<*, *>) {
                        throw ArishException.SchemaValidationException(key, "Expected Object/Map for '$key', but got ${value::class.simpleName}")
                    }
                }
                ArgumentType.ARRAY -> {
                    if (value !is List<*>) {
                        throw ArishException.SchemaValidationException(key, "Expected List/Array for '$key', but got ${value::class.simpleName}")
                    }
                }
            }
        }
    }
}
