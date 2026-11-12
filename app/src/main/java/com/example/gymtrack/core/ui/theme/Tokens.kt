package com.example.gymtrack.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Design language v2 (#333, docs/design/DESIGN_LANGUAGE_V2.md): one component set, two skins.
 * Components read these semantic roles instead of raw colours; a theme only swaps tokens.
 */
enum class Skin {
    /** Crisp: OLED dark or light. */
    NORMAL,

    /** Hand-drawn: cream ruled paper, graphite, blue pen, highlighter, red pen. */
    NOTEBOOK,
}

/**
 * Colour roles. Each colour has one meaning (README rule 5) and never carries meaning alone.
 *
 * - [action] is the primary-action colour used as text, icon or outline on [bg];
 *   [actionContainer] + [onAction] are a filled primary button.
 * - [active] marks the current row or selection: an amber outline (Normal) or a highlighter
 *   band (Notebook).
 * - [warn] is a mistake hint, [error] a real error; Notebook uses red pen for both.
 * - [catPush]/[catPull]/[catLegs] are for small marks only, always paired with a shape.
 * - [muscleNone]..[muscleHigh] tint muscle maps; pair them with a fill pattern.
 */
@Immutable
data class GymTrackColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val ink: Color,
    val inkMuted: Color,
    val hairline: Color,
    val action: Color,
    val actionContainer: Color,
    val onAction: Color,
    val active: Color,
    val warn: Color,
    val error: Color,
    val catPush: Color,
    val catPull: Color,
    val catLegs: Color,
    val muscleNone: Color,
    val muscleLow: Color,
    val muscleModerate: Color,
    val muscleHigh: Color,
    /** Bottom navigation background. */
    val navBar: Color,
    /** Notebook ruled lines; transparent in Normal. */
    val paperRule: Color,
    /** Notebook margin line; transparent in Normal. */
    val paperMargin: Color,
    /** Chart lines when several exercises share one chart; always paired with a legend label. */
    val series: List<Color>,
)

/** 4-pt grid. */
@Immutable
data class GymTrackSpacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    /** Minimum touch target (Material/Android accessibility guidance). */
    val touchTarget: Dp = 48.dp,
)

@Immutable
data class GymTrackRadii(
    val small: Dp = 8.dp,
    val card: Dp = 14.dp,
    val sheet: Dp = 24.dp,
)

/**
 * Type scale: display 28, title 22, body 15, small 13, label 11 (uppercase, tracking 0.08em).
 * Notebook uses Caveat (display/title) and Patrick Hand (the rest) at slightly larger sizes,
 * because handwriting fonts read smaller. [setNumber] is for numbers in set rows (≥17 sp).
 * Labels are not uppercased by the style; call `.uppercase()` on the text.
 */
@Immutable
data class GymTrackType(
    val display: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val small: TextStyle,
    val label: TextStyle,
    val setNumber: TextStyle,
)

@Immutable
data class GymTrackTokens(
    val skin: Skin,
    /** Light text on a dark page (status-bar icons, image choices). */
    val isDark: Boolean,
    val colors: GymTrackColors,
    val type: GymTrackType,
    val spacing: GymTrackSpacing = GymTrackSpacing(),
    val radii: GymTrackRadii = GymTrackRadii(),
) {
    val isNotebook: Boolean get() = skin == Skin.NOTEBOOK
}

val LocalGymTrackTokens = staticCompositionLocalOf { NormalDarkTokens }

/** Read tokens in composables: `GymTrackTheme.tokens.colors.action`. */
object GymTrackTheme {
    val tokens: GymTrackTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalGymTrackTokens.current
}
