package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = CizgiColor.Navy,
    onPrimary = CizgiColor.White,
    primaryContainer = CizgiColor.SurfaceVariant,
    onPrimaryContainer = CizgiColor.Navy,
    secondary = CizgiColor.Blue,
    onSecondary = CizgiColor.White,
    secondaryContainer = CizgiColor.BlueContainer,
    onSecondaryContainer = CizgiColor.Navy,
    tertiary = CizgiColor.Gold,
    onTertiary = CizgiColor.Navy,
    tertiaryContainer = CizgiColor.GoldContainer,
    onTertiaryContainer = CizgiColor.Navy,
    background = CizgiColor.Paper,
    onBackground = CizgiColor.TextPrimary,
    surface = CizgiColor.Surface,
    onSurface = CizgiColor.TextPrimary,
    surfaceVariant = CizgiColor.SurfaceVariant,
    onSurfaceVariant = CizgiColor.TextSecondary,
    outline = CizgiColor.BorderLight,
    outlineVariant = CizgiColor.BorderLight.copy(alpha = 0.5f),
    error = CizgiColor.Red,
    onError = CizgiColor.White,
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFF0B1726),
    primaryContainer = Color(0xFF1B2C42),
    onPrimaryContainer = Color(0xFFBFDBFE),
    secondary = Color(0xFF93C5FD),
    onSecondary = Color(0xFF0B1726),
    secondaryContainer = Color(0xFF1E3A5F),
    onSecondaryContainer = Color(0xFFDBEAFE),
    tertiary = CizgiColor.Gold,
    onTertiary = Color(0xFF0B1726),
    tertiaryContainer = Color(0xFF3B2E15),
    onTertiaryContainer = CizgiColor.Gold,
    background = Color(0xFF08101A),
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF101C2B),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF192A3E),
    onSurfaceVariant = Color(0xFFA3B3C7),
    outline = Color(0xFF2A3D54),
    outlineVariant = Color(0xFF1E2F44),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
)

@Composable
fun CizgiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CizgiTypography,
        content = content,
    )
}
