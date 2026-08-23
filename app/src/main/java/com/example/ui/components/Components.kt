package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.core.model.AiState
import com.example.ui.theme.JarvisAlertRed
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisBlue
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisElectricTeal
import com.example.ui.theme.JarvisGlass
import com.example.ui.theme.JarvisSuccessGreen
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisSurfaceBorder
import com.example.ui.theme.JarvisSurfaceElevated
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary
import com.example.ui.viewmodel.ConfirmationAction

@Composable
fun StateBadge(aiState: AiState, modifier: Modifier = Modifier) {
    val (label, color) = when (aiState) {
        AiState.IDLE -> "SYSTEM IDLE // STANDBY" to JarvisCyan
        AiState.LISTENING -> "VOICE STREAM // LISTENING" to JarvisElectricTeal
        AiState.THINKING -> "NEURAL PROCESSING // REASONING" to JarvisAmber
        AiState.EXECUTING_TOOL -> "TOOL SUBSYSTEM // EXECUTING" to JarvisBlue
        AiState.SPEAKING -> "AUDIO SYNTHESIS // TRANSMITTING" to JarvisCyan
        AiState.AWAITING_CONFIRMATION -> "AUTHORIZATION REQUIRED" to JarvisAmber
        AiState.ERROR -> "SYSTEM NOTICE // RECOVERED" to JarvisAlertRed
    }

    val transition = rememberInfiniteTransition(label = "badge_pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = alpha)),
        modifier = modifier.testTag("state_badge")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                color = color,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun WaveformVisualizer(
    isSpeaking: Boolean,
    amplitude: Float,
    barCount: Int = 18,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(28.dp)
            .testTag("waveform_visualizer")
    ) {
        for (i in 0 until barCount) {
            val factor = if (isSpeaking) {
                ((Math.sin(i * 0.7 + System.currentTimeMillis() * 0.008) + 1.0) * 0.5 * amplitude).coerceIn(0.15, 1.0).toFloat()
            } else 0.15f

            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((28 * factor).dp.coerceAtLeast(4.dp))
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(JarvisCyan, JarvisBlue)
                        )
                    )
            )
        }
    }
}

@Composable
fun GlowingCard(
    modifier: Modifier = Modifier,
    borderColor: Color = JarvisSurfaceBorder,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisSurface),
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
    ) {
        content()
    }
}

@Composable
fun ConfirmationDialog(
    action: ConfirmationAction?,
    onResolve: (Boolean) -> Unit
) {
    if (action == null) return

    Dialog(onDismissRequest = { onResolve(false) }) {
        GlowingCard(
            borderColor = JarvisAmber,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("confirmation_dialog")
        ) {
            Column(
                modifier = Modifier
                    .background(JarvisSurfaceElevated)
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = JarvisAmber,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = action.title,
                        color = JarvisTextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = action.description,
                    color = JarvisTextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = JarvisSurface,
                    border = BorderStroke(1.dp, JarvisGlass),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Target Subsystem: [${action.toolId.uppercase()}]\nParameters: ${action.args}",
                        color = JarvisCyan,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = { onResolve(false) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisTextSecondary),
                        border = BorderStroke(1.dp, JarvisSurfaceBorder),
                        modifier = Modifier.testTag("confirm_deny_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Decline", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Decline")
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = { onResolve(true) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JarvisAmber,
                            contentColor = Color(0xFF030712)
                        ),
                        modifier = Modifier.testTag("confirm_approve_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Approve", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Authorize", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
