package com.focuslock.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object FocusColors {
    val Background = Color(0xFF0B0D10)
    val Surface = Color(0xFF14171C)
    val SurfaceHigh = Color(0xFF1C2027)
    val Outline = Color(0xFF2A3038)
    val Text = Color(0xFFE8ECEF)
    val Muted = Color(0xFF8A939E)
    val Accent = Color(0xFF7CE0B5)
    val Break = Color(0xFFF2C46D)
    val Error = Color(0xFFFF7A7A)
}

private val scheme = darkColorScheme(
    primary = FocusColors.Accent,
    onPrimary = Color(0xFF06140E),
    secondary = FocusColors.Break,
    onSecondary = Color(0xFF1C1405),
    background = FocusColors.Background,
    onBackground = FocusColors.Text,
    surface = FocusColors.Surface,
    onSurface = FocusColors.Text,
    surfaceVariant = FocusColors.SurfaceHigh,
    onSurfaceVariant = FocusColors.Muted,
    surfaceContainer = FocusColors.Surface,
    surfaceContainerHigh = FocusColors.SurfaceHigh,
    surfaceContainerHighest = FocusColors.SurfaceHigh,
    outline = FocusColors.Outline,
    outlineVariant = FocusColors.Outline,
    error = FocusColors.Error,
)

@Composable
fun FocusTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
