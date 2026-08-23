package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.core.model.AiState
import com.example.ui.theme.JarvisAlertRed
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisBlue
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisElectricTeal
import com.example.ui.theme.JarvisSuccessGreen
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ArcReactorOrb(
    aiState: AiState,
    amplitude: Float = 0f,
    orbSize: Dp = 200.dp,
    onClick: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ArcReactorRotation")

    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outerRotation"
    )

    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "innerRotation"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val coreColor = when (aiState) {
        AiState.IDLE -> JarvisCyan
        AiState.LISTENING -> JarvisElectricTeal
        AiState.THINKING -> JarvisAmber
        AiState.EXECUTING_TOOL -> JarvisBlue
        AiState.SPEAKING -> JarvisCyan
        AiState.AWAITING_CONFIRMATION -> JarvisAmber
        AiState.ERROR -> JarvisAlertRed
    }

    val secondaryColor = when (aiState) {
        AiState.IDLE -> JarvisBlue
        AiState.LISTENING -> JarvisSuccessGreen
        AiState.THINKING -> JarvisAmber
        AiState.EXECUTING_TOOL -> JarvisElectricTeal
        AiState.SPEAKING -> JarvisElectricTeal
        AiState.AWAITING_CONFIRMATION -> JarvisAlertRed
        AiState.ERROR -> JarvisAmber
    }

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .size(orbSize)
            .testTag("arc_reactor_orb")
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = (size.minDimension / 2f) * 0.85f * pulseScale * (1f + amplitude * 0.25f)

            // 1. Ambient Glow Aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        coreColor.copy(alpha = 0.35f + amplitude * 0.3f),
                        secondaryColor.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 1.35f
                ),
                radius = baseRadius * 1.35f,
                center = center
            )

            // 2. Outer Static Ring
            drawCircle(
                color = secondaryColor.copy(alpha = 0.4f),
                radius = baseRadius,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            // 3. Rotating Segmented Outer Ring
            rotate(outerRotation, pivot = center) {
                val segments = 8
                val sweep = 360f / segments
                for (i in 0 until segments) {
                    val startAngle = i * sweep + 5f
                    val arcSweep = sweep - 12f
                    drawArc(
                        color = coreColor.copy(alpha = 0.85f),
                        startAngle = startAngle,
                        sweepAngle = arcSweep,
                        useCenter = false,
                        topLeft = Offset(center.x - baseRadius * 0.92f, center.y - baseRadius * 0.92f),
                        size = Size(baseRadius * 1.84f, baseRadius * 1.84f),
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            // 4. Counter-Rotating Middle Ring with Nodes
            rotate(innerRotation, pivot = center) {
                val midRadius = baseRadius * 0.65f
                drawCircle(
                    color = coreColor.copy(alpha = 0.5f),
                    radius = midRadius,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // 6 Concentric Reactor Nodes
                for (i in 0 until 6) {
                    val angle = Math.toRadians((i * 60).toDouble())
                    val nodeX = center.x + (midRadius * cos(angle)).toFloat()
                    val nodeY = center.y + (midRadius * sin(angle)).toFloat()
                    drawCircle(
                        color = secondaryColor,
                        radius = 4.dp.toPx() * (1f + amplitude * 0.5f),
                        center = Offset(nodeX, nodeY)
                    )
                    drawLine(
                        color = coreColor.copy(alpha = 0.6f),
                        start = center,
                        end = Offset(nodeX, nodeY),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }

            // 5. Core Holographic Arc-Reactor Center
            val coreRadius = baseRadius * 0.35f * (1f + amplitude * 0.4f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White,
                        coreColor,
                        secondaryColor.copy(alpha = 0.8f)
                    ),
                    center = center,
                    radius = coreRadius
                ),
                radius = coreRadius,
                center = center
            )

            // Dynamic Particle Ring based on voice amplitude
            if (amplitude > 0.05f) {
                val waveRadius = baseRadius * (0.95f + amplitude * 0.35f)
                drawCircle(
                    color = coreColor.copy(alpha = (1f - amplitude * 0.6f).coerceIn(0.1f, 0.9f)),
                    radius = waveRadius,
                    center = center,
                    style = Stroke(width = (2f + amplitude * 4f).dp.toPx())
                )
            }
        }
    }
}
