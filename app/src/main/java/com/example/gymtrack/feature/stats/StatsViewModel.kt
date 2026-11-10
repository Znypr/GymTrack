package com.example.gymtrack.feature.stats

import com.example.gymtrack.core.time.AppClock
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.data.WeightUnit
import com.example.gymtrack.core.data.repository.NoteRepository
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
    /** Workouts in the range that were never finished; not counted anywhere else (#322). */
    val unfinishedCount: Int = 0,
    /** Unit every weight in this state is expressed in (the user's default unit, #326). */
    val displayUnit: WeightUnit = WeightUnit.KG,
)

class StatsViewModel(
    private val repository: NoteRepository,
    context: Context,
) : ViewModel() {

    private data class StatsCacheKey(
        val range: TimeRange,
        val notesSignature: Long,
        val factsSignature: Long,
        val displayUnit: WeightUnit,
        val dayBucket: Long?,
    ) {
        val storageKey: String = buildString {
            append(range.name)
            append('_')
            append(displayUnit.name)
            append('_')
            append(notesSignature)
            append('_')
            append(factsSignature)
            append('_')
            append(dayBucket ?: "all")
        }
    }

    private data class StatsInputs(
        val notes: List<NoteLine>,
        val facts: StatsWorkoutFacts,
        val range: TimeRange,
        val displayUnit: WeightUnit,
    )

    private val persistentCache = StatsPersistentCacheStore(context.applicationContext)
    private val _timeRange = MutableStateFlow(TimeRange.ALL_TIME)
    private val _displayUnit = MutableStateFlow(WeightUnit.KG)
    private val _uiState = MutableStateFlow(StatsState(isLoading = true, loadingMessage = "Preparing statistics…"))
    val uiState = _uiState.asStateFlow()

    private val statsCache = linkedMapOf<StatsCacheKey, StatsState>()

    init {
        viewModelScope.launch {
            combine(
                repository.getAllNotes(),
                repository.getStatsWorkoutFacts(),
                _timeRange,
                _displayUnit,
            ) { notes, facts, range, unit -> StatsInputs(notes, facts, range, unit) }
                .collectLatest { (allNotes, facts, range, displayUnit) ->
                    val key = StatsCacheKey(
                        range = range,
                        notesSignature = allNotes.statsSignature(),
                        factsSignature = facts.signature(),
                        displayUnit = displayUnit,
                        dayBucket = range.currentDayBucket(),
                    )
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
                            "Loading cached statistics for ${allNotes.size} workouts…"
                        },
                    )

                    val persisted = withContext(Dispatchers.IO) {
                        persistentCache.read(key.storageKey)
                    }?.copy(
                        currentRange = range,
                        sourceNoteCount = allNotes.size,
                        isLoading = false,
                        loadingMessage = null,
                    )
                    if (persisted != null) {
                        statsCache[key] = persisted
                        trimCache()
                        _uiState.value = persisted
                        return@collectLatest
                    }

                    _uiState.value = _uiState.value.copy(
                        isLoading = true,
                        loadingMessage = if (allNotes.isEmpty()) {
                            "Preparing statistics…"
                        } else {
                            "Building statistics for ${allNotes.size} workouts…"
                        },
                    )

                    val calculated = withContext(Dispatchers.Default) {
                        buildStatsState(allNotes, range, facts, displayUnit, AppClock.nowMillis())
                    }.copy(
                        sourceNoteCount = allNotes.size,
                        isLoading = false,
                        loadingMessage = null,
                    )
                    statsCache[key] = calculated
                    trimCache()
                    _uiState.value = calculated

                    viewModelScope.launch(Dispatchers.IO) {
                        persistentCache.write(key.storageKey, calculated)
                    }
                }
        }
    }

    /** The unit Stats shows weights in; call with the user's current default unit. */
    fun setDisplayUnit(unit: WeightUnit) {
        if (_displayUnit.value != unit) {
            _displayUnit.value = unit
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

    private fun TimeRange.currentDayBucket(): Long? {
        return if (this == TimeRange.ALL_TIME) {
            null
        } else {
            AppClock.nowMillis() / TimeUnit.DAYS.toMillis(1)
        }
    }

    class Factory(
        private val repository: NoteRepository,
        private val context: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StatsViewModel(repository, context) as T
        }
    }

    private companion object {
        const val MAX_RETAINED_STATS_RESULTS = 8
    }
}
