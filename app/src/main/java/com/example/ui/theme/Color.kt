package com.example.ui.theme

import androidx.compose.ui.graphics.Color

object CizgiColor {
    // Primary Brand Colors (From Çizgi Design Blueprint)
    val Navy = Color(0xFF10243A)        // Primary brand & text
    val NavyDark = Color(0xFF0A1827)    // Deep container navy
    val Ground = Color(0xFF07111C)      // Dark theme background
    val GroundSurface = Color(0xFF0F1E2E) // Dark theme card surface

    // Light Theme Surfaces
    val White = Color(0xFFFFFFFF)
    val Paper = Color(0xFFF7F9FC)       // App background in light mode
    val Surface = Color(0xFFFFFFFF)     // Card background in light mode
    val SurfaceVariant = Color(0xFFEEF1F5) // Secondary surface & chip background
    val SurfaceHover = Color(0xFFE5E9EF) // Slightly deeper surface

    // Accents & Meanings (Restrained, per blueprint)
    val Gold = Color(0xFFF1B548)        // Reserved for CANLI, GOL, SON DAKİKA
    val GoldContainer = Color(0xFFFEF3DC) // Soft gold highlight background
    val Blue = Color(0xFF1769AA)        // Trusted editorial link / button
    val BlueContainer = Color(0xFFE8F1F8) // Subtle blue container
    val Mist = Color(0xFFEAF2F9)        // Soft muted blue-white
    val Green = Color(0xFF16A34A)       // Live match / success state
    val Red = Color(0xFFDC2626)         // Breaking urgent / alert

    // Neutral Borders & Typography
    val TextPrimary = Color(0xFF10243A) // Deep navy body & title text
    val TextSecondary = Color(0xFF475569) // Editorial secondary muted text
    val TextTertiary = Color(0xFF94A3B8) // Time, metadata, subtle hints
    val TextInverse = Color(0xFFF8FAFC) // White text on dark surfaces
    val BorderLight = Color(0xFFE2E8F0) // Subtle card & divider border
    val Rule = Color(0xFFC5CCD6)        // Neutral divider / skeleton rule
    val BorderDark = Color(0xFF1E2E42)  // Dark theme border
}
