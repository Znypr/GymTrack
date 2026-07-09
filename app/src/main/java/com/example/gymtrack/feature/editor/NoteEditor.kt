package com.example.gymtrack.feature.editor

import android.annotation.SuppressLint
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.gymtrack.core.data.Category
import com.example.gymtrack.core.data.Settings
import com.example.gymtrack.feature.editor.components.EditorHeroHeader
import com.example.gymtrack.feature.editor.components.EditorListSection
import com.example.gymtrack.feature.editor.components.LearningsPopup
import com.example.gymtrack.feature.editor.components.NoteTimer

internal const val NOTE_EDITOR_FINISH_ACTION_TEST_TAG = "note-editor-finish-action"
internal const val NOTE_EDITOR_TIMER_CONTROLS_TEST_TAG = "note-editor-timer-controls"
internal const val NOTE_EDITOR_WEIGHT_UNIT_INDICATOR_TEST_TAG = "note-editor-weight-unit-indicator"

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditor(
    viewModel: EditorViewModel,
    settings: Settings,
    isLastNote: Boolean,
    onCancel: () -> Unit,
    onSaveSuccess: () -> Unit,
) {
    val note by viewModel.uiState.collectAsState()
    val saveError by viewModel.saveError.collectAsState()
    val exerciseSuggestions by viewModel.exerciseSuggestions.collectAsState()
    val isEditingExisting = viewModel.currentId != -1L
    val startTimerOnOpen = remember(viewModel) { viewModel.currentId == -1L }

    if (isEditingExisting && note == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val state = rememberNoteEditorState(viewModel, settings, note, onSaveSuccess)
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(settings.defaultWeightUnit) {
        viewModel.currentDefaultWeightUnit = settings.defaultWeightUnit
    }

    LaunchedEffect(saveError) {
        saveError?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSaveError()
        }
    }

    LaunchedEffect(Unit) {
        if (state.lines.isNotEmpty()) {
            state.listState.scrollToItem(state.lines.lastIndex)
        }
    }

    val noteTimestamp = state.noteTimestamp
    var selectedCategory by remember(note) {
        mutableStateOf(
            settings.categories.find { it.name == note?.categoryName }
                ?: viewModel.currentCategory
                ?: Category("Push", 0xFFFF3B30),
        )
    }
    var learningsValue by remember(note) {
        mutableStateOf(TextFieldValue(note?.learnings ?: ""))
    }
    var showLearnings by remember { mutableStateOf(false) }

    LaunchedEffect(selectedCategory) {
        viewModel.currentCategory = selectedCategory
        viewModel.refreshExerciseSuggestionsForCategory(selectedCategory.name)
    }
    LaunchedEffect(learningsValue.text) { viewModel.currentLearnings = learningsValue.text }
    LaunchedEffect(note) { note?.let { viewModel.currentTitle = it.title } }

    BackHandler { state.saveNote(isLastNote = isLastNote, exit = true) }

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

    val view = LocalView.current
    SideEffect {
        val window = (view.context as Activity).window
        WindowInsetsControllerCompat(window, view).isAppearanceLightStatusBars = !settings.darkMode
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = {},
                navigationIcon = {
                    IconButton(onClick = { state.saveNote(isLastNote = isLastNote, exit = true) }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                actions = {
                    NoteEditorFinishAction(
                        isVisible = isLastNote,
                        onFinish = {
                            state.saveNote(
                                isLastNote = true,
                                exit = true,
                                finishWorkout = true,
                            )
                        },
                    )
                    IconButton(onClick = { showLearnings = true }) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = "Learnings",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                EditorHeroHeader(
                    timestamp = noteTimestamp,
                    settings = settings,
                    selectedCategory = selectedCategory,
                    onCategorySelect = {
                        selectedCategory = it
                        viewModel.currentCategory = it
                        viewModel.refreshExerciseSuggestionsForCategory(it.name)
                        state.markDirty()
                    },
                    topPadding = padding.calculateTopPadding(),
                )

                NoteEditorTimerControls(
                    isVisible = isLastNote,
                    noteTimestamp = noteTimestamp,
                    startTimerOnOpen = startTimerOnOpen,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )

                NoteEditorWeightUnitIndicator(
                    unitLabel = settings.defaultWeightUnit.displayLabel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                ) {
                    Spacer(Modifier.height(16.dp))
                    EditorListSection(
                        state = state,
                        suggestedExercises = exerciseSuggestions,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            LearningsPopup(
                showLearnings = showLearnings,
                learningsValue = learningsValue,
                onDismiss = { showLearnings = false },
                onValueChange = {
                    learningsValue = it
                    viewModel.currentLearnings = it.text
                    state.markDirty()
                },
            )
        }
    }
}

@Composable
internal fun NoteEditorWeightUnitIndicator(
    unitLabel: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "Default weight unit: $unitLabel",
        modifier = modifier.testTag(NOTE_EDITOR_WEIGHT_UNIT_INDICATOR_TEST_TAG),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
internal fun NoteEditorFinishAction(
    isVisible: Boolean,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isVisible) return

    TextButton(
        modifier = modifier.testTag(NOTE_EDITOR_FINISH_ACTION_TEST_TAG),
        onClick = onFinish,
    ) {
        Text("Finish")
    }
}

@Composable
internal fun NoteEditorTimerControls(
    isVisible: Boolean,
    noteTimestamp: Long,
    startTimerOnOpen: Boolean,
    modifier: Modifier = Modifier,
    timerContent: @Composable (Modifier) -> Unit = { timerModifier ->
        NoteTimer(
            noteTimestamp = noteTimestamp,
            startOnOpen = startTimerOnOpen,
            modifier = timerModifier,
        )
    },
) {
    if (!isVisible) return

    timerContent(modifier.testTag(NOTE_EDITOR_TIMER_CONTROLS_TEST_TAG))
}
