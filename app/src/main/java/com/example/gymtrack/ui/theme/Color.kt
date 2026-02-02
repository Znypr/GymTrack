package com.example.gymtrack.ui.theme

import androidx.compose.ui.graphics.Color

// Spotify-inspired Dark Theme Palette
val SpotBlack = Color(0xFF121212)       // Main Background
val SpotDarkGray = Color(0xFF212121)    // Card Surface
val SpotLightGray = Color(0xFF535353)   // Secondary Text
val SpotWhite = Color(0xFFFFFFFF)       // Primary Text
val SpotGreen = Color(0xFF1DB954)       // Primary Action (GymTrack Green)

// Category Gradients (For "Album Art" look)
val PushGradient = listOf(Color(0xFFFF3B30), Color(0xFF991812))
val PullGradient = listOf(Color(0xFFAF52DE), Color(0xFF5E187F))
val LegsGradient = listOf(Color(0xFF34C759), Color(0xFF0F5720))
val DefaultGradient = listOf(Color(0xFF535353), Color(0xFF121212))

// --- DARK MODE (Apple "Midnight" Style) ---
val AppleBlack = Color(0xFF000000)       // Background
val AppleDarkGray = Color(0xFF1C1C1E)    // Cards/Surface
val TextWhite = Color(0xFFFFFFFF)
val TextGray = Color(0xFF8E8E93)

// --- LIGHT MODE (Apple "Standard" Style) ---
val AppleWhite = Color(0xFFF2F2F7)       // Background (Subtle off-white)
val AppleLightCard = Color(0xFFFFFFFF)   // Cards (Pure white)
val TextBlack = Color(0xFF000000)

// --- ACCENTS (Shared) ---
val NeonGreen = Color(0xFF34C759)
val NeonRed = Color(0xFFFF3B30)
val NeonPurple = Color(0xFFAF52DE)
val SupersetBlue = Color(0xFF64B5F6)
// Helpers
fun Color.darken(factor: Float): Color {
    return Color(
        red = (red * (1 - factor)).coerceIn(0f, 1f),
        green = (green * (1 - factor)).coerceIn(0f, 1f),
        blue = (blue * (1 - factor)).coerceIn(0f, 1f),
        alpha = alpha,
    )
}

fun Color.lighten(factor: Float): Color {
    return Color(
        red = (red + (1 - red) * factor).coerceIn(0f, 1f),
        green = (green + (1 - green) * factor).coerceIn(0f, 1f),
        blue = (blue + (1 - blue) * factor).coerceIn(0f, 1f),
        alpha = alpha,
    )
}