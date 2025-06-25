package com.example.gymtrack.util

import androidx.compose.ui.graphics.Color

val presetColors = listOf(
    0xFFE57373,
    0xFF64B5F6,
    0xFF81C784,
    0xFFFFB74D,
    0xFFBA68C8,
    0xFFA1887F,
)

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
