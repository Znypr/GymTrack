package com.example.gymtrack.feature.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtrack.core.data.Category
import com.example.gymtrack.core.data.ExerciseFlag
import com.example.gymtrack.core.ui.components.AppDivider
import com.example.gymtrack.core.ui.components.CategoryKind
import com.example.gymtrack.core.ui.components.CategoryMark
import com.example.gymtrack.core.ui.theme.GymTrackTheme
import com.example.gymtrack.core.ui.theme.SketchShape
import com.example.gymtrack.core.ui.theme.highlighter
import com.example.gymtrack.core.ui.theme.sketchBorder
import com.example.gymtrack.feature.editor.DEFAULT_REST_SECONDS
import com.example.gymtrack.feature.editor.REST_CHOICES_SECONDS
import com.example.gymtrack.feature.editor.formatRest

internal const val EDITOR_ACCESSORY_BAR_TEST_TAG = "editor-accessory-bar"
internal const val EDITOR_BACK_TEST_TAG = "editor-back"
internal const val NOTE_EDITOR_FINISH_ACTION_TEST_TAG = "note-editor-finish-action"
internal const val EDITOR_FLAG_SEGMENT_TEST_TAG = "editor-flag-segment"
internal const val EDITOR_OVERFLOW_TEST_TAG = "editor-overflow"
internal const val EDITOR_MENU_TIMER_TEST_TAG = "editor-menu-timer"
internal const val EDITOR_MENU_NOTES_TEST_TAG = "editor-menu-notes"
internal const val EDITOR_MENU_CATEGORY_TEST_TAG = "editor-menu-category"
internal const val EDITOR_MENU_HINTS_TEST_TAG = "editor-menu-hints"
internal const val EDITOR_MENU_REST_TEST_TAG = "editor-menu-rest"
internal const val EDITOR_MENU_REST_SKIP_TEST_TAG = "editor-menu-rest-skip"
internal const val EDITOR_REST_COUNTDOWN_TEST_TAG = "editor-rest-countdown"

internal fun editorRestChoiceTestTag(seconds: Int?): String = "editor-rest-choice-${seconds ?: "usual"}"

internal fun editorFlagOptionTestTag(flag: ExerciseFlag): String = "editor-flag-option-${flag.name.lowercase()}"

internal fun editorCategoryOptionTestTag(name: String): String = "editor-category-option-$name"

/** Short label of a flag in the segment. Bi is the default and is never drawn on a row. */
internal fun ExerciseFlag.segmentLabel(): String = when (this) {
    ExerciseFlag.BILATERAL -> "Bi"
    ExerciseFlag.UNILATERAL -> "L/R"
    ExerciseFlag.SUPERSET -> "SS"
}

internal fun ExerciseFlag.spokenLabel(): String = when (this) {
    ExerciseFlag.BILATERAL -> "Both sides"
    ExerciseFlag.UNILATERAL -> "One side at a time"
    ExerciseFlag.SUPERSET -> "Superset"
}

/** Which ⋯ menu page is open. Hoisted so a long-press on the top line can open the category page. */
enum class EditorMenuPage { CLOSED, MAIN, CATEGORIES, REST }

/**
 * Bottom bar in the thumb zone (EDITOR_V2 §4): ‹ Back · flag segment · ⋯ · Finish. It sits right
 * above the keypad (or the IME), and alone at the bottom when neither is open. Colours come from
 * the design tokens; Notebook draws it in pencil on the paper.
 */
@Composable
fun EditorAccessoryBar(
    onBack: () -> Unit,
    currentFlag: ExerciseFlag?,
    onFlagSelected: (ExerciseFlag) -> Unit,
    menuPage: EditorMenuPage,
    onMenuPageChange: (EditorMenuPage) -> Unit,
    timer: WorkoutTimerUi?,
    onNotes: () -> Unit,
    categories: List<Category>,
    onCategorySelected: (Category) -> Unit,
    hintsEnabled: Boolean,
    onHintsToggle: () -> Unit,
    showFinish: Boolean,
    finishEnabled: Boolean,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    currentCategoryName: String? = null,
    /** The rest countdown (#349); null for a past workout. */
    rest: RestTimerUi? = null,
) {
    val tokens = GymTrackTheme.tokens
    val c = tokens.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(if (tokens.isNotebook) c.bg else c.surface)
            .testTag(EDITOR_ACCESSORY_BAR_TEST_TAG),
    ) {
        if (tokens.isNotebook) AppDivider(seed = 37) else Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag(EDITOR_BACK_TEST_TAG)) {
                EditorGlyphView(EditorGlyph.BACK, contentDescription = "Back", tint = c.ink)
            }
            if (currentFlag != null) {
                FlagSegment(selected = currentFlag, onSelected = onFlagSelected)
            }
            Spacer(Modifier.weight(1f))
            Box {
                IconButton(
                    onClick = { onMenuPageChange(EditorMenuPage.MAIN) },
                    modifier = Modifier.testTag(EDITOR_OVERFLOW_TEST_TAG),
                ) {
                    // A running rest shows its countdown here, so the bar keeps its width on a
                    // 360 dp phone (#349); the menu behind it has Skip and the rest length.
                    val restLeft = rest?.remainingSeconds ?: 0
                    if (restLeft > 0) {
                        Text(
                            text = formatRest(restLeft),
                            style = tokens.type.small,
                            fontWeight = FontWeight.Bold,
                            color = c.ink,
                            modifier = Modifier
                                .testTag(EDITOR_REST_COUNTDOWN_TEST_TAG)
                                .semantics { contentDescription = "Rest ${formatRest(restLeft)} left. More options" },
                        )
                    } else {
                        EditorGlyphView(EditorGlyph.MORE, contentDescription = "More: notes, timer, category, hints", tint = c.ink)
                    }
                }
                EditorOverflowMenu(
                    page = menuPage,
                    onPageChange = onMenuPageChange,
                    timer = timer,
                    onNotes = onNotes,
                    categories = categories,
                    currentCategoryName = currentCategoryName,
                    onCategorySelected = onCategorySelected,
                    hintsEnabled = hintsEnabled,
                    onHintsToggle = onHintsToggle,
                    rest = rest,
                )
            }
            if (showFinish) {
                FinishPill(enabled = finishEnabled, onClick = onFinish)
            }
        }
    }
}

/**
 * Finish: the action colour (amber fill in Normal, blue pen in Notebook), sized for the 52 dp bar.
 * A thin local version of the shared PrimaryButton, whose 56 dp minimum does not fit here.
 */
@Composable
private fun FinishPill(enabled: Boolean, onClick: () -> Unit) {
    val tokens = GymTrackTheme.tokens
    val c = tokens.colors
    val skin = if (tokens.isNotebook) {
        val shape = SketchShape(seed = 53, cornerRadius = 18.dp)
        Modifier
            .clip(shape)
            .background(c.actionContainer.copy(alpha = 0.75f), shape)
            .sketchBorder(c.action, seed = 53, strokeWidth = 2.dp, cornerRadius = 18.dp)
    } else {
        val shape = RoundedCornerShape(percent = 50)
        Modifier.clip(shape).background(c.actionContainer, shape)
    }
    Box(
        modifier = Modifier
            .padding(start = 4.dp, end = 4.dp)
            .heightIn(min = 44.dp)
            .widthIn(min = 88.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .then(skin)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .testTag(NOTE_EDITOR_FINISH_ACTION_TEST_TAG)
            .semantics { if (!enabled) stateDescription = "Log a set first" }
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Finish",
            color = c.onAction,
            style = if (tokens.isNotebook) tokens.type.title.copy(fontSize = 22.sp) else tokens.type.body.copy(fontSize = 16.sp),
            fontWeight = if (tokens.isNotebook) null else FontWeight.SemiBold,
        )
    }
}

/** `Bi · L/R · SS`: one tap sets the current exercise and all its sets (EDITOR_V2 §3). */
@Composable
internal fun FlagSegment(
    selected: ExerciseFlag,
    onSelected: (ExerciseFlag) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = GymTrackTheme.tokens
    val c = tokens.colors
    val notebook = tokens.isNotebook
    val outline = if (notebook) {
        Modifier.sketchBorder(c.ink, seed = 61, strokeWidth = 1.4.dp, cornerRadius = 20.dp)
    } else {
        Modifier
            .clip(RoundedCornerShape(percent = 50))
            .border(1.dp, c.hairline, RoundedCornerShape(percent = 50))
    }
    Row(
        modifier = modifier
            .padding(start = 2.dp)
            .height(40.dp)
            .then(outline)
            .selectableGroup()
            .testTag(EDITOR_FLAG_SEGMENT_TEST_TAG)
            .semantics { stateDescription = "Exercise mode: ${selected.spokenLabel()}" },
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExerciseFlag.entries.forEachIndexed { index, flag ->
            val isSelected = flag == selected
            val mark = when {
                !isSelected -> Modifier
                // Notebook: the chosen mode is highlighted like a word on paper.
                notebook -> Modifier.highlighter(c.active, seed = 70 + index)
                else -> Modifier.background(c.surface2)
            }
            Box(
                modifier = Modifier
                    .widthIn(min = 48.dp)
                    .height(40.dp)
                    .then(mark)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelected(flag) })
                    .semantics { contentDescription = flag.spokenLabel() }
                    .testTag(editorFlagOptionTestTag(flag))
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = flag.segmentLabel(),
                    style = if (notebook) tokens.type.body else tokens.type.small,
                    // Never colour alone: the chosen one is also bold.
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) c.ink else c.inkMuted,
                )
            }
            if (index < ExerciseFlag.entries.lastIndex) {
                Box(Modifier.size(width = 1.dp, height = 24.dp).background(if (notebook) c.inkMuted.copy(alpha = 0.6f) else c.hairline))
            }
        }
    }
}

@Composable
private fun EditorOverflowMenu(
    page: EditorMenuPage,
    onPageChange: (EditorMenuPage) -> Unit,
    timer: WorkoutTimerUi?,
    onNotes: () -> Unit,
    categories: List<Category>,
    currentCategoryName: String?,
    onCategorySelected: (Category) -> Unit,
    hintsEnabled: Boolean,
    onHintsToggle: () -> Unit,
    rest: RestTimerUi?,
) {
    val tokens = GymTrackTheme.tokens
    val c = tokens.colors
    val itemStyle = tokens.type.body
    DropdownMenu(
        expanded = page != EditorMenuPage.CLOSED,
        onDismissRequest = { onPageChange(EditorMenuPage.CLOSED) },
        containerColor = if (tokens.isNotebook) c.bg else c.surface,
    ) {
        when (page) {
            EditorMenuPage.MAIN -> {
                if (rest != null && rest.isRunning) {
                    DropdownMenuItem(
                        text = { Text("Skip rest (${formatRest(rest.remainingSeconds)} left)", style = itemStyle, color = c.ink) },
                        onClick = {
                            rest.skip()
                            onPageChange(EditorMenuPage.CLOSED)
                        },
                        modifier = Modifier.testTag(EDITOR_MENU_REST_SKIP_TEST_TAG),
                    )
                }
                // #297: notes open from here, with a notes icon (not ☰, which reads as navigation).
                DropdownMenuItem(
                    text = { Text("Notes & learnings", style = itemStyle, color = c.ink) },
                    leadingIcon = { EditorGlyphView(EditorGlyph.NOTES, contentDescription = null, tint = c.ink) },
                    onClick = {
                        onPageChange(EditorMenuPage.CLOSED)
                        onNotes()
                    },
                    modifier = Modifier.testTag(EDITOR_MENU_NOTES_TEST_TAG),
                )
                if (timer != null) {
                    DropdownMenuItem(
                        text = { Text(if (timer.isRunning) "Pause timer" else "Resume timer", style = itemStyle, color = c.ink) },
                        onClick = {
                            timer.togglePause()
                            onPageChange(EditorMenuPage.CLOSED)
                        },
                        modifier = Modifier.testTag(EDITOR_MENU_TIMER_TEST_TAG),
                    )
                }
                DropdownMenuItem(
                    text = { Text("Change category", style = itemStyle, color = c.ink) },
                    onClick = { onPageChange(EditorMenuPage.CATEGORIES) },
                    modifier = Modifier.testTag(EDITOR_MENU_CATEGORY_TEST_TAG),
                )
                DropdownMenuItem(
                    text = { Text(if (hintsEnabled) "Hide keypad hints" else "Show keypad hints", style = itemStyle, color = c.ink) },
                    onClick = {
                        onHintsToggle()
                        onPageChange(EditorMenuPage.CLOSED)
                    },
                    modifier = Modifier.testTag(EDITOR_MENU_HINTS_TEST_TAG),
                )
                if (rest != null) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (rest.enabled) "Rest timer: ${formatRest(rest.overrideSeconds ?: rest.usualSeconds ?: DEFAULT_REST_SECONDS)}" else "Rest timer after each set",
                                style = itemStyle,
                                color = c.ink,
                            )
                        },
                        onClick = {
                            if (rest.enabled) {
                                onPageChange(EditorMenuPage.REST)
                            } else {
                                rest.setEnabled(true)
                                onPageChange(EditorMenuPage.CLOSED)
                            }
                        },
                        modifier = Modifier.testTag(EDITOR_MENU_REST_TEST_TAG),
                    )
                }
            }
            EditorMenuPage.REST -> if (rest != null) {
                Text(
                    "Rest after each set",
                    style = tokens.type.small,
                    color = c.inkMuted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                AppDivider()
                val usualLabel = rest.usualSeconds?.let { "Usual for this exercise (${formatRest(it)})" } ?: "Default (${formatRest(DEFAULT_REST_SECONDS)})"
                (listOf<Int?>(null) + REST_CHOICES_SECONDS).forEach { seconds ->
                    val isCurrent = rest.overrideSeconds == seconds
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (seconds == null) usualLabel else formatRest(seconds),
                                style = itemStyle,
                                color = c.ink,
                                fontWeight = if (isCurrent) FontWeight.Bold else null,
                            )
                        },
                        onClick = {
                            rest.choose(seconds)
                            onPageChange(EditorMenuPage.CLOSED)
                        },
                        modifier = Modifier.testTag(editorRestChoiceTestTag(seconds)),
                    )
                }
                DropdownMenuItem(
                    text = { Text("Turn rest timer off", style = itemStyle, color = c.ink) },
                    onClick = {
                        rest.setEnabled(false)
                        onPageChange(EditorMenuPage.CLOSED)
                    },
                )
            }
            EditorMenuPage.CATEGORIES -> {
                Text(
                    "Category",
                    style = tokens.type.small,
                    color = c.inkMuted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                AppDivider()
                categories.forEach { category ->
                    val kind = CategoryKind.fromName(category.name)
                    val isCurrent = category.name == currentCategoryName
                    DropdownMenuItem(
                        text = {
                            Text(
                                category.name,
                                style = itemStyle,
                                color = c.ink,
                                fontWeight = if (isCurrent) FontWeight.Bold else null,
                            )
                        },
                        leadingIcon = {
                            CategoryMark(
                                kind = kind,
                                label = category.name,
                                customColor = if (kind == CategoryKind.OTHER) Color(category.color) else null,
                            )
                        },
                        onClick = {
                            // Picking the category it already has changes nothing (no dirty save).
                            if (!isCurrent) onCategorySelected(category)
                            onPageChange(EditorMenuPage.CLOSED)
                        },
                        modifier = Modifier.testTag(editorCategoryOptionTestTag(category.name)),
                    )
                }
            }
            EditorMenuPage.CLOSED -> Unit
        }
    }
}
