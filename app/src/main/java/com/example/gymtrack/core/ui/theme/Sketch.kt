package com.example.gymtrack.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/*
 * Notebook drawing primitives. Everything is built inside drawWithCache / Shape outlines, so the
 * paths are computed once per size and seed; a frame only replays them.
 */

/** A stable seed from any key (a label, an id), so the same element always looks the same. */
fun sketchSeed(key: Any?): Int = key?.hashCode() ?: 0

/** One drawable piece of a pencil stroke: a path at one weight and opacity. */
@Immutable
internal class PencilRun(val path: Path, val stroke: Stroke, val alpha: Float)

/**
 * Splits a [SketchStroke] into short runs so each can carry its own weight: the stroke gets
 * heavier and lighter along its length like a pencil under changing pressure.
 */
internal fun SketchStroke.toPencilRuns(
    baseWidth: Float,
    alpha: Float,
    runLength: Int = 6,
    pathEffect: PathEffect? = null,
): List<PencilRun> {
    if (size < 2) return emptyList()
    if (pathEffect != null) {
        // Dashes need one continuous path so the dash pattern is not restarted per run.
        val path = Path().apply {
            moveTo(xs[0], ys[0])
            for (i in 1 until size) lineTo(xs[i], ys[i])
        }
        return listOf(PencilRun(path, Stroke(baseWidth, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = pathEffect), alpha))
    }
    val runs = ArrayList<PencilRun>(size / runLength + 1)
    var start = 0
    while (start < size - 1) {
        val end = minOf(start + runLength, size - 1)
        var pressure = 0f
        val path = Path()
        path.moveTo(xs[start], ys[start])
        for (i in start..end) {
            if (i > start) path.lineTo(xs[i], ys[i])
            pressure += this.pressure[i]
        }
        pressure /= (end - start + 1)
        runs += PencilRun(
            path = path,
            stroke = Stroke(width = baseWidth * pressure, cap = StrokeCap.Round, join = StrokeJoin.Round),
            alpha = alpha * (0.75f + 0.25f * pressure.coerceAtMost(1f)),
        )
        start = end // runs share their joint point, round caps hide the seam
    }
    return runs
}

internal fun DrawScope.drawPencilRuns(runs: List<PencilRun>, color: Color) {
    for (run in runs) drawPath(run.path, color.copy(alpha = color.alpha * run.alpha), style = run.stroke)
}

/**
 * Sketched rounded-rectangle border: two re-traced passes with seeded irregular radii, a slow
 * wobble and varying weight. Drawn over the content, like [androidx.compose.foundation.border].
 *
 * @param seed pick a different seed per element (see [sketchSeed]) so boxes don't match.
 * @param dashed one dashed pass (ghost/suggestion pills).
 */
fun Modifier.sketchBorder(
    color: Color,
    seed: Int,
    strokeWidth: Dp = 1.6.dp,
    cornerRadius: Dp = 14.dp,
    wobble: Dp = 1.2.dp,
    passes: Int = 2,
    dashed: Boolean = false,
): Modifier = drawWithCache {
    val runs = sketchRectRuns(seed, strokeWidth, cornerRadius, wobble, if (dashed) 1 else passes, dashed)
    onDrawWithContent {
        drawContent()
        drawPencilRuns(runs, color)
    }
}

private fun CacheDrawScope.sketchRectRuns(
    seed: Int,
    strokeWidth: Dp,
    cornerRadius: Dp,
    wobble: Dp,
    passes: Int,
    dashed: Boolean,
): List<PencilRun> {
    val sw = strokeWidth.toPx()
    val wob = wobble.toPx()
    val dash = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())) else null
    return (0 until passes).flatMap { pass ->
        SketchGeometry.roundedRect(
            width = size.width,
            height = size.height,
            seed = seed,
            pass = pass,
            cornerRadius = cornerRadius.toPx(),
            wobble = wob,
            step = 3.dp.toPx(),
            closed = dashed,
            overshoot = 0.035f + 0.02f * pass,
            inset = sw / 2f + wob + (if (pass > 0) 0.6f * pass else 0f),
        ).toPencilRuns(
            baseWidth = if (pass == 0) sw else sw * 0.7f,
            alpha = if (pass == 0) 0.92f else 0.5f,
            pathEffect = dash,
        )
    }
}

/**
 * The fill/clip outline that matches [sketchBorder] with the same seed (its main pass).
 * Use it for `clip`, `background` and hatch fills so they line up with the drawn border.
 */
@Immutable
class SketchShape(
    private val seed: Int,
    private val cornerRadius: Dp = 14.dp,
    private val wobble: Dp = 1.2.dp,
    private val strokeWidth: Dp = 1.6.dp,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val stroke = with(density) {
            SketchGeometry.roundedRect(
                width = size.width,
                height = size.height,
                seed = seed,
                pass = 0,
                cornerRadius = cornerRadius.toPx(),
                wobble = wobble.toPx(),
                step = 3.dp.toPx(),
                closed = true,
                inset = strokeWidth.toPx() / 2f + wobble.toPx(),
            )
        }
        val path = Path().apply {
            moveTo(stroke.xs[0], stroke.ys[0])
            for (i in 1 until stroke.size) lineTo(stroke.xs[i], stroke.ys[i])
            close()
        }
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean =
        other is SketchShape && other.seed == seed && other.cornerRadius == cornerRadius &&
            other.wobble == wobble && other.strokeWidth == strokeWidth

    override fun hashCode(): Int = ((seed * 31 + cornerRadius.hashCode()) * 31 + wobble.hashCode()) * 31 + strokeWidth.hashCode()
}

/**
 * Pencil hatching instead of an opaque fill: slightly bowed, uneven parallel lines, clipped to
 * [shape]. [cross] adds a second direction (dense shading, e.g. muscle level "high").
 */
fun Modifier.pencilHatch(
    color: Color,
    seed: Int,
    shape: Shape? = null,
    spacing: Dp = 6.dp,
    angleDegrees: Float = -35f,
    cross: Boolean = false,
    strokeWidth: Dp = 1.dp,
): Modifier = drawWithCache {
    val clip = shape?.let { Path().apply { addOutline(it.createOutline(size, layoutDirection, this@drawWithCache)) } }
    val lines = buildList {
        addAll(hatchRuns(seed, spacing.toPx(), angleDegrees, strokeWidth.toPx()))
        if (cross) addAll(hatchRuns(seed + 1, spacing.toPx() * 1.15f, angleDegrees + 75f, strokeWidth.toPx() * 0.9f))
    }
    onDrawBehind {
        if (clip != null) clipPath(clip) { drawPencilRuns(lines, color) } else drawPencilRuns(lines, color)
    }
}

private fun CacheDrawScope.hatchRuns(seed: Int, spacing: Float, angleDegrees: Float, width: Float): List<PencilRun> {
    val rnd = Random(seed * 131 + 5)
    val a = Math.toRadians(angleDegrees.toDouble())
    val dx = cos(a).toFloat()
    val dy = sin(a).toFloat()
    val nx = -dy
    val ny = dx
    val cx = size.width / 2f
    val cy = size.height / 2f
    val radius = hypot(size.width, size.height) / 2f
    val runs = ArrayList<PencilRun>()
    var k = -radius + spacing * rnd.nextFloat()
    var index = 0
    while (k <= radius) {
        val jitter = (rnd.nextFloat() - 0.5f) * spacing * 0.35f
        val ox = cx + nx * (k + jitter)
        val oy = cy + ny * (k + jitter)
        val startTrim = radius * (rnd.nextFloat() * 0.12f)
        val endTrim = radius * (rnd.nextFloat() * 0.12f)
        val line = SketchGeometry.line(
            x0 = ox - dx * (radius - startTrim),
            y0 = oy - dy * (radius - startTrim),
            x1 = ox + dx * (radius - endTrim),
            y1 = oy + dy * (radius - endTrim),
            seed = seed * 1000 + index,
            wobble = spacing * 0.2f,
            step = 6.dp.toPx(),
        )
        val path = Path().apply {
            moveTo(line.xs[0], line.ys[0])
            for (i in 1 until line.size) lineTo(line.xs[i], line.ys[i])
        }
        runs += PencilRun(path, Stroke(width * (0.8f + 0.4f * rnd.nextFloat()), cap = StrokeCap.Round), 0.55f + 0.4f * rnd.nextFloat())
        k += spacing
        index++
    }
    return runs
}

/**
 * Notebook page: cream paper, printed ruled lines every [ruleSpacing] and a faint red margin
 * line at [marginX]. Rules are printed, so they stay straight. Apply to the scrolling content
 * if the lines should move with it.
 */
fun Modifier.notebookPaper(
    paper: Color = NotebookColors.bg,
    rule: Color = NotebookColors.paperRule,
    margin: Color = NotebookColors.paperMargin,
    ruleSpacing: Dp = 28.dp,
    marginX: Dp? = 44.dp,
): Modifier = drawWithCache {
    val spacingPx = ruleSpacing.toPx()
    val ruleWidth = 1.dp.toPx()
    val rules = Path().apply {
        var y = spacingPx - ruleWidth / 2f
        while (y < size.height) {
            moveTo(0f, y)
            lineTo(size.width, y)
            y += spacingPx
        }
    }
    val ruleStroke = Stroke(ruleWidth)
    val marginPx = marginX?.toPx()
    val marginWidth = 1.5.dp.toPx()
    onDrawBehind {
        drawRect(paper)
        drawPath(rules, rule, style = ruleStroke)
        if (marginPx != null) drawLine(margin, Offset(marginPx, 0f), Offset(marginPx, size.height), marginWidth)
    }
}

/**
 * A highlighter swipe behind the content (Notebook `active`): a band from ~20% to ~85% of the
 * height with uneven, slightly slanted ends.
 */
fun Modifier.highlighter(color: Color, seed: Int): Modifier = drawWithCache {
    val rnd = Random(seed * 17 + 11)
    val h = size.height
    val w = size.width
    val wob = 1.5.dp.toPx()
    val band = Path().apply {
        moveTo(rnd.nextFloat() * 4.dp.toPx(), h * 0.2f + (rnd.nextFloat() - 0.5f) * wob)
        lineTo(w - rnd.nextFloat() * 6.dp.toPx(), h * 0.18f + (rnd.nextFloat() - 0.5f) * wob)
        lineTo(w - rnd.nextFloat() * 3.dp.toPx(), h * 0.86f + (rnd.nextFloat() - 0.5f) * wob)
        lineTo(rnd.nextFloat() * 6.dp.toPx(), h * 0.84f + (rnd.nextFloat() - 0.5f) * wob)
        close()
    }
    onDrawBehind { drawPath(band, color) }
}

/** A hand-drawn line along the bottom edge (Notebook dividers, underlines). */
fun Modifier.sketchUnderline(color: Color, seed: Int, strokeWidth: Dp = 1.4.dp, inset: Dp = 0.dp): Modifier = drawWithCache {
    val y = size.height - strokeWidth.toPx()
    val runs = SketchGeometry.line(
        x0 = inset.toPx(),
        y0 = y,
        x1 = size.width - inset.toPx(),
        y1 = y,
        seed = seed,
        wobble = 1.dp.toPx(),
        step = 4.dp.toPx(),
    ).toPencilRuns(strokeWidth.toPx(), alpha = 0.85f)
    onDrawWithContent {
        drawContent()
        drawPencilRuns(runs, color)
    }
}

/**
 * Sheet outline with a torn-paper top edge (Notebook bottom sheet). Sides and bottom are
 * straight because they sit on the screen edges.
 */
@Immutable
class TornTopShape(private val seed: Int, private val tear: Dp = 4.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val rnd = Random(seed * 37 + 1)
        val amp = with(density) { tear.toPx() }
        val stepMin = with(density) { 5.dp.toPx() }
        val stepMax = with(density) { 12.dp.toPx() }
        val path = Path().apply {
            moveTo(0f, size.height)
            lineTo(0f, amp * rnd.nextFloat())
            var x = 0f
            while (x < size.width) {
                x = minOf(size.width, x + stepMin + rnd.nextFloat() * (stepMax - stepMin))
                lineTo(x, amp * rnd.nextFloat())
            }
            lineTo(size.width, size.height)
            close()
        }
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean = other is TornTopShape && other.seed == seed && other.tear == tear
    override fun hashCode(): Int = seed * 31 + tear.hashCode()
}
