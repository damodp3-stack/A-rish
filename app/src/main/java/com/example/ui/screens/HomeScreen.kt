package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.core.model.AiState
import com.example.core.model.AssistantLanguage
import com.example.core.model.TaskStatus
import com.example.ui.components.ArcReactorOrb
import com.example.ui.components.GlowingCard
import com.example.ui.components.StateBadge
import com.example.ui.components.WaveformVisualizer
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
import com.example.ui.theme.JarvisTextCyan
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary
import com.example.ui.viewmodel.JarvisUiState
import com.example.ui.viewmodel.JarvisViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    uiState: JarvisUiState,
    viewModel: JarvisViewModel,
    onNavigateToChat: () -> Unit,
    onNavigateToTasks: () -> Unit,
    onNavigateToResearch: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))

            // Top Status Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "J.A.R.V.I.S. OS",
                        color = JarvisCyan,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "ONEPLUS 15R // PROTOCOL V2.4",
                        color = JarvisTextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = JarvisSurfaceElevated,
                    border = BorderStroke(1.dp, JarvisGlass)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (uiState.isOfflineMode) JarvisAmber else JarvisSuccessGreen)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (uiState.isOfflineMode) "OFFLINE EDGE" else "GEMINI CLOUD",
                            color = JarvisTextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // State Badge
            StateBadge(aiState = uiState.aiState)

            Spacer(modifier = Modifier.height(28.dp))

            // Central Animated Arc Reactor Orb
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                ArcReactorOrb(
                    aiState = uiState.aiState,
                    amplitude = uiState.speechAmplitude,
                    orbSize = 220.dp,
                    onClick = {
                        if (uiState.isListening) {
                            viewModel.stopVoiceListening()
                        } else if (uiState.isSpeaking) {
                            viewModel.stopSpeaking()
                        } else {
                            viewModel.startVoiceListening()
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Waveform & Spoken text subtitle
            if (uiState.isSpeaking || uiState.isListening) {
                WaveformVisualizer(
                    isSpeaking = uiState.isSpeaking || uiState.isListening,
                    amplitude = uiState.speechAmplitude
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            if (uiState.recognizedSpeechText.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = JarvisSurfaceElevated,
                    border = BorderStroke(1.dp, JarvisGlass),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = "\"${uiState.recognizedSpeechText}\"",
                        color = JarvisTextCyan,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Voice Interaction Control Button
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                FloatingActionButton(
                    onClick = {
                        if (uiState.isListening) {
                            viewModel.stopVoiceListening()
                        } else if (uiState.isSpeaking) {
                            viewModel.stopSpeaking()
                        } else {
                            viewModel.startVoiceListening()
                        }
                    },
                    containerColor = if (uiState.isListening) JarvisAlertRed else JarvisCyan,
                    contentColor = Color(0xFF030712),
                    shape = CircleShape,
                    modifier = Modifier
                        .size(64.dp)
                        .testTag("home_voice_ptt_button")
                ) {
                    Icon(
                        imageVector = when {
                            uiState.isListening -> Icons.Default.MicOff
                            uiState.isSpeaking -> Icons.Default.Stop
                            else -> Icons.Default.Mic
                        },
                        contentDescription = "Voice Assistant",
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = when {
                    uiState.isListening -> "Tap to Stop Listening"
                    uiState.isSpeaking -> "Tap to Interrupt Speech"
                    else -> "Tap to Speak (${uiState.language.displayName})"
                },
                color = JarvisTextMuted,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Quick Directives
            Text(
                text = "SYSTEM DIRECTIVES & QUICK PROTOCOLS",
                color = JarvisCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            val quickCommands = listOf(
                "Run hardware & battery telemetry" to "device_diagnostics",
                "Deep research on quantum neural AI" to "research",
                "வணக்கம் JARVIS, வானிலை எப்படி உள்ளது?" to "weather_tamil",
                "Plan today's schedule & reminders" to "schedule",
                "Tanglish briefing on active projects" to "tanglish_briefing"
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickCommands.forEach { (commandText, tag) ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = JarvisSurface,
                        border = BorderStroke(1.dp, JarvisSurfaceBorder),
                        modifier = Modifier
                            .testTag("quick_command_$tag")
                            .clickable {
                                when (tag) {
                                    "research" -> onNavigateToResearch()
                                    else -> {
                                        viewModel.sendMessage(commandText)
                                        onNavigateToChat()
                                    }
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = JarvisCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = commandText,
                                color = JarvisTextPrimary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Telemetry & Active Status Card
            GlowingCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = "Telemetry",
                                tint = JarvisCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "CORE TELEMETRY",
                                color = JarvisTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Text(
                            text = "STATUS: NOMINAL",
                            color = JarvisSuccessGreen,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "• Target Hardware: OnePlus 15R (Snapdragon 8 Gen Elite / 16GB RAM)\n• AI Engine: ${uiState.selectedModel}\n• Voice Locale: ${uiState.language.displayName}\n• Local Security Vault: Active (Keystore TEE)",
                        color = JarvisTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}
