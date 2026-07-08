package com.example.gymtrack.feature.stats.components.charts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.gymtrack.core.data.ExerciseGroupWithCount
import com.example.gymtrack.core.data.ExerciseProgressSeries
import com.example.gymtrack.core.data.ExerciseProgressVariant
import com.example.gymtrack.core.data.GraphPoint
import com.example.gymtrack.core.data.WorkoutRepository
import com.example.gymtrack.feature.stats.AdaptiveCard
import com.example.gymtrack.feature.stats.TimeRange
import com.example.gymtrack.feature.stats.calculateExerciseOutliers
import kotlinx.coroutines.flow.flowOf
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@Composable
private fun ExerciseProgressChartGraph(
    series: List<ExerciseProgressSeries>,
    focusedVariantKey: String?,
    anomalies: List<GraphPoint>,
    modifier: Modifier = Modifier,
) {
    val allPoints = series.flatMap { it.points }
    if (allPoints.isEmpty()) {
        Box(modifier.height(200.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                "No data for this period",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            )
        }
        return
    }

    val yValues = allPoints.map { it.avgVal }
    val scaleY = buildScaleY(yValues)

    val minTime = allPoints.minOf { it.originTimestamp }
    val maxTime = allPoints.maxOf { it.originTimestamp }
    val timeRange = (maxTime - minTime).coerceAtLeast(1L)

    val theme = rememberChartTheme()
    val colorScheme = MaterialTheme.colorScheme
    val seriesColors = listOf(
        colorScheme.primary,
        colorScheme.tertiary,
        colorScheme.secondary,
        colorScheme.error,
        colorScheme.onSurfaceVariant,
    )
    val dateFormatter = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    val density = LocalDensity.current
    val textPaint = makeTextPaint(theme.label, with(density) { 10.sp.toPx() })
    val anomalyColor = MaterialTheme.colorScheme.error
    val anomalyInnerColor = MaterialTheme.colorScheme.surface

    Column(modifier.fillMaxWidth()) {
        Canvas(modifier = modifier) {
            val layout = ChartLayout()
            val chartW = layout.width(size.width)
            val x0 = layout.originX()
            val y0 = layout.originY(size.height)

            drawYGridAndLabels(scaleY, theme, layout, textPaint)

            val anomalySet = anomalies.map { it.originTimestamp }.toSet()
            val orderedSeries = series.sortedBy { if (focusedVariantKey != null && it.key == focusedVariantKey) 1 else 0 }

            orderedSeries.forEachIndexed { seriesIndex, currentSeries ->
                val isFocused = focusedVariantKey == null || currentSeries.key == focusedVariantKey
                val alpha = if (isFocused) 1f else 0.22f
                val color = seriesColors[seriesIndex % seriesColors.size].copy(alpha = alpha)
                val points = currentSeries.points.map { point ->
                    val fractionX = (point.originTimestamp - minTime) / timeRange.toFloat()
                    val x = x0 + fractionX * chartW
                    val y = scaleY.yToPx(point.avgVal, y0, layout.topPad)
                    x to y
                }

                if (points.size >= 2) {
                    val linePath = createSmoothPath(points)
                    val shouldFill = focusedVariantKey == currentSeries.key || (focusedVariantKey == null && seriesIndex == 0)
                    if (shouldFill) {
                        val fillPath = createFillPath(linePath, size.width, y0)
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(color.copy(alpha = 0.20f), Color.Transparent),
                                startY = layout.topPad,
                                endY = y0,
                            ),
                        )
                    }
                    drawPath(
                        path = linePath,
                        color = color,
                        style = Stroke(width = if (isFocused) 5f else 4f),
                    )
                } else {
                    points.firstOrNull()?.let { point ->
                        drawCircle(color, radius = if (isFocused) 7f else 5f, center = Offset(point.first, point.second))
                    }
                }

                if (isFocused) {
                    points.zip(currentSeries.points).forEach { (coord, raw) ->
                        if (raw.originTimestamp in anomalySet) {
                            drawCircle(anomalyColor, radius = 8f, center = Offset(coord.first, coord.second))
                            drawCircle(anomalyInnerColor, radius = 4f, center = Offset(coord.first, coord.second))
                        }
                    }
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

    val allExercises by (repository?.getExerciseGroupsSortedByCount(cutoffTimestamp)
        ?: remember { flowOf(emptyList()) })
        .collectAsState(initial = emptyList())

    var selectedExercise by remember { mutableStateOf<ExerciseGroupWithCount?>(null) }
    var expandedDropdown by remember { mutableStateOf(false) }
    var focusedVariantKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(allExercises) {
        val currentInList = allExercises.find { it.name == selectedExercise?.name }
        if (currentInList != null) {
            selectedExercise = currentInList
        } else if (allExercises.isNotEmpty()) {
            selectedExercise = allExercises.first()
        } else {
            selectedExercise = null
        }
    }

    LaunchedEffect(selectedExercise?.name) {
        focusedVariantKey = null
    }

    val fullSeries by remember(selectedExercise) {
        selectedExercise?.let { repository?.getWeightHistoryForExerciseGroup(it) }
            ?: flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val filteredSeries = remember(fullSeries, cutoffTimestamp) {
        fullSeries.mapNotNull { currentSeries ->
            val points = if (cutoffTimestamp == 0L) {
                currentSeries.points
            } else {
                currentSeries.points.filter { it.originTimestamp >= cutoffTimestamp }
            }
            currentSeries.copy(points = points).takeIf { it.points.isNotEmpty() }
        }
    }

    val anomalyInput = remember(filteredSeries, focusedVariantKey) {
        if (focusedVariantKey == null) {
            filteredSeries.flatMap { it.points }
        } else {
            filteredSeries.firstOrNull { it.key == focusedVariantKey }?.points.orEmpty()
        }
    }
    val anomalies = remember(anomalyInput) { calculateExerciseOutliers(anomalyInput) }

    AdaptiveCard(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Exercise Progress",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))

            ExposedDropdownMenuBox(
                expanded = expandedDropdown,
                onExpandedChange = { expandedDropdown = !expandedDropdown },
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
                        unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                )
                DropdownMenu(
                    expanded = expandedDropdown,
                    onDismissRequest = { expandedDropdown = false },
                ) {
                    allExercises.forEach { exercise ->
                        DropdownMenuItem(
                            text = { ExerciseOptionLabel(exercise) },
                            onClick = {
                                selectedExercise = exercise
                                expandedDropdown = false
                            },
                        )
                    }
                }
            }

            selectedExercise?.variants?.takeIf { it.isNotEmpty() }?.let { variants ->
                Spacer(Modifier.height(8.dp))
                VariantLabelRow(
                    variants = variants,
                    focusedVariantKey = focusedVariantKey,
                    onVariantClick = { variant ->
                        focusedVariantKey = if (focusedVariantKey == variant.key) null else variant.key
                    },
                )
            }

            if (anomalies.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "⚠ ${anomalies.size} conservative anomaly${if (anomalies.size == 1) "" else "ies"} detected",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(16.dp))

            ExerciseProgressChartGraph(
                series = filteredSeries,
                focusedVariantKey = focusedVariantKey,
                anomalies = anomalies,
                modifier = Modifier.fillMaxWidth().height(200.dp),
            )
        }
    }
}

@Composable
private fun ExerciseOptionLabel(exercise: ExerciseGroupWithCount) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "${exercise.name} (${exercise.setTotalCount} sets)",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        if (exercise.variants.isNotEmpty()) {
            VariantLabelRow(
                variants = exercise.variants.take(3),
                focusedVariantKey = null,
                onVariantClick = null,
            )
        }
    }
}

@Composable
private fun VariantLabelRow(
    variants: List<ExerciseProgressVariant>,
    focusedVariantKey: String?,
    onVariantClick: ((ExerciseProgressVariant) -> Unit)?,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        variants.take(5).forEach { variant ->
            VariantLabelChip(
                variant = variant,
                selected = focusedVariantKey == variant.key,
                dimmed = focusedVariantKey != null && focusedVariantKey != variant.key,
                onClick = onVariantClick,
            )
        }
    }
}

@Composable
private fun VariantLabelChip(
    variant: ExerciseProgressVariant,
    selected: Boolean,
    dimmed: Boolean,
    onClick: ((ExerciseProgressVariant) -> Unit)?,
) {
    val accent = variantAccentColor(variant.label)
    val alpha = if (dimmed) 0.38f else 1f
    Surface(
        modifier = if (onClick != null) Modifier.clickable { onClick(variant) } else Modifier,
        color = if (selected) accent.copy(alpha = 0.14f) else Color.Transparent,
        contentColor = accent.copy(alpha = alpha),
        shape = RoundedCornerShape(percent = 50),
        border = BorderStroke(1.dp, accent.copy(alpha = if (dimmed) 0.32f else 0.85f)),
    ) {
        Text(
            text = variant.label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun variantAccentColor(label: String): Color {
    val lower = label.lowercase(Locale.getDefault())
    return when {
        listOf("prime", "atlantis", "hammer strength", "cybex", "gym80", "life fitness").any { lower.contains(it) } -> Color(0xFF2E7D32)
        listOf("rope", "bar", "handle", "v-bar", "ez-bar").any { lower.contains(it) } -> Color(0xFF6A1B9A)
        listOf("cable", "machine", "dumbbell", "barbell", "smith", "bodyweight").any { lower.contains(it) } -> Color(0xFF1565C0)
        listOf("unilateral", "alternating").any { lower.contains(it) } -> Color(0xFFC62828)
        else -> MaterialTheme.colorScheme.primary
    }
}
