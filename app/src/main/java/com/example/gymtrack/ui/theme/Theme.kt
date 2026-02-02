package com.example.gymtrack.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors

private val DarkColorScheme = darkColorScheme(
    primary = SpotGreen,
    background = SpotBlack,
    surface = SpotDarkGray,
    onPrimary = SpotBlack,
    onSurface = SpotWhite,
    onBackground = SpotWhite
)

private val LightColorScheme = lightColorScheme(
    primary = AppleBlack,
    onPrimary = TextWhite,
    secondary = NeonGreen,
    background = AppleWhite,        // <--- Apple style Light Gray background
    surface = AppleLightCard,       // <--- White cards
    surfaceVariant = AppleWhite,
    onSurface = TextBlack,
    onBackground = TextBlack
)

@Composable
fun GymTrackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val selectionColors = TextSelectionColors(
        handleColor = NeonGreen,
        backgroundColor = NeonGreen.copy(alpha = 0.4f)
    )

    CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AppShapes,
            content = content
        )
    }
}