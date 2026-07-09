package com.example.gymtrack.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.data.repository.NoteRepository
import com.example.gymtrack.core.util.WorkoutParser
import com.example.gymtrack.core.util.parseDurationSeconds
import com.example.gymtrack.core.util.parseNoteText
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class TimeRange(val label: String, val days: Int) {
    LAST_WEEK("Last Week", 7),
    LAST_MONTH("Last Month", 30),
    LAST_3_MONTHS("Last 3 Months", 90),
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
    val weeklyWorkoutCounts: List<WeeklyWorkoutCount> = emptyList(),
    val trainingInsights: List<TrainingInsightRow> = emptyList(),
    val currentRange: TimeRange = TimeRange.ALL_TIME,
    val filteredNotes: List<NoteLine> = emptyList(),
    val isLoading: Boolean = false,
    val loadingMessage: String? = null,
    val sourceNoteCount: Int = 0,
)

class StatsViewModel(
    private val repository: NoteRepository,
) : ViewModel() {

    private data class StatsCacheKey(
        val range: TimeRange,
        val notesSignature: Long,
    )

    private val _timeRange = MutableStateFlow(TimeRange.ALL_TIME)
    private val _uiState = MutableStateFlow(StatsState(isLoading = true, loadingMessage = "Preparing statistics…"))
    val uiState = _uiState.asStateFlow()

    private val statsCache = linkedMapOf<StatsCacheKey, StatsState>()

    init {
        viewModelScope.launch {
            combine(repository.getAllNotes(), _timeRange) { notes, range -> notes to range }
                .collectLatest { (allNotes, range) ->
                    val key = StatsCacheKey(range = range, notesSignature = allNotes.statsSignature())
                    val cached = statsCache[key]
                    if (cached != null) {
                        _uiState.value = cached
                        return@collectLatest
                    }

                    _uiState.value = _uiState.value.copy(
                        currentRange = range,
                        sourceNoteCount = allNotes.size,
                        isLoading = true,
                        loadingMessage = if (allNotes.isEmpty()) {
                            "Preparing statistics…"
                        } else {
                            "Building statistics for ${allNotes.size} workouts…"
                        },
                    )

                    val calculated = calculateStats(allNotes, range).copy(
                        sourceNoteCount = allNotes.size,
                        isLoading = false,
                        loadingMessage = null,
                    )
                    statsCache[key] = calculated
                    trimCache()
                    _uiState.value = calculated
                }
        }
    }

    fun setTimeRange(range: TimeRange) {
        if (_timeRange.value != range) {
            _timeRange.value = range
        }
    }

    private fun trimCache() {
        while (statsCache.size > MAX_RETAINED_STATS_RESULTS) {
            statsCache.remove(statsCache.keys.first())
        }
    }

    private fun List<NoteLine>.statsSignature(): Long {
        var result = size.toLong()
        for (note in this) {
            result = 31 * result + note.timestamp
            result = 31 * result + note.text.hashCode()
            result = 31 * result + note.rowMetadata.hashCode()
            result = 31 * result + (note.categoryName?.hashCode() ?: 0)
            result = 31 * result + (note.categoryColor ?: 0L)
        }
        return result
    }

    private suspend fun calculateStats(allNotes: List<NoteLine>, range: TimeRange): StatsState = withContext(Dispatchers.Default) {
        val cutoff = if (range.days > 0) {
            System.currentTimeMillis() - TimeUnit.DAYS.toMillis(range.days.toLong())
        } else 0L

        val notes = allNotes.filter { it.timestamp >= cutoff }

        if (notes.isEmpty()) return@withContext StatsState(currentRange = range, filteredNotes = emptyList())

        val parser = WorkoutParser()

        val (mainCount, subCount) = notes.fold(0 to 0) { acc, note ->
            val lines = parseNoteText(note.text, note.rowMetadata).first
            var main = 0
            var sub = 0
            for (l in lines) {
                if (l.isBlank()) continue
                if (l.startsWith("    ")) sub++ else main++
            }
            (acc.first + main) to (acc.second + sub)
        }
        val avgSets = if (mainCount > 0) subCount.toFloat() / mainCount else 0f

        val minTime = notes.minOf { it.timestamp }
        val maxTime = notes.maxOf { it.timestamp }

        val durationWeeks = if (range == TimeRange.ALL_TIME) {
            val diff = (maxTime - minTime).coerceAtLeast(1L)
            diff.toFloat() / TimeUnit.DAYS.toMillis(7)
        } else {
            range.days / 7f
        }

        val avgWeekly = if (durationWeeks > 0) notes.size / durationWeeks else 0f

        val counts = notes.groupingBy { it.categoryName ?: "Other" }.eachCount()

        val durationAvgs = notes.groupBy { it.categoryName ?: "Other" }
            .mapValues { entry ->
                val durations = entry.value.mapNotNull { note ->
                    parseNoteText(note.text, note.rowMetadata).second.mapNotNull {
                        if (it.isBlank()) null else parseDurationSeconds(it)
                    }.maxOrNull()
                }
                if (durations.isEmpty()) 0f else durations.average().toFloat() / 60f
            }

        val heatmap = Array(7) { IntArray(24) }
        notes.forEach { n ->
            val c = Calendar.getInstance().apply { timeInMillis = n.timestamp }
            val day = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
            val hour = c.get(Calendar.HOUR_OF_DAY)
            heatmap[day][hour]++
        }

        val exerciseCounts = notes.flatMap { note ->
            val sets = parser.parseWorkout(note.text, rowMetadata = note.rowMetadata)
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
            weeklyWorkoutCounts = buildWeeklyWorkoutCounts(notes),
            trainingInsights = buildTrainingInsights(notes, parser),
            currentRange = range,
            filteredNotes = notes,
        )
    }

    class Factory(private val repository: NoteRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StatsViewModel(repository) as T
        }
    }

    private companion object {
        const val MAX_RETAINED_STATS_RESULTS = 8
    }
}
