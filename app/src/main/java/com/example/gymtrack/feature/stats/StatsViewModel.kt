package com.example.gymtrack.feature.stats

import com.example.gymtrack.core.time.AppClock
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.data.WeightUnit
import com.example.gymtrack.core.data.repository.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    /** Weekly average workout duration in minutes (week start, minutes), for the duration chart. */
    val weeklyDurations: List<Pair<Long, Float>> = emptyList(),
    /** Workouts left out of the duration chart because their duration is unreasonable. */
    val unreasonableDurationCount: Int = 0,
    /** Timed sets (`60s`, `45s 10`) in the counted workouts (#283). */
    val timedSets: TimedSetSummary = TimedSetSummary(),
    /** Progress of timed exercises in seconds or load × seconds (#283). */
    val timedProgress: List<TimedProgressGroup> = emptyList(),
)

/**
 * Stats screen state. Nothing is computed until the Stats screen collects [uiState] (#126: the
 * ViewModel is created with the nav host at startup, so it must not build there). Loading runs
 * off the main thread through the process-wide [StatsEngine] (#303).
 */
class StatsViewModel(
    private val repository: NoteRepository,
    context: Context,
) : ViewModel() {

    private data class StatsInputs(
        val notes: List<NoteLine>,
        val facts: StatsWorkoutFacts,
        val range: TimeRange,
        val displayUnit: WeightUnit,
    )

    private val engine = StatsEngine.shared(StatsPersistentCacheStore(context.applicationContext))
    private val _timeRange = MutableStateFlow(TimeRange.ALL_TIME)
    private val _displayUnit = MutableStateFlow(WeightUnit.KG)
    private val _uiState = MutableStateFlow(StatsState(isLoading = true, loadingMessage = "Preparing statistics…"))
    val uiState: StateFlow<StatsState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.subscriptionCount
                .map { it > 0 }
                .distinctUntilChanged()
                .collectLatest { visible -> if (visible) runPipeline() }
        }
    }

    private suspend fun runPipeline() {
        combine(
            repository.getAllNotes(),
            repository.getStatsWorkoutFacts(),
            _timeRange,
            _displayUnit,
        ) { notes, facts, range, unit -> StatsInputs(notes, facts, range, unit) }
            .collectLatest { (allNotes, facts, range, displayUnit) ->
                withContext(Dispatchers.Default) {
                    engine.load(
                        notes = allNotes,
                        facts = facts,
                        range = range,
                        displayUnit = displayUnit,
                        nowMillis = AppClock.nowMillis(),
                        onBuilding = {
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
                        },
                        publish = { state ->
                            _uiState.value = state.copy(
                                currentRange = range,
                                sourceNoteCount = allNotes.size,
                                isLoading = false,
                                loadingMessage = null,
                            )
                        },
                    )
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

    class Factory(
        private val repository: NoteRepository,
        private val context: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StatsViewModel(repository, context) as T
        }
    }
}
