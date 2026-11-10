package com.example.gymtrack.feature.editor.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.MoreHoriz
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
 * Editor-only icons that the shared [com.example.gymtrack.core.ui.components.AppIcon] set lacks
 * (back, more, notes). Crisp Material icon in Normal, pencil glyph in Notebook (README rule 4).
 * Lead: candidates to move into AppIcon (core/ui) so other screens can use them.
 */
internal enum class EditorGlyph(val normal: ImageVector) {
    BACK(Icons.AutoMirrored.Filled.ArrowBack),
    MORE(Icons.Filled.MoreHoriz),

    /** Notes & learnings (#297): a page with lines and a pencil, never ☰. */
    NOTES(Icons.Filled.EditNote),
}

@Composable
internal fun EditorGlyphView(
    glyph: EditorGlyph,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = GymTrackTheme.tokens.colors.ink,
    size: Dp = 24.dp,
) {
    if (!GymTrackTheme.tokens.isNotebook) {
        Icon(glyph.normal, contentDescription = contentDescription, modifier = modifier.size(size), tint = tint)
        return
    }
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
                val runs = sketchGlyph(glyph, this.size.minDimension / 24f, 1.dp.toPx())
                onDrawBehind { drawPencilRuns(runs, tint) }
            },
    )
}

/** Pencil polylines on a 24-unit grid. */
private fun sketchGlyph(glyph: EditorGlyph, scale: Float, unit: Float): List<PencilRun> {
    val width = 1.8f * unit
    val wobble = 0.45f * unit
    val step = 2f * unit
    val seed = glyph.ordinal * 13 + 5
    fun line(x0: Float, y0: Float, x1: Float, y1: Float, k: Int, alpha: Float = 0.95f) =
        SketchGeometry.line(x0 * scale, y0 * scale, x1 * scale, y1 * scale, seed + k, wobble, step)
            .toPencilRuns(width, alpha)
    return when (glyph) {
        EditorGlyph.BACK -> line(20f, 12f, 4.5f, 12f, 0) + line(4.5f, 12f, 11f, 5.5f, 1) + line(4.5f, 12f, 11f, 18.5f, 2)
        EditorGlyph.MORE -> listOf(6f, 12f, 18f).flatMapIndexed { i, x ->
            SketchGeometry.ellipse(x * scale, 12f * scale, 1.6f * scale, 1.6f * scale, seed + i, 0, wobble * 0.5f, step * 0.5f)
                .toPencilRuns(width * 1.2f, 0.95f)
        }
        EditorGlyph.NOTES ->
            SketchGeometry.polygon(
                floatArrayOf(4f * scale, 16f * scale, 16f * scale, 4f * scale),
                floatArrayOf(3.5f * scale, 3.5f * scale, 20.5f * scale, 20.5f * scale),
                seed, pass = 0, wobble = wobble, step = step,
            ).toPencilRuns(width, 0.95f) +
                line(7f, 8f, 13f, 8f, 3, 0.8f) + line(7f, 12f, 13f, 12f, 4, 0.8f) + line(7f, 16f, 10.5f, 16f, 5, 0.8f) +
                // The pencil across the corner.
                line(13.5f, 19f, 21f, 9.5f, 6) + line(15.5f, 20.5f, 22.5f, 11.5f, 7, 0.7f)
    }
}
