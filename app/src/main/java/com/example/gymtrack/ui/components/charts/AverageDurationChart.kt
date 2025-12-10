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
import com.example.gymtrack.util.parseDurationSeconds
import com.example.gymtrack.util.parseNoteText

@Composable
fun AverageDurationChart(
    notes: List<NoteLine>,
    modifier: Modifier = Modifier
) {
    val averages: List<Pair<String, Float>> = notes
        .groupBy { (it.categoryName ?: "Other") }
        .map { (cat, list) ->
            val mins = list.mapNotNull { n ->
                parseNoteText(n.text).second.mapNotNull { s ->
                    if (s.isBlank()) null else parseDurationSeconds(s)
                }.maxOrNull()?.div(60f)
            }
            cat to if (mins.isEmpty()) 0f else mins.average().toFloat()
        }
        .sortedBy { it.first }

    if (averages.isEmpty()) return

    val theme = rememberChartTheme()
    val density = LocalDensity.current
    val yPaint = makeTextPaint(theme.label, with(density) { 11.sp.toPx() })
    val xPaint = makeTextPaint(theme.label, with(density) { 12.sp.toPx() })

    val yValues = averages.map { it.second }
    val scaleY = buildScaleY(yValues)

    Column(modifier.fillMaxWidth()) {
        Text("Avg workout length by category", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        ) {
            val layout = ChartLayout(leftPad = 56f, rightPad = 12f, topPad = 8f, bottomPad = 28f)
            val chartW = layout.width(size.width)
            val chartH = layout.height(size.height)
            val x0 = layout.originX()
            val y0 = layout.originY(size.height)

            drawAxes(::drawLine, theme, layout, size.width, size.height)
            drawYGridAndLabels(
                drawContext.canvas.nativeCanvas, ::drawLine, scaleY, theme, layout,
                size.width, size.height, yPaint
            )

            val n = averages.size
            val groupGap = chartW / (n * 2f)
            val barW = groupGap

            averages.forEachIndexed { idx, (cat, valueMin) ->
                val xLeft = x0 + groupGap * (1 + idx * 2)
                val barH = chartH * (valueMin / scaleY.top)
                drawRect(
                    color = Color.White.copy(alpha = 0.60f),
                    topLeft = Offset(xLeft, y0 - barH),
                    size = Size(barW, barH)
                )
                drawXTickLabel(drawContext.canvas.nativeCanvas, cat, xLeft + barW / 2f, y0, xPaint)
            }
        }
    }
}
