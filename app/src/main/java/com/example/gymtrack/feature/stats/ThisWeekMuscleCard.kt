package com.example.gymtrack.feature.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.gymtrack.core.data.DerivedIndexWriter
import com.example.gymtrack.core.muscle.Muscle
import com.example.gymtrack.core.muscle.MuscleLevel
import com.example.gymtrack.core.muscle.MuscleLoadSource
import com.example.gymtrack.core.muscle.MuscleWeek
import com.example.gymtrack.core.time.AppClock
import com.example.gymtrack.core.ui.components.AppCard
import com.example.gymtrack.core.ui.components.AppDivider
import com.example.gymtrack.core.ui.components.BodyMap
import com.example.gymtrack.core.ui.components.BodyMapLegend
import com.example.gymtrack.core.ui.components.BodyMapLevelList
import com.example.gymtrack.core.ui.components.BodyMapView
import com.example.gymtrack.core.ui.components.bodyMapMuscles
import com.example.gymtrack.core.ui.components.setsPhrase
import com.example.gymtrack.core.ui.theme.GymTrackTheme

const val THIS_WEEK_CARD_TAG = "progress-this-week"
const val THIS_WEEK_NOT_TRAINED_TAG = "progress-this-week-not-trained"
const val THIS_WEEK_DETAIL_TAG = "progress-this-week-detail"

/**
 * Loads this ISO week's muscle load from [source] and shows [ThisWeekMuscleCard]. [refreshKey]
 * reloads it (pass something that changes when a workout is saved, e.g. the workout count).
 */
@Composable
fun ThisWeekMuscleRoute(source: MuscleLoadSource, refreshKey: Any?, modifier: Modifier = Modifier) {
    val week by produceState<MuscleWeek?>(null, source, refreshKey) {
        value = source.weekLevels(DerivedIndexWriter.weekKey(AppClock.nowMillis()))
    }
    ThisWeekMuscleCard(week, modifier)
}

/** This ISO week's "Not trained yet" line for the Today tab, or null (see [ThisWeekText.todayLine]). */
@Composable
fun rememberTodayNotTrainedLine(source: MuscleLoadSource, refreshKey: Any?): String? {
    val line by produceState<String?>(null, source, refreshKey) {
        value = ThisWeekText.todayLine(source.weekLevels(DerivedIndexWriter.weekKey(AppClock.nowMillis())))
    }
    return line
}

/** Lines the card says in words; pure so tests can check them (#311). */
internal object ThisWeekText {
    /** Mapped regions with no sets this week, in [Muscle] order. */
    fun notTrained(week: MuscleWeek): List<Muscle> = Muscle.entries.filter { week.levelOf(it) == MuscleLevel.NONE }

    /**
     * Today tab's mini line under the week strip (DESIGN_LANGUAGE_V2): "Not trained yet: calves,
     * rear delts". Null when nothing is mapped or every mapped muscle was trained; at most [max]
     * names, then "+N more".
     */
    fun todayLine(week: MuscleWeek, max: Int = 4): String? {
        if (week.isAllUnmapped) return null
        val list = notTrained(week)
        if (list.isEmpty()) return null
        if (Muscle.entries.none { week.levelOf(it) != MuscleLevel.NONE && week.levelOf(it) != MuscleLevel.UNMAPPED }) {
            return "No muscles trained this week yet"
        }
        val names = list.take(max).joinToString { it.label.lowercase() }
        val more = if (list.size > max) " +${list.size - max} more" else ""
        return "Not trained yet: $names$more"
    }

    fun notTrainedLine(week: MuscleWeek): String {
        if (week.isAllUnmapped) return "Muscles aren't mapped for your exercises yet, so nothing is marked as trained."
        val list = notTrained(week)
        return if (list.isEmpty()) "Every mapped muscle trained this week." else "Not trained yet: ${list.joinToString { it.label }}"
    }

    /** "Quads · 9 sets" or why there are none. */
    fun detailHeadline(week: MuscleWeek, muscle: Muscle): String = when (week.levelOf(muscle)) {
        MuscleLevel.UNMAPPED -> "${muscle.label} · not mapped yet"
        else -> {
            val sets = week.setsOf(muscle)
            if (sets <= 0f) "${muscle.label} · no sets this week" else "${muscle.label} · ${setsPhrase(sets)}"
        }
    }
}

/**
 * Progress tab "This week" card: the body map, the legend, the text list, "Not trained yet" and,
 * after tapping a region (or picking it with TalkBack's actions), that region's exercises and sets.
 *
 * @param week null while loading.
 */
@Composable
fun ThisWeekMuscleCard(week: MuscleWeek?, modifier: Modifier = Modifier) {
    val tokens = GymTrackTheme.tokens
    val c = tokens.colors
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = selectedId?.let(Muscle::fromId)
    AppCard(modifier.fillMaxWidth().testTag(THIS_WEEK_CARD_TAG), seed = 73) {
        Text(
            text = "This week",
            style = tokens.type.title,
            color = c.ink,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = "Working sets per muscle, Monday to Sunday",
            style = tokens.type.small,
            color = c.inkMuted,
        )
        Spacer(Modifier.height(tokens.spacing.sm))
        if (week == null) {
            Text("Loading…", style = tokens.type.small, color = c.inkMuted)
            return@AppCard
        }
        val levels = week.levels
        BodyMap(
            levels = levels,
            view = BodyMapView.BOTH,
            highlight = selected,
            onRegionTap = { m -> selectedId = if (selectedId == m.id) null else m.id },
            modifier = Modifier.widthIn(max = 340.dp).fillMaxWidth().align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(tokens.spacing.xs))
        BodyMapLegend(showUnmapped = levels.values.any { it == MuscleLevel.UNMAPPED })
        if (selected != null) {
            Spacer(Modifier.height(tokens.spacing.sm))
            RegionDetail(week, selected)
        }
        Spacer(Modifier.height(tokens.spacing.sm))
        Text(
            text = ThisWeekText.notTrainedLine(week),
            style = tokens.type.body,
            color = c.ink,
            modifier = Modifier.testTag(THIS_WEEK_NOT_TRAINED_TAG),
        )
        if (!week.isAllUnmapped) {
            BodyMapLevelList(levels, Modifier.padding(top = tokens.spacing.xxs))
        }
        if (selected == null && bodyMapMuscles(BodyMapView.BOTH).isNotEmpty()) {
            Spacer(Modifier.height(tokens.spacing.xxs))
            Text("Tap a muscle to see its exercises.", style = tokens.type.small, color = c.inkMuted)
        }
    }
}

@Composable
private fun RegionDetail(week: MuscleWeek, muscle: Muscle) {
    val tokens = GymTrackTheme.tokens
    val c = tokens.colors
    val exercises = remember(week, muscle) { week.exercises[muscle].orEmpty().sortedByDescending { it.sets } }
    Column(
        Modifier
            .fillMaxWidth()
            .testTag(THIS_WEEK_DETAIL_TAG)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(ThisWeekText.detailHeadline(week, muscle), style = tokens.type.body, color = c.ink)
        exercises.forEachIndexed { index, ex ->
            if (index > 0) AppDivider(seed = index * 13)
            Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = ex.exercise,
                    style = tokens.type.body,
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(setsPhrase(ex.sets), style = tokens.type.small, color = c.inkMuted)
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "This week · demo", widthDp = 360)
@Composable
private fun ThisWeekDemoPreview(
    @androidx.compose.ui.tooling.preview.PreviewParameter(com.example.gymtrack.core.ui.components.ThemeTokensProvider::class)
    tokens: com.example.gymtrack.core.ui.theme.GymTrackTokens,
) = com.example.gymtrack.core.ui.theme.GymTrackTheme(tokens = tokens) {
    ThisWeekMuscleCard(com.example.gymtrack.core.ui.components.BodyMapDemoWeek)
}

@androidx.compose.ui.tooling.preview.Preview(name = "This week · unmapped", widthDp = 360)
@Composable
private fun ThisWeekUnmappedPreview(
    @androidx.compose.ui.tooling.preview.PreviewParameter(com.example.gymtrack.core.ui.components.ThemeTokensProvider::class)
    tokens: com.example.gymtrack.core.ui.theme.GymTrackTokens,
) = com.example.gymtrack.core.ui.theme.GymTrackTheme(tokens = tokens) {
    ThisWeekMuscleCard(MuscleWeek.unmapped)
}
