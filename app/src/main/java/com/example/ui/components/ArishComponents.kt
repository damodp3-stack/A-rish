package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.AiState
import com.example.ui.theme.ArishColors
import com.example.ui.theme.ArishShapes
import com.example.ui.theme.ArishSpacing
import com.example.ui.theme.ArishTypography

enum class ArishButtonStyle {
    PRIMARY,
    SECONDARY,
    OUTLINE,
    TEXT,
    DESTRUCTIVE
}

enum class ArishCardVariant {
    STANDARD,
    ELEVATED,
    INTERACTIVE,
    SUCCESS,
    WARNING,
    ERROR
}

enum class ToolExecutionStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * Universal Button meeting 48dp touch target and semantic hierarchy
 */
@Composable
fun ArishButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ArishButtonStyle = ArishButtonStyle.PRIMARY,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    testTag: String = "arish_button"
) {
    val containerColor = when (style) {
        ArishButtonStyle.PRIMARY -> ArishColors.Primary
        ArishButtonStyle.SECONDARY -> ArishColors.SurfaceElevated
        ArishButtonStyle.OUTLINE -> Color.Transparent
        ArishButtonStyle.TEXT -> Color.Transparent
        ArishButtonStyle.DESTRUCTIVE -> ArishColors.Error
    }

    val contentColor = when (style) {
        ArishButtonStyle.PRIMARY -> ArishColors.OnPrimary
        ArishButtonStyle.SECONDARY -> ArishColors.TextPrimary
        ArishButtonStyle.OUTLINE -> ArishColors.Primary
        ArishButtonStyle.TEXT -> ArishColors.Primary
        ArishButtonStyle.DESTRUCTIVE -> ArishColors.OnError
    }

    val border = when (style) {
        ArishButtonStyle.OUTLINE -> BorderStroke(1.dp, ArishColors.Primary)
        ArishButtonStyle.SECONDARY -> BorderStroke(1.dp, ArishColors.SurfaceBorderSubtle)
        else -> null
    }

    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        shape = ArishShapes.Medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = ArishColors.SurfaceElevated.copy(alpha = 0.5f),
            disabledContentColor = ArishColors.TextMuted
        ),
        border = border,
        modifier = modifier
            .height(48.dp)
            .testTag(testTag)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = contentColor,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(ArishSpacing.Space8))
                }
                Text(
                    text = text,
                    style = ArishTypography.LabelLarge.copy(
                        color = if (enabled) contentColor else ArishColors.TextMuted
                    )
                )
            }
        }
    }
}

/**
 * Accessible Icon Button with strict 48dp touch target
 */
@Composable
fun ArishIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = ArishColors.TextPrimary,
    backgroundColor: Color = Color.Transparent,
    enabled: Boolean = true,
    testTag: String = "arish_icon_button"
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = backgroundColor,
            contentColor = tint,
            disabledContentColor = ArishColors.TextMuted
        ),
        modifier = modifier
            .size(48.dp)
            .semantics { role = Role.Button }
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else ArishColors.TextMuted,
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * Standard A-RISH Semantic Card
 */
@Composable
fun ArishCard(
    modifier: Modifier = Modifier,
    variant: ArishCardVariant = ArishCardVariant.STANDARD,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val backgroundColor = when (variant) {
        ArishCardVariant.STANDARD -> ArishColors.Surface
        ArishCardVariant.ELEVATED -> ArishColors.SurfaceElevated
        ArishCardVariant.INTERACTIVE -> ArishColors.SurfaceContainer
        ArishCardVariant.SUCCESS -> ArishColors.Success.copy(alpha = 0.08f)
        ArishCardVariant.WARNING -> ArishColors.Warning.copy(alpha = 0.08f)
        ArishCardVariant.ERROR -> ArishColors.Error.copy(alpha = 0.08f)
    }

    val borderColor = when (variant) {
        ArishCardVariant.STANDARD -> ArishColors.SurfaceBorderSubtle
        ArishCardVariant.ELEVATED -> ArishColors.SurfaceBorder
        ArishCardVariant.INTERACTIVE -> ArishColors.Primary.copy(alpha = 0.35f)
        ArishCardVariant.SUCCESS -> ArishColors.Success.copy(alpha = 0.35f)
        ArishCardVariant.WARNING -> ArishColors.Warning.copy(alpha = 0.35f)
        ArishCardVariant.ERROR -> ArishColors.Error.copy(alpha = 0.35f)
    }

    Card(
        shape = ArishShapes.Large,
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable { onClick() } else Modifier
        )
    ) {
        content()
    }
}

/**
 * Clean, Human-Centered Message Bubble
 */
@Composable
fun ArishMessage(
    content: String,
    isUser: Boolean,
    timestamp: String? = null,
    onCopy: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val background = if (isUser) ArishColors.SurfaceElevated else ArishColors.SurfaceContainer
    val borderColor = if (isUser) ArishColors.Primary.copy(alpha = 0.25f) else ArishColors.SurfaceBorderSubtle
    val alignment = if (isUser) Alignment.End else Alignment.Start

    Column(
        horizontalAlignment = alignment,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = ArishSpacing.Space4)
    ) {
        Surface(
            shape = ArishShapes.Large,
            color = background,
            border = BorderStroke(1.dp, borderColor),
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Column(modifier = Modifier.padding(ArishSpacing.Space12)) {
                Text(
                    text = content,
                    style = ArishTypography.BodyLarge,
                    color = ArishColors.TextPrimary
                )

                if (timestamp != null || onCopy != null) {
                    Spacer(modifier = Modifier.height(ArishSpacing.Space6))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = timestamp ?: "",
                            style = ArishTypography.LabelSmall,
                            color = ArishColors.TextMuted
                        )
                        if (onCopy != null) {
                            IconButton(
                                onClick = onCopy,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy message",
                                    tint = ArishColors.TextMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Compact, Non-Intrusive Tool Activity Card
 */
@Composable
fun ArishToolActivityCard(
    toolName: String,
    statusText: String,
    status: ToolExecutionStatus = ToolExecutionStatus.COMPLETED,
    details: String? = null,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape = ArishShapes.Medium,
        color = ArishColors.SurfaceContainer,
        border = BorderStroke(1.dp, ArishColors.SurfaceBorderSubtle),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(ArishSpacing.Space12)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    when (status) {
                        ToolExecutionStatus.RUNNING, ToolExecutionStatus.PENDING -> {
                            CircularProgressIndicator(
                                color = ArishColors.Primary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        ToolExecutionStatus.COMPLETED -> {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Completed",
                                tint = ArishColors.Success,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        ToolExecutionStatus.FAILED, ToolExecutionStatus.CANCELLED -> {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "Failed",
                                tint = ArishColors.Error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(ArishSpacing.Space10))

                    Column {
                        Text(
                            text = statusText,
                            style = ArishTypography.BodyLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                            color = ArishColors.TextPrimary
                        )
                        Text(
                            text = "Tool: $toolName",
                            style = ArishTypography.LabelSmall,
                            color = ArishColors.TextMuted
                        )
                    }
                }

                if (details != null) {
                    IconButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                            contentDescription = "Toggle Details",
                            tint = ArishColors.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(visible = expanded && details != null) {
                Column(modifier = Modifier.padding(top = ArishSpacing.Space8)) {
                    Surface(
                        shape = ArishShapes.Small,
                        color = ArishColors.SurfaceElevated,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = details ?: "",
                            style = ArishTypography.MonospaceCode.copy(fontSize = 11.sp),
                            modifier = Modifier.padding(ArishSpacing.Space8)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Human-Friendly Assistant State Badge
 */
@Composable
fun ArishStateIndicator(
    aiState: AiState,
    modifier: Modifier = Modifier
) {
    val (label, tint) = when (aiState) {
        AiState.IDLE -> "Ready" to ArishColors.AccentEmerald
        AiState.LISTENING -> "Listening…" to ArishColors.Primary
        AiState.THINKING -> "Thinking…" to ArishColors.Warning
        AiState.EXECUTING_TOOL -> "Working…" to ArishColors.AccentIndigo
        AiState.SPEAKING -> "Speaking" to ArishColors.Primary
        AiState.AWAITING_CONFIRMATION -> "Approval Required" to ArishColors.Warning
        AiState.ERROR -> "Notice" to ArishColors.Error
    }

    Surface(
        shape = ArishShapes.Full,
        color = tint.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.25f)),
        modifier = modifier.testTag("arish_state_indicator")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = ArishSpacing.Space12, vertical = ArishSpacing.Space4)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(tint)
            )
            Spacer(modifier = Modifier.width(ArishSpacing.Space8))
            Text(
                text = label,
                color = tint,
                style = ArishTypography.LabelSmall.copy(
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}

/**
 * Human-Centered Empty State with Actionable Suggestion Chips
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ArishEmptyState(
    title: String,
    description: String,
    icon: ImageVector = Icons.Default.Info,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    suggestionChips: List<String> = emptyList(),
    onSuggestionClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(ArishSpacing.Space24),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = ArishColors.SurfaceElevated,
            border = BorderStroke(1.dp, ArishColors.SurfaceBorderSubtle),
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ArishColors.Primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(ArishSpacing.Space16))

        Text(
            text = title,
            style = ArishTypography.TitleMedium,
            color = ArishColors.TextPrimary
        )

        Spacer(modifier = Modifier.height(ArishSpacing.Space6))

        Text(
            text = description,
            style = ArishTypography.BodyMedium,
            color = ArishColors.TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = ArishSpacing.Space16)
        )

        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(ArishSpacing.Space16))
            ArishButton(
                text = actionLabel,
                onClick = onAction,
                icon = Icons.Default.AutoAwesome
            )
        }

        if (suggestionChips.isNotEmpty() && onSuggestionClick != null) {
            Spacer(modifier = Modifier.height(ArishSpacing.Space20))
            FlowRow(
                horizontalArrangement = Arrangement.Center,
                verticalArrangement = Arrangement.spacedBy(ArishSpacing.Space8),
                modifier = Modifier.fillMaxWidth()
            ) {
                suggestionChips.forEach { chipText ->
                    Surface(
                        shape = ArishShapes.Full,
                        color = ArishColors.SurfaceElevated,
                        border = BorderStroke(1.dp, ArishColors.SurfaceBorderSubtle),
                        modifier = Modifier
                            .clickable { onSuggestionClick(chipText) }
                            .padding(horizontal = ArishSpacing.Space4)
                    ) {
                        Text(
                            text = chipText,
                            style = ArishTypography.LabelMedium.copy(color = ArishColors.TextPrimary),
                            modifier = Modifier.padding(horizontal = ArishSpacing.Space12, vertical = ArishSpacing.Space6)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Actionable Error Notice Banner
 */
@Composable
fun ArishErrorBanner(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = ArishShapes.Medium,
        color = ArishColors.Error.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, ArishColors.Error.copy(alpha = 0.3f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = ArishSpacing.Space8)
    ) {
        Row(
            modifier = Modifier.padding(ArishSpacing.Space12),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = ArishColors.Error,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(ArishSpacing.Space10))
                Text(
                    text = message,
                    style = ArishTypography.BodyMedium.copy(color = ArishColors.TextPrimary)
                )
            }

            if (onRetry != null) {
                OutlinedButton(
                    onClick = onRetry,
                    shape = ArishShapes.Small,
                    border = BorderStroke(1.dp, ArishColors.Error),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ArishColors.Error),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("Retry", style = ArishTypography.LabelSmall.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}
