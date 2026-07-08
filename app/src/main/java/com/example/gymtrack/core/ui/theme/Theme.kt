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
    background = SpotBlack,
    surface = SpotDarkGray,
    onPrimary = AppleBlack,
    onSurface = SpotWhite,
    onBackground = SpotWhite,
)

private val LightColorScheme = lightColorScheme(
    primary = GymAccent,
    onPrimary = TextBlack,
    secondary = GymAccent,
    background = AppleWhite,
    surface = AppleLightCard,
    surfaceVariant = AppleWhite,
    onSurface = TextBlack,
    onBackground = TextBlack,
)

@Composable
fun GymTrackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val selectionColors = TextSelectionColors(
        handleColor = GymAccent,
        backgroundColor = GymAccent.copy(alpha = 0.4f),
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
