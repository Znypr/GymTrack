package com.example.gymtrack.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.gymtrack.core.ui.theme.GymTrackColors
import com.example.gymtrack.core.ui.theme.GymTrackTheme
import com.example.gymtrack.core.ui.theme.SketchGeometry
import com.example.gymtrack.core.ui.theme.drawPencilRuns
import com.example.gymtrack.core.ui.theme.toPencilRuns

/** Workout category as shape + colour (never colour alone): Push ▲, Pull ◆, Legs ●, other ■. */
enum class CategoryKind(val label: String) {
    PUSH("Push"),
    PULL("Pull"),
    LEGS("Legs"),
    OTHER("Other");

    companion object {
        /** Maps a stored category name; user-made categories are [OTHER]. */
        fun fromName(name: String?): CategoryKind = when (name?.trim()?.lowercase()) {
            "push" -> PUSH
            "pull" -> PULL
            "legs", "leg" -> LEGS
            else -> OTHER
        }
    }
}

/** Token colour for a category; [OTHER] uses [custom] (the user's colour) or muted ink. */
fun GymTrackColors.categoryColor(kind: CategoryKind, custom: Color? = null): Color = when (kind) {
    CategoryKind.PUSH -> catPush
    CategoryKind.PULL -> catPull
    CategoryKind.LEGS -> catLegs
    CategoryKind.OTHER -> custom ?: inkMuted
}

/**
 * A small category mark. Normal: a crisp filled shape. Notebook: a hand-drawn outline with a
 * pencil scribble inside, in the category's ink.
 *
 * @param label shown next to the mark when [showLabel]; always used as the spoken description.
 * @param customColor colour for user-made categories ([CategoryKind.OTHER]).
 */
@Composable
fun CategoryMark(
    kind: CategoryKind,
    modifier: Modifier = Modifier,
    label: String = kind.label,
    showLabel: Boolean = false,
    size: Dp = 12.dp,
    customColor: Color? = null,
) {
    val tokens = GymTrackTheme.tokens
    val color = tokens.colors.categoryColor(kind, customColor)
    val notebook = tokens.isNotebook
    val mark = Modifier
        .size(size)
        .drawWithCache {
            val s = this.size.minDimension
            if (notebook) {
                val unit = 1.dp.toPx()
                val runs = sketchMarkRuns(kind, s, unit)
                onDrawBehind { drawPencilRuns(runs, color) }
            } else {
                val path = crispMarkPath(kind, s)
                onDrawBehind { drawCrispMark(kind, path, s, color) }
            }
        }
    if (showLabel) {
        Row(
            modifier = modifier.clearAndSetSemantics { contentDescription = "$label category" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Spacer(mark)
            Text(label, style = tokens.type.small, color = tokens.colors.ink)
        }
    } else {
        Spacer(modifier.then(mark).clearAndSetSemantics { contentDescription = "$label category" })
    }
}

private fun crispMarkPath(kind: CategoryKind, s: Float): Path? = when (kind) {
    CategoryKind.PUSH -> Path().apply {
        moveTo(s / 2f, s * 0.06f)
        lineTo(s * 0.97f, s * 0.92f)
        lineTo(s * 0.03f, s * 0.92f)
        close()
    }
    CategoryKind.PULL -> Path().apply {
        moveTo(s / 2f, 0f)
        lineTo(s, s / 2f)
        lineTo(s / 2f, s)
        lineTo(0f, s / 2f)
        close()
    }
    CategoryKind.LEGS -> null
    CategoryKind.OTHER -> Path().apply {
        moveTo(s * 0.1f, s * 0.1f)
        lineTo(s * 0.9f, s * 0.1f)
        lineTo(s * 0.9f, s * 0.9f)
        lineTo(s * 0.1f, s * 0.9f)
        close()
    }
}

private fun DrawScope.drawCrispMark(kind: CategoryKind, path: Path?, s: Float, color: Color) {
    if (kind == CategoryKind.LEGS || path == null) {
        drawCircle(color, radius = s / 2f, center = Offset(s / 2f, s / 2f))
    } else {
        drawPath(path, color)
    }
}

private fun sketchMarkRuns(kind: CategoryKind, s: Float, unit: Float) = buildList {
    val width = 1.6f * unit
    val wobble = 0.5f * unit
    val step = 1.5f * unit
    val seed = kind.ordinal * 31 + 3
    val outline = when (kind) {
        CategoryKind.PUSH -> SketchGeometry.polygon(
            floatArrayOf(s / 2f, s * 0.95f, s * 0.05f), floatArrayOf(s * 0.08f, s * 0.9f, s * 0.9f),
            seed, pass = 0, wobble = wobble, step = step,
        )
        CategoryKind.PULL -> SketchGeometry.polygon(
            floatArrayOf(s / 2f, s * 0.95f, s / 2f, s * 0.05f), floatArrayOf(s * 0.05f, s / 2f, s * 0.95f, s / 2f),
            seed, pass = 0, wobble = wobble, step = step,
        )
        CategoryKind.LEGS -> SketchGeometry.ellipse(s / 2f, s / 2f, s * 0.42f, s * 0.42f, seed, pass = 0, wobble = wobble, step = step)
        CategoryKind.OTHER -> SketchGeometry.polygon(
            floatArrayOf(s * 0.1f, s * 0.9f, s * 0.9f, s * 0.1f), floatArrayOf(s * 0.1f, s * 0.1f, s * 0.9f, s * 0.9f),
            seed, pass = 0, wobble = wobble, step = step,
        )
    }
    addAll(outline.toPencilRuns(width, alpha = 0.95f))
    // Pencil scribble inside (no opaque fill): two short strokes across the middle.
    addAll(SketchGeometry.line(s * 0.32f, s * 0.62f, s * 0.62f, s * 0.36f, seed + 1, wobble, step).toPencilRuns(width * 0.8f, 0.6f))
    addAll(SketchGeometry.line(s * 0.4f, s * 0.74f, s * 0.7f, s * 0.5f, seed + 2, wobble, step).toPencilRuns(width * 0.8f, 0.5f))
}
