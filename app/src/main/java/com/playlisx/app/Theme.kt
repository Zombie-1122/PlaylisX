package com.playlisx.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF000000)
val Gold = Color(0xFFFFD400)
val Panel = Color(0xFF121212)
val Panel2 = Color(0xFF1E1E1E)
val Muted = Color(0xFFB0B0B0)

private val scheme = darkColorScheme(
    primary = Gold,
    onPrimary = Ink,
    secondary = Gold,
    onSecondary = Ink,
    background = Ink,
    onBackground = Color.White,
    surface = Panel,
    onSurface = Color.White,
    surfaceVariant = Panel2,
    onSurfaceVariant = Muted,
    outline = Color(0xFF4A4A4A),
    outlineVariant = Color(0xFF2A2A2A),
    error = Color(0xFFFF5252),
    primaryContainer = Color(0xFF3A3000),
    onPrimaryContainer = Gold,
    secondaryContainer = Color(0xFF3A3000),
    onSecondaryContainer = Gold
)

@Composable
fun PlaylisXTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
