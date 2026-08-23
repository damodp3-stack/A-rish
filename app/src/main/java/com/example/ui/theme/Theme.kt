package com.example.ui.theme

import androidx.compose.runtime.Composable

/**
 * Backward compatibility alias forwarding to [ArishTheme]
 */
@Composable
fun JarvisTheme(
    content: @Composable () -> Unit
) {
    ArishTheme(darkTheme = true, content = content)
}
