package com.example.gymtrack.feature.stats

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.data.Settings

// Reusing the styling logic
private val CardDeepDark = Color(0xFF181818)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    notes: List<NoteLine>,
    settings: Settings,
    onBack: () -> Unit
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    val textColor = MaterialTheme.colorScheme.onSurface

    val totalWorkouts = notes.size
    val totalSets = remember(notes) {
        notes.sumOf { note -> note.text.lines().count { it.trim().matches(Regex("^\\d+x.*")) } }
    }
    val favCategory = remember(notes) {
        notes.groupingBy { it.categoryName }.eachCount().maxByOrNull { it.value }?.key ?: "-"
    }

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = { Text("Your Statistics", fontWeight = FontWeight.Bold, fontSize = 24.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = textColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor, titleContentColor = textColor)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Quick Stats
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatBadge("Workouts", "$totalWorkouts", Modifier.weight(1f))
                    StatBadge("Sets", "$totalSets", Modifier.weight(1f))
                    StatBadge("Favorite", favCategory, Modifier.weight(1f))
                }
            }

            // Exercise Progress
            item {
                Text("Exercise Progression", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = textColor)
                Spacer(Modifier.height(12.dp))
                AdaptiveCard(height = 300.dp) {
                    // TODO: Insert your ExerciseProgressChart(notes, settings) here
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Chart Placeholder", color = textColor.copy(alpha = 0.5f))
                    }
                }
            }

            // Top Exercises
            item {
                Text("Top Exercises", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = textColor)
                Spacer(Modifier.height(12.dp))
                AdaptiveCard(height = 250.dp) {
                    // TODO: Insert your TopExercisesChart(notes) here
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Bar Chart Placeholder", color = textColor.copy(alpha = 0.5f))
                    }
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
fun StatBadge(label: String, value: String, modifier: Modifier = Modifier) {
    AdaptiveCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), maxLines = 1)
        }
    }
}

@Composable
fun AdaptiveCard(modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp? = null, content: @Composable () -> Unit) {
    val isDark = MaterialTheme.colorScheme.background.run { red < 0.5 && green < 0.5 && blue < 0.5 }
    val cardColor = if (isDark) CardDeepDark else MaterialTheme.colorScheme.surface

    var mod = modifier
    if (height != null) mod = mod.height(height)

    Card(
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(12.dp),
        modifier = mod,
        elevation = CardDefaults.cardElevation(defaultElevation = if(isDark) 0.dp else 2.dp)
    ) {
        content()
    }
}