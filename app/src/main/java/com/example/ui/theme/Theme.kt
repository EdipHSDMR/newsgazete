package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = CizgiColor.Blue, onPrimary = Color.White,
    primaryContainer = CizgiColor.BlueContainer, onPrimaryContainer = CizgiColor.Navy,
    secondary = CizgiColor.Navy, onSecondary = Color.White,
    secondaryContainer = CizgiColor.SurfaceVariant, onSecondaryContainer = CizgiColor.Navy,
    tertiary = CizgiColor.Gold, onTertiary = CizgiColor.Navy,
    tertiaryContainer = CizgiColor.GoldContainer, onTertiaryContainer = CizgiColor.Navy,
    background = CizgiColor.Paper, onBackground = CizgiColor.Navy,
    surface = Color.White, onSurface = CizgiColor.Navy,
    surfaceVariant = CizgiColor.SurfaceVariant, onSurfaceVariant = CizgiColor.TextSecondary,
    surfaceContainer = Color.White, surfaceContainerHigh = CizgiColor.SurfaceVariant,
    outline = CizgiColor.Rule, outlineVariant = CizgiColor.BorderLight,
    error = CizgiColor.Red, onError = Color.White,
    errorContainer = Color(0xFFFFECEE), onErrorContainer = Color(0xFFAE3541),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DAEFF), onPrimary = Color(0xFF172044),
    primaryContainer = Color(0xFF26345B), onPrimaryContainer = Color(0xFFE1E7FF),
    secondary = Color(0xFFE5EAF6), onSecondary = CizgiColor.Navy,
    secondaryContainer = Color(0xFF263247), onSecondaryContainer = Color(0xFFE4E9F5),
    tertiary = CizgiColor.Gold, onTertiary = CizgiColor.Navy,
    tertiaryContainer = Color(0xFF3C3020), onTertiaryContainer = CizgiColor.Gold,
    background = CizgiColor.Ground, onBackground = Color(0xFFEAF0FA),
    surface = CizgiColor.GroundSurface, onSurface = Color(0xFFEAF0FA),
    surfaceVariant = Color(0xFF232E40), onSurfaceVariant = Color(0xFFA4AFC2),
    surfaceContainer = CizgiColor.GroundSurface, surfaceContainerHigh = Color(0xFF232E40),
    outline = Color(0xFF354157), outlineVariant = Color(0xFF273349),
    error = Color(0xFFFF9097), onError = Color(0xFF621C25),
    errorContainer = Color(0xFF3B202B), onErrorContainer = Color(0xFFFFBBC0),
)
@Composable
fun CizgiTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        var context = view.context
        while (context is ContextWrapper && context !is Activity) context = context.baseContext
        (context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = CizgiTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(30.dp),
        ),
        content = content,
    )
}
