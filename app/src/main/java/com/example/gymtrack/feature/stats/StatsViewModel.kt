package com.example.gymtrack.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.data.repository.NoteRepository
import com.example.gymtrack.core.util.parseDurationSeconds
import com.example.gymtrack.core.util.parseNoteText
import com.example.gymtrack.core.util.WorkoutParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

enum class TimeRange(val label: String, val days: Int) {
    LAST_WEEK("Last Week", 7),
    LAST_MONTH("Last Month", 30),
    LAST_3_MONTHS("Last 3 Months", 90), // [FIX] Added missing option
    LAST_6_MONTHS("Last 6 Months", 180),
    LAST_YEAR("Last Year", 365),
    ALL_TIME("All Time", -1)
}

data class StatsState(
    val totalNotes: Int = 0,
    val avgWorkoutsPerWeek: Float = 0f,
    val avgSets: Float = 0f,
    val categoryCounts: Map<String, Int> = emptyMap(),
    val averageDurations: Map<String, Float> = emptyMap(),
    val heatmapData: Array<IntArray> = Array(7) { IntArray(24) },
    val topExercises: List<Pair<String, Int>> = emptyList(),
    val currentRange: TimeRange = TimeRange.ALL_TIME,
    val filteredNotes: List<NoteLine> = emptyList()
)

class StatsViewModel(
    private val repository: NoteRepository
) : ViewModel() {

    private val _timeRange = MutableStateFlow(TimeRange.ALL_TIME)

    val uiState: StateFlow<StatsState> = combine(
        repository.getAllNotes(),
        _timeRange
    ) { notes, range ->
        calculateStats(notes, range)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsState())

    fun setTimeRange(range: TimeRange) {
        _timeRange.value = range
    }

    private suspend fun calculateStats(allNotes: List<NoteLine>, range: TimeRange): StatsState = withContext(Dispatchers.Default) {
        // [FIX] Strict math using TimeUnit.
        // "Last Month" = Exact last 30 days relative to NOW.
        val cutoff = if (range.days > 0) {
            System.currentTimeMillis() - TimeUnit.DAYS.toMillis(range.days.toLong())
        } else 0L

        val notes = allNotes.filter { it.timestamp >= cutoff }

        if (notes.isEmpty()) return@withContext StatsState(currentRange = range, filteredNotes = emptyList())

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

        // 2. Weekly Avg
        val minTime = notes.minOf { it.timestamp }
        val maxTime = notes.maxOf { it.timestamp }

        // Calculate exact weeks in the selected range (or actual data span if All Time)
        val durationWeeks = if (range == TimeRange.ALL_TIME) {
            val diff = (maxTime - minTime).coerceAtLeast(1L)
            diff.toFloat() / TimeUnit.DAYS.toMillis(7)
        } else {
            range.days / 7f
        }

        val avgWeekly = if (durationWeeks > 0) notes.size / durationWeeks else 0f

        // 3. Category Counts & Durations
        val counts = notes.groupingBy { it.categoryName ?: "Other" }.eachCount()

        val durationAvgs = notes.groupBy { it.categoryName ?: "Other" }
            .mapValues { entry ->
                val durations = entry.value.mapNotNull { note ->
                    parseNoteText(note.text).second.mapNotNull {
                        if (it.isBlank()) null else parseDurationSeconds(it)
                    }.maxOrNull()
                }
                if (durations.isEmpty()) 0f else durations.average().toFloat() / 60f
            }

        // 4. Heatmap
        val heatmap = Array(7) { IntArray(24) }
        notes.forEach { n ->
            val c = Calendar.getInstance().apply { timeInMillis = n.timestamp }
            val day = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
            val hour = c.get(Calendar.HOUR_OF_DAY)
            heatmap[day][hour]++
        }

        // 5. Top Exercises
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
            avgWorkoutsPerWeek = avgWeekly,
            avgSets = avgSets,
            categoryCounts = counts,
            averageDurations = durationAvgs,
            heatmapData = heatmap,
            topExercises = exerciseCounts,
            currentRange = range,
            filteredNotes = notes
        )
    }

    class Factory(private val repository: NoteRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StatsViewModel(repository) as T
        }
    }
}