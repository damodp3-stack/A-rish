package com.example.ui.theme

import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView

/**
 * A-RISH Haptic Feedback Abstraction
 * Provides consistent, subtle haptic pulses for key micro-interactions:
 * Light, Medium, Heavy, Success, Warning, Error.
 */
enum class ArishHapticFeedbackType {
    LIGHT,
    MEDIUM,
    HEAVY,
    SUCCESS,
    WARNING,
    ERROR
}

class ArishHapticController(
    private val hapticFeedback: HapticFeedback,
    private val view: android.view.View?
) {
    fun perform(type: ArishHapticFeedbackType) {
        try {
            when (type) {
                ArishHapticFeedbackType.LIGHT -> {
                    view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        ?: hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                ArishHapticFeedbackType.MEDIUM -> {
                    view?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        ?: hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                ArishHapticFeedbackType.HEAVY -> {
                    view?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        ?: hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                ArishHapticFeedbackType.SUCCESS -> {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        view?.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    } else {
                        view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                }
                ArishHapticFeedbackType.WARNING -> {
                    view?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
                ArishHapticFeedbackType.ERROR -> {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        view?.performHapticFeedback(HapticFeedbackConstants.REJECT)
                    } else {
                        view?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    }
                }
            }
        } catch (_: Exception) {
            // Graceful fallback on devices without haptic engines
        }
    }
}

@Composable
fun rememberArishHaptics(): ArishHapticController {
    val hapticFeedback = LocalHapticFeedback.current
    val view = LocalView.current
    return remember(hapticFeedback, view) {
        ArishHapticController(hapticFeedback, view)
    }
}
