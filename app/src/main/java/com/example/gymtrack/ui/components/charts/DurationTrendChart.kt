package com.example.gymtrack.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtrack.data.NoteLine
import com.example.gymtrack.util.parseDurationSeconds
import com.example.gymtrack.util.parseNoteText
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max

@Composable
fun DurationTrendChart(
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
        parseNoteText(n.text).second.mapNotNull {
            if (it.isBlank()) null else parseDurationSeconds(it)
        }.maxOrNull()?.div(60f)

    val weekly = notes.groupBy { isoWeekStart(it.timestamp) }.toSortedMap().map { (t, list) ->
        val mins = list.mapNotNull { noteMin(it) }
        t to (if (mins.isEmpty()) 0f else mins.average().toFloat())
    }
    if (weekly.isEmpty()) return

    val rolling = if (showRollingAvg) {
        weekly.indices.map { i ->
            val from = max(0, i - 3)
            val slice = weekly.subList(from, i + 1).map { it.second }
            weekly[i].first to (if (slice.isEmpty()) 0f else slice.average().toFloat())
        }
    } else emptyList()

    val theme = rememberChartTheme()
    val density = LocalDensity.current
    val yPaint = makeTextPaint(theme.label, with(density) { 11.sp.toPx() })
    val xPaint = makeTextPaint(theme.label, with(density) { 11.sp.toPx() })
    val axisPaint = makeTextPaint(theme.label, with(density) { 12.sp.toPx() })

    val allY = buildList {
        addAll(weekly.map { it.second })
        addAll(rolling.map { it.second })
    }
    val scaleY = buildScaleY(allY)

    val df = SimpleDateFormat("dd MMM", Locale.getDefault())

    Column(modifier.fillMaxWidth()) {
        Text("Average workout duration (weekly)", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
        ) {
            val layout = ChartLayout()
            val chartW = layout.width(size.width)
            val chartH = layout.height(size.height)
            val x0 = layout.originX()
            val y0 = layout.originY(size.height)

            drawAxes(::drawLine, theme, layout, size.width, size.height)
            drawYGridAndLabels(
                drawContext.canvas.nativeCanvas, ::drawLine, scaleY, theme, layout,
                size.width, size.height, yPaint
            )

            val n = weekly.size
            val dx = if (n <= 1) chartW else chartW / (n - 1).toFloat()
            fun xAt(i: Int) = x0 + dx * i
            fun yAt(v: Float) = scaleY.yToPx(v, chartH, layout.topPad)

            val lblEvery = max(1, n / 8)
            weekly.forEachIndexed { i, (t, _) ->
                if (i % lblEvery == 0 || i == n - 1) {
                    drawXTickLabel(drawContext.canvas.nativeCanvas, df.format(t), xAt(i), y0, xPaint)
                }
            }

            for (i in 1 until n) {
                drawLine(theme.primary, Offset(xAt(i - 1), yAt(weekly[i - 1].second)), Offset(xAt(i), yAt(weekly[i].second)), 3f)
            }
            weekly.forEachIndexed { i, pair ->
                drawCircle(theme.primary, radius = 4f, center = Offset(xAt(i), yAt(pair.second)))
            }

            if (showRollingAvg && rolling.isNotEmpty()) {
                for (i in 1 until rolling.size) {
                    drawLine(theme.secondary, Offset(xAt(i - 1), yAt(rolling[i - 1].second)), Offset(xAt(i), yAt(rolling[i].second)), 2f)
                }
            }

            drawContext.canvas.nativeCanvas.drawText("Week start", x0 + 6f, y0 + axisPaint.textSize + 14f, axisPaint)
        }
    }
}
