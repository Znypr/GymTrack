package com.example.gymtrack.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.gymtrack.core.muscle.Muscle
import com.example.gymtrack.core.muscle.MuscleLevel
import com.example.gymtrack.core.ui.theme.GymTrackTheme
import com.example.gymtrack.core.ui.theme.GymTrackTokens

const val BODY_MAP_TAG = "body-map"

/**
 * Anatomical muscle map (#311): the approved artwork (docs/design/reference/Anatomy.dc.html,
 * 26 regions, front and back) in one Canvas.
 *
 * Levels follow ANATOMY.md: solid = high, hatched = low/moderate, outlined grey = none, dotted
 * outline = not mapped. Pattern always differs, never colour alone, and red is never used. The
 * Notebook skin draws graphite with a slight wobble and hatching, without opaque fills, so the
 * paper shows through.
 *
 * Performance: shapes, hatching and wobble are built once per process in artwork units and only
 * replayed under a scale transform, so a level change re-runs this one draw block (no
 * recomposition of the parents, no path rebuilds).
 *
 * Accessibility: TalkBack reads [bodyMapDescription]; with [onRegionTap] every region is also
 * offered as a custom action ("Show Quads"), and [BodyMapLevelList] is the visible text list.
 * Taps: ≥48 dp targets (a 24 dp slop around every region) and an extra 12 dp invisible band
 * around the small regions ([BODY_MAP_SMALL_REGIONS]).
 *
 * @param levels a missing key is [MuscleLevel.NONE].
 * @param highlight one region drawn with a heavy outline (the "next muscle" or the tapped one).
 * @param showCaptions "Front" / "Back" under the figures.
 */
@Composable
fun BodyMap(
    levels: Map<Muscle, MuscleLevel>,
    modifier: Modifier = Modifier,
    view: BodyMapView = BodyMapView.BOTH,
    highlight: Muscle? = null,
    onRegionTap: ((Muscle) -> Unit)? = null,
    showCaptions: Boolean = true,
) {
    val tokens = GymTrackTheme.tokens
    val palette = remember(tokens.skin, tokens.isDark) { BodyMapPalette.of(tokens) }
    val description = remember(levels, view) { bodyMapDescription(levels, view) }
    val tap by rememberUpdatedState(onRegionTap)
    val actions = remember(view, onRegionTap != null) {
        if (onRegionTap == null) {
            emptyList()
        } else {
            bodyMapMuscles(view).map { m -> CustomAccessibilityAction("Show ${m.label}") { tap?.invoke(m); true } }
        }
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(BodyMapGeometry.aspectRatio(view))
                .testTag(BODY_MAP_TAG)
                .semantics {
                    contentDescription = description
                    role = Role.Image
                    if (actions.isNotEmpty()) customActions = actions
                }
                .then(
                    if (onRegionTap == null) {
                        Modifier
                    } else {
                        Modifier.pointerInput(view) {
                            val slop = 24.dp.toPx()
                            val bonus = 12.dp.toPx()
                            detectTapGestures { p ->
                                val layout = BodyMapLayout(view, size.width.toFloat(), size.height.toFloat())
                                layout.hitTest(p.x, p.y, slop, bonus)?.let { tap?.invoke(it) }
                            }
                        }
                    },
                )
                // Only this draw block reads levels/highlight: a change invalidates the canvas alone.
                .drawBehind {
                    drawBodyMap(BodyMapLayout(view, size.width, size.height), levels, highlight, palette)
                },
        )
        if (showCaptions) {
            Row(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
                BodyMapGeometry.figures(view).forEach { fig ->
                    Box(Modifier.weight(fig.window.width), contentAlignment = Alignment.Center) {
                        val front = fig === BodyMapGeometry.front
                        Text(
                            text = when {
                                tokens.isNotebook -> if (front) "Front" else "Back"
                                else -> if (front) "FRONT" else "BACK"
                            },
                            style = if (tokens.isNotebook) tokens.type.body else tokens.type.label,
                            color = tokens.colors.inkMuted,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The legend: four swatches (none, low, moderate, high) with words and thresholds, plus "Not
 * mapped" when [showUnmapped].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BodyMapLegend(modifier: Modifier = Modifier, showUnmapped: Boolean = false) {
    val tokens = GymTrackTheme.tokens
    val palette = remember(tokens.skin, tokens.isDark) { BodyMapPalette.of(tokens) }
    val levels = buildList {
        addAll(listOf(MuscleLevel.NONE, MuscleLevel.LOW, MuscleLevel.MODERATE, MuscleLevel.HIGH))
        if (showUnmapped) add(MuscleLevel.UNMAPPED)
    }
    FlowRow(
        modifier = modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.xxs),
    ) {
        levels.forEach { level ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(14.dp)) { drawSwatch(level, palette) }
                Spacer(Modifier.width(tokens.spacing.xxs))
                Text(level.legendCaption(), style = tokens.type.small, color = tokens.colors.inkMuted)
            }
        }
    }
}

/**
 * The text-list equivalent of the map: one line per level ("High · Upper chest, Quads").
 * NONE is left out by default because screens say it their own way ("Not trained yet").
 */
@Composable
fun BodyMapLevelList(
    levels: Map<Muscle, MuscleLevel>,
    modifier: Modifier = Modifier,
    view: BodyMapView = BodyMapView.BOTH,
    includeNone: Boolean = false,
) {
    val tokens = GymTrackTheme.tokens
    val groups = remember(levels, view) { bodyMapGroups(levels, view) }
    val shown = bodyMapMuscles(view)
    val allUnmapped = shown.all { (levels[it] ?: MuscleLevel.NONE) == MuscleLevel.UNMAPPED }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (allUnmapped) {
            Text("Not mapped · all regions", style = tokens.type.small, color = tokens.colors.inkMuted)
            return@Column
        }
        groups.forEach { (level, muscles) ->
            if (level == MuscleLevel.NONE && !includeNone) return@forEach
            Text(
                text = "${level.word()} · ${muscles.joinToString { it.label }}",
                style = tokens.type.small,
                color = if (level == MuscleLevel.UNMAPPED) tokens.colors.inkMuted else tokens.colors.ink,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Palette

/** One hatch line family as drawn: geometry plus pen width and opacity (artwork units). */
@Immutable
internal data class HatchLayer(val spec: HatchSpec, val width: Float, val alpha: Float) {
    val crispStroke = Stroke(width, cap = StrokeCap.Butt)
    val pencilStroke = Stroke(width, cap = StrokeCap.Round)
}

@Immutable
internal class LevelStyle(
    /** Opaque fill (Normal) or translucent graphite tint (Notebook high); null = paper shows through. */
    val fill: Color?,
    val hatch: List<HatchLayer>,
    val hatchInk: Color,
    val stroke: Color,
    val strokeStyle: Stroke,
    val strokeAlpha: Float = 1f,
)

/**
 * Colours and pens per skin. Normal dark is the approved artwork's teal palette; Normal light
 * swaps in #00796B (contrast per the design review); Notebook is the artwork's pencil palette
 * minus its paper fills.
 */
@Immutable
internal class BodyMapPalette(
    val pencil: Boolean,
    val silhouetteFill: Color?,
    val silhouetteStroke: Color?,
    val partFill: Color?,
    val partStroke: Color,
    val partStrokeStyle: Stroke,
    val partAlpha: Float,
    val detail: Color,
    val none: LevelStyle,
    val low: LevelStyle,
    val moderate: LevelStyle,
    val high: LevelStyle,
    val unmapped: LevelStyle,
    val outline: Color?,
    val highlightHalo: Color?,
    val highlightInk: Color,
    val highlightWidth: Float,
) {
    fun style(level: MuscleLevel): LevelStyle = when (level) {
        MuscleLevel.NONE -> none
        MuscleLevel.LOW -> low
        MuscleLevel.MODERATE -> moderate
        MuscleLevel.HIGH -> high
        MuscleLevel.UNMAPPED -> unmapped
    }

    companion object {
        private val separator = Stroke(0.9f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        private val tealLow = listOf(HatchLayer(HatchSpec(6f, 1.1f, 45f), 2.2f, 1f))
        private val tealModerate = listOf(
            HatchLayer(HatchSpec(4f, 0.9f, 45f), 1.8f, 1f),
            HatchLayer(HatchSpec(4f, 0.8f, 135f), 1.6f, 1f),
        )
        internal val pencilLow = listOf(
            HatchLayer(HatchSpec(14f, 1.0f, 40f), 0.8f, 0.62f),
            HatchLayer(HatchSpec(14f, 8.2f, 40f), 0.65f, 0.45f),
        )
        internal val pencilModerate = listOf(
            HatchLayer(HatchSpec(6f, 0.8f, 40f), 0.85f, 0.78f),
            HatchLayer(HatchSpec(6f, 3.8f, 40f), 0.7f, 0.58f),
        )
        internal val pencilHigh = listOf(
            HatchLayer(HatchSpec(5f, 0.8f, 40f), 0.95f, 0.87f),
            HatchLayer(HatchSpec(5f, 3.2f, 40f), 0.8f, 0.71f),
            HatchLayer(HatchSpec(3.3f, 1.6f, 130f), 0.7f, 0.64f),
        )

        fun of(tokens: GymTrackTokens): BodyMapPalette = when {
            tokens.isNotebook -> notebook
            tokens.isDark -> normalDark
            else -> normalLight
        }

        val normalDark = BodyMapPalette(
            pencil = false,
            silhouetteFill = Color(0xFF1A1A1C),
            silhouetteStroke = Color(0xFF2C2C2E),
            partFill = Color(0xFF232325),
            partStroke = Color(0xFF38383B),
            partStrokeStyle = Stroke(0.7f),
            partAlpha = 1f,
            detail = Color(0xFF48484C),
            none = LevelStyle(Color(0xFF3A3A3C), emptyList(), Color.Transparent, Color(0xFF6C6C70), separator),
            low = LevelStyle(Color(0xFF0E3F39), tealLow, Color(0xFF1A8A7C), Color(0xFF0B0B0C), separator),
            moderate = LevelStyle(Color(0xFF0E3F39), tealModerate, Color(0xFF23B3A0), Color(0xFF0B0B0C), separator),
            high = LevelStyle(Color(0xFF2DD4BF), emptyList(), Color.Transparent, Color(0xFF0B0B0C), separator),
            unmapped = LevelStyle(
                Color(0xFF3A3A3C), emptyList(), Color.Transparent, Color(0xFFC7C7CC),
                Stroke(0.9f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.6f, 1.8f))),
            ),
            outline = null,
            highlightHalo = Color(0xFF000000),
            highlightInk = Color(0xFFFFFFFF),
            highlightWidth = 2.2f,
        )

        val normalLight = BodyMapPalette(
            pencil = false,
            silhouetteFill = Color(0xFFE5E5EA),
            silhouetteStroke = Color(0xFFC7C7CC),
            partFill = Color(0xFFDADADF),
            partStroke = Color(0xFFC7C7CC),
            partStrokeStyle = Stroke(0.7f),
            partAlpha = 1f,
            detail = Color(0xFFAEAEB2),
            none = LevelStyle(Color(0xFFF7F7FA), emptyList(), Color.Transparent, Color(0xFF6C6C70), separator),
            low = LevelStyle(Color(0xFFD3EEEA), tealLow, Color(0xFF00796B), Color(0xFFFFFFFF), separator),
            moderate = LevelStyle(Color(0xFFD3EEEA), tealModerate, Color(0xFF00796B), Color(0xFFFFFFFF), separator),
            high = LevelStyle(Color(0xFF00796B), emptyList(), Color.Transparent, Color(0xFFFFFFFF), separator),
            unmapped = LevelStyle(
                Color(0xFFF7F7FA), emptyList(), Color.Transparent, Color(0xFF6C6C70),
                Stroke(0.9f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.6f, 1.8f))),
            ),
            outline = null,
            highlightHalo = Color(0xFFFFFFFF),
            highlightInk = Color(0xFF1C1C1E),
            highlightWidth = 2.2f,
        )

        private val graphite = Color(0xFF2B2A28)
        private val pencilStroke = Stroke(0.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)

        val notebook = BodyMapPalette(
            pencil = true,
            silhouetteFill = null,
            silhouetteStroke = null,
            partFill = null,
            partStroke = graphite,
            partStrokeStyle = Stroke(0.75f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            partAlpha = 0.7f,
            detail = graphite.copy(alpha = 0.45f),
            none = LevelStyle(null, emptyList(), graphite, graphite, pencilStroke, strokeAlpha = 0.6f),
            low = LevelStyle(null, pencilLow, graphite, graphite, pencilStroke, strokeAlpha = 0.6f),
            moderate = LevelStyle(null, pencilModerate, graphite, graphite, pencilStroke, strokeAlpha = 0.6f),
            // A light graphite smudge under the cross-hatch (the artwork's #D8D2C4 on paper), translucent.
            high = LevelStyle(graphite.copy(alpha = 0.13f), pencilHigh, graphite, graphite, pencilStroke, strokeAlpha = 0.6f),
            unmapped = LevelStyle(
                null, emptyList(), graphite, graphite,
                Stroke(1.3f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.2f, 2.6f))),
                strokeAlpha = 0.9f,
            ),
            outline = graphite,
            highlightHalo = null,
            highlightInk = Color(0xFF1F4FB5),
            highlightWidth = 1.6f,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Cached paths (artwork units). Built lazily once per process, on the main thread.

private const val PENCIL_WOBBLE = 0.85f
private const val PENCIL_STEP = 2.5f

private fun polylinePath(polys: List<FloatArray>, close: Boolean, into: Path = Path()): Path {
    for (p in polys) {
        if (p.size < 4) continue
        into.moveTo(p[0], p[1])
        var i = 2
        while (i < p.size) { into.lineTo(p[i], p[i + 1]); i += 2 }
        if (close) into.close()
    }
    return into
}

private fun opsPath(ops: List<PathOp>): Path {
    val path = Path()
    for (op in ops) when (op) {
        is PathOp.Move -> path.moveTo(op.x, op.y)
        is PathOp.Line -> path.lineTo(op.x, op.y)
        is PathOp.Cubic -> path.cubicTo(op.x1, op.y1, op.x2, op.y2, op.x, op.y)
        PathOp.Close -> path.close()
    }
    return path
}

/** Every path of one figure for one skin family (crisp or pencil). */
private class FigurePaths(private val fig: FigureGeometry, private val pencil: Boolean) {
    private fun shape(d: String, close: Boolean = true): Path {
        val ops = SvgPath.parse(d)
        return if (!pencil) {
            opsPath(ops)
        } else {
            polylinePath(SvgPath.flatten(ops, maxStep = PENCIL_STEP).map { PencilWobble.apply(it, PENCIL_WOBBLE) }, close)
        }
    }

    val silhouette = fig.art.silhouette.map { shape(it) }
    val partsUnder = fig.art.partsUnder.map { shape(it) }
    val partsOver = fig.art.partsOver.map { shape(it) }
    val details = fig.art.details.map { shape(it, close = false) }
    val pieces: List<Path> = fig.pieces.map { piece ->
        if (!pencil) {
            opsPath(piece.ops)
        } else {
            polylinePath(SvgPath.flatten(piece.ops, maxStep = PENCIL_STEP).map { PencilWobble.apply(it, PENCIL_WOBBLE) }, close = true)
        }
    }
    val outline: List<Path> = if (pencil) fig.art.pencilOutline.map { shape(it, close = false) } else emptyList()
    val ghost: List<Path> = if (pencil) fig.art.pencilGhost.map { shape(it, close = false) } else emptyList()

    private val hatchCache = HashMap<Long, Path>()
    private val highlightCache = HashMap<Muscle, Path>()

    /** The hatch lines of [layer] clipped to piece [index]. */
    fun hatch(index: Int, layerKey: Int, layer: HatchLayer): Path = hatchCache.getOrPut(index.toLong() shl 32 or layerKey.toLong()) {
        val piece = fig.pieces[index]
        val segments = Hatching.clip(piece.polygons, piece.bounds, layer.spec)
        val path = Path()
        var i = 0
        while (i < segments.size) {
            if (pencil) {
                val line = PencilWobble.segment(segments[i], segments[i + 1], segments[i + 2], segments[i + 3], PENCIL_WOBBLE, PENCIL_STEP)
                polylinePath(listOf(line), close = false, into = path)
            } else {
                path.moveTo(segments[i], segments[i + 1])
                path.lineTo(segments[i + 2], segments[i + 3])
            }
            i += 4
        }
        path
    }

    /** Outline of every piece of [muscle] (Normal) or the artwork's hand-drawn loop (Notebook). */
    fun highlight(muscle: Muscle): Path? {
        if (muscle !in fig.muscles) return null
        return highlightCache.getOrPut(muscle) {
            val loop = fig.art.pencilLoops[muscle]
            if (pencil && loop != null) {
                shape(loop, close = false)
            } else {
                Path().also { p -> fig.pieces.forEachIndexed { i, piece -> if (piece.muscle == muscle) p.addPath(pieces[i]) } }
            }
        }
    }
}

private object BodyMapPathCache {
    private val cache = HashMap<Pair<FigureGeometry, Boolean>, FigurePaths>()
    fun of(fig: FigureGeometry, pencil: Boolean): FigurePaths = cache.getOrPut(fig to pencil) { FigurePaths(fig, pencil) }
}

// ---------------------------------------------------------------------------------------------
// Drawing

private val ghostStroke = Stroke(0.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
private val outlineStroke = Stroke(1.15f, cap = StrokeCap.Round, join = StrokeJoin.Round)
private val silhouetteStroke = Stroke(1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
private val detailStroke = Stroke(0.7f, cap = StrokeCap.Round)
private val haloStroke = Stroke(4.2f, join = StrokeJoin.Round)

private fun DrawScope.drawBodyMap(
    layout: BodyMapLayout,
    levels: Map<Muscle, MuscleLevel>,
    highlight: Muscle?,
    palette: BodyMapPalette,
) {
    val highlightStroke = Stroke(palette.highlightWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    layout.figures.forEachIndexed { f, fig ->
        val paths = BodyMapPathCache.of(fig, palette.pencil)
        withTransform({
            translate(layout.dx[f], layout.dy)
            scale(layout.scale, layout.scale, Offset.Zero)
        }) {
            for (p in paths.silhouette) {
                palette.silhouetteFill?.let { drawPath(p, it) }
                palette.silhouetteStroke?.let { drawPath(p, it, style = silhouetteStroke) }
            }
            drawParts(paths.partsUnder, palette)
            fig.pieces.forEachIndexed { i, piece ->
                val style = palette.style(levels[piece.muscle] ?: MuscleLevel.NONE)
                val path = paths.pieces[i]
                style.fill?.let { drawPath(path, it) }
                style.hatch.forEachIndexed { k, layer ->
                    drawPath(paths.hatch(i, levelKey(style, palette) * 8 + k, layer), style.hatchInk, alpha = layer.alpha, style = if (palette.pencil) layer.pencilStroke else layer.crispStroke)
                }
                drawPath(path, style.stroke, alpha = style.strokeAlpha, style = style.strokeStyle)
            }
            drawParts(paths.partsOver, palette)
            for (p in paths.details) drawPath(p, palette.detail, style = detailStroke)
            palette.outline?.let { ink ->
                for (p in paths.outline) drawPath(p, ink, alpha = 0.85f, style = outlineStroke)
                translate(0.5f, 0.45f) { for (p in paths.ghost) drawPath(p, ink, alpha = 0.35f, style = ghostStroke) }
            }
            if (highlight != null) {
                paths.highlight(highlight)?.let { hl ->
                    palette.highlightHalo?.let { drawPath(hl, it, style = haloStroke) }
                    drawPath(hl, palette.highlightInk, style = highlightStroke)
                }
            }
        }
    }
}

/** Distinct cache keys for the hatch families of each level. */
private fun levelKey(style: LevelStyle, palette: BodyMapPalette): Int = when (style) {
    palette.low -> 1
    palette.moderate -> 2
    palette.high -> 3
    else -> 0
}

private fun DrawScope.drawParts(parts: List<Path>, palette: BodyMapPalette) {
    for (p in parts) {
        palette.partFill?.let { drawPath(p, it) }
        drawPath(p, palette.partStroke, alpha = palette.partAlpha, style = palette.partStrokeStyle)
    }
}

/** Legend swatch: a 30 × 30 artwork-unit square (about the map's own hatch density) in the level's style. */
private const val SWATCH = 30f
private val swatchSquare = listOf(floatArrayOf(0f, 0f, SWATCH, 0f, SWATCH, SWATCH, 0f, SWATCH))
private val swatchBounds = MapBounds(0f, 0f, SWATCH, SWATCH)
private val swatchHatch = HashMap<HatchLayer, Pair<Path, Path>>()

private fun DrawScope.drawSwatch(level: MuscleLevel, palette: BodyMapPalette) {
    val s = size.minDimension / SWATCH
    val style = palette.style(level)
    val square = polylinePath(swatchSquare, close = true)
    scale(s, s, Offset.Zero) {
        style.fill?.let { drawPath(square, it) }
        style.hatch.forEach { layer ->
            val (crisp, pencil) = swatchHatch.getOrPut(layer) {
                val seg = Hatching.clip(swatchSquare, swatchBounds, layer.spec)
                val crispPath = Path()
                val pencilPath = Path()
                var i = 0
                while (i < seg.size) {
                    crispPath.moveTo(seg[i], seg[i + 1]); crispPath.lineTo(seg[i + 2], seg[i + 3])
                    polylinePath(listOf(PencilWobble.segment(seg[i], seg[i + 1], seg[i + 2], seg[i + 3], 0.4f, PENCIL_STEP)), false, pencilPath)
                    i += 4
                }
                crispPath to pencilPath
            }
            drawPath(if (palette.pencil) pencil else crisp, style.hatchInk, alpha = layer.alpha, style = if (palette.pencil) layer.pencilStroke else layer.crispStroke)
        }
        drawPath(square, style.stroke, alpha = style.strokeAlpha, style = style.strokeStyle)
    }
}

