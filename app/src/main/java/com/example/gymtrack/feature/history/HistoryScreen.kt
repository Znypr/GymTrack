package com.example.gymtrack.feature.history

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gymtrack.core.data.Category
import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.data.Settings
import com.example.gymtrack.core.ui.components.AppDivider
import com.example.gymtrack.core.ui.components.CategoryKind
import com.example.gymtrack.core.ui.components.CategoryMark
import com.example.gymtrack.core.ui.components.activeMark
import com.example.gymtrack.core.ui.theme.GymTrackTheme
import com.example.gymtrack.core.ui.theme.sketchBorder
import com.example.gymtrack.feature.home.LegacyCsvImportProgress
import com.example.gymtrack.feature.home.PendingWorkoutDelete
import com.example.gymtrack.feature.home.TextAction
import com.example.gymtrack.feature.home.WorkoutRowFacts
import com.example.gymtrack.feature.home.canMarkFinished
import com.example.gymtrack.feature.home.deleteNeedsConfirmation
import com.example.gymtrack.feature.home.deletedWorkoutsMessage
import com.example.gymtrack.feature.home.screenBackground
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

const val HISTORY_LIST_TEST_TAG = "history-list"
const val HISTORY_SEARCH_TEST_TAG = "history-search"
const val HISTORY_SORT_TEST_TAG = "history-sort"
const val HISTORY_DELETE_TEST_TAG = "history-delete"
const val HISTORY_EXPORT_TEST_TAG = "history-export"
const val HISTORY_MARK_FINISHED_TEST_TAG = "history-mark-finished"
const val HISTORY_CLEAR_SELECTION_TEST_TAG = "history-clear-selection"
fun historyCategoryChipTestTag(name: String?): String = "history-chip-${name ?: "all"}"

/**
 * History tab (DESIGN_LANGUAGE_V2 "History tab"): month sections of compact rows on top, and
 * every control at the bottom in the thumb zone: category chips, the search field and the sort
 * control (it always says its current order, #297). Long-press selects; the selection bar then
 * replaces the controls with Delete (Undo, #292), Export and, for unfinished workouts,
 * Mark finished (#293).
 */
@Composable
fun HistoryScreen(
    notes: List<NoteLine>,
    rowFacts: Map<Long, WorkoutRowFacts>,
    finishedTimestamps: Set<Long>,
    settings: Settings,
    onOpen: (NoteLine) -> Unit,
    onDelete: (Set<NoteLine>) -> Unit,
    onExport: (Set<NoteLine>, onDone: () -> Unit) -> Unit,
    onMarkFinished: (Set<NoteLine>) -> Unit,
    modifier: Modifier = Modifier,
    pendingDelete: PendingWorkoutDelete? = null,
    onUndoDelete: () -> Unit = {},
    onDeleteFinal: () -> Unit = {},
    showLegacyCsvImport: Boolean = false,
    legacyCsvImportProgress: LegacyCsvImportProgress? = null,
    onImport: (List<Uri>) -> Unit = {},
) {
    val tokens = GymTrackTheme.tokens
    val c = tokens.colors
    var query by rememberSaveable { mutableStateOf("") }
    var categoryFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var newestFirst by rememberSaveable { mutableStateOf(true) }
    var selectedIds by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    var confirmDeleteAll by remember { mutableStateOf<Set<NoteLine>?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val filter = HistoryFilter(query, categoryFilter, newestFirst)
    val visible = remember(notes, filter) { filterHistory(notes, filter) }
    val zone = remember { ZoneId.systemDefault() }
    val sections = remember(visible, zone) { groupByMonth(visible, zone) }
    val selected = remember(notes, selectedIds) {
        val ids = selectedIds.toSet()
        notes.filterTo(LinkedHashSet()) { it.timestamp in ids }
    }
    val selectionMode = selected.isNotEmpty()
    val dayFormatter = remember { DateTimeFormatter.ofPattern("EEE d", Locale.getDefault()) }
    val monthFormatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()) }
    val chipCategories = remember(settings.categories, notes) { chipCategories(settings.categories, notes) }

    fun toggle(note: NoteLine) {
        selectedIds = if (note.timestamp in selectedIds) selectedIds - note.timestamp else selectedIds + note.timestamp
    }

    BackHandler(enabled = selectionMode) { selectedIds = emptyList() }

    // #292: every delete can be undone until the snackbar goes away.
    LaunchedEffect(pendingDelete?.id) {
        val pending = pendingDelete ?: return@LaunchedEffect
        snackbarHostState.currentSnackbarData?.dismiss()
        val result = snackbarHostState.showSnackbar(
            message = deletedWorkoutsMessage(pending.count),
            actionLabel = "Undo",
            withDismissAction = true,
            duration = SnackbarDuration.Long,
        )
        when (result) {
            SnackbarResult.ActionPerformed -> onUndoDelete()
            SnackbarResult.Dismissed -> onDeleteFinal()
        }
    }

    confirmDeleteAll?.let { toDelete ->
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = null },
            title = { Text(if (toDelete.size == 1) "Delete your only workout?" else "Delete all ${toDelete.size} workouts?") },
            text = { Text("Every workout is selected. You can undo right after deleting.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteAll = null
                    onDelete(toDelete)
                    selectedIds = emptyList()
                }) { Text("Delete all", color = c.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = null }) { Text("Cancel") } },
        )
    }

    Column(modifier.fillMaxSize().screenBackground(tokens)) {
        legacyCsvImportProgress?.let { ImportProgress(it) }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (sections.isEmpty()) {
                Text(
                    if (notes.isEmpty()) "No workouts yet" else "No workouts match",
                    style = tokens.type.body,
                    color = c.inkMuted,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(tokens.spacing.lg),
                )
            } else {
                LazyColumn(
                    state = rememberLazyListState(),
                    modifier = Modifier.fillMaxSize().testTag(HISTORY_LIST_TEST_TAG),
                    contentPadding = PaddingValues(vertical = tokens.spacing.xs),
                ) {
                    sections.forEach { (month, monthNotes) ->
                        item(key = "month-$month") {
                            Text(
                                month.format(monthFormatter).uppercase(),
                                style = tokens.type.label,
                                color = c.inkMuted,
                                modifier = Modifier
                                    .padding(horizontal = tokens.spacing.md)
                                    .padding(top = tokens.spacing.md, bottom = tokens.spacing.xxs)
                                    .semantics { heading() },
                            )
                        }
                        items(monthNotes, key = { it.timestamp }) { note ->
                            val isSelected = note.timestamp in selectedIds
                            WorkoutRow(
                                note = note,
                                facts = rowFacts[note.timestamp],
                                dateLabel = Instant.ofEpochMilli(note.timestamp).atZone(zone).format(dayFormatter),
                                unfinished = note.timestamp !in finishedTimestamps,
                                settings = settings,
                                selected = isSelected,
                                onClick = { if (selectionMode) toggle(note) else onOpen(note) },
                                onLongClick = { toggle(note) },
                                modifier = Modifier.testTag(historyRowTestTag(note.timestamp)),
                            )
                        }
                    }
                }
            }
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
        }
        AppDivider()
        if (selectionMode) {
            SelectionBar(
                count = selected.size,
                allSelected = selected.size == notes.size,
                canMarkFinished = selected.all { canMarkFinished(it, it.timestamp in finishedTimestamps) },
                onSelectAll = {
                    selectedIds = if (selected.size == notes.size) emptyList() else visible.map { it.timestamp }
                },
                onClear = { selectedIds = emptyList() },
                onDelete = {
                    if (deleteNeedsConfirmation(selected, notes)) {
                        confirmDeleteAll = selected
                    } else {
                        onDelete(selected)
                        selectedIds = emptyList()
                    }
                },
                onExport = { onExport(selected) { selectedIds = emptyList() } },
                onMarkFinished = {
                    onMarkFinished(selected)
                    selectedIds = emptyList()
                },
            )
        } else {
            Controls(
                chipCategories = chipCategories,
                categoryFilter = categoryFilter,
                onCategory = { categoryFilter = it },
                query = query,
                onQuery = { query = it },
                newestFirst = newestFirst,
                onToggleSort = { newestFirst = !newestFirst },
                showLegacyCsvImport = showLegacyCsvImport,
                importEnabled = legacyCsvImportProgress == null,
                onImport = onImport,
            )
        }
    }
}

/** Settings categories plus any stored category that is not in Settings any more (#296). */
internal fun chipCategories(settingsCategories: List<Category>, notes: List<NoteLine>): List<Category> {
    val known = mutableSetOf<String>()
    val fromSettings = settingsCategories.filter { known.add(it.name.trim().lowercase()) }
    val extra = notes.mapNotNull { note ->
        val name = note.categoryName?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
        if (known.add(name.lowercase())) Category(name, note.categoryColor ?: 0xFF808080) else null
    }
    // Unique by name, ignoring case: the chips are a keyed lazy row.
    return fromSettings + extra
}

@Composable
private fun Controls(
    chipCategories: List<Category>,
    categoryFilter: String?,
    onCategory: (String?) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    newestFirst: Boolean,
    onToggleSort: () -> Unit,
    showLegacyCsvImport: Boolean,
    importEnabled: Boolean,
    onImport: (List<Uri>) -> Unit,
) {
    val tokens = GymTrackTheme.tokens
    val c = tokens.colors
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onImport(uris)
    }
    Column(Modifier.fillMaxWidth().padding(vertical = tokens.spacing.xs)) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = tokens.spacing.md),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item(key = "all") {
                FilterChip(label = "All", selected = categoryFilter == null, onClick = { onCategory(null) }, testTag = historyCategoryChipTestTag(null))
            }
            items(chipCategories, key = { it.name.trim().lowercase() }) { category ->
                FilterChip(
                    label = category.name,
                    selected = categoryFilter.equals(category.name, ignoreCase = true),
                    onClick = { onCategory(if (categoryFilter.equals(category.name, ignoreCase = true)) null else category.name) },
                    testTag = historyCategoryChipTestTag(category.name),
                    mark = { CategoryMark(CategoryKind.fromName(category.name), label = category.name, size = 12.dp, customColor = Color(category.color)) },
                )
            }
            if (showLegacyCsvImport) {
                item(key = "csv") {
                    TextAction(
                        "Import CSV",
                        enabled = importEnabled,
                        onClick = { importLauncher.launch(arrayOf("text/*", "text/csv", "application/vnd.ms-excel")) },
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = tokens.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                placeholder = { Text("Search exercise or note", style = tokens.type.body, color = c.inkMuted) },
                singleLine = true,
                textStyle = tokens.type.body.copy(color = c.ink),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.action,
                    unfocusedBorderColor = c.hairline.takeIf { !tokens.isNotebook } ?: c.inkMuted,
                    cursorColor = c.action,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
                modifier = Modifier.weight(1f).testTag(HISTORY_SEARCH_TEST_TAG),
            )
            Spacer(Modifier.width(tokens.spacing.xs))
            TextAction(
                text = if (newestFirst) "${sortLabel(true)} ↓" else "${sortLabel(false)} ↑",
                onClick = onToggleSort,
                modifier = Modifier.testTag(HISTORY_SORT_TEST_TAG),
            )
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    mark: (@Composable () -> Unit)? = null,
) {
    val tokens = GymTrackTheme.tokens
    val c = tokens.colors
    val outline = if (tokens.isNotebook) {
        Modifier.sketchBorder(if (selected) c.ink else c.inkMuted, seed = label.hashCode(), strokeWidth = if (selected) 2.dp else 1.2.dp, cornerRadius = 16.dp)
    } else {
        Modifier.activeMark(selected, tokens).then(
            if (selected) Modifier else Modifier.border(1.dp, c.hairline, RoundedCornerShape(tokens.radii.small)),
        )
    }
    Row(
        modifier = Modifier
            .heightIn(min = tokens.spacing.touchTarget)
            .padding(vertical = tokens.spacing.xxs)
            .then(outline)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .testTag(testTag)
            .padding(horizontal = tokens.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (mark != null) {
            mark()
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            style = tokens.type.small,
            color = if (selected) c.ink else c.inkMuted,
            fontWeight = if (selected && !tokens.isNotebook) FontWeight.SemiBold else null,
        )
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    allSelected: Boolean,
    canMarkFinished: Boolean,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onMarkFinished: () -> Unit,
) {
    val tokens = GymTrackTheme.tokens
    val c = tokens.colors
    Column(Modifier.fillMaxWidth().padding(vertical = tokens.spacing.xs)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = tokens.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$count selected",
                style = tokens.type.body,
                color = c.ink,
                modifier = Modifier.weight(1f).padding(start = tokens.spacing.sm),
            )
            TextAction(if (allSelected) "Select none" else "Select all", onClick = onSelectAll)
            TextAction("Cancel", onClick = onClear, modifier = Modifier.testTag(HISTORY_CLEAR_SELECTION_TEST_TAG))
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = tokens.spacing.xs),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TextAction("Delete", onClick = onDelete, color = c.error, modifier = Modifier.testTag(HISTORY_DELETE_TEST_TAG))
            TextAction("Export", onClick = onExport, modifier = Modifier.testTag(HISTORY_EXPORT_TEST_TAG))
            if (canMarkFinished) {
                TextAction(
                    "Mark finished",
                    onClick = onMarkFinished,
                    color = c.action,
                    bold = true,
                    modifier = Modifier.testTag(HISTORY_MARK_FINISHED_TEST_TAG),
                )
            }
        }
    }
}

@Composable
private fun ImportProgress(progress: LegacyCsvImportProgress) {
    val tokens = GymTrackTheme.tokens
    Column(
        Modifier.fillMaxWidth().padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("${progress.phase} · ${progress.detailMessage()}", style = tokens.type.small, color = tokens.colors.ink)
        LinearProgressIndicator(progress = { progress.progressFraction }, modifier = Modifier.fillMaxWidth())
    }
}
