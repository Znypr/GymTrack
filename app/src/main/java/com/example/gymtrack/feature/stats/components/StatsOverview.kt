package com.example.gymtrack.feature.stats.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.List
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.gymtrack.core.data.Note
import com.example.gymtrack.core.util.WorkoutParser

@Composable
fun StatsOverview(notes: List<Note>) {
    // We remember the parser to avoid recreating it on every recomposition
    val parser = remember { WorkoutParser() }

    val (totalVolume, totalSets) = remember(notes) {
        var vol = 0.0
        var sets = 0

        notes.forEach { note ->
            // Parse the full workout text using the robust parser
            val parsedSets = parser.parseWorkout(note.text)

            // Add to totals
            sets += parsedSets.size
            vol += parsedSets.sumOf { (it.weight * it.reps).toDouble() }
        }

        vol to sets
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        MetricCard(
            title = "Total Volume",
            value = "${(totalVolume / 1000).toInt()}k kg",
            icon = Icons.Default.FitnessCenter,
            modifier = Modifier.weight(1f)
        )
        MetricCard(
            title = "Total Sets",
            value = totalSets.toString(),
            icon = Icons.Default.List,
            modifier = Modifier.weight(1f)
        )
    }
}