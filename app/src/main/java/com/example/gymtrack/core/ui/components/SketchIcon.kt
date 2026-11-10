package com.example.gymtrack.core.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.gymtrack.core.ui.theme.GymTrackTheme
import com.example.gymtrack.core.ui.theme.PencilRun
import com.example.gymtrack.core.ui.theme.SketchGeometry
import com.example.gymtrack.core.ui.theme.drawPencilRuns
import com.example.gymtrack.core.ui.theme.toPencilRuns

/**
 * Icons that exist in both skins: a crisp Material icon in Normal and a hand-drawn pencil
 * glyph in Notebook (README rule 4: no crisp vector stickers on paper).
 */
enum class AppIcon(val normal: ImageVector) {
    TODAY(Icons.Filled.Today),
    HISTORY(Icons.Filled.History),
    PROGRESS(Icons.AutoMirrored.Filled.ShowChart),
    RESUME(Icons.Filled.PlayArrow),
    ADD(Icons.Filled.Add),
    CHECK(Icons.Filled.Check),
    ALERT(Icons.Filled.PriorityHigh),
    SETTINGS(Icons.Filled.Settings),
    CHEVRON(Icons.AutoMirrored.Filled.KeyboardArrowRight),
}

/**
 * Draws [icon] in the current skin. Pass a [contentDescription] unless a text label next to it
 * already says the same thing (then pass null so it is not read twice).
 */
@Composable
fun AppIconView(
    icon: AppIcon,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = GymTrackTheme.tokens.colors.ink,
    size: Dp = 24.dp,
) {
    if (GymTrackTheme.tokens.isNotebook) {
        val semantics = if (contentDescription != null) {
            Modifier.semantics { this.contentDescription = contentDescription }
        } else {
            Modifier
        }
        Spacer(
            modifier
                .size(size)
                .then(semantics)
                .drawWithCache {
                    val runs = sketchGlyphRuns(icon, scale = this.size.minDimension / 24f, unit = 1.dp.toPx())
                    onDrawBehind { drawPencilRuns(runs, tint) }
                },
        )
    } else {
        Icon(icon.normal, contentDescription = contentDescription, modifier = modifier.size(size), tint = tint)
    }
}

/** Glyphs on a 24-unit grid, as polylines (open), closed polygons and circles. */
private sealed interface GlyphPart {
    class Lines(vararg val points: Float) : GlyphPart
    class Polygon(vararg val points: Float) : GlyphPart
    class Circle(val cx: Float, val cy: Float, val r: Float) : GlyphPart
}

private fun glyph(icon: AppIcon): List<GlyphPart> = when (icon) {
    AppIcon.TODAY -> listOf(
        GlyphPart.Polygon(4f, 6f, 20f, 6f, 20f, 20f, 4f, 20f),
        GlyphPart.Lines(4f, 10.5f, 20f, 10.5f),
        GlyphPart.Lines(8.5f, 3f, 8.5f, 7.5f),
        GlyphPart.Lines(15.5f, 3f, 15.5f, 7.5f),
        GlyphPart.Lines(8.5f, 15f, 11f, 17.5f, 15.5f, 13f),
    )
    AppIcon.HISTORY -> listOf(
        GlyphPart.Circle(12f, 12f, 8.5f),
        GlyphPart.Lines(12f, 7f, 12f, 12.5f, 15.5f, 14.5f),
    )
    AppIcon.PROGRESS -> listOf(
        GlyphPart.Lines(4f, 4f, 4f, 20f, 20.5f, 20f),
        GlyphPart.Lines(6.5f, 16.5f, 10.5f, 12f, 13.5f, 14.5f, 19f, 7.5f),
        GlyphPart.Lines(15.5f, 7.2f, 19f, 7.5f, 19.2f, 11f),
    )
    AppIcon.RESUME -> listOf(GlyphPart.Polygon(8f, 5f, 19f, 12f, 8f, 19f))
    AppIcon.ADD -> listOf(GlyphPart.Lines(12f, 5f, 12f, 19f), GlyphPart.Lines(5f, 12f, 19f, 12f))
    AppIcon.CHECK -> listOf(GlyphPart.Lines(5f, 12.5f, 10f, 17.5f, 19f, 7f))
    AppIcon.ALERT -> listOf(
        GlyphPart.Lines(12f, 5f, 12f, 14f),
        GlyphPart.Circle(12f, 18.5f, 1.1f),
    )
    AppIcon.SETTINGS -> buildList {
        add(GlyphPart.Circle(12f, 12f, 4f))
        add(GlyphPart.Circle(12f, 12f, 7.2f))
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            val c = kotlin.math.cos(a).toFloat()
            val s = kotlin.math.sin(a).toFloat()
            add(GlyphPart.Lines(12f + 7.2f * c, 12f + 7.2f * s, 12f + 9.6f * c, 12f + 9.6f * s))
        }
    }
    AppIcon.CHEVRON -> listOf(GlyphPart.Lines(9f, 6f, 15f, 12f, 9f, 18f))
}

private fun sketchGlyphRuns(icon: AppIcon, scale: Float, unit: Float): List<PencilRun> {
    val width = 1.9f * unit
    val wobble = 0.7f * unit
    val step = 2.5f * unit
    val seed = icon.ordinal * 101 + 7
    return glyph(icon).flatMapIndexed { index, part ->
        val partSeed = seed + index * 13
        when (part) {
            is GlyphPart.Lines -> {
                val p = part.points
                (0 until p.size / 2 - 1).flatMap { i ->
                    SketchGeometry.line(
                        p[2 * i] * scale, p[2 * i + 1] * scale, p[2 * i + 2] * scale, p[2 * i + 3] * scale,
                        seed = partSeed + i, wobble = wobble, step = step,
                    ).toPencilRuns(width, alpha = 0.95f)
                }
            }
            is GlyphPart.Polygon -> {
                val xs = FloatArray(part.points.size / 2) { part.points[2 * it] * scale }
                val ys = FloatArray(part.points.size / 2) { part.points[2 * it + 1] * scale }
                SketchGeometry.polygon(xs, ys, partSeed, pass = 0, wobble = wobble, step = step).toPencilRuns(width, 0.95f)
            }
            is GlyphPart.Circle -> SketchGeometry.ellipse(
                part.cx * scale, part.cy * scale, part.r * scale, part.r * scale,
                seed = partSeed, pass = 0, wobble = wobble * 0.6f, step = step,
            ).toPencilRuns(width, 0.95f)
        }
    }
}
