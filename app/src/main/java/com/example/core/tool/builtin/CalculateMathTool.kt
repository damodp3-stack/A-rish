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
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt

/**
 * Deterministic mathematical expression evaluator.
 * Evaluates standard arithmetic expressions, functions, and precedence safely.
 * Zero external calls, zero LLM dependency.
 */
class CalculateMathTool(
    override val id: String = "calculate"
) : ToolContract {

    override val name: String = "Mathematical Calculator"
    override val description: String = "Evaluates arithmetic expressions (+, -, *, /, %, ^, sqrt, abs, min, max, round) deterministically."
    override val primaryCapability: CapabilityId = CapabilityId.CALCULATE_MATH
    override val baseRiskLevel: RiskLevel = RiskLevel.LOW
    override val sideEffectSemantics: SideEffectSemantics = SideEffectSemantics.NO_SIDE_EFFECT
    override val deliveryGuarantee: DeliveryGuarantee = DeliveryGuarantee.EXACTLY_ONCE
    override val requiredPermissions: List<PermissionRequirement> = emptyList()

    override val argumentSchema: ToolArgumentSchema = ToolArgumentSchema(
        properties = mapOf(
            "expression" to ArgumentProperty(
                type = ArgumentType.STRING,
                description = "Mathematical expression to evaluate (e.g. '2 + 2', 'sqrt(144) * 5', '100 / 4')",
                isRequired = true
            )
        ),
        requiredKeys = listOf("expression")
    )

    override suspend fun execute(args: Map<String, Any?>): ToolOutcome {
        val startTime = System.currentTimeMillis()
        val expression = args["expression"]?.toString()
            ?: return ToolOutcome.failure(id, "Mandatory argument 'expression' is missing")

        return try {
            val result = MathExpressionParser(expression).parse()
            val duration = System.currentTimeMillis() - startTime

            // Format result (trim trailing .0 if integer)
            val formattedResult = if (result % 1.0 == 0.0 && !result.isInfinite() && !result.isNaN()) {
                result.toLong().toString()
            } else {
                result.toString()
            }

            ToolOutcome.success(
                toolId = id,
                data = mapOf(
                    "expression" to expression,
                    "result" to result,
                    "formattedResult" to formattedResult
                ),
                summary = "$expression = $formattedResult",
                semantics = sideEffectSemantics,
                durationMs = duration
            )
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            ToolOutcome.failure(
                toolId = id,
                errorMessage = "Calculation error: ${e.message}",
                errorDetails = e.stackTraceToString(),
                durationMs = duration
            )
        }
    }

    fun evaluate(expression: String): Double {
        return MathExpressionParser(expression).parse()
    }

    private class MathExpressionParser(rawExpression: String) {
        private val sanitized = rawExpression.replace(" ", "").lowercase()
        private var pos = -1
        private var ch = -1

        private fun nextChar() {
            ch = if (++pos < sanitized.length) sanitized[pos].code else -1
        }

        private fun eat(charToEat: Int): Boolean {
            while (ch == ' '.code) nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parse(): Double {
            if (sanitized.isBlank()) throw IllegalArgumentException("Expression is blank")
            nextChar()
            val result = parseExpression()
            if (pos < sanitized.length) throw IllegalArgumentException("Unexpected trailing character: '${sanitized[pos]}'")
            return result
        }

        private fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                when {
                    eat('+'.code) -> x += parseTerm()
                    eat('-'.code) -> x -= parseTerm()
                    else -> return x
                }
            }
        }

        private fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                when {
                    eat('*'.code) -> x *= parseFactor()
                    eat('/'.code) -> {
                        val divisor = parseFactor()
                        if (divisor == 0.0) throw ArithmeticException("Division by zero")
                        x /= divisor
                    }
                    eat('%'.code) -> {
                        val divisor = parseFactor()
                        if (divisor == 0.0) throw ArithmeticException("Modulo by zero")
                        x %= divisor
                    }
                    else -> return x
                }
            }
        }

        private fun parseFactor(): Double {
            if (eat('+'.code)) return +parseFactor()
            if (eat('-'.code)) return -parseFactor()

            var x: Double
            val startPos = pos

            if (eat('('.code)) {
                x = parseExpression()
                if (!eat(')'.code)) throw IllegalArgumentException("Missing closing parenthesis")
            } else if ((ch in '0'.code..'9'.code) || ch == '.'.code) {
                while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                x = sanitized.substring(startPos, pos).toDouble()
            } else if (ch in 'a'.code..'z'.code) {
                while (ch in 'a'.code..'z'.code) nextChar()
                val func = sanitized.substring(startPos, pos)
                if (!eat('('.code)) throw IllegalArgumentException("Missing parenthesis after function $func")
                val arg = parseExpression()
                if (!eat(')'.code)) throw IllegalArgumentException("Missing closing parenthesis for function $func")
                x = when (func) {
                    "sqrt" -> {
                        if (arg < 0) throw ArithmeticException("Square root of negative number")
                        sqrt(arg)
                    }
                    "abs" -> abs(arg)
                    "floor" -> floor(arg)
                    "ceil" -> ceil(arg)
                    "round" -> round(arg)
                    else -> throw IllegalArgumentException("Unknown math function: $func")
                }
            } else {
                throw IllegalArgumentException("Unexpected character: '${ch.toChar()}'")
            }

            if (eat('^'.code)) x = x.pow(parseFactor())

            return x
        }
    }
}
