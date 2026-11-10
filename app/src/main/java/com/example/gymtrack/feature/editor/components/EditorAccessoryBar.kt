package com.example.gymtrack.feature.editor.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gymtrack.core.data.Category
import com.example.gymtrack.core.data.ExerciseFlag

internal const val EDITOR_ACCESSORY_BAR_TEST_TAG = "editor-accessory-bar"
internal const val EDITOR_BACK_TEST_TAG = "editor-back"
internal const val NOTE_EDITOR_FINISH_ACTION_TEST_TAG = "note-editor-finish-action"
internal const val EDITOR_FLAG_SEGMENT_TEST_TAG = "editor-flag-segment"
internal const val EDITOR_OVERFLOW_TEST_TAG = "editor-overflow"
internal const val EDITOR_MENU_TIMER_TEST_TAG = "editor-menu-timer"
internal const val EDITOR_MENU_NOTES_TEST_TAG = "editor-menu-notes"
internal const val EDITOR_MENU_CATEGORY_TEST_TAG = "editor-menu-category"
internal const val EDITOR_MENU_HINTS_TEST_TAG = "editor-menu-hints"

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
enum class EditorMenuPage { CLOSED, MAIN, CATEGORIES }

/**
 * Bottom bar in the thumb zone (EDITOR_V2 §4): ‹ Back · flag segment · ⋯ · Finish. It sits right
 * above the keypad (or the IME), and alone at the bottom when neither is open.
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
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag(EDITOR_ACCESSORY_BAR_TEST_TAG),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag(EDITOR_BACK_TEST_TAG)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
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
                    Icon(
                        Icons.Filled.MoreHoriz,
                        contentDescription = "More: timer, notes, category, hints",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                EditorOverflowMenu(
                    page = menuPage,
                    onPageChange = onMenuPageChange,
                    timer = timer,
                    onNotes = onNotes,
                    categories = categories,
                    onCategorySelected = onCategorySelected,
                    hintsEnabled = hintsEnabled,
                    onHintsToggle = onHintsToggle,
                )
            }
            if (showFinish) {
                Button(
                    onClick = onFinish,
                    enabled = finishEnabled,
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier
                        .padding(start = 4.dp, end = 4.dp)
                        .heightIn(min = 44.dp)
                        .testTag(NOTE_EDITOR_FINISH_ACTION_TEST_TAG)
                        .semantics {
                            if (!finishEnabled) stateDescription = "Log a set first"
                        },
                ) {
                    Text("Finish", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** `Bi · L/R · SS`: one tap sets the current exercise and all its sets (EDITOR_V2 §3). */
@Composable
internal fun FlagSegment(
    selected: ExerciseFlag,
    onSelected: (ExerciseFlag) -> Unit,
    modifier: Modifier = Modifier,
) {
    val outline = MaterialTheme.colorScheme.outline
    Row(
        modifier = modifier
            .padding(start = 2.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(percent = 50))
            .border(BorderStroke(1.dp, outline), RoundedCornerShape(percent = 50))
            .selectableGroup()
            .testTag(EDITOR_FLAG_SEGMENT_TEST_TAG)
            .semantics { stateDescription = "Exercise mode: ${selected.spokenLabel()}" },
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExerciseFlag.entries.forEachIndexed { index, flag ->
            val isSelected = flag == selected
            Box(
                modifier = Modifier
                    .widthIn(min = 48.dp)
                    .height(40.dp)
                    .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelected(flag) })
                    .semantics { contentDescription = flag.spokenLabel() }
                    .testTag(editorFlagOptionTestTag(flag))
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = flag.segmentLabel(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (index < ExerciseFlag.entries.lastIndex) {
                Box(Modifier.size(width = 1.dp, height = 40.dp).background(outline))
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
    onCategorySelected: (Category) -> Unit,
    hintsEnabled: Boolean,
    onHintsToggle: () -> Unit,
) {
    DropdownMenu(
        expanded = page != EditorMenuPage.CLOSED,
        onDismissRequest = { onPageChange(EditorMenuPage.CLOSED) },
    ) {
        when (page) {
            EditorMenuPage.MAIN -> {
                if (timer != null) {
                    DropdownMenuItem(
                        text = { Text(if (timer.isRunning) "Pause timer" else "Resume timer") },
                        onClick = {
                            timer.togglePause()
                            onPageChange(EditorMenuPage.CLOSED)
                        },
                        modifier = Modifier.testTag(EDITOR_MENU_TIMER_TEST_TAG),
                    )
                }
                DropdownMenuItem(
                    text = { Text("Notes & learnings") },
                    onClick = {
                        onPageChange(EditorMenuPage.CLOSED)
                        onNotes()
                    },
                    modifier = Modifier.testTag(EDITOR_MENU_NOTES_TEST_TAG),
                )
                DropdownMenuItem(
                    text = { Text("Change category") },
                    onClick = { onPageChange(EditorMenuPage.CATEGORIES) },
                    modifier = Modifier.testTag(EDITOR_MENU_CATEGORY_TEST_TAG),
                )
                DropdownMenuItem(
                    text = { Text(if (hintsEnabled) "Hide keypad hints" else "Show keypad hints") },
                    onClick = {
                        onHintsToggle()
                        onPageChange(EditorMenuPage.CLOSED)
                    },
                    modifier = Modifier.testTag(EDITOR_MENU_HINTS_TEST_TAG),
                )
            }
            EditorMenuPage.CATEGORIES -> {
                Text(
                    "Category",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                HorizontalDivider()
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.name) },
                        leadingIcon = {
                            Box(
                                Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(category.color)),
                            )
                        },
                        onClick = {
                            onCategorySelected(category)
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
