package com.example.gymtrack.feature.stats.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtrack.core.data.ExerciseWithCount
import com.example.gymtrack.core.data.GraphPoint
import com.example.gymtrack.core.data.WorkoutRepository
import com.example.gymtrack.feature.stats.AdaptiveCard
import com.example.gymtrack.feature.stats.TimeRange
import kotlinx.coroutines.flow.flowOf
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

private fun calculateOutliers(data: List<GraphPoint>): List<GraphPoint> {
    if (data.size < 5) return emptyList()
    val values = data.map { it.avgVal }.sorted()
    val q1 = values[values.size / 4]
    val q3 = values[values.size * 3 / 4]
    val iqr = q3 - q1
    val lower = q1 - 1.5f * iqr
    val upper = q3 + 1.5f * iqr
    return data.filter { it.avgVal < lower || it.avgVal > upper }
}

@Composable
private fun ExerciseProgressChartGraph(
    dataPoints: List<GraphPoint>,
    anomalies: List<GraphPoint>,
    modifier: Modifier = Modifier
) {
    if (dataPoints.isEmpty()) {
        Box(modifier.height(200.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                "No data for this period",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )
        }
        return
    }

    val yValues = dataPoints.map { it.avgVal }
    val scaleY = buildScaleY(yValues)

    val minTime = dataPoints.minOf { it.originTimestamp }
    val maxTime = dataPoints.maxOf { it.originTimestamp }
    val timeRange = (maxTime - minTime).coerceAtLeast(1L)

    val theme = rememberChartTheme()
    val dateFormatter = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    val density = LocalDensity.current
    val textPaint = makeTextPaint(theme.label, with(density) { 10.sp.toPx() })

    Column(modifier.fillMaxWidth()) {
        Canvas(modifier = modifier) {
            val layout = ChartLayout()
            val chartW = layout.width(size.width)
            val x0 = layout.originX()
            val y0 = layout.originY(size.height)

            drawYGridAndLabels(scaleY, theme, layout, textPaint)

            val points = dataPoints.map { point ->
                val fractionX = (point.originTimestamp - minTime) / timeRange.toFloat()
                val x = x0 + fractionX * chartW
                val y = scaleY.yToPx(point.avgVal, y0, layout.topPad)
                x to y
            }

            val linePath = createSmoothPath(points)
            val fillPath = createFillPath(linePath, size.width, y0)

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(theme.primary.copy(alpha = 0.28f), Color.Transparent),
                    startY = layout.topPad,
                    endY = y0
                )
            )

            drawPath(
                path = linePath,
                color = theme.primary,
                style = Stroke(width = 5f)
            )

            val anomalySet = anomalies.map { it.originTimestamp }.toSet()
            points.zip(dataPoints).forEach { (coord, raw) ->
                if (raw.originTimestamp in anomalySet) {
                    drawCircle(MaterialTheme.colorScheme.error, radius = 8f, center = Offset(coord.first, coord.second))
                    drawCircle(MaterialTheme.colorScheme.surface, radius = 4f, center = Offset(coord.first, coord.second))
                }
            }

            drawXTickLabel(dateFormatter.format(Date(minTime)), x0, y0, textPaint)
            drawXTickLabel(dateFormatter.format(Date(maxTime)), x0 + chartW, y0, textPaint)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseProgressCard(
    repository: WorkoutRepository?,
    timeRange: TimeRange,
    modifier: Modifier = Modifier,
) {
    val cutoffTimestamp = remember(timeRange) {
        if (timeRange == TimeRange.ALL_TIME) {
            0L
        } else {
            System.currentTimeMillis() - TimeUnit.DAYS.toMillis(timeRange.days.toLong())
        }
    }

    val allExercises by (repository?.getExercisesSortedByCount(cutoffTimestamp)
        ?: remember { flowOf(emptyList()) })
        .collectAsState(initial = emptyList())

    var selectedExercise by remember { mutableStateOf<ExerciseWithCount?>(null) }
    var expandedDropdown by remember { mutableStateOf(false) }

    LaunchedEffect(allExercises) {
        val currentInList = allExercises.find { it.exerciseId == selectedExercise?.exerciseId }
        if (currentInList != null) {
            selectedExercise = currentInList
        } else if (allExercises.isNotEmpty()) {
            selectedExercise = allExercises.first()
        } else {
            selectedExercise = null
        }
    }

    val fullGraphData by remember(selectedExercise) {
        selectedExercise?.let { repository?.getWeightHistory(it.exerciseId) }
            ?: flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val filteredGraphData = remember(fullGraphData, cutoffTimestamp) {
        if (cutoffTimestamp == 0L) fullGraphData
        else fullGraphData.filter { it.originTimestamp >= cutoffTimestamp }
    }

    val anomalies = remember(filteredGraphData) { calculateOutliers(filteredGraphData) }

    AdaptiveCard(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Exercise Progress",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(12.dp))

            ExposedDropdownMenuBox(
                expanded = expandedDropdown,
                onExpandedChange = { expandedDropdown = !expandedDropdown }
            ) {
                OutlinedTextField(
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    readOnly = true,
                    value = selectedExercise?.let { "${it.name} (${it.setTotalCount} sets)" } ?: "No exercises found",
                    onValueChange = {},
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedDropdown) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f),
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTrailingIconColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                )
                DropdownMenu(
                    expanded = expandedDropdown,
                    onDismissRequest = { expandedDropdown = false }
                ) {
                    allExercises.forEach { exercise ->
                        DropdownMenuItem(
                            text = { Text("${exercise.name} (${exercise.setTotalCount} sets)") },
                            onClick = {
                                selectedExercise = exercise
                                expandedDropdown = false
                            }
                        )
                    }
                }
            }

            if (anomalies.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "⚠ ${anomalies.size} Anomalies Detected",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(16.dp))

            ExerciseProgressChartGraph(
                dataPoints = filteredGraphData,
                anomalies = anomalies,
                modifier = Modifier.fillMaxWidth().height(200.dp)
            )
        }
    }
}
