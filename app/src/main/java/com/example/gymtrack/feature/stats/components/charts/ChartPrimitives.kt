package com.example.gymtrack.feature.stats.components.charts

import android.graphics.Paint
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.ceil

// --- THEME ---
val SpotifyGreen = Color(0xFF1DB954)

data class ChartTheme(
    val label: Color,
    val grid: Color,
    val axis: Color,
    val primary: Color,
    val background: Color
)

@Composable
fun rememberChartTheme(): ChartTheme {
    val isDark = isSystemInDarkTheme()
    val colorScheme = MaterialTheme.colorScheme

    // Use deep dark background in dark mode, surface in light mode
    val bg = if (isDark) Color(0xFF181818) else colorScheme.surface

    return remember(isDark, colorScheme) {
        ChartTheme(
            label = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f),
            grid = if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f),
            axis = Color.Transparent,
            primary = SpotifyGreen,
            background = bg
        )
    }
}

// --- LAYOUT & SCALING ---

data class ChartLayout(
    val leftPad: Float = 60f,
    val rightPad: Float = 16f,
    val topPad: Float = 16f,
    val bottomPad: Float = 32f
) {
    fun width(totalW: Float) = totalW - leftPad - rightPad
    fun height(totalH: Float) = totalH
    fun originX() = leftPad
    fun originY(totalH: Float) = totalH - bottomPad
}

data class ScaleY(val min: Float, val max: Float, val range: Float, val top: Float) {
    fun yToPx(value: Float, height: Float, topPad: Float): Float {
        if (top == 0f) return height
        val fraction = value / top
        return height - (fraction * (height - topPad))
    }
}

fun buildScaleY(values: List<Float>): ScaleY {
    if (values.isEmpty()) return ScaleY(0f, 10f, 10f, 10f)
    val maxVal = values.maxOrNull() ?: 0f
    val top = if (maxVal == 0f) 10f else ceil(maxVal * 1.2f / 5f) * 5f
    return ScaleY(0f, top, top, top)
}

fun makeTextPaint(color: Color, textSizePx: Float): Paint {
    return Paint().apply {
        this.color = color.toArgb()
        this.textSize = textSizePx
        this.isAntiAlias = true
    }
}

// --- DRAWING PRIMITIVES (Fixed as DrawScope Extensions) ---

// Fixes "drawAxes" or "drawYGridAndLabels" unresolved errors
fun DrawScope.drawYGridAndLabels(
    scale: ScaleY,
    theme: ChartTheme,
    layout: ChartLayout,
    textPaint: Paint
) {
    val steps = 5
    val stepVal = scale.top / steps
    val xStart = layout.originX()
    val xEnd = size.width - layout.rightPad
    val yOrigin = layout.originY(size.height)

    for (i in 0..steps) {
        val value = stepVal * i
        val y = scale.yToPx(value, yOrigin, layout.topPad)

        // Draw Grid Line (Compose)
        drawLine(
            color = theme.grid,
            start = Offset(xStart, y),
            end = Offset(xEnd, y),
            strokeWidth = 1f
        )

        // Draw Label (Native)
        val label = if (value % 1.0f == 0f) value.toInt().toString() else String.format("%.1f", value)
        val p = textPaint
        val w = p.measureText(label)
        // Draw text slightly to the left of the Y-axis
        drawContext.canvas.nativeCanvas.drawText(label, xStart - w - 12f, y + (p.textSize / 3), p)
    }
}

// Fixes "drawXTickLabel" unresolved error
fun DrawScope.drawXTickLabel(
    label: String,
    x: Float,
    yOrigin: Float,
    textPaint: Paint
) {
    val p = textPaint
    val w = p.measureText(label)
    // Draw text centered at X, below Y-axis
    drawContext.canvas.nativeCanvas.drawText(label, x - (w / 2), yOrigin + p.textSize + 12f, p)
}

// --- PATH HELPERS ---

fun createSmoothPath(points: List<Pair<Float, Float>>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points.first().first, points.first().second)
    for (i in 0 until points.size - 1) {
        val p0 = points[i]
        val p1 = points[i + 1]
        val controlX1 = (p0.first + p1.first) / 2f
        val controlY1 = p0.second
        val controlX2 = (p0.first + p1.first) / 2f
        val controlY2 = p1.second
        path.cubicTo(controlX1, controlY1, controlX2, controlY2, p1.first, p1.second)
    }
    return path
}

fun createFillPath(linePath: Path, width: Float, height: Float): Path {
    val fillPath = Path()
    fillPath.addPath(linePath)
    fillPath.lineTo(width, height)
    fillPath.lineTo(0f, height)
    fillPath.close()
    return fillPath
}