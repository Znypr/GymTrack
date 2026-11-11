package com.example.gymtrack.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.gymtrack.core.data.Settings
import com.example.gymtrack.core.data.WorkoutRepository
import com.example.gymtrack.core.ui.theme.GymTrackTheme
import com.example.gymtrack.core.ui.theme.sketchBorder
import com.example.gymtrack.feature.stats.components.charts.ExerciseProgressCard

/**
 * Old `stats` route entry point, kept so the current nav host compiles until the Progress tab is
 * wired (wave 2). It shows the new [ProgressScreen] layout without the Plan and Settings rows
 * (those need the new navigation). New code: use [ProgressScreen] / [ProgressRoute].
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun StatsScreen(
    state: StatsState,
    workoutRepository: WorkoutRepository,
    settings: Settings,
    onTimeRangeSelected: (TimeRange) -> Unit,
    onBack: () -> Unit,
) {
    ProgressContent(
        state = ProgressState(stats = state, categories = settings.categories),
        onRangeSelected = onTimeRangeSelected,
        onOpenSettings = null,
        onStartCategory = null,
        exerciseProgress = {
            ExerciseProgressCard(
                repository = workoutRepository,
                timeRange = state.currentRange,
                displayUnit = settings.defaultWeightUnit,
                timedProgress = state.timedProgress,
            )
        },
    )
}

/**
 * Card for the Progress charts, in the app's skin: Normal = surface, radius 14, 1 dp hairline;
 * Notebook = no fill (paper shows through) and a sketched pencil box. No inner padding.
 */
@Composable
fun AdaptiveCard(
    modifier: Modifier = Modifier,
    height: Dp? = null,
    content: @Composable () -> Unit,
) {
    val tokens = GymTrackTheme.tokens
    val c = tokens.colors
    var mod = modifier.fillMaxWidth()
    if (height != null) mod = mod.height(height)
    val skin = if (tokens.isNotebook) {
        Modifier.sketchBorder(c.ink, seed = (height?.value ?: 0f).toInt() + 3, strokeWidth = 1.5.dp, cornerRadius = tokens.radii.card)
    } else {
        val shape = RoundedCornerShape(tokens.radii.card)
        Modifier.clip(shape).background(c.surface, shape).border(1.dp, c.hairline, shape)
    }
    Box(mod.then(skin)) { content() }
}

/** `95` -> `1:35`, `3725` -> `1:02:05`. */
internal fun formatHoldTime(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(java.util.Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}

/** Tag on a Progress number's value text, e.g. `stats-badge-value-Workouts`. */
fun statsBadgeValueTestTag(label: String): String = "stats-badge-value-$label"
