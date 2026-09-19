package com.alraisi.aluminum.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AluminumBlue = Color(0xFF1B2A3A)
private val Steel = Color(0xFF90A4AE)
private val Accent = Color(0xFF1565C0)

private val LightColors = lightColorScheme(
    primary = Accent,
    secondary = Steel,
    tertiary = Color(0xFF00897B),
    surface = Color(0xFFF2F4F7),
    surfaceVariant = Color(0xFFE4E9EE),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF001B3F)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9FC2FF),
    secondary = Steel,
    surface = Color(0xFF10161D),
    surfaceVariant = Color(0xFF1B2530)
)

@Composable
fun AlRaisiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
