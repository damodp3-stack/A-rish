package com.example.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * A-RISH Motion & Animation Tokens
 * Follows Material 3 motion tokens: fast, standard, emphasis, and reduced motion awareness.
 */
object ArishMotion {
    // Duration Tokens (milliseconds)
    const val DurationFastShort = 100
    const val DurationFast = 150
    const val DurationStandard = 250
    const val DurationEmphasis = 350
    const val DurationLong = 500

    // Easing Tokens
    val EasingStandard: Easing = FastOutSlowInEasing
    val EasingDecelerate: Easing = LinearOutSlowInEasing
    val EasingLinear: Easing = LinearEasing
    val EasingEmphasized: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    // Pre-built Tweens
    fun <T> fastTween() = tween<T>(durationMillis = DurationFast, easing = EasingStandard)
    fun <T> standardTween() = tween<T>(durationMillis = DurationStandard, easing = EasingStandard)
    fun <T> emphasisTween() = tween<T>(durationMillis = DurationEmphasis, easing = EasingEmphasized)

    /**
     * Checks if the device has requested reduced animations / animator duration scale = 0
     */
    @Composable
    fun isReducedMotionEnabled(): Boolean {
        val context = LocalContext.current
        return try {
            val scale = android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            )
            scale == 0f
        } catch (_: Exception) {
            false
        }
    }
}
