package com.example.gymtrack.feature.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.gymtrack.core.data.Settings
import com.example.gymtrack.core.ui.theme.GymTrackTheme
import com.example.gymtrack.feature.editor.components.EditorAccessoryBar
import com.example.gymtrack.feature.editor.components.EditorListSection
import com.example.gymtrack.feature.editor.components.EditorMenuPage
import com.example.gymtrack.feature.editor.components.EditorSetKeypad
import com.example.gymtrack.feature.editor.components.EditorTopLine
import com.example.gymtrack.feature.editor.components.LearningsPopup
import com.example.gymtrack.feature.editor.components.rememberRestTimer
import com.example.gymtrack.feature.editor.components.rememberWorkoutTimer
import kotlinx.coroutines.launch

/**
 * Editor v2 (docs/design/EDITOR_V2.md): a read-only top line, the compact list, and everything
 * actionable at the bottom in the thumb zone (accessory bar + keypad). Storage is unchanged.
 */
@Composable
fun NoteEditor(
    viewModel: EditorViewModel,
    settings: Settings,
    isLastNote: Boolean,
    onCancel: () -> Unit,
    onSaveSuccess: () -> Unit,
    /** Category picked on Today for a NEW workout (null = the first category). Ignored when reopening. */
    chosenCategoryName: String? = null,
    /** Explicit Finish of the active workout (not Back); receives the workout's timestamp. */
    onWorkoutFinished: (Long) -> Unit = {},
) {
    val note by viewModel.uiState.collectAsState()
    val saveError by viewModel.saveError.collectAsState()
    val isEditingExisting = viewModel.currentId != -1L
    val startTimerOnOpen = remember(viewModel) { viewModel.currentId == -1L }

    if (isEditingExisting && note == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val state = rememberNoteEditorState(viewModel, settings, note, onSaveSuccess)
    SideEffect { state.onWorkoutFinished = onWorkoutFinished }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(settings.defaultWeightUnit) {
        viewModel.currentDefaultWeightUnit = settings.defaultWeightUnit
    }

    LaunchedEffect(isLastNote) { viewModel.isActiveWorkout = isLastNote }

    // #304 / #349: history is parsed once, off the main thread; Enter only looks it up.
    LaunchedEffect(viewModel) { viewModel.loadSetHistory(settings.defaultWeightUnit, state.noteTimestamp) }
    val setHistory by viewModel.setHistory.collectAsState()
    LaunchedEffect(setHistory) { setHistory?.let(state::useHistory) }
    // #308: last sessions from the derived index, once per editor; the pill only reads them.
    LaunchedEffect(viewModel) { viewModel.loadSuggestionHistory(beforeTimestamp = state.noteTimestamp) }
    val suggestionHistory by viewModel.suggestionHistory.collectAsState()
    LaunchedEffect(suggestionHistory) { suggestionHistory?.let(state::useSuggestionHistory) }

    LaunchedEffect(saveError) {
        saveError?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSaveError()
        }
    }

    // A past workout opens at its top with the keypad closed until a row is tapped (#328). The
    // active workout reopens on its open set row with the keypad up: the keypad effect in
    // EditorListSection scrolls that row into view, so no jump to the top first.
    LaunchedEffect(state.noteTimestamp) {
        state.restoreKeypadOnOpen(isActiveWorkout = isLastNote)
        if (state.keypadRowId == null && state.lines.isNotEmpty()) state.listState.scrollToItem(0)
    }

    // #296: one category rule (EditorCategory). A new workout takes Today's choice or the first
    // category; an existing one keeps exactly what is stored. Idempotent, so it never replaces a
    // category the user picked.
    // Applied after composition (no state writes during it); the first frame shows the same name.
    SideEffect {
        if (!isEditingExisting) viewModel.category.startNew(settings.categories, chosenCategoryName)
    }
    val shownCategoryName = viewModel.category.name
        ?: if (!isEditingExisting) newWorkoutCategory(settings.categories, chosenCategoryName)?.name else null
    val categoryDisplay = categoryDisplay(shownCategoryName, settings.categories)
    var learningsValue by remember(viewModel) { mutableStateOf(TextFieldValue(note?.learnings ?: "")) }
    var showLearnings by remember { mutableStateOf(false) }
    var menuPage by remember { mutableStateOf(EditorMenuPage.CLOSED) }
    // Session-level toggle; a persisted setting waits for the shared SettingsStore (theme session).
    var hintsEnabled by rememberSaveable { mutableStateOf(true) }
    var editAsTextIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(learningsValue.text) { viewModel.currentLearnings = learningsValue.text }
    LaunchedEffect(note) { note?.let { viewModel.currentTitle = it.title } }

    val exitEditor = { state.saveNote(isLastNote = isLastNote, exit = true) }
    BackHandler { exitEditor() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                state.saveNote(isLastNote = isLastNote, exit = false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val timer = if (isLastNote) rememberWorkoutTimer(state.noteTimestamp, startTimerOnOpen) else null
    // Derived so that typing (which changes row texts) does not recompose the whole editor.
    val currentExercise by remember(state) { derivedStateOf { state.currentExerciseIndex } }
    val currentFlag by remember(state) {
        derivedStateOf {
            state.currentExerciseIndex?.let { index ->
                state.lines.getOrNull(index)
                    ?.takeIf { it.text.value.text.isNotBlank() || it.id == state.focusedRowId }
                    ?.flag?.value
            }
        }
    }
    val hasLoggedSet by remember(state) { derivedStateOf { state.hasLoggedSet } }
    val keypadOpen by remember(state) { derivedStateOf { state.keypadRowId != null } }

    val onDeleteSet: (Int) -> Unit = remember(state) {
        { index ->
            state.deleteSetRow(index)?.let { deleted ->
                scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    val result = snackbarHostState.showSnackbar(
                        message = "Set deleted",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) state.undoDelete(deleted)
                }
            }
        }
    }
    val onEditSetAsText: (Int) -> Unit = remember { { index -> editAsTextIndex = index } }
    val onGuardFix: (Long) -> Unit = remember(state) {
        { rowId ->
            state.applyGuardHintFix(rowId)?.let { deleted ->
                scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    val result = snackbarHostState.showSnackbar(
                        message = "Set removed",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) state.undoDelete(deleted)
                }
            }
        }
    }
    val rest = rememberRestTimer(state, active = isLastNote)

    Scaffold(
        containerColor = GymTrackTheme.tokens.colors.bg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
            ) {
                EditorTopLine(
                    category = categoryDisplay,
                    timestamp = state.noteTimestamp,
                    timer = timer,
                    onLongPress = { menuPage = EditorMenuPage.CATEGORIES },
                )
                EditorListSection(
                    state = state,
                    defaultUnit = settings.defaultWeightUnit,
                    hintsEnabled = hintsEnabled,
                    onDeleteSet = onDeleteSet,
                    onEditSetAsText = onEditSetAsText,
                    onGuardFix = onGuardFix,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
                EditorAccessoryBar(
                    onBack = exitEditor,
                    currentFlag = currentFlag,
                    onFlagSelected = { flag -> currentExercise?.let { state.setFlag(it, flag) } },
                    menuPage = menuPage,
                    onMenuPageChange = { menuPage = it },
                    timer = timer,
                    onNotes = { showLearnings = true },
                    categories = settings.categories,
                    currentCategoryName = viewModel.category.name,
                    onCategorySelected = {
                        viewModel.category.choose(it)
                        state.markDirty()
                    },
                    hintsEnabled = hintsEnabled,
                    onHintsToggle = { hintsEnabled = !hintsEnabled },
                    rest = rest,
                    showFinish = isLastNote,
                    finishEnabled = hasLoggedSet,
                    onFinish = {
                        state.closeKeypad()
                        state.saveNote(isLastNote = true, exit = true, finishWorkout = true)
                    },
                )
                if (keypadOpen) {
                    EditorSetKeypad(state)
                }
            }

            LearningsPopup(
                showLearnings = showLearnings,
                learningsValue = learningsValue,
                onDismiss = { showLearnings = false },
                onValueChange = {
                    val textChanged = it.text != learningsValue.text
                    learningsValue = it
                    // A cursor move is not an edit: only typed text marks the workout dirty.
                    if (textChanged) {
                        viewModel.currentLearnings = it.text
                        state.markDirty()
                    }
                },
            )
        }
    }

    editAsTextIndex?.let { index ->
        EditSetAsTextDialog(
            initial = state.lines.getOrNull(index)?.text?.value?.text?.let { splitSetRow(it).entry }.orEmpty(),
            onDismiss = { editAsTextIndex = null },
            onConfirm = { entry ->
                state.replaceSetEntry(index, entry)
                editAsTextIndex = null
            },
        )
    }
}

/**
 * Long-press → "Edit as text": the only IME path for a set, for what the keypad cannot type (a
 * per-set unit like `lb`). Only the entry is editable; indent and times are never shown.
 */
@Composable
private fun EditSetAsTextDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit set") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.replace("\n", "") },
                singleLine = true,
                supportingText = { Text("Reps × weight, e.g. 8x 70 lb") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
