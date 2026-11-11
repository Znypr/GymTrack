package com.example.gymtrack.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gymtrack.core.data.Category
import com.example.gymtrack.core.data.DEFAULT_CATEGORIES
import com.example.gymtrack.core.data.Settings
import com.example.gymtrack.core.data.WorkoutRepository
import com.example.gymtrack.core.ui.components.AppCard
import com.example.gymtrack.core.ui.components.AppDivider
import com.example.gymtrack.core.ui.components.AppIcon
import com.example.gymtrack.core.ui.components.AppIconView
import com.example.gymtrack.core.ui.components.CategoryKind
import com.example.gymtrack.core.ui.components.CategoryMark
import com.example.gymtrack.core.ui.theme.GymTrackTheme
import com.example.gymtrack.feature.settings.NavRow
import com.example.gymtrack.feature.settings.ScreenHeader
import com.example.gymtrack.feature.settings.SectionLabel
import com.example.gymtrack.feature.settings.SegmentedChoice
import com.example.gymtrack.feature.stats.components.charts.ExerciseProgressCard
import com.example.gymtrack.feature.stats.components.charts.WeeklyConsistencyChart
import com.example.gymtrack.feature.stats.components.charts.WorkoutDurationTrendChart
import com.example.gymtrack.feature.suggest.GoalChip
import com.example.gymtrack.feature.suggest.GoalSheet
import com.example.gymtrack.feature.suggest.TrainingGoal
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import java.util.Locale

/** What the Progress tab shows: the Stats result plus the categories for the Plan row. */
@Immutable
data class ProgressState(
    val stats: StatsState = StatsState(),
    val categories: List<Category> = DEFAULT_CATEGORIES,
)

/** The range control's choices, in order: 4W · 3M · 1Y · All. */
val PROGRESS_RANGES: List<Pair<TimeRange, String>> = listOf(
    TimeRange.LAST_4_WEEKS to "4W",
    TimeRange.LAST_3_MONTHS to "3M",
    TimeRange.LAST_YEAR to "1Y",
    TimeRange.ALL_TIME to "All",
)

private val RANGE_SPOKEN = listOf("Last 4 weeks", "Last 3 months", "Last year", "All time")

/** Tag on the Plan row of a category, e.g. `progress-start-Push`. */
fun progressStartTestTag(category: String): String = "progress-start-$category"

const val PROGRESS_SETTINGS_TAG = "progress-settings"

/**
 * Progress tab (DESIGN_LANGUAGE_V2 "Progress tab"; replaces StatsScreen's layout).
 *
 * Top to bottom: a read-only header, the cards (Workouts, weekly consistency, exercise progress,
 * duration trend), the Plan row ("start from Push · Pull · Legs") and a Settings row. The range
 * control (4W/3M/1Y/All) is pinned at the bottom of this screen, just above the app's bottom
 * navigation, in the thumb zone. Honest numbers only: see docs/STATS_RULES.md.
 *
 * @param onRangeSelected range tapped in the bottom control; pass to [StatsViewModel.setTimeRange].
 * @param onOpenSettings the Settings row at the end.
 * @param onStartCategory a Plan row; gets the category name ("Push"). Starts a workout of it.
 * @param exerciseProgress the exercise-progress card; it reads the repository, so the host
 *   provides it ([ProgressRoute] does). Null hides the card (previews, tests).
 */
@Composable
fun ProgressScreen(
    state: ProgressState,
    onRangeSelected: (TimeRange) -> Unit,
    onOpenSettings: () -> Unit,
    onStartCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
    exerciseProgress: (@Composable () -> Unit)? = null,
    goal: TrainingGoal = TrainingGoal.NONE,
    onGoalSelected: ((TrainingGoal) -> Unit)? = null,
) {
    ProgressContent(
        state = state,
        onRangeSelected = onRangeSelected,
        onOpenSettings = onOpenSettings,
        onStartCategory = onStartCategory,
        modifier = modifier,
        exerciseProgress = exerciseProgress,
        goal = goal,
        onGoalSelected = onGoalSelected,
    )
}

/**
 * Ready-made Progress tab for the nav host: collects [viewModel] (which starts the Stats build,
 * #126), keeps the display unit in sync with Settings and wires the exercise-progress card.
 */
@Composable
fun ProgressRoute(
    viewModel: StatsViewModel,
    workoutRepository: WorkoutRepository,
    settings: Settings,
    onOpenSettings: () -> Unit,
    onStartCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** The header goal chip (#312); null hides it. */
    onGoalSelected: ((TrainingGoal) -> Unit)? = null,
) {
    val stats by viewModel.uiState.collectAsState()
    LaunchedEffect(settings.defaultWeightUnit) { viewModel.setDisplayUnit(settings.defaultWeightUnit) }
    ProgressScreen(
        state = ProgressState(stats = stats, categories = settings.categories),
        onRangeSelected = viewModel::setTimeRange,
        onOpenSettings = onOpenSettings,
        onStartCategory = onStartCategory,
        modifier = modifier,
        goal = settings.trainingGoal,
        onGoalSelected = onGoalSelected,
        exerciseProgress = {
            ExerciseProgressCard(
                repository = workoutRepository,
                timeRange = stats.currentRange,
                displayUnit = settings.defaultWeightUnit,
                timedProgress = stats.timedProgress,
            )
        },
    )
}

@Composable
internal fun ProgressContent(
    state: ProgressState,
    onRangeSelected: (TimeRange) -> Unit,
    onOpenSettings: (() -> Unit)?,
    onStartCategory: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
    exerciseProgress: (@Composable () -> Unit)? = null,
    goal: TrainingGoal = TrainingGoal.NONE,
    onGoalSelected: ((TrainingGoal) -> Unit)? = null,
) {
    val tokens = GymTrackTheme.tokens
    val stats = state.stats
    var goalSheetOpen by rememberSaveable { mutableStateOf(false) }
    val rangeIndex = PROGRESS_RANGES.indexOfFirst { it.first == stats.currentRange }
    Column(modifier.fillMaxSize().background(tokens.colors.bg)) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = tokens.spacing.md),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.md),
        ) {
            item {
                Column {
                    ScreenHeader(
                        title = "Progress",
                        subtitle = RANGE_SPOKEN.getOrNull(rangeIndex) ?: stats.currentRange.label,
                    )
                    // #312: opt-in coaching goal, one tap from the stats it is about.
                    if (onGoalSelected != null) {
                        GoalChip(goal = goal, onClick = { goalSheetOpen = true })
                    }
                }
            }
            if (stats.isLoading) {
                item { LoadingCard(stats.loadingMessage ?: "Building statistics…", stats.sourceNoteCount) }
            }
            item { WorkoutsCard(stats) }
            item {
                AdaptiveCard(height = 300.dp) {
                    Column(Modifier.padding(tokens.spacing.md)) {
                        WeeklyConsistencyChart(data = stats.weeklyWorkoutCounts)
                    }
                }
            }
            if (exerciseProgress != null) {
                item { exerciseProgress() }
            }
            item {
                AdaptiveCard(height = 300.dp) {
                    Column(Modifier.padding(tokens.spacing.md)) {
                        WorkoutDurationTrendChart(
                            weekly = stats.weeklyDurations,
                            filteredOutliers = stats.unreasonableDurationCount,
                            showRollingAvg = true,
                        )
                    }
                }
            }
            if (onStartCategory != null && state.categories.isNotEmpty()) {
                item { PlanSection(state.categories, onStartCategory) }
            }
            if (onOpenSettings != null) {
                item {
                    AppCard(Modifier.fillMaxWidth(), seed = 61) {
                        NavRow(
                            title = "Settings",
                            subtitle = "Units, theme, categories, backup, help, legal",
                            icon = AppIcon.SETTINGS,
                            onClick = onOpenSettings,
                            modifier = Modifier.testTag(PROGRESS_SETTINGS_TAG),
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(tokens.spacing.md)) }
        }
        // Thumb zone: the range control sits just above the bottom navigation.
        AppDivider(seed = 67)
        SegmentedChoice(
            options = PROGRESS_RANGES.map { it.second },
            selectedIndex = rangeIndex,
            onSelect = { onRangeSelected(PROGRESS_RANGES[it].first) },
            contentDescriptions = RANGE_SPOKEN,
            modifier = Modifier
                .background(tokens.colors.bg)
                .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.xxs),
        )
    }
    if (goalSheetOpen && onGoalSelected != null) {
        GoalSheet(selected = goal, onSelect = onGoalSelected, onDismiss = { goalSheetOpen = false })
    }
}

@Composable
private fun WorkoutsCard(stats: StatsState) {
    val tokens = GymTrackTheme.tokens
    val c = tokens.colors
    AppCard(Modifier.fillMaxWidth(), seed = 31) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "${stats.totalNotes}",
                style = tokens.type.display,
                color = c.ink,
                modifier = Modifier.testTag(statsBadgeValueTestTag("Workouts")),
            )
            Spacer(Modifier.width(tokens.spacing.xs))
            Text(
                text = if (stats.totalNotes == 1) "workout finished" else "workouts finished",
                style = tokens.type.body,
                color = c.inkMuted,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        if (stats.unfinishedCount > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIconView(AppIcon.ALERT, contentDescription = null, tint = c.inkMuted, size = 14.dp)
                Spacer(Modifier.width(tokens.spacing.xxs))
                Text(
                    text = "+${stats.unfinishedCount} unfinished, not counted",
                    style = tokens.type.small,
                    color = c.inkMuted,
                    modifier = Modifier.testTag(statsBadgeValueTestTag("Workouts") + "-caption"),
                )
            }
        }
        Spacer(Modifier.height(tokens.spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md)) {
            MiniStat("per week", String.format(Locale.ROOT, "%.1f", stats.avgWorkoutsPerWeek), Modifier.weight(1f), "Weekly Avg")
            MiniStat("sets / exercise", String.format(Locale.ROOT, "%.1f", stats.avgSets), Modifier.weight(1f), "Avg Sets")
            MiniStat(
                "most trained",
                stats.categoryCounts.maxByOrNull { it.value }?.key ?: "-",
                Modifier.weight(1f),
                "Favorite",
            )
        }
        // Timed sets (#283) count as sets; this says how many and their total hold time.
        if (stats.timedSets.setCount > 0) {
            Spacer(Modifier.height(tokens.spacing.xs))
            Text(
                text = "${stats.timedSets.setCount} timed sets · ${formatHoldTime(stats.timedSets.totalSeconds)} total hold",
                style = tokens.type.small,
                color = c.inkMuted,
                modifier = Modifier.testTag(statsBadgeValueTestTag("Timed sets")),
            )
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier, tagLabel: String) {
    val tokens = GymTrackTheme.tokens
    Column(modifier) {
        Text(
            text = value,
            style = tokens.type.title,
            color = tokens.colors.ink,
            maxLines = 1,
            modifier = Modifier.testTag(statsBadgeValueTestTag(tagLabel)),
        )
        Text(label, style = tokens.type.small, color = tokens.colors.inkMuted, maxLines = 1)
    }
}

@Composable
private fun PlanSection(categories: List<Category>, onStartCategory: (String) -> Unit) {
    val tokens = GymTrackTheme.tokens
    Column {
        SectionLabel("Plan")
        AppCard(Modifier.fillMaxWidth(), seed = 47) {
            Text("Start from", style = tokens.type.small, color = tokens.colors.inkMuted)
            categories.forEachIndexed { index, category ->
                if (index > 0) AppDivider(seed = index * 7)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable(role = Role.Button, onClickLabel = "Start a ${category.name} workout") {
                            onStartCategory(category.name)
                        }
                        .testTag(progressStartTestTag(category.name)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CategoryMark(
                        kind = CategoryKind.fromName(category.name),
                        label = category.name,
                        size = 14.dp,
                        customColor = androidx.compose.ui.graphics.Color(category.color),
                    )
                    Spacer(Modifier.width(tokens.spacing.sm))
                    Text(
                        text = category.name,
                        style = tokens.type.body,
                        color = tokens.colors.ink,
                        fontWeight = if (tokens.isNotebook) null else FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    Text("Start", style = tokens.type.small, color = tokens.colors.action)
                    AppIconView(AppIcon.CHEVRON, contentDescription = null, tint = tokens.colors.action, size = 20.dp)
                }
            }
        }
    }
}

@Composable
private fun LoadingCard(message: String, sourceNoteCount: Int) {
    val tokens = GymTrackTheme.tokens
    AppCard(Modifier.fillMaxWidth(), seed = 13) {
        Text(message, style = tokens.type.body, color = tokens.colors.ink)
        Spacer(Modifier.height(tokens.spacing.xxs))
        Text(
            text = if (sourceNoteCount > 0) {
                "Large histories take a moment the first time. Later visits reuse the result."
            } else {
                "Preparing your progress view."
            },
            style = tokens.type.small,
            color = tokens.colors.inkMuted,
        )
        Spacer(Modifier.height(tokens.spacing.xs))
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            color = tokens.colors.action,
            trackColor = tokens.colors.hairline,
        )
    }
}
