package com.example.gymtrack.core.ui.components

import com.example.gymtrack.core.muscle.Muscle
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * Body map geometry (#311), plain Kotlin with no Android or Compose types so shapes, layout,
 * hit-testing and hatch clipping are unit-testable. Everything is in artwork units (the
 * 820 × 460 viewBox of docs/design/reference/Anatomy.dc.html); BodyMap.kt scales it to dp.
 */

/** Which figure(s) a [BodyMap] shows. Single views are preferred wherever space allows (ANATOMY.md). */
enum class BodyMapView { FRONT, BACK, BOTH }

/**
 * Regions too small to tap reliably at the "both" size (ANATOMY.md, science review fix 13).
 * They get an enlarged invisible hit area and always appear in the text list.
 */
val BODY_MAP_SMALL_REGIONS: Set<Muscle> = setOf(Muscle.BRACHIALIS, Muscle.DELT_SIDE, Muscle.SERRATUS, Muscle.TIBIALIS)

/** Axis-aligned bounds in artwork units. */
internal data class MapBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    fun inflate(by: Float) = MapBounds(left - by, top - by, right + by, bottom + by)
    fun union(o: MapBounds) = MapBounds(min(left, o.left), min(top, o.top), max(right, o.right), max(bottom, o.bottom))

    companion object {
        fun of(polygons: List<FloatArray>): MapBounds {
            var l = Float.MAX_VALUE
            var t = Float.MAX_VALUE
            var r = -Float.MAX_VALUE
            var b = -Float.MAX_VALUE
            for (p in polygons) {
                var i = 0
                while (i < p.size) {
                    l = min(l, p[i]); r = max(r, p[i])
                    t = min(t, p[i + 1]); b = max(b, p[i + 1])
                    i += 2
                }
            }
            return MapBounds(l, t, r, b)
        }
    }
}

/** One SVG path command; absolute coordinates only (the artwork uses M, L, C, Z). */
internal sealed interface PathOp {
    data class Move(val x: Float, val y: Float) : PathOp
    data class Line(val x: Float, val y: Float) : PathOp
    data class Cubic(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val x: Float, val y: Float) : PathOp
    data object Close : PathOp
}

internal object SvgPath {
    private val token = Regex("[MLCZmlcz]|-?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?")

    /** Parses absolute M/L/C/Z path data; implicit repeats are supported (extra pairs after M are lines). */
    fun parse(d: String): List<PathOp> {
        val ops = ArrayList<PathOp>()
        var cmd = ' '
        val nums = ArrayList<Float>(8)
        fun flush() {
            when (cmd) {
                'M' -> {
                    require(nums.size % 2 == 0) { "M needs pairs: $d" }
                    var i = 0
                    while (i < nums.size) {
                        ops += if (i == 0) PathOp.Move(nums[0], nums[1]) else PathOp.Line(nums[i], nums[i + 1])
                        i += 2
                    }
                }
                'L' -> {
                    require(nums.size % 2 == 0) { "L needs pairs: $d" }
                    var i = 0
                    while (i < nums.size) { ops += PathOp.Line(nums[i], nums[i + 1]); i += 2 }
                }
                'C' -> {
                    require(nums.size % 6 == 0) { "C needs 6 numbers: $d" }
                    var i = 0
                    while (i < nums.size) {
                        ops += PathOp.Cubic(nums[i], nums[i + 1], nums[i + 2], nums[i + 3], nums[i + 4], nums[i + 5])
                        i += 6
                    }
                }
                'Z' -> ops += PathOp.Close
            }
            nums.clear()
        }
        for (m in token.findAll(d)) {
            val v = m.value
            val c = v[0]
            if (c.isLetter()) {
                flush()
                require(c.isUpperCase()) { "Only absolute commands are supported: $c" }
                cmd = c
                if (cmd == 'Z') flush()
            } else {
                nums += v.toFloat()
            }
        }
        flush()
        return ops
    }

    /**
     * Polylines (x0, y0, x1, y1, …) per subpath. Curves are sampled [curveSteps] times; with
     * [maxStep] > 0 straight runs are subdivided too (needed before applying pencil wobble).
     */
    fun flatten(ops: List<PathOp>, curveSteps: Int = 8, maxStep: Float = 0f): List<FloatArray> {
        val out = ArrayList<FloatArray>()
        var cur = FloatList()
        var x = 0f
        var y = 0f
        var sx = 0f
        var sy = 0f
        fun lineTo(nx: Float, ny: Float) {
            if (maxStep > 0f) {
                val n = max(1, ceil(hypot(nx - x, ny - y) / maxStep).toInt())
                for (i in 1..n) cur.add(x + (nx - x) * i / n, y + (ny - y) * i / n)
            } else {
                cur.add(nx, ny)
            }
            x = nx; y = ny
        }
        for (op in ops) when (op) {
            is PathOp.Move -> {
                if (cur.size >= 4) out += cur.toArray()
                cur = FloatList()
                x = op.x; y = op.y; sx = x; sy = y
                cur.add(x, y)
            }
            is PathOp.Line -> lineTo(op.x, op.y)
            is PathOp.Cubic -> {
                val x0 = x
                val y0 = y
                val steps = if (maxStep > 0f) {
                    val approx = hypot(op.x1 - x0, op.y1 - y0) + hypot(op.x2 - op.x1, op.y2 - op.y1) + hypot(op.x - op.x2, op.y - op.y2)
                    max(curveSteps, ceil(approx / maxStep).toInt())
                } else {
                    curveSteps
                }
                for (i in 1..steps) {
                    val t = i.toFloat() / steps
                    val u = 1 - t
                    val px = u * u * u * x0 + 3 * u * u * t * op.x1 + 3 * u * t * t * op.x2 + t * t * t * op.x
                    val py = u * u * u * y0 + 3 * u * u * t * op.y1 + 3 * u * t * t * op.y2 + t * t * t * op.y
                    cur.add(px, py)
                }
                x = op.x; y = op.y
            }
            PathOp.Close -> {
                if (x != sx || y != sy) lineTo(sx, sy)
            }
        }
        if (cur.size >= 4) out += cur.toArray()
        return out
    }
}

/** Minimal growable float buffer (avoids boxing while flattening ~300 paths). */
internal class FloatList(capacity: Int = 64) {
    private var data = FloatArray(capacity)
    var size = 0
        private set

    fun add(a: Float, b: Float) {
        if (size + 2 > data.size) data = data.copyOf(max(data.size * 2, size + 2))
        data[size++] = a
        data[size++] = b
    }

    fun toArray(): FloatArray = data.copyOf(size)
}

/** One fillable piece of a region (a region has one piece per side, abs has eight). */
internal class RegionPiece(
    val muscle: Muscle,
    val ops: List<PathOp>,
    val polygons: List<FloatArray>,
) {
    val bounds: MapBounds = MapBounds.of(polygons)

    /** Even-odd point-in-polygon over all subpaths. */
    fun contains(x: Float, y: Float): Boolean {
        var inside = false
        for (p in polygons) {
            val n = p.size / 2
            var j = n - 1
            for (i in 0 until n) {
                val xi = p[2 * i]
                val yi = p[2 * i + 1]
                val xj = p[2 * j]
                val yj = p[2 * j + 1]
                if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
                j = i
            }
        }
        return inside
    }

    /** 0 inside, else the distance to the nearest edge (artwork units). */
    fun distanceTo(x: Float, y: Float): Float {
        if (contains(x, y)) return 0f
        var best = Float.MAX_VALUE
        for (p in polygons) {
            val n = p.size / 2
            for (i in 0 until n) {
                val j = (i + 1) % n
                best = min(best, segmentDistance(x, y, p[2 * i], p[2 * i + 1], p[2 * j], p[2 * j + 1]))
            }
        }
        return best
    }
}

private fun segmentDistance(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = bx - ax
    val dy = by - ay
    val len2 = dx * dx + dy * dy
    val t = if (len2 == 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0f, 1f)
    return hypot(px - (ax + t * dx), py - (ay + t * dy))
}

/** One figure (front or back): its region pieces in draw order and its visible window. */
internal class FigureGeometry(val art: BodyMapArtView, val pieces: List<RegionPiece>, val window: MapBounds) {
    val muscles: Set<Muscle> = pieces.mapTo(LinkedHashSet()) { it.muscle }
}

internal object BodyMapGeometry {
    /** Space between the front and back figure in the "both" view, artwork units. */
    const val FIGURE_GAP = 14f
    private const val WINDOW_MARGIN = 3f

    val front: FigureGeometry by lazy { build(BodyMapArt.front) }
    val back: FigureGeometry by lazy { build(BodyMapArt.back) }

    private fun build(art: BodyMapArtView): FigureGeometry {
        val pieces = art.regions.map { r ->
            val ops = SvgPath.parse(r.d)
            RegionPiece(r.muscle, ops, SvgPath.flatten(ops))
        }
        val outline = (art.silhouette + art.partsUnder + art.partsOver)
            .map { MapBounds.of(SvgPath.flatten(SvgPath.parse(it))) }
            .reduce(MapBounds::union)
        val window = pieces.fold(outline) { acc, p -> acc.union(p.bounds) }.inflate(WINDOW_MARGIN)
        return FigureGeometry(art, pieces, window)
    }

    fun figures(view: BodyMapView): List<FigureGeometry> = when (view) {
        BodyMapView.FRONT -> listOf(front)
        BodyMapView.BACK -> listOf(back)
        BodyMapView.BOTH -> listOf(front, back)
    }

    /** Width ÷ height of the drawing, so callers can size the map without distortion. */
    fun aspectRatio(view: BodyMapView): Float {
        val figs = figures(view)
        val top = figs.minOf { it.window.top }
        val bottom = figs.maxOf { it.window.bottom }
        val width = figs.sumOf { it.window.width.toDouble() }.toFloat() + FIGURE_GAP * (figs.size - 1)
        return width / (bottom - top)
    }
}

/**
 * Where each figure lands in a canvas of [width] × [height] px: one uniform [scale], centred.
 * screen = model × scale + (dx[i], dy).
 */
internal class BodyMapLayout(val view: BodyMapView, val width: Float, val height: Float) {
    val figures: List<FigureGeometry> = BodyMapGeometry.figures(view)
    val scale: Float
    val dx: FloatArray
    val dy: Float

    init {
        val top = figures.minOf { it.window.top }
        val bottom = figures.maxOf { it.window.bottom }
        val contentW = figures.sumOf { it.window.width.toDouble() }.toFloat() + BodyMapGeometry.FIGURE_GAP * (figures.size - 1)
        val contentH = bottom - top
        scale = max(1e-3f, min(width / contentW, height / contentH))
        val ox = (width - contentW * scale) / 2f
        val oy = (height - contentH * scale) / 2f
        dy = oy - top * scale
        dx = FloatArray(figures.size)
        var start = 0f
        figures.forEachIndexed { i, f ->
            dx[i] = ox + (start - f.window.left) * scale
            start += f.window.width + BodyMapGeometry.FIGURE_GAP
        }
    }

    /**
     * The region under a tap at ([x], [y]) px, or null.
     *
     * - A tap inside a region picks it (the topmost piece wins).
     * - Small regions ([BODY_MAP_SMALL_REGIONS]) are enlarged invisibly by [smallBonusPx] on every
     *   side and win against a bigger neighbour inside that band.
     * - A tap beside the body snaps to the nearest region within [slopPx], so every region has at
     *   least a [slopPx] × 2 target.
     */
    fun hitTest(x: Float, y: Float, slopPx: Float, smallBonusPx: Float): Muscle? {
        var best: Muscle? = null
        var bestScore = Float.MAX_VALUE
        figures.forEachIndexed { i, fig ->
            val mx = (x - dx[i]) / scale
            val my = (y - dy) / scale
            val reach = (max(slopPx, smallBonusPx) / scale)
            if (mx < fig.window.left - reach || mx > fig.window.right + reach) return@forEachIndexed
            for (k in fig.pieces.indices.reversed()) { // topmost first; strict < keeps it on ties
                val piece = fig.pieces[k]
                val b = piece.bounds
                if (mx < b.left - reach || mx > b.right + reach || my < b.top - reach || my > b.bottom + reach) continue
                val distPx = piece.distanceTo(mx, my) * scale
                val score = if (piece.muscle in BODY_MAP_SMALL_REGIONS) distPx - smallBonusPx else distPx
                val limit = if (piece.muscle in BODY_MAP_SMALL_REGIONS) 0f else slopPx
                if (score <= limit && score < bestScore) {
                    bestScore = score
                    best = piece.muscle
                }
            }
        }
        return best
    }
}

/**
 * One family of parallel hatch lines in a fixed pattern space (like an SVG pattern with
 * userSpaceOnUse), so neighbouring regions line up: line k sits at k × [spacing] + [phase] along
 * the normal of a line rotated [angleDegrees] from vertical.
 */
internal data class HatchSpec(val spacing: Float, val phase: Float, val angleDegrees: Float)

internal object Hatching {
    /**
     * The hatch lines of [spec] clipped to [polygons] (even-odd), as segments x0, y0, x1, y1.
     * Clipping is done here once, so drawing needs no clipPath.
     */
    fun clip(polygons: List<FloatArray>, bounds: MapBounds, spec: HatchSpec): FloatArray {
        val a = Math.toRadians(spec.angleDegrees.toDouble())
        val nx = cos(a).toFloat()
        val ny = sin(a).toFloat()
        val dirX = -ny
        val dirY = nx
        // Projections of the bounds corners onto the normal give the range of lines to try.
        val corners = floatArrayOf(bounds.left, bounds.top, bounds.right, bounds.top, bounds.left, bounds.bottom, bounds.right, bounds.bottom)
        var lo = Float.MAX_VALUE
        var hi = -Float.MAX_VALUE
        var i = 0
        while (i < 8) {
            val p = corners[i] * nx + corners[i + 1] * ny
            lo = min(lo, p); hi = max(hi, p)
            i += 2
        }
        val out = FloatList(256)
        val ts = ArrayList<Float>(16)
        var k = ceil((lo - spec.phase) / spec.spacing).toInt()
        val kEnd = floor((hi - spec.phase) / spec.spacing).toInt()
        while (k <= kEnd) {
            val off = k * spec.spacing + spec.phase
            // Line: P(t) = off·n + t·dir. Intersect with every edge.
            val ox = off * nx
            val oy = off * ny
            ts.clear()
            for (p in polygons) {
                val n = p.size / 2
                for (e in 0 until n) {
                    val f = (e + 1) % n
                    val ax = p[2 * e]
                    val ay = p[2 * e + 1]
                    val bx = p[2 * f]
                    val by = p[2 * f + 1]
                    val da = ax * nx + ay * ny - off
                    val db = bx * nx + by * ny - off
                    if ((da > 0f) == (db > 0f)) continue
                    val s = da / (da - db)
                    val ix = ax + (bx - ax) * s
                    val iy = ay + (by - ay) * s
                    ts += (ix - ox) * dirX + (iy - oy) * dirY
                }
            }
            if (ts.size >= 2) {
                ts.sort()
                var j = 0
                while (j + 1 < ts.size) {
                    val t0 = ts[j]
                    val t1 = ts[j + 1]
                    if (t1 - t0 > 0.15f) {
                        out.add(ox + dirX * t0, oy + dirY * t0)
                        out.add(ox + dirX * t1, oy + dirY * t1)
                    }
                    j += 2
                }
            }
            k++
        }
        return out.toArray()
    }
}

/**
 * Pencil wobble for the Notebook skin: a smooth displacement field that depends only on the
 * position, like the artwork's feDisplacementMap. Shared borders of neighbouring regions move
 * together, so pieces still meet; the same input always gives the same drawing (no "boiling").
 */
internal object PencilWobble {
    fun dx(x: Float, y: Float, amp: Float): Float =
        amp * (0.6f * sin(x * 0.071f + y * 0.043f + 1.3f) + 0.4f * sin(x * 0.023f - y * 0.117f + 4.1f))

    fun dy(x: Float, y: Float, amp: Float): Float =
        amp * (0.6f * sin(x * 0.052f - y * 0.067f + 2.7f) + 0.4f * sin(-x * 0.109f + y * 0.029f + 0.4f))

    /** Displaces every point of [poly] in place-free fashion (returns a new array). */
    fun apply(poly: FloatArray, amp: Float): FloatArray {
        val out = FloatArray(poly.size)
        var i = 0
        while (i < poly.size) {
            val x = poly[i]
            val y = poly[i + 1]
            out[i] = x + dx(x, y, amp)
            out[i + 1] = y + dy(x, y, amp)
            i += 2
        }
        return out
    }

    /** A straight segment as a wobbly polyline, subdivided every [step] units. */
    fun segment(x0: Float, y0: Float, x1: Float, y1: Float, amp: Float, step: Float): FloatArray {
        val n = max(1, ceil(hypot(x1 - x0, y1 - y0) / step).toInt())
        val out = FloatArray((n + 1) * 2)
        for (i in 0..n) {
            val x = x0 + (x1 - x0) * i / n
            val y = y0 + (y1 - y0) * i / n
            out[2 * i] = x + dx(x, y, amp)
            out[2 * i + 1] = y + dy(x, y, amp)
        }
        return out
    }
}

/** Signed area of a polygon (artwork units²), for tests and size checks. */
internal fun polygonArea(p: FloatArray): Float {
    var s = 0f
    val n = p.size / 2
    for (i in 0 until n) {
        val j = (i + 1) % n
        s += p[2 * i] * p[2 * j + 1] - p[2 * j] * p[2 * i + 1]
    }
    return abs(s) / 2f
}

/** Smallest side of the bounding box of the largest piece of [muscle], in artwork units. */
internal fun largestPieceMinSide(muscle: Muscle): Float {
    val pieces = (BodyMapGeometry.front.pieces + BodyMapGeometry.back.pieces).filter { it.muscle == muscle }
    val largest = pieces.maxByOrNull { p -> p.polygons.sumOf { polygonArea(it).toDouble() } } ?: return 0f
    return min(largest.bounds.width, largest.bounds.height)
}
