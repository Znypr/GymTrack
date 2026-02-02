package com.example.gymtrack.feature.stats.components.charts

import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.ceil

/* ---- Layout + scaling ---- */
data class ChartLayout(
    val leftPad: Float = 56f,
    val rightPad: Float = 12f,
    val topPad: Float = 8f,
    val bottomPad: Float = 34f,
) {
    fun width(totalW: Float) = totalW - leftPad - rightPad
    fun height(totalH: Float) = totalH - topPad - bottomPad
    fun originX() = leftPad
    fun originY(totalH: Float) = topPad + height(totalH) // bottom-left of plot area
}

data class ScaleY(val top: Float, val step: Int) {
    fun yToPx(v: Float, chartH: Float, topPad: Float): Float {
        if (top <= 0f) return topPad + chartH
        val t = (v / top).coerceIn(0f, 1f)
        return topPad + chartH * (1f - t)
    }
}

/* “Nice” steps for minutes and counts */
fun niceStepMinutes(maxVal: Float): Int = when {
    maxVal <= 20f  -> 5
    maxVal <= 60f  -> 10
    maxVal <= 120f -> 15
    maxVal <= 240f -> 30
    else           -> 60
}
fun niceStepCount(maxVal: Float): Int = when {
    maxVal <= 5f   -> 1
    maxVal <= 20f  -> 2
    maxVal <= 50f  -> 5
    else           -> 10
}

fun buildScaleY(values: List<Float>, minTop: Float = 1f, stepPicker: (Float)->Int = ::niceStepMinutes): ScaleY {
    val yMax = (values.maxOrNull() ?: 0f).coerceAtLeast(minTop)
    val step = stepPicker(yMax.toFloat())
    val top = (ceil(yMax / step).toInt().coerceAtLeast(1)) * step.toFloat()
    return ScaleY(top = top, step = step)
}

/* ---- Theme + drawing helpers ---- */
data class ChartTheme(
    val label: Color,
    val grid: Color,
    val axis: Color,
    val primary: Color,
    val secondary: Color,
)

@Composable
fun rememberChartTheme(): ChartTheme {
    val label = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
    return ChartTheme(
        label = label,
        grid = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        axis = label,
        primary = MaterialTheme.colorScheme.primary,
        secondary = MaterialTheme.colorScheme.secondary,
    )
}

fun makeTextPaint(color: Color, sizePx: Float) = Paint().apply {
    this.color = android.graphics.Color.argb(
        (color.alpha * 255).toInt(),
        (color.red * 255).toInt(),
        (color.green * 255).toInt(),
        (color.blue * 255).toInt()
    )
    textSize = sizePx
    isAntiAlias = true
}

fun drawAxes(
    drawLine: (Color, Offset, Offset, Float) -> Unit,
    theme: ChartTheme,
    layout: ChartLayout,
    totalW: Float,
    totalH: Float,
) {
    val x0 = layout.originX()
    val yTop = totalH - layout.height(totalH) - layout.topPad
    val y0 = layout.originY(totalH)
    drawLine(theme.axis, Offset(x0, yTop), Offset(x0, y0), 2f)
    drawLine(theme.axis, Offset(x0, y0), Offset(x0 + layout.width(totalW), y0), 2f)
}

fun drawYGridAndLabels(
    native: Canvas,
    drawLine: (Color, Offset, Offset, Float) -> Unit,
    scaleY: ScaleY,
    theme: ChartTheme,
    layout: ChartLayout,
    totalW: Float,
    totalH: Float,
    yPaint: Paint,
    yAxisTitle: String = "Minutes",
) {
    val chartW = layout.width(totalW)
    val chartH = layout.height(totalH)
    val topPad = layout.topPad
    val x0 = layout.originX()

    for (v in 0..scaleY.top.toInt() step scaleY.step) {
        val y = scaleY.yToPx(v.toFloat(), chartH, topPad)
        drawLine(theme.grid, Offset(x0, y), Offset(x0 + chartW, y), 1f)
        val txt = v.toString()
        native.drawText(
            txt,
            x0 - 8f - yPaint.measureText(txt),
            y + (yPaint.textSize / 2.8f),
            yPaint
        )
    }
    // Y-axis title
    native.save()
    native.rotate(-90f, 0f, 0f)
    native.drawText(
        yAxisTitle,
        -(layout.topPad + chartH / 2f + yPaint.measureText(yAxisTitle) / 2f),
        16f,
        yPaint
    )
    native.restore()
}

fun drawXTickLabel(
    native: Canvas,
    label: String,
    xCenter: Float,
    yBase: Float,
    paint: Paint
) {
    val w = paint.measureText(label)
    native.drawText(label, xCenter - w / 2f, yBase + paint.textSize + 6f, paint)
}
