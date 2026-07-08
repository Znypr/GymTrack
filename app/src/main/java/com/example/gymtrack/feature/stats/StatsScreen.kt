package com.example.gymtrack.feature.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gymtrack.core.data.Settings
import com.example.gymtrack.core.data.WorkoutRepository
import com.example.gymtrack.feature.stats.components.charts.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    state: StatsState,
    workoutRepository: WorkoutRepository,
    settings: Settings,
    onTimeRangeSelected: (TimeRange) -> Unit,
    onBack: () -> Unit
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    val textColor = MaterialTheme.colorScheme.onBackground
    var rangeMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = { Text("Statistics", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = textColor)
                    }
                },
                actions = {
                    Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                        Surface(
                            onClick = { rangeMenuExpanded = true },
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            tonalElevation = 1.dp,
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = state.currentRange.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.width(2.dp))
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = rangeMenuExpanded,
                            onDismissRequest = { rangeMenuExpanded = false }
                        ) {
                            TimeRange.values().forEach { range ->
                                DropdownMenuItem(
                                    text = { Text(range.label) },
                                    onClick = {
                                        onTimeRangeSelected(range)
                                        rangeMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = backgroundColor,
                    titleContentColor = textColor
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatBadge("Workouts", "${state.totalNotes}", Modifier.weight(1f))
                        StatBadge("Weekly Avg", String.format("%.1f", state.avgWorkoutsPerWeek), Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatBadge("Avg Sets", String.format("%.1f", state.avgSets), Modifier.weight(1f))
                        StatBadge("Favorite", state.categoryCounts.maxByOrNull { it.value }?.key ?: "-", Modifier.weight(1f))
                    }
                }
            }

            item {
                ExerciseProgressCard(
                    repository = workoutRepository,
                    timeRange = state.currentRange,
                    weightUnitLabel = settings.defaultWeightUnit.displayLabel
                )
            }

            item {
                AdaptiveCard(height = 300.dp) {
                    Column(Modifier.padding(16.dp)) {
                        WorkoutDurationTrendChart(notes = state.filteredNotes, showRollingAvg = true)
                    }
                }
            }

            item {
                AdaptiveCard(height = 320.dp) {
                    Column(Modifier.padding(16.dp)) {
                        TimeOfDayHeatmap(data = state.heatmapData)
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
fun AdaptiveCard(
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp? = null,
    content: @Composable () -> Unit
) {
    var mod = modifier.fillMaxWidth()
    if (height != null) mod = mod.height(height)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp),
        modifier = mod
    ) { content() }
}

@Composable
fun StatBadge(label: String, value: String, modifier: Modifier = Modifier) {
    AdaptiveCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 1
            )
        }
    }
}
