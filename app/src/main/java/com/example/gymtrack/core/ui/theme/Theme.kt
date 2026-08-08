package com.example.gymtrack.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val DarkColorScheme = darkColorScheme(
    primary = GymAccent,
    onPrimary = GymTextPrimaryLight,
    secondary = GymRecoveryBlue,
    onSecondary = GymTextPrimaryLight,
    tertiary = GymEffortOrange,
    onTertiary = GymTextPrimaryLight,
    background = GymDarkBackground,
    onBackground = GymTextPrimaryDark,
    surface = GymDarkSurface,
    onSurface = GymTextPrimaryDark,
    surfaceVariant = GymDarkSurfaceRaised,
    onSurfaceVariant = GymTextSecondaryDark,
    outline = GymDarkOutline,
    error = GymDanger,
    onError = GymTextPrimaryDark,
)

private val LightColorScheme = lightColorScheme(
    primary = GymAccent,
    onPrimary = GymTextPrimaryLight,
    secondary = GymAccentStrong,
    onSecondary = GymTextPrimaryLight,
    tertiary = GymEffortOrange,
    onTertiary = GymTextPrimaryLight,
    background = GymLightBackground,
    onBackground = GymTextPrimaryLight,
    surface = GymLightSurface,
    onSurface = GymTextPrimaryLight,
    surfaceVariant = GymLightSurfaceRaised,
    onSurfaceVariant = GymTextSecondaryLight,
    outline = GymLightOutline,
    error = GymDanger,
    onError = GymTextPrimaryDark,
)

@Composable
fun GymTrackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val selectionColors = TextSelectionColors(
        handleColor = GymAccent,
        backgroundColor = GymAccent.copy(alpha = 0.35f),
    )

    CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AppShapes,
            content = content,
        )
    }
}
