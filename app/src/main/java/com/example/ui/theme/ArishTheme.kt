package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * A-RISH Semantic Dark Color Scheme
 * Calm Obsidian & Deep Slate canvas with high-contrast readable typography
 * and disciplined Lucid Cyan accents.
 */
val ArishDarkColorScheme = darkColorScheme(
    primary = ArishColors.Primary,
    onPrimary = ArishColors.OnPrimary,
    primaryContainer = ArishColors.PrimaryContainer,
    onPrimaryContainer = ArishColors.OnPrimaryContainer,
    secondary = ArishColors.PrimaryVariant,
    onSecondary = ArishColors.OnPrimary,
    secondaryContainer = ArishColors.DarkSurfaceElevated,
    onSecondaryContainer = ArishColors.Primary,
    tertiary = ArishColors.AccentTeal,
    onTertiary = ArishColors.OnPrimary,
    background = ArishColors.DarkBackground,
    onBackground = ArishColors.TextPrimary,
    surface = ArishColors.DarkSurface,
    onSurface = ArishColors.TextPrimary,
    surfaceVariant = ArishColors.DarkSurfaceElevated,
    onSurfaceVariant = ArishColors.TextSecondary,
    surfaceContainer = ArishColors.DarkSurfaceContainer,
    outline = ArishColors.DarkSurfaceBorder,
    outlineVariant = ArishColors.DarkSurfaceBorderSubtle,
    error = ArishColors.Error,
    onError = ArishColors.OnError
)

/**
 * A-RISH Semantic Light Color Scheme
 * Clean, soft slate canvas with high-contrast text and crisp accents.
 */
val ArishLightColorScheme = lightColorScheme(
    primary = ArishColors.PrimaryVariant,
    onPrimary = Color.White,
    primaryContainer = ArishColors.LightSurfaceElevated,
    onPrimaryContainer = ArishColors.PrimaryVariant,
    secondary = ArishColors.AccentTeal,
    onSecondary = Color.White,
    secondaryContainer = ArishColors.LightSurfaceContainer,
    onSecondaryContainer = ArishColors.LightTextPrimary,
    tertiary = ArishColors.AccentIndigo,
    onTertiary = Color.White,
    background = ArishColors.LightBackground,
    onBackground = ArishColors.LightTextPrimary,
    surface = ArishColors.LightSurface,
    onSurface = ArishColors.LightTextPrimary,
    surfaceVariant = ArishColors.LightSurfaceElevated,
    onSurfaceVariant = ArishColors.LightTextSecondary,
    surfaceContainer = ArishColors.LightSurfaceContainer,
    outline = ArishColors.LightSurfaceBorder,
    outlineVariant = ArishColors.LightSurfaceBorderSubtle,
    error = ArishColors.Error,
    onError = Color.White
)

val ArishMaterialTypography = Typography(
    displayLarge = ArishTypography.DisplayLarge,
    headlineMedium = ArishTypography.HeadlineMedium,
    titleLarge = ArishTypography.TitleLarge,
    titleMedium = ArishTypography.TitleMedium,
    titleSmall = ArishTypography.TitleSmall,
    bodyLarge = ArishTypography.BodyLarge,
    bodyMedium = ArishTypography.BodyMedium,
    bodySmall = ArishTypography.BodySmall,
    labelLarge = ArishTypography.LabelLarge,
    labelMedium = ArishTypography.LabelMedium,
    labelSmall = ArishTypography.LabelSmall
)

@Composable
fun ArishTheme(
    darkTheme: Boolean = true, // Dark-first assistant identity
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) ArishDarkColorScheme else ArishLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ArishMaterialTypography,
        content = content
    )
}
