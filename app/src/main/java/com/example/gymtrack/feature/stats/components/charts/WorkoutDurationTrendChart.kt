package com.example.gymtrack.feature.stats.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.util.parseDurationSeconds
import com.example.gymtrack.core.util.parseNoteText
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun WorkoutDurationTrendChart(
    notes: List<NoteLine>,
    showRollingAvg: Boolean = true,
    modifier: Modifier = Modifier
) {
    fun isoWeekStart(ts: Long): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = ts
            firstDayOfWeek = Calendar.MONDAY
            minimalDaysInFirstWeek = 4
        }
        val dow = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
        c.add(Calendar.DAY_OF_YEAR, -dow)
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun noteMin(n: NoteLine): Float? =
        parseNoteText(n.text).second
            .mapNotNull { if (it.isBlank()) null else parseDurationSeconds(it) }
            .maxOrNull()?.div(60f)

    // Aggregate Data
    val weekly = notes
        .groupBy { isoWeekStart(it.timestamp) }
        .toSortedMap()
        .map { (t, list) ->
            val mins = list.mapNotNull { noteMin(it) }
            t to (if (mins.isEmpty()) 0f else mins.average().toFloat())
        }

    if (weekly.isEmpty()) {
        Box(modifier.fillMaxWidth().height(200.dp)) {
            Text("No data", color = Color.Gray)
        }
        return
    }

    val scaleY = buildScaleY(weekly.map { it.second })
    val theme = rememberChartTheme()
    val density = LocalDensity.current
    val textPaint = makeTextPaint(theme.label, with(density) { 10.sp.toPx() })
    val df = SimpleDateFormat("dd MMM", Locale.getDefault())

    Column(modifier.fillMaxWidth()) {
        Text("Workout Duration (Weekly)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(16.dp))

        Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
            val layout = ChartLayout()
            val chartW = layout.width(size.width)
            val chartH = layout.height(size.height)
            val x0 = layout.originX()
            val y0 = layout.originY(size.height)

            // Draw Grid
            drawYGridAndLabels(scaleY, theme, layout, textPaint)

            val n = weekly.size
            val dx = if (n <= 1) chartW else chartW / (n - 1).toFloat()

            // Calculate Points
            val points = weekly.mapIndexed { i, pair ->
                val x = x0 + dx * i
                val y = scaleY.yToPx(pair.second, y0, layout.topPad)
                x to y
            }

            // Draw Fill (Gradient)
            val linePath = createSmoothPath(points)
            val fillPath = createFillPath(linePath, size.width, y0)

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(theme.primary.copy(alpha = 0.3f), Color.Transparent),
                    startY = layout.topPad,
                    endY = y0
                )
            )

            // Draw Line
            drawPath(
                path = linePath,
                color = theme.primary,
                style = Stroke(width = 6f)
            )

            // Draw X Labels (First and Last)
            drawXTickLabel(df.format(weekly.first().first), x0, y0, textPaint)
            if (n > 1) {
                drawXTickLabel(df.format(weekly.last().first), x0 + chartW, y0, textPaint)
            }
        }
    }
}