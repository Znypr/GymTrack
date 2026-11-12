package com.example.gymtrack.feature.today

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.gymtrack.core.data.Category
import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.data.Settings
import com.example.gymtrack.core.ui.components.AppBottomSheet
import com.example.gymtrack.core.ui.components.AppDivider
import com.example.gymtrack.core.ui.components.AppIcon
import com.example.gymtrack.core.ui.components.AppIconView
import com.example.gymtrack.core.ui.components.InfoLine
import com.example.gymtrack.core.ui.components.PrimaryButton
import com.example.gymtrack.core.ui.components.activeMark
import com.example.gymtrack.core.ui.theme.GymTrackTheme
import com.example.gymtrack.core.ui.theme.sketchBorder
import com.example.gymtrack.feature.history.WorkoutRow
import com.example.gymtrack.feature.history.todayRecentRowTestTag
import com.example.gymtrack.feature.home.CategoryChoiceRow
import com.example.gymtrack.feature.home.TextAction
import com.example.gymtrack.feature.home.WorkoutPrefill
import com.example.gymtrack.feature.home.WorkoutRowFacts
import com.example.gymtrack.feature.home.prefillFromLast
import com.example.gymtrack.feature.home.prefillPreview
import com.example.gymtrack.feature.home.prefillsFromLast
import com.example.gymtrack.feature.home.screenBackground
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

const val TODAY_START_PRIMARY_TEST_TAG = "today-start-primary"
const val TODAY_START_BLANK_TEST_TAG = "today-start-blank"
const val TODAY_START_PREVIEW_TEST_TAG = "today-start-preview"
const val TODAY_OTHER_WORKOUT_TEST_TAG = "today-other-workout"
const val TODAY_SEE_ALL_TEST_TAG = "today-see-all"
const val TODAY_RESTORE_BACKUP_TEST_TAG = "today-restore-backup"
const val TODAY_NOT_TRAINED_TEST_TAG = "today-not-trained"
const val TODAY_WEEK_STRIP_TEST_TAG = "today-week-strip"

/**
 * Today tab (DESIGN_LANGUAGE_V2 "Today tab"). Top, read-only: the date and this week's strip.
 * Bottom, thumb zone: Recent (last 3 + See all), the Next up line, a big Start <Category>,
 * "Other workout ▾" and "Start blank". First launch shows the empty state instead (#294).
 *
 * [onStart] with null starts a blank workout. [onStartFromLast] starts a workout prefilled with
 * the exercise names of the last workout of that category (#305); the primary button uses it when
 * the guessed next category has one. Every start goes through the shell's start sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    notes: List<NoteLine>,
    loaded: Boolean,
    rowFacts: Map<Long, WorkoutRowFacts>,
    finishedTimestamps: Set<Long>,
    settings: Settings,
    today: LocalDate,
    hasActiveWorkout: Boolean,
    onStart: (Category?) -> Unit,
    onStartFromLast: (WorkoutPrefill) -> Unit,
    onOpen: (NoteLine) -> Unit,
    onSeeAll: () -> Unit,
    onRestoreBackup: () -> Unit,
    modifier: Modifier = Modifier,
    notTrainedLine: String? = null,
) {
    val tokens = GymTrackTheme.tokens
    val zone = remember { ZoneId.systemDefault() }
    val locale = remember { Locale.getDefault() }
    val dateLine = remember(today) { today.format(DateTimeFormatter.ofPattern("EEE d MMM", locale)) }
    var showOther by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .screenBackground(tokens)
            .padding(horizontal = tokens.spacing.md),
    ) {
        Spacer(Modifier.height(tokens.spacing.md))
        InfoLine(listOf(dateLine))
        if (!loaded) return@Column

        if (notes.isEmpty() && !hasActiveWorkout) {
            EmptyToday(onStart = { onStart(null) }, onRestoreBackup = onRestoreBackup)
            return@Column
        }

        val week = remember(notes, today) { weekStrip(notes, today, zone) }
        Spacer(Modifier.height(tokens.spacing.sm))
        WeekStrip(week, locale)
        if (notTrainedLine != null) {
            Spacer(Modifier.height(tokens.spacing.xs))
            Text(
                notTrainedLine,
                style = tokens.type.small,
                color = tokens.colors.inkMuted,
                modifier = Modifier.fillMaxWidth().testTag(TODAY_NOT_TRAINED_TEST_TAG),
            )
        }

        val categoryNames = remember(settings.categories) { settings.categories.map { it.name } }
        val next = remember(notes, categoryNames, today) { nextUp(notes, categoryNames, today, zone) }
        val nextCategory = next?.let { n ->
            settings.categories.firstOrNull { it.name.equals(n.category, ignoreCase = true) }
                ?: notes.lastOrNull { it.categoryName.equals(n.category, ignoreCase = true) }
                    ?.let { Category(n.category, it.categoryColor ?: 0xFF808080) }
        }
        // Prefills are computed ahead of the tap, so tap → editor does no parsing (#305: < 100 ms).
        val prefills = remember(notes, settings.categories) { prefillsFromLast(notes, settings.categories) }
        val nextPrefill = remember(notes, nextCategory, prefills) {
            nextCategory?.let { c ->
                prefills[c.name.trim().lowercase()] ?: prefillFromLast(notes, c.name, settings.categories)
            }
        }
        val recent = remember(notes) { recentWorkouts(notes) }
        val dayFormatter = remember { DateTimeFormatter.ofPattern("EEE d MMM", locale) }

        // Everything tappable lives below this point (README rule 2).
        Spacer(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState(), reverseScrolling = true)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("RECENT", style = tokens.type.label, color = tokens.colors.inkMuted, modifier = Modifier.weight(1f))
                TextAction("See all", onClick = onSeeAll, icon = null, modifier = Modifier.testTag(TODAY_SEE_ALL_TEST_TAG))
            }
            recent.forEach { note ->
                WorkoutRow(
                    note = note,
                    facts = rowFacts[note.timestamp],
                    dateLabel = Instant.ofEpochMilli(note.timestamp).atZone(zone).format(dayFormatter),
                    unfinished = note.timestamp !in finishedTimestamps,
                    settings = settings,
                    onClick = { onOpen(note) },
                    modifier = Modifier.testTag(todayRecentRowTestTag(note.timestamp)),
                )
            }
            AppDivider(Modifier.padding(vertical = tokens.spacing.xs))
            if (next != null) {
                Text(
                    nextUpLine(next, locale),
                    style = tokens.type.small,
                    color = tokens.colors.inkMuted,
                    modifier = Modifier.padding(bottom = tokens.spacing.xs),
                )
            }
            if (nextCategory != null) {
                if (nextPrefill != null) {
                    // What the one tap brings: names only, so nothing is a surprise in the editor.
                    Text(
                        prefillPreview(nextPrefill.exercises),
                        style = tokens.type.small,
                        color = tokens.colors.inkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = tokens.spacing.xs).testTag(TODAY_START_PREVIEW_TEST_TAG),
                    )
                }
                PrimaryButton(
                    text = if (nextPrefill != null) "Start from last ${nextPrefill.category.name}" else "Start ${nextCategory.name}",
                    onClick = { if (nextPrefill != null) onStartFromLast(nextPrefill) else onStart(nextCategory) },
                    icon = AppIcon.RESUME,
                    modifier = Modifier.fillMaxWidth().testTag(TODAY_START_PRIMARY_TEST_TAG),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextAction(
                        "Other workout ▾",
                        onClick = { showOther = true },
                        modifier = Modifier.testTag(TODAY_OTHER_WORKOUT_TEST_TAG),
                    )
                    TextAction(
                        "Start blank",
                        onClick = { onStart(null) },
                        icon = AppIcon.ADD,
                        modifier = Modifier.testTag(TODAY_START_BLANK_TEST_TAG),
                    )
                }
            } else {
                // No category history yet: the blank start is the primary; nothing shown twice.
                PrimaryButton(
                    text = "Start workout",
                    onClick = { onStart(null) },
                    icon = AppIcon.ADD,
                    modifier = Modifier.fillMaxWidth().testTag(TODAY_START_BLANK_TEST_TAG),
                )
                TextAction(
                    "Other workout ▾",
                    onClick = { showOther = true },
                    modifier = Modifier.testTag(TODAY_OTHER_WORKOUT_TEST_TAG),
                )
            }
            Spacer(Modifier.height(tokens.spacing.xs))
        }

        if (showOther) {
            AppBottomSheet(onDismissRequest = { showOther = false }) {
                Column(Modifier.fillMaxWidth().padding(bottom = tokens.spacing.lg)) {
                    settings.categories
                        .filterNot { it.name.equals(nextCategory?.name, ignoreCase = true) }
                        .forEach { category ->
                            val prefill = prefills[category.name.trim().lowercase()]
                            CategoryChoiceRow(
                                category,
                                label = if (prefill != null) "Start from last ${category.name}" else "Start ${category.name}",
                                detail = prefill?.let { prefillPreview(it.exercises) },
                                onClick = {
                                    showOther = false
                                    if (prefill != null) onStartFromLast(prefill) else onStart(category)
                                },
                            )
                        }
                }
            }
        }
    }
}

/** "Next up: Pull · usually Tue/Fri · last 5 days ago". Parts without data are dropped. */
internal fun nextUpLine(next: NextUp, locale: Locale): String = buildList {
    add("Next up: ${next.category}")
    if (next.usualDays.isNotEmpty()) {
        add("usually " + next.usualDays.joinToString("/") { it.getDisplayName(TextStyle.SHORT, locale) })
    }
    next.lastDaysAgo?.let { days ->
        add(
            when (days) {
                0L -> "last today"
                1L -> "last yesterday"
                else -> "last $days days ago"
            },
        )
    }
}.joinToString(" · ")

@Composable
private fun WeekStrip(days: List<WeekDay>, locale: Locale) {
    val tokens = GymTrackTheme.tokens
    val c = tokens.colors
    Row(
        Modifier.fillMaxWidth().testTag(TODAY_WEEK_STRIP_TEST_TAG),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        days.forEach { day ->
            val name = day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale)
            val status = when (day.status) {
                DayStatus.DONE -> "done"
                DayStatus.PLANNED -> "planned"
                DayStatus.REST -> "rest"
            }
            Column(
                Modifier
                    .weight(1f)
                    .activeMark(day.isToday, tokens, seed = day.date.dayOfMonth)
                    .padding(vertical = tokens.spacing.xxs)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "${day.date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)}, " +
                            "$status${if (day.isToday) ", today" else ""}"
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    name,
                    style = tokens.type.small,
                    color = if (day.isToday) c.ink else c.inkMuted,
                    fontWeight = if (day.isToday && !tokens.isNotebook) FontWeight.SemiBold else null,
                )
                Spacer(Modifier.height(4.dp))
                Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                    when (day.status) {
                        DayStatus.DONE -> AppIconView(AppIcon.CHECK, contentDescription = null, tint = c.ink, size = 20.dp)
                        DayStatus.PLANNED -> PlannedRing()
                        DayStatus.REST -> Text("–", style = tokens.type.small, color = c.inkMuted, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

/** An empty ring: planned, not done yet (shape, not colour). */
@Composable
private fun PlannedRing() {
    val tokens = GymTrackTheme.tokens
    val ring = if (tokens.isNotebook) {
        Modifier.sketchBorder(tokens.colors.ink, seed = 9, strokeWidth = 1.4.dp, cornerRadius = 8.dp)
    } else {
        Modifier.border(1.5.dp, tokens.colors.ink, CircleShape)
    }
    Box(Modifier.size(16.dp).then(ring))
}

/** First launch (#294): what the app does, the set format, Start, and Restore a backup. */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.EmptyToday(
    onStart: () -> Unit,
    onRestoreBackup: () -> Unit,
) {
    val tokens = GymTrackTheme.tokens
    val c = tokens.colors
    Spacer(Modifier.weight(1f))
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs)) {
        Text("Your training log", style = tokens.type.title, color = c.ink)
        Text(
            "Type an exercise, then each set as reps × weight. The timer runs while you train.",
            style = tokens.type.body,
            color = c.inkMuted,
        )
        Text("8x 80 = 8 reps at 80", style = tokens.type.setNumber, color = c.ink)
        Spacer(Modifier.height(tokens.spacing.sm))
        PrimaryButton(
            text = "Start workout",
            onClick = onStart,
            icon = AppIcon.ADD,
            modifier = Modifier.fillMaxWidth().testTag(TODAY_START_BLANK_TEST_TAG),
        )
        TextAction(
            "Restore a backup",
            onClick = onRestoreBackup,
            icon = AppIcon.SETTINGS,
            modifier = Modifier.align(Alignment.CenterHorizontally).testTag(TODAY_RESTORE_BACKUP_TEST_TAG),
        )
        Spacer(Modifier.height(tokens.spacing.xs))
    }
}
