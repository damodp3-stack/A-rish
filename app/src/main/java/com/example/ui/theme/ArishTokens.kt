package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A-RISH Design System Semantic Tokens
 * Designed for "Calm, Intelligent Personal Assistant" experience.
 * High contrast, legible typography, disciplined subtle cyan/slate accents.
 */
object ArishColors {
    // Dark Theme Palette (Primary & Default Experience)
    val DarkBackground = Color(0xFF090D16)          // Pure Obsidian
    val DarkSurface = Color(0xFF101726)             // Deep Slate Surface
    val DarkSurfaceContainer = Color(0xFF162034)    // Surface Container
    val DarkSurfaceElevated = Color(0xFF1E2B44)     // Elevated Floating Card
    val DarkSurfaceBorder = Color(0xFF263756)       // Structural Border
    val DarkSurfaceBorderSubtle = Color(0x33476694) // Subtle Card Divider

    // Light Theme Palette (Calm, High-Contrast Soft Slate)
    val LightBackground = Color(0xFFF8FAFC)         // Slate 50
    val LightSurface = Color(0xFFFFFFFF)            // Pure White Card
    val LightSurfaceContainer = Color(0xFFF1F5F9)   // Slate 100
    val LightSurfaceElevated = Color(0xFFE2E8F0)    // Slate 200
    val LightSurfaceBorder = Color(0xFFCBD5E1)      // Slate 300
    val LightSurfaceBorderSubtle = Color(0x1F64748B)// Subtle Slate Outline

    // Active Default Alias (Dark-First)
    val Background = DarkBackground
    val Surface = DarkSurface
    val SurfaceContainer = DarkSurfaceContainer
    val SurfaceElevated = DarkSurfaceElevated
    val SurfaceBorder = DarkSurfaceBorder
    val SurfaceBorderSubtle = DarkSurfaceBorderSubtle

    // Brand Primary & Lucent Accents (Disciplined Cyan)
    val Primary = Color(0xFF22D3EE)                 // Lucid Cyan
    val PrimaryVariant = Color(0xFF06B6D4)          // Cyan 500
    val PrimaryGlow = Color(0x3322D3EE)
    val OnPrimary = Color(0xFF05131E)               // High Contrast On-Primary
    val PrimaryContainer = Color(0xFF0E3048)
    val OnPrimaryContainer = Color(0xFF67E8F9)

    // Supporting Ambient Accents
    val AccentTeal = Color(0xFF2DD4BF)
    val AccentIndigo = Color(0xFF818CF8)
    val AccentAmber = Color(0xFFFBBF24)
    val AccentRose = Color(0xFFFB7185)
    val AccentEmerald = Color(0xFF34D399)

    // Text & Content Readability (Dark Mode)
    val TextPrimary = Color(0xFFF8FAFC)              // Slate 50 (High contrast)
    val TextSecondary = Color(0xFF94A3B8)            // Slate 400 (Readable metadata)
    val TextMuted = Color(0xFF64748B)                // Slate 500 (Subtle hints)
    val TextHighlight = Color(0xFF38BDF8)            // Sky 400

    // Text (Light Mode)
    val LightTextPrimary = Color(0xFF0F172A)         // Slate 900
    val LightTextSecondary = Color(0xFF475569)       // Slate 600
    val LightTextMuted = Color(0xFF94A3B8)           // Slate 400

    // Semantic Status Tokens
    val Success = Color(0xFF34D399)                  // Emerald
    val OnSuccess = Color(0xFF022C19)
    val Warning = Color(0xFFFBBF24)                  // Amber
    val OnWarning = Color(0xFF2A1B02)
    val Error = Color(0xFFF87171)                    // Rose / Coral
    val OnError = Color(0xFF320707)
    val Info = Color(0xFF38BDF8)                     // Sky
    val Offline = Color(0xFF94A3B8)                  // Slate
}

object ArishSpacing {
    val Space2: Dp = 2.dp
    val Space4: Dp = 4.dp
    val Space6: Dp = 6.dp
    val Space8: Dp = 8.dp
    val Space10: Dp = 10.dp
    val Space12: Dp = 12.dp
    val Space16: Dp = 16.dp
    val Space20: Dp = 20.dp
    val Space24: Dp = 24.dp
    val Space32: Dp = 32.dp
    val Space40: Dp = 40.dp
    val Space48: Dp = 48.dp
    val Space64: Dp = 64.dp
}

object ArishShapes {
    val Small = RoundedCornerShape(8.dp)
    val Medium = RoundedCornerShape(12.dp)
    val Large = RoundedCornerShape(16.dp)
    val ExtraLarge = RoundedCornerShape(24.dp)
    val Full = RoundedCornerShape(50)
}

object ArishElevation {
    val Level0: Dp = 0.dp
    val Level1: Dp = 1.dp
    val Level2: Dp = 3.dp
    val Level3: Dp = 6.dp
    val Level4: Dp = 8.dp
}

object ArishTypography {
    // Primary human-facing typography (Sans-serif)
    val DisplayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        color = ArishColors.TextPrimary
    )

    val HeadlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        color = ArishColors.TextPrimary
    )

    val TitleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        color = ArishColors.TextPrimary
    )

    val TitleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        color = ArishColors.TextPrimary
    )

    val TitleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = ArishColors.TextPrimary
    )

    val BodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.15.sp,
        color = ArishColors.TextPrimary
    )

    val BodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        color = ArishColors.TextSecondary
    )

    val BodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = ArishColors.TextMuted
    )

    val LabelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
        color = ArishColors.TextPrimary
    )

    val LabelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
        color = ArishColors.TextSecondary
    )

    val LabelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.4.sp,
        color = ArishColors.TextMuted
    )

    // Dedicated Monospace for Telemetry, Diagnostics & Code ONLY
    val MonospaceTelemetry = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
        color = ArishColors.TextSecondary
    )

    val MonospaceCode = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        color = ArishColors.Primary
    )
}
