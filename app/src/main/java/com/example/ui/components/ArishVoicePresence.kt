package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.core.model.AiState
import com.example.ui.theme.ArishColors
import com.example.ui.theme.ArishMotion
import kotlin.math.sin

/**
 * Reusable A-RISH Voice Presence Component
 * Communicates assistant audio state without overwhelming the interface.
 * 
 * Supports:
 * - States: Idle, Listening, Thinking, Speaking, Error
 * - Dynamic Audio Amplitude (0f..100f)
 * - Reduced Motion Awareness
 * - Configurable size / touch target
 */
@Composable
fun ArishVoicePresence(
    state: AiState,
    amplitude: Float = 0f,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    onClick: (() -> Unit)? = null,
    testTag: String = "arish_voice_presence"
) {
    val isReducedMotion = ArishMotion.isReducedMotionEnabled()

    val infiniteTransition = rememberInfiniteTransition(label = "voice_presence_anim")
    
    // Idle gentle breathing loop
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing"
    )

    // Thinking rotation angle
    val thinkingAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "thinking_angle"
    )

    val primaryColor = when (state) {
        AiState.IDLE -> ArishColors.Primary
        AiState.LISTENING -> ArishColors.Primary
        AiState.THINKING -> ArishColors.Warning
        AiState.EXECUTING_TOOL -> ArishColors.AccentIndigo
        AiState.SPEAKING -> ArishColors.AccentTeal
        AiState.AWAITING_CONFIRMATION -> ArishColors.Warning
        AiState.ERROR -> ArishColors.Error
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .testTag(testTag)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        when (state) {
            AiState.LISTENING -> {
                // Multi-bar fluid dynamic waveform
                ArishWaveformBars(
                    amplitude = amplitude,
                    barColor = primaryColor,
                    isReducedMotion = isReducedMotion
                )
            }
            AiState.THINKING, AiState.EXECUTING_TOOL -> {
                // Calm orbital ring
                Canvas(modifier = Modifier.size(size * 0.85f)) {
                    val radius = size.toPx() * 0.35f
                    val stroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    
                    // Background track
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.15f),
                        radius = radius,
                        style = stroke
                    )

                    // Rotating indicator arc
                    val startAngle = if (isReducedMotion) 0f else thinkingAngle
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(
                                primaryColor.copy(alpha = 0.1f),
                                primaryColor,
                                primaryColor.copy(alpha = 0.9f)
                            )
                        ),
                        startAngle = startAngle,
                        sweepAngle = 120f,
                        useCenter = false,
                        style = stroke
                    )
                }
            }
            else -> {
                // Calm Breathing Halo (Idle & Speaking)
                val currentScale = if (isReducedMotion) 1.0f else {
                    if (state == AiState.SPEAKING) {
                        1.0f + (amplitude / 100f).coerceIn(0f, 0.25f)
                    } else {
                        breathingScale
                    }
                }

                Canvas(modifier = Modifier.size(size)) {
                    val centerOffset = Offset(size.toPx() / 2f, size.toPx() / 2f)
                    val baseRadius = (size.toPx() / 2f) * 0.65f * currentScale

                    // Outer soft halo
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.12f),
                        radius = baseRadius * 1.35f,
                        center = centerOffset
                    )

                    // Middle ambient ring
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.25f),
                        radius = baseRadius * 1.1f,
                        center = centerOffset
                    )

                    // Core luminous dot
                    drawCircle(
                        color = primaryColor,
                        radius = baseRadius * 0.6f,
                        center = centerOffset
                    )
                }
            }
        }
    }
}

/**
 * 5-bar responsive fluid waveform reacting directly to voice input amplitude
 */
@Composable
fun ArishWaveformBars(
    amplitude: Float,
    barColor: Color,
    isReducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val normalizedAmp = (amplitude / 100f).coerceIn(0f, 1f)
    val barCount = 5

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        for (i in 0 until barCount) {
            val factor = if (i == 2) 1.0f else if (i == 1 || i == 3) 0.75f else 0.5f
            val dynamicHeight = if (isReducedMotion) {
                14.dp
            } else {
                (8.dp + (26.dp * normalizedAmp * factor))
            }

            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .height(dynamicHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )

            if (i < barCount - 1) {
                Spacer(modifier = Modifier.width(3.dp))
            }
        }
    }
}
