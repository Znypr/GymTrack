package com.example.gymtrack.core.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import com.example.gymtrack.core.data.ThemeChoice

/**
 * Root theme. Provides [GymTrackTokens] (read with `GymTrackTheme.tokens`) and maps them onto
 * MaterialTheme, so screens that still use Material colours and typography follow the choice.
 */
@Composable
fun GymTrackTheme(
    themeChoice: ThemeChoice = ThemeChoice.SYSTEM,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val tokens = tokensFor(themeChoice, systemDark)
    GymTrackTheme(tokens = tokens, content = content)
}

/** Same as above for a resolved token set (previews, tests). */
@Composable
fun GymTrackTheme(
    tokens: GymTrackTokens,
    content: @Composable () -> Unit,
) {
    val colorScheme = remember(tokens) { tokens.materialColorScheme() }
    val typography = remember(tokens.skin) {
        if (tokens.isNotebook) notebookMaterialTypography() else Typography
    }
    val selectionColors = remember(tokens) {
        TextSelectionColors(
            handleColor = tokens.colors.action,
            backgroundColor = tokens.colors.action.copy(alpha = 0.3f),
        )
    }

    val view = LocalView.current
    if (!LocalInspectionMode.current) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowInsetsControllerCompat(window, view).apply {
                isAppearanceLightStatusBars = !tokens.isDark
                isAppearanceLightNavigationBars = !tokens.isDark
            }
        }
    }

    CompositionLocalProvider(
        LocalGymTrackTokens provides tokens,
        LocalTextSelectionColors provides selectionColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = AppShapes,
            content = content,
        )
    }
}

/**
 * Material mapping. Dark keeps the v1 OLED look (every container black) so unported screens
 * do not change; light and Notebook use the page colour for containers.
 */
internal fun GymTrackTokens.materialColorScheme(): ColorScheme {
    val c = colors
    return if (isDark) {
        darkColorScheme(
            primary = c.actionContainer,
            onPrimary = c.onAction,
            primaryContainer = c.actionContainer,
            onPrimaryContainer = c.onAction,
            secondary = c.action,
            onSecondary = c.onAction,
            background = c.bg,
            onBackground = c.ink,
            surface = c.bg,
            onSurface = c.ink,
            surfaceVariant = c.bg,
            onSurfaceVariant = c.inkMuted,
            surfaceTint = c.bg,
            surfaceDim = c.bg,
            surfaceBright = c.bg,
            surfaceContainerLowest = c.bg,
            surfaceContainerLow = c.bg,
            surfaceContainer = c.bg,
            surfaceContainerHigh = c.bg,
            surfaceContainerHighest = c.bg,
            outline = c.inkMuted,
            outlineVariant = c.hairline,
            error = c.error,
            onError = Color.Black,
        )
    } else {
        val notebook = isNotebook
        lightColorScheme(
            // Notebook: blue-pen buttons. Normal light: the amber fill, as in v1.
            primary = if (notebook) c.action else c.actionContainer,
            onPrimary = if (notebook) c.surface else c.onAction,
            primaryContainer = c.actionContainer,
            onPrimaryContainer = c.onAction,
            secondary = c.action,
            onSecondary = c.surface,
            background = c.bg,
            onBackground = c.ink,
            surface = if (notebook) c.bg else c.surface,
            onSurface = c.ink,
            surfaceVariant = c.surface2,
            onSurfaceVariant = c.inkMuted,
            surfaceTint = Color.Transparent,
            surfaceContainerLowest = if (notebook) c.bg else c.surface,
            surfaceContainerLow = if (notebook) c.bg else c.surface,
            surfaceContainer = if (notebook) c.bg else c.surface,
            surfaceContainerHigh = if (notebook) c.surface else c.surface,
            surfaceContainerHighest = if (notebook) c.surface else c.surface,
            outline = c.inkMuted,
            outlineVariant = c.hairline,
            error = c.error,
            onError = Color.White,
        )
    }
}
