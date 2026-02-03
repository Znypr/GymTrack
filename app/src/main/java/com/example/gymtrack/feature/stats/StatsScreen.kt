package com.example.gymtrack.feature.stats

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtrack.core.data.Settings
import com.example.gymtrack.core.data.WorkoutRepository
import com.example.gymtrack.feature.stats.components.charts.*

private val CardDeepDark = Color(0xFF121212)

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
    val textColor = MaterialTheme.colorScheme.onSurface
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
                    // [FIX] Wrap in Box to anchor the Dropdown correctly
                    Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {

                        // [FIX] Use Surface(onClick = ...) instead of Row(Modifier.clickable)
                        // This ensures the button captures clicks properly in the TopBar.
                        Surface(
                            onClick = { rangeMenuExpanded = true },
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = textColor
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = state.currentRange.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textColor
                                )
                                Spacer(Modifier.width(2.dp))
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = textColor
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor, titleContentColor = textColor)
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
            // 1. Quick Stats
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

            // 2. Exercise Progress
            item {
                ExerciseProgressCard(repository = workoutRepository,timeRange = state.currentRange)
            }

            // 3. Duration Trend
            item {
                AdaptiveCard(height = 280.dp) {
                    Column(Modifier.padding(16.dp)) {
                        WorkoutDurationTrendChart(notes = state.filteredNotes, showRollingAvg = true)
                    }
                }
            }

            // 7. Heatmap
            item {
                AdaptiveCard(height = 300.dp) {
                    Column(Modifier.padding(16.dp)) {
                        TimeOfDayHeatmap(data = state.heatmapData)
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

// --- HELPERS ---

@Composable
fun AdaptiveCard(modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp? = null, content: @Composable () -> Unit) {
    var mod = modifier.fillMaxWidth()
    if (height != null) mod = mod.height(height)
    Card(
        colors = CardDefaults.cardColors(containerColor = CardDeepDark),
        shape = RoundedCornerShape(12.dp),
        modifier = mod
    ) { content() }
}

@Composable
fun StatBadge(label: String, value: String, modifier: Modifier = Modifier) {
    AdaptiveCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), maxLines = 1)
        }
    }
}