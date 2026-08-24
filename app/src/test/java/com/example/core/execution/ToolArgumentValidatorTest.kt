package com.example.core.execution

import com.example.core.domain.error.ArishException
import com.example.core.domain.tool.ArgumentProperty
import com.example.core.domain.tool.ArgumentType
import com.example.core.domain.tool.ToolArgumentSchema
import com.example.core.domain.validation.ToolArgumentValidator
import org.junit.Assert.assertThrows
import org.junit.Test

class ToolArgumentValidatorTest {

    private val schema = ToolArgumentSchema(
        properties = mapOf(
            "query" to ArgumentProperty(
                type = ArgumentType.STRING,
                description = "Search query",
                isRequired = true
            ),
            "limit" to ArgumentProperty(
                type = ArgumentType.INTEGER,
                description = "Max items",
                isRequired = false
            ),
            "mode" to ArgumentProperty(
                type = ArgumentType.STRING,
                description = "Mode of operation",
                isRequired = false,
                allowedValues = listOf("FAST", "DEEP")
            )
        ),
        requiredKeys = listOf("query")
    )

    @Test
    fun `validateArguments succeeds on valid arguments`() {
        val args = mapOf<String, Any?>(
            "query" to "Android Architecture",
            "limit" to 10,
            "mode" to "FAST"
        )
        ToolArgumentValidator.validateArguments(schema, args)
    }

    @Test
    fun `validateArguments throws SchemaValidationException when required key is missing`() {
        val args = mapOf<String, Any?>(
            "limit" to 5
        )
        assertThrows(ArishException.SchemaValidationException::class.java) {
            ToolArgumentValidator.validateArguments(schema, args)
        }
    }

    @Test
    fun `validateArguments throws SchemaValidationException when required key is blank`() {
        val args = mapOf<String, Any?>(
            "query" to "   "
        )
        assertThrows(ArishException.SchemaValidationException::class.java) {
            ToolArgumentValidator.validateArguments(schema, args)
        }
    }

    @Test
    fun `validateArguments throws SchemaValidationException on type mismatch`() {
        val args = mapOf<String, Any?>(
            "query" to 12345 // expected String
        )
        assertThrows(ArishException.SchemaValidationException::class.java) {
            ToolArgumentValidator.validateArguments(schema, args)
        }
    }

    @Test
    fun `validateArguments throws SchemaValidationException on unallowed value`() {
        val args = mapOf<String, Any?>(
            "query" to "Kotlin",
            "mode" to "INVALID_MODE"
        )
        assertThrows(ArishException.SchemaValidationException::class.java) {
            ToolArgumentValidator.validateArguments(schema, args)
        }
    }
}
