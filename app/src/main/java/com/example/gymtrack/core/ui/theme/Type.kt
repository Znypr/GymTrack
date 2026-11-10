package com.example.gymtrack.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.gymtrack.R

// Set of Material typography styles to start with (Normal theme; unchanged from v1).
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    )
)

/**
 * Bundled handwriting fonts (OFL 1.1, see assets/licenses/FONTS.md). Compose loads a resource
 * font the first time text uses it and caches it, so the Normal theme never pays for them.
 */
val CaveatFamily = FontFamily(
    Font(R.font.caveat_semibold, FontWeight.SemiBold),
    Font(R.font.caveat_semibold, FontWeight.Bold),
)

val PatrickHandFamily = FontFamily(
    Font(R.font.patrick_hand_regular, FontWeight.Normal),
)

private const val TABULAR_NUMBERS = "tnum"

internal val NormalType = GymTrackType(
    display = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
    title = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    body = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    small = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    label = TextStyle(
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.08.em,
    ),
    setNumber = TextStyle(
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = TABULAR_NUMBERS,
    ),
)

internal val NotebookType = GymTrackType(
    display = TextStyle(fontFamily = CaveatFamily, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 36.sp),
    title = TextStyle(fontFamily = CaveatFamily, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 30.sp),
    body = TextStyle(fontFamily = PatrickHandFamily, fontSize = 17.sp, lineHeight = 22.sp),
    small = TextStyle(fontFamily = PatrickHandFamily, fontSize = 15.sp, lineHeight = 19.sp),
    label = TextStyle(
        fontFamily = PatrickHandFamily,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.06.em,
    ),
    // Readability over style: Patrick Hand's figures stay clear at 18 sp.
    setNumber = TextStyle(
        fontFamily = PatrickHandFamily,
        fontSize = 18.sp,
        lineHeight = 22.sp,
        fontFeatureSettings = TABULAR_NUMBERS,
    ),
)

/** Material styles in Notebook: headings in Caveat, everything else in Patrick Hand. */
internal fun notebookMaterialTypography(): Typography {
    val base = Typography()
    fun TextStyle.hand() = copy(fontFamily = PatrickHandFamily, fontWeight = FontWeight.Normal)
    fun TextStyle.heading() = copy(fontFamily = CaveatFamily, fontWeight = FontWeight.SemiBold)
    return Typography(
        displayLarge = base.displayLarge.heading(),
        displayMedium = base.displayMedium.heading(),
        displaySmall = base.displaySmall.heading(),
        headlineLarge = base.headlineLarge.heading(),
        headlineMedium = base.headlineMedium.heading(),
        headlineSmall = base.headlineSmall.heading(),
        titleLarge = base.titleLarge.heading().copy(fontSize = 26.sp, lineHeight = 30.sp),
        titleMedium = base.titleMedium.hand(),
        titleSmall = base.titleSmall.hand(),
        bodyLarge = base.bodyLarge.hand().copy(fontSize = 17.sp),
        bodyMedium = base.bodyMedium.hand().copy(fontSize = 16.sp),
        bodySmall = base.bodySmall.hand().copy(fontSize = 14.sp),
        labelLarge = base.labelLarge.hand(),
        labelMedium = base.labelMedium.hand(),
        labelSmall = base.labelSmall.hand(),
    )
}
