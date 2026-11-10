package com.example.gymtrack.core.ui.theme

import androidx.compose.ui.graphics.Color
import com.example.gymtrack.core.data.ThemeChoice

/** Normal · dark OLED (DESIGN_LANGUAGE_V2 "Theme 1"). */
val NormalDarkColors = GymTrackColors(
    bg = Color(0xFF000000),
    surface = Color(0xFF1C1C1E),
    surface2 = Color(0xFF111113),
    ink = Color(0xFFFFFFFF),
    inkMuted = Color(0xFFA1A1A6),
    hairline = Color(0xFF2C2C2E),
    action = Color(0xFFF2B705),
    actionContainer = Color(0xFFF2B705),
    onAction = Color(0xFF000000),
    active = Color(0xFFF2B705),
    warn = Color(0xFFF2B705),
    error = Color(0xFFFF453A),
    catPush = Color(0xFFFF3B30),
    catPull = Color(0xFFAF52DE),
    catLegs = Color(0xFF34C759),
    muscleNone = Color(0xFF3A3A3C),
    muscleLow = Color(0xFF1A8A7C),
    muscleModerate = Color(0xFF22B09F),
    muscleHigh = Color(0xFF2DD4BF),
    navBar = Color(0xFF0B0B0C),
    paperRule = Color.Transparent,
    paperMargin = Color.Transparent,
)

/**
 * Normal · light. Same roles; foreground colours are darkened so text reaches 4.5:1 and
 * marks 3:1 on the light page. The filled action button keeps the amber.
 */
val NormalLightColors = GymTrackColors(
    bg = Color(0xFFF2F2F7),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFE9E9EE),
    ink = Color(0xFF1C1C1E),
    inkMuted = Color(0xFF5E5E63),
    hairline = Color(0xFFD1D1D6),
    action = Color(0xFF7F5C00),
    actionContainer = Color(0xFFF2B705),
    onAction = Color(0xFF000000),
    active = Color(0xFF7F5C00),
    warn = Color(0xFF7F5C00),
    error = Color(0xFFC4001A),
    catPush = Color(0xFFD12A22),
    catPull = Color(0xFF8944AB),
    catLegs = Color(0xFF248A3D),
    muscleNone = Color(0xFFD1D1D6),
    muscleLow = Color(0xFF7FD8CC),
    muscleModerate = Color(0xFF2BB3A3),
    muscleHigh = Color(0xFF0F8F80),
    navBar = Color(0xFFFFFFFF),
    paperRule = Color.Transparent,
    paperMargin = Color.Transparent,
)

/** Notebook: cream paper, graphite, blue fountain pen, highlighter, red pen. */
val NotebookColors = GymTrackColors(
    bg = Color(0xFFF6F1E4),
    surface = Color(0xFFFFFDF6),
    surface2 = Color(0xFFEFE8D6),
    ink = Color(0xFF2B2A28),
    inkMuted = Color(0xFF6E6A62),
    hairline = Color(0xFFC9C2B0),
    action = Color(0xFF1F4FB5),
    actionContainer = Color(0xFFDCE6F7),
    onAction = Color(0xFF1F4FB5),
    active = Color(0xA6FFEC5C),
    warn = Color(0xFFC62828),
    error = Color(0xFFC62828),
    catPush = Color(0xFFC0504D),
    catPull = Color(0xFF7E57C2),
    catLegs = Color(0xFF2E7D32),
    // Pencil: graphite at rising pressure. Pair with hatch density (none = outline only).
    muscleNone = Color(0x592B2A28),
    muscleLow = Color(0x4D2B2A28),
    muscleModerate = Color(0x8C2B2A28),
    muscleHigh = Color(0xCC2B2A28),
    navBar = Color(0xFFF6F1E4),
    paperRule = Color(0xFFC9DAEA),
    paperMargin = Color(0xFFE3A6A6),
)

val NormalDarkTokens = GymTrackTokens(Skin.NORMAL, isDark = true, colors = NormalDarkColors, type = NormalType)
val NormalLightTokens = GymTrackTokens(Skin.NORMAL, isDark = false, colors = NormalLightColors, type = NormalType)
val NotebookTokens = GymTrackTokens(Skin.NOTEBOOK, isDark = false, colors = NotebookColors, type = NotebookType)

/** Resolves a stored choice; SYSTEM = Normal following the system light/dark setting. */
fun tokensFor(choice: ThemeChoice, systemDark: Boolean): GymTrackTokens = when (choice) {
    ThemeChoice.NORMAL_DARK -> NormalDarkTokens
    ThemeChoice.NORMAL_LIGHT -> NormalLightTokens
    ThemeChoice.NOTEBOOK -> NotebookTokens
    ThemeChoice.SYSTEM -> if (systemDark) NormalDarkTokens else NormalLightTokens
}
