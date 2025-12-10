package com.example.gymtrack.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtrack.data.NoteLine
import java.util.Calendar
import androidx.compose.ui.graphics.lerp


@Composable
fun TimeOfDayHeatmap(
    data: Array<IntArray>,
    modifier: Modifier = Modifier
) {
    val counts = Array(7) { IntArray(24) }
    val maxCount = data.maxOf { it.maxOrNull() ?: 0 }.coerceAtLeast(1)

    val theme = rememberChartTheme()
    val density = LocalDensity.current
    val labelPaint = makeTextPaint(theme.label, with(density) { 11.sp.toPx() })


    // Read theme colors here (composable context) …
    val base = MaterialTheme.colorScheme.surfaceVariant
    val accent = MaterialTheme.colorScheme.primary

    // …and use a NON-composable helper (or inline) inside Canvas
    fun cellColor(v: Int, from: Color, to: Color): Color {
        val t = (v.toFloat() / maxCount).coerceIn(0f, 1f)
        return lerp(from, to, t)
    }

    val dayNames = listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun")

    Column(modifier.fillMaxWidth()) {
        Text("Training time heatmap", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
        ) {
            val layout = ChartLayout(leftPad = 44f, rightPad = 12f, topPad = 12f, bottomPad = 24f)
            val chartW = layout.width(size.width)
            val chartH = layout.height(size.height)
            val x0 = layout.originX()
            val yTop = size.height - chartH - layout.bottomPad

            val cellW = chartW / 24f
            val cellH = chartH / 7f

            for (d in 0 until 7) {
                for (h in 0 until 24) {
                    val x = x0 + h * cellW
                    val y = yTop + d * cellH
                    drawRect(
                        color = cellColor(counts[d][h], base, accent),
                        topLeft = Offset(x, y),
                        size = Size(cellW - 1f, cellH - 1f)
                    )
                }
            }

            // Hour labels
            for (h in 0 until 24 step 2) {
                val lbl = h.toString()
                val cx = x0 + h * cellW + cellW / 2f
                drawXTickLabel(drawContext.canvas.nativeCanvas, lbl, cx, yTop + chartH, labelPaint)
            }

            // Day labels
            for (d in 0 until 7) {
                val lbl = dayNames[d]
                val cy = yTop + d * cellH + cellH / 2f + labelPaint.textSize / 2.8f
                drawContext.canvas.nativeCanvas.drawText(
                    lbl, x0 - 8f - labelPaint.measureText(lbl), cy, labelPaint
                )
            }
        }
    }
}
