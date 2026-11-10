package com.example.gymtrack.core.ui.theme

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * One pencil stroke: points plus a pressure value per point (1 = nominal stroke weight).
 * Plain arrays, no Android or Compose types, so the geometry is unit-testable.
 */
class SketchStroke(val xs: FloatArray, val ys: FloatArray, val pressure: FloatArray) {
    val size: Int get() = xs.size
}

/**
 * Hand-drawn outlines for the Notebook theme (README rule 4): seeded irregular corner radii,
 * a slow wobble along the outline, varying pressure and, for open strokes, an overshoot where
 * the pencil passes its starting point. The same seed always gives the same drawing, so an
 * element never "boils" between frames; callers cache the result per size.
 */
object SketchGeometry {

    /**
     * Outline of a sketched rounded rectangle inside [width] × [height].
     *
     * @param seed which drawing; elements pass different seeds so they don't look identical.
     * @param pass 0 = the main stroke (also the clip/fill outline), 1+ = re-traces. All passes
     *   share the corner radii and differ in wobble, start point and pressure.
     * @param closed true for a fill/clip outline (no overshoot, ends where it started).
     * @param overshoot extra length past the start, as a fraction of the perimeter (open only).
     * @param inset distance the outline is pulled in from the bounds, so wobble and stroke
     *   width stay inside the element.
     */
    fun roundedRect(
        width: Float,
        height: Float,
        seed: Int,
        pass: Int = 0,
        cornerRadius: Float,
        wobble: Float,
        step: Float,
        closed: Boolean = false,
        overshoot: Float = 0.05f,
        inset: Float = 0f,
    ): SketchStroke {
        val w = max(width - 2 * inset, 1f)
        val h = max(height - 2 * inset, 1f)
        val dense = denseRoundedRect(w, h, seed, cornerRadius)
        val perimeter = dense.length
        val rnd = Random(seed * 7919 + pass * 104729 + 17)

        // Integer frequencies keep the wobble periodic, so closed outlines meet seamlessly.
        val f1 = 2 + rnd.nextInt(3)
        val f2 = 5 + rnd.nextInt(5)
        val f3 = 1 + rnd.nextInt(3)
        val p1 = rnd.nextFloat() * TWO_PI
        val p2 = rnd.nextFloat() * TWO_PI
        val p3 = rnd.nextFloat() * TWO_PI
        val start = if (closed) 0f else rnd.nextFloat() * perimeter
        val drift = (rnd.nextFloat() - 0.4f) * wobble
        val total = if (closed) perimeter else perimeter * (1f + overshoot)
        val count = max(12, (total / max(step, 0.5f)).toInt() + 1)

        val xs = FloatArray(count)
        val ys = FloatArray(count)
        val pressure = FloatArray(count)
        val point = FloatArray(4)
        for (i in 0 until count) {
            val travelled = total * i / (count - 1)
            val s = start + travelled
            dense.pointAt(s, point)
            val t = (s / perimeter) * TWO_PI
            var offset = wobble * (0.6f * sin(f1 * t + p1) + 0.4f * sin(f2 * t + p2))
            if (!closed && travelled > perimeter) {
                // The overshoot drifts off the line it is re-tracing, like a real pencil.
                offset += drift * (travelled - perimeter) / (total - perimeter)
            }
            // Never leave the element: a stroke outside its bounds would be clipped or overlap.
            xs[i] = (inset + point[0] + point[2] * offset).coerceIn(0f, width)
            ys[i] = (inset + point[1] + point[3] * offset).coerceIn(0f, height)

            var p = 0.85f + 0.3f * sin(f3 * t + p3)
            if (!closed) {
                val taper = 0.06f * total
                val fromEnd = min(travelled, total - travelled)
                if (fromEnd < taper) p *= 0.4f + 0.6f * (fromEnd / taper)
            }
            pressure[i] = p.coerceIn(0.3f, 1.25f)
        }
        return SketchStroke(xs, ys, pressure)
    }

    /** A wobbly straight-ish line from (x0, y0) to (x1, y1), e.g. dividers and hatching. */
    fun line(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        seed: Int,
        wobble: Float,
        step: Float,
    ): SketchStroke {
        val rnd = Random(seed * 31 + 7)
        val length = hypot(x1 - x0, y1 - y0)
        val count = max(3, (length / max(step, 0.5f)).toInt() + 1)
        val nx = if (length == 0f) 0f else -(y1 - y0) / length
        val ny = if (length == 0f) 0f else (x1 - x0) / length
        val bow = (rnd.nextFloat() * 2 - 1) * wobble
        val p = rnd.nextFloat() * TWO_PI
        val xs = FloatArray(count)
        val ys = FloatArray(count)
        val pressure = FloatArray(count)
        for (i in 0 until count) {
            val t = i / (count - 1f)
            val offset = bow * sin(PI.toFloat() * t) + wobble * 0.35f * sin(TWO_PI * 2 * t + p)
            xs[i] = x0 + (x1 - x0) * t + nx * offset
            ys[i] = y0 + (y1 - y0) * t + ny * offset
            pressure[i] = (0.55f + 0.45f * sin(PI.toFloat() * t) + 0.1f * sin(TWO_PI * 3 * t + p)).coerceIn(0.3f, 1.2f)
        }
        return SketchStroke(xs, ys, pressure)
    }

    /**
     * A hand-drawn closed polygon through ([px], [py]) (e.g. a triangle or diamond mark):
     * each side is a slightly bowed line and the pencil overshoots the first corner.
     */
    fun polygon(px: FloatArray, py: FloatArray, seed: Int, pass: Int, wobble: Float, step: Float): SketchStroke {
        val rnd = Random(seed * 613 + pass * 59 + 1)
        val xs = ArrayList<Float>()
        val ys = ArrayList<Float>()
        val ps = ArrayList<Float>()
        val n = px.size
        for (side in 0..n) { // n sides plus a short overshoot along the first side
            val a = side % n
            val b = (side + 1) % n
            val jx = (rnd.nextFloat() - 0.5f) * wobble
            val jy = (rnd.nextFloat() - 0.5f) * wobble
            val endT = if (side == n) 0.3f else 1f
            val segment = line(
                px[a] + jx, py[a] + jy,
                px[a] + (px[b] - px[a]) * endT, py[a] + (py[b] - py[a]) * endT,
                seed = seed * 7 + pass * 3 + side, wobble = wobble * 0.6f, step = step,
            )
            for (i in 0 until segment.size) {
                if (side > 0 && i == 0) continue
                xs += segment.xs[i]
                ys += segment.ys[i]
                ps += 0.8f + 0.25f * segment.pressure[i]
            }
        }
        return SketchStroke(xs.toFloatArray(), ys.toFloatArray(), ps.toFloatArray())
    }

    /** A hand-drawn ellipse with an overshoot (circles, dots, the "!" ring). */
    fun ellipse(
        cx: Float,
        cy: Float,
        rx: Float,
        ry: Float,
        seed: Int,
        pass: Int,
        wobble: Float,
        step: Float,
        overshoot: Float = 0.12f,
    ): SketchStroke {
        val rnd = Random(seed * 389 + pass * 97 + 2)
        val f1 = 2 + rnd.nextInt(2)
        val p1 = rnd.nextFloat() * TWO_PI
        val start = rnd.nextFloat() * TWO_PI
        val sweep = TWO_PI * (1f + overshoot)
        val circumference = TWO_PI * max(rx, ry)
        val count = max(16, (circumference * (1f + overshoot) / max(step, 0.5f)).toInt() + 1)
        val xs = FloatArray(count)
        val ys = FloatArray(count)
        val pressure = FloatArray(count)
        for (i in 0 until count) {
            val t = i / (count - 1f)
            val a = start + sweep * t
            // Radius grows a little over the stroke so the overshoot does not land on the start.
            val r = 1f + (wobble / max(rx, 1f)) * (0.7f * sin(f1 * a + p1) + 0.8f * t)
            xs[i] = cx + rx * r * cos(a)
            ys[i] = cy + ry * r * sin(a)
            pressure[i] = (0.6f + 0.5f * sin(PI.toFloat() * t)).coerceIn(0.3f, 1.2f)
        }
        return SketchStroke(xs, ys, pressure)
    }

    /** Corner radii for a seed, as [rxTL, ryTL, rxTR, ryTR, rxBR, ryBR, rxBL, ryBL]. */
    fun cornerRadii(width: Float, height: Float, seed: Int, cornerRadius: Float): FloatArray {
        val rnd = Random(seed * 2654435761L.toInt() + 3)
        val maxRx = width / 2f
        val maxRy = height / 2f
        return FloatArray(8) { i ->
            val r = cornerRadius * (0.55f + rnd.nextFloat() * 0.9f)
            if (i % 2 == 0) min(r, maxRx) else min(r, maxRy)
        }
    }

    private fun denseRoundedRect(w: Float, h: Float, seed: Int, cornerRadius: Float): DensePolyline {
        val r = cornerRadii(w, h, seed, cornerRadius)
        val (rxTL, ryTL, rxTR, ryTR) = listOf(r[0], r[1], r[2], r[3])
        val (rxBR, ryBR, rxBL, ryBL) = listOf(r[4], r[5], r[6], r[7])
        val out = DensePolyline()
        out.line(rxTL, 0f, w - rxTR, 0f)
        out.arc(w - rxTR, ryTR, rxTR, ryTR, -90f, 0f)
        out.line(w, ryTR, w, h - ryBR)
        out.arc(w - rxBR, h - ryBR, rxBR, ryBR, 0f, 90f)
        out.line(w - rxBR, h, rxBL, h)
        out.arc(rxBL, h - ryBL, rxBL, ryBL, 90f, 180f)
        out.line(0f, h - ryBL, 0f, ryTL)
        out.arc(rxTL, ryTL, rxTL, ryTL, 180f, 270f)
        out.finish()
        return out
    }

    private const val TWO_PI = (2 * PI).toFloat()
}

/** A closed clockwise polyline with arc-length lookup and outward normals. */
private class DensePolyline {
    private val xs = ArrayList<Float>(256)
    private val ys = ArrayList<Float>(256)
    private lateinit var cumulative: FloatArray
    var length: Float = 0f
        private set

    fun line(x0: Float, y0: Float, x1: Float, y1: Float) {
        val n = max(1, (hypot(x1 - x0, y1 - y0) / 2f).toInt())
        for (i in 0 until n) {
            val t = i / n.toFloat()
            add(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t)
        }
    }

    fun arc(cx: Float, cy: Float, rx: Float, ry: Float, fromDeg: Float, toDeg: Float) {
        val n = max(2, ((toDeg - fromDeg) / 6f).toInt())
        for (i in 0 until n) {
            val a = Math.toRadians((fromDeg + (toDeg - fromDeg) * i / n).toDouble())
            add(cx + rx * cos(a).toFloat(), cy + ry * sin(a).toFloat())
        }
    }

    private fun add(x: Float, y: Float) {
        if (xs.isNotEmpty() && xs.last() == x && ys.last() == y) return
        xs += x
        ys += y
    }

    fun finish() {
        cumulative = FloatArray(xs.size + 1)
        for (i in xs.indices) {
            val j = (i + 1) % xs.size
            cumulative[i + 1] = cumulative[i] + hypot(xs[j] - xs[i], ys[j] - ys[i])
        }
        length = max(cumulative.last(), 1e-3f)
    }

    /** Writes [x, y, normalX, normalY] at arc length [s] (wrapping) into [out]. */
    fun pointAt(s: Float, out: FloatArray) {
        val wrapped = ((s % length) + length) % length
        var lo = 0
        var hi = xs.size
        while (hi - lo > 1) {
            val mid = (lo + hi) ushr 1
            if (cumulative[mid] <= wrapped) lo = mid else hi = mid
        }
        val i = lo
        val j = (i + 1) % xs.size
        val segment = cumulative[i + 1] - cumulative[i]
        val t = if (segment == 0f) 0f else (wrapped - cumulative[i]) / segment
        val dx = xs[j] - xs[i]
        val dy = ys[j] - ys[i]
        val len = max(hypot(dx, dy), 1e-6f)
        out[0] = xs[i] + dx * t
        out[1] = ys[i] + dy * t
        // Clockwise in screen coordinates (y down): outward normal = (dy, -dx).
        out[2] = dy / len
        out[3] = -dx / len
    }
}
