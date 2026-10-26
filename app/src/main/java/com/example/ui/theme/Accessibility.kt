package com.example.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.animation.*

val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
fun MotionVisibility(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(visible,
        enter = if (LocalReducedMotion.current) EnterTransition.None else fadeIn() + expandVertically(),
        exit = if (LocalReducedMotion.current) ExitTransition.None else fadeOut() + shrinkVertically(),
    ) { content() }
}
