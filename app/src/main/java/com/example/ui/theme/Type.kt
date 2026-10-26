package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun type(size: Int, height: Int, weight: FontWeight = FontWeight.Normal, tracking: Float = 0f) =
    TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = weight, fontSize = size.sp, lineHeight = height.sp, letterSpacing = tracking.sp)

val CizgiTypography = Typography(
    displayLarge = type(32, 38, FontWeight.Bold, -1f),
    displayMedium = type(28, 34, FontWeight.Bold, -0.8f),
    displaySmall = type(26, 32, FontWeight.Bold, -0.6f),
    headlineLarge = type(26, 33, FontWeight.Bold, -0.6f),
    headlineMedium = type(23, 30, FontWeight.Bold, -0.5f),
    headlineSmall = type(21, 28, FontWeight.SemiBold, -0.4f),
    titleLarge = type(19, 25, FontWeight.Bold, -0.3f),
    titleMedium = type(16, 22, FontWeight.SemiBold, -0.2f),
    titleSmall = type(14, 20, FontWeight.SemiBold, -0.1f),
    bodyLarge = type(15, 25),
    bodyMedium = type(14, 22),
    bodySmall = type(12, 18),
    labelLarge = type(13, 18, FontWeight.SemiBold),
    labelMedium = type(11, 16, FontWeight.Medium, 0.1f),
    labelSmall = type(10, 14, FontWeight.Medium, 0.15f),
)
