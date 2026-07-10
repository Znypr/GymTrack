package com.example.gymtrack.core.ui.theme

import androidx.compose.ui.graphics.Color

// GymTrack visual system
val GymDarkBackground = Color(0xFF0B0D0F)
val GymDarkSurface = Color(0xFF15181C)
val GymDarkSurfaceRaised = Color(0xFF20242A)
val GymDarkOutline = Color(0xFF383E46)

val GymLightBackground = Color(0xFFF5F3EE)
val GymLightSurface = Color(0xFFFFFFFF)
val GymLightSurfaceRaised = Color(0xFFE9E2D4)
val GymLightOutline = Color(0xFFD3C7B4)

val GymTextPrimaryDark = Color(0xFFF8F5EC)
val GymTextSecondaryDark = Color(0xFFC7BFAF)
val GymTextPrimaryLight = Color(0xFF171512)
val GymTextSecondaryLight = Color(0xFF5D574E)

// Shared training accents
val GymAccent = Color(0xFFF2B705)
val GymAccentStrong = Color(0xFFD99A00)
val GymDanger = Color(0xFFFF5A4F)
val GymRecoveryBlue = Color(0xFF6BA6FF)
val GymEffortOrange = Color(0xFFE8753A)
val SupersetBlue = GymRecoveryBlue

// Category gradients for workout groups
val PushGradient = listOf(Color(0xFFE4503D), Color(0xFF8E241C))
val PullGradient = listOf(Color(0xFF8D6BFF), Color(0xFF44308F))
val LegsGradient = listOf(Color(0xFF5EA95D), Color(0xFF244B28))
val DefaultGradient = listOf(GymDarkSurfaceRaised, GymDarkBackground)

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
