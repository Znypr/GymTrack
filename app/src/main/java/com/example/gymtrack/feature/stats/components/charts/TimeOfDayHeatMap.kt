package com.example.gymtrack.feature.stats.components.charts

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TimeOfDayHeatmap(
    data: Array<IntArray>,
    modifier: Modifier = Modifier
) {
    val theme = rememberChartTheme()
    val density = LocalDensity.current
    val labelPaint = makeTextPaint(theme.label, with(density) { 10.sp.toPx() })
    val maxCount = data.maxOf { it.maxOrNull() ?: 0 }.coerceAtLeast(1)

    fun getCellColor(value: Int): Color {
        if (value == 0) return Color(0xFF1E1E1E) // Empty cell
        val fraction = value.toFloat() / maxCount
        val alpha = when {
            fraction < 0.25f -> 0.2f
            fraction < 0.5f -> 0.4f
            fraction < 0.75f -> 0.7f
            else -> 1.0f
        }
        return SpotifyGreen.copy(alpha = alpha)
    }

    val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    Column(modifier.fillMaxWidth()) {
        Text("Training Time", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(12.dp))

        Canvas(modifier = Modifier.fillMaxWidth().height(240.dp)) {
            val layout = ChartLayout(leftPad = 60f, bottomPad = 40f)
            val chartW = layout.width(size.width)
            val chartH = layout.height(size.height)
            val x0 = layout.originX()
            val y0 = layout.topPad

            val cellW = chartW / 24f
            val cellH = chartH / 7f
            val spacing = 2f

            // Cells
            for (d in 0 until 7) {
                for (h in 0 until 24) {
                    val count = data[d][h]
                    val x = x0 + h * cellW
                    val y = y0 + d * cellH

                    drawRect(
                        color = getCellColor(count),
                        topLeft = Offset(x, y),
                        size = Size(cellW - spacing, cellH - spacing)
                    )
                }
            }

            // Labels
            for (d in 0 until 7) {
                val y = y0 + d * cellH + cellH / 1.5f
                drawContext.canvas.nativeCanvas.drawText(dayNames[d], 0f, y, labelPaint)
            }

            for (h in 0..24 step 6) {
                val x = x0 + h * cellW
                // Using DrawScope extension manually here if needed, or raw text
                drawContext.canvas.nativeCanvas.drawText("${h}h", x, y0 + chartH + 30f, labelPaint)
            }
        }
    }
}