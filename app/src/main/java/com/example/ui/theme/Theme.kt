package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val JarvisColorScheme = darkColorScheme(
  primary = JarvisCyan,
  onPrimary = Color(0xFF030712),
  primaryContainer = JarvisSurfaceElevated,
  onPrimaryContainer = JarvisCyan,
  secondary = JarvisBlue,
  onSecondary = Color(0xFF030712),
  secondaryContainer = JarvisSurface,
  onSecondaryContainer = JarvisTextCyan,
  tertiary = JarvisElectricTeal,
  onTertiary = Color(0xFF030712),
  background = JarvisBackground,
  onBackground = JarvisTextPrimary,
  surface = JarvisSurface,
  onSurface = JarvisTextPrimary,
  surfaceVariant = JarvisSurfaceElevated,
  onSurfaceVariant = JarvisTextSecondary,
  outline = JarvisSurfaceBorder,
  outlineVariant = JarvisGlass,
  error = JarvisAlertRed,
  onError = Color.White
)

@Composable
fun JarvisTheme(
  content: @Composable () -> Unit
) {
  MaterialTheme(
    colorScheme = JarvisColorScheme,
    typography = Typography,
    content = content
  )
}
