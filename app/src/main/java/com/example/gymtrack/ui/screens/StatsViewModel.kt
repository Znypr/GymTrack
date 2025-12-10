package com.example.gymtrack.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.gymtrack.data.NoteLine
import com.example.gymtrack.data.repository.NoteRepository
import com.example.gymtrack.util.parseDurationSeconds
import com.example.gymtrack.util.parseNoteText
import com.example.gymtrack.util.WorkoutParser // Needed for SetsDistribution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.util.Calendar

data class StatsState(
    val totalNotes: Int = 0,
    val totalCategories: Int = 0,
    val avgSets: Float = 0f,
    val categoryCounts: Map<String, Int> = emptyMap(),
    val averageDurations: Map<String, Float> = emptyMap(),
    val heatmapData: Array<IntArray> = Array(7) { IntArray(24) },
    val topExercises: List<Pair<String, Int>> = emptyList(),
)

class StatsViewModel(
    private val repository: NoteRepository
) : ViewModel() {

    val uiState: StateFlow<StatsState> = repository.getAllNotes()
        .map { notes -> calculateStats(notes) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsState())

    private suspend fun calculateStats(notes: List<NoteLine>): StatsState = withContext(Dispatchers.Default) {
        if (notes.isEmpty()) return@withContext StatsState()

        val parser = WorkoutParser()

        // 1. Overview Stats
        val (mainCount, subCount) = notes.fold(0 to 0) { acc, note ->
            val lines = parseNoteText(note.text).first
            var main = 0
            var sub = 0
            for (l in lines) {
                if (l.isBlank()) continue
                if (l.startsWith("    ")) sub++ else main++
            }
            (acc.first + main) to (acc.second + sub)
        }
        val avgSets = if (mainCount > 0) subCount.toFloat() / mainCount else 0f

        // 2. Category & Duration Stats
        val counts = notes.groupingBy { it.categoryName ?: "Other" }.eachCount()
        val avgs = notes.groupBy { it.categoryName ?: "Other" }
            .mapValues { entry ->
                val durations = entry.value.mapNotNull { note ->
                    parseNoteText(note.text).second.mapNotNull {
                        if (it.isBlank()) null else parseDurationSeconds(it)
                    }.maxOrNull()
                }
                if (durations.isEmpty()) 0f else durations.average().toFloat() / 60f
            }

        // 3. Heatmap
        val heatmap = Array(7) { IntArray(24) }
        notes.forEach { n ->
            val c = Calendar.getInstance().apply { timeInMillis = n.timestamp }
            val day = ((c.get(Calendar.DAY_OF_WEEK) + 5) % 7) // Mon=0
            val hour = c.get(Calendar.HOUR_OF_DAY)
            heatmap[day][hour]++
        }

        // 4. Top Exercises
        val exerciseCounts = notes.flatMap { note ->
            val sets = parser.parseWorkout(note.text)
            sets.map { it.exerciseName }
        }
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
            .take(5)
        StatsState(
            totalNotes = notes.size,
            totalCategories = notes.mapNotNull { it.categoryName }.distinct().size,
            avgSets = avgSets,
            categoryCounts = counts,
            averageDurations = avgs,
            heatmapData = heatmap,       // Pass to state
            topExercises = exerciseCounts, // Pass to state
        )
    }

    class Factory(private val repository: NoteRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StatsViewModel(repository) as T
        }
    }
}