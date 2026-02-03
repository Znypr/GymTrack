package com.example.gymtrack.feature.editor // Adjust to your package

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

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditor(
    viewModel: EditorViewModel,
    settings: Settings,
    isLastNote: Boolean,
    onCancel: () -> Unit, // This is actually "On Back"
    onSaveSuccess: () -> Unit
) {
    // 1. Load Data
    val note by viewModel.uiState.collectAsState()
    val isEditingExisting = viewModel.currentId != -1L

    // Wait for data if editing an existing note
    if (isEditingExisting && note == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    // [CRITICAL FIX]
    // Key the state to 'viewModel' so it is created ONLY ONCE.
    // Passing 'note' as a key caused the entire screen to reset on every Autosave.
    val state = rememberNoteEditorState(viewModel, settings, note, onSaveSuccess)

    LaunchedEffect(Unit) {
        if (state.lines.isNotEmpty()) {
            val lastIdx = state.lines.lastIndex
            state.listState.scrollToItem(lastIdx)
            // Request focus so the keyboard opens and cursor is visible
            state.lines[lastIdx].focusRequester.requestFocus()
        }
    }
    val noteTimestamp = note?.timestamp ?: System.currentTimeMillis()

    // 2. Header State
    var selectedCategory by remember(note) {
        mutableStateOf(settings.categories.find { it.name == note?.categoryName } ?: Category("Push", 0xFFFF3B30))
    }
    var learningsValue by remember(note) { mutableStateOf(TextFieldValue(note?.learnings ?: "")) }
    var showLearnings by remember { mutableStateOf(false) }

    // Sync ViewModel
    LaunchedEffect(selectedCategory) { viewModel.currentCategory = selectedCategory }
    LaunchedEffect(learningsValue.text) { viewModel.currentLearnings = learningsValue.text }
    LaunchedEffect(note) { note?.let { viewModel.currentTitle = it.title } }

    // 3. Lifecycle & Back Handling
    BackHandler { state.saveNote(isLastNote, exit = true) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) state.saveNote(isLastNote, exit = false)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Status Bar styling
    val view = LocalView.current
    SideEffect {
        val window = (view.context as Activity).window
        val controller = WindowInsetsControllerCompat(window, view)
        // Force dark icons if light mode, light icons if dark mode
        controller.isAppearanceLightStatusBars = !settings.darkMode
    }

    // 4. Main Scaffold
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // Minimal Transparent Top Bar to allow Header to shine
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = {},
                navigationIcon = {
                    IconButton(onClick = { state.saveNote(isLastNote, exit = true) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    IconButton(onClick = { showLearnings = true }) {
                        Icon(Icons.Default.Menu, contentDescription = "Learnings", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {

                // --- NEW HERO HEADER ---
                EditorHeroHeader(
                    timestamp = noteTimestamp,
                    settings = settings,
                    selectedCategory = selectedCategory,
                    onCategorySelect = {
                        selectedCategory = it
                        state.saved = false
                    },
                    topPadding = padding.calculateTopPadding()
                )

                // --- CONTENT AREA ---
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                ) {
                    Spacer(Modifier.height(16.dp))

                    // The Editor List
                    EditorListSection(state, modifier = Modifier.fillMaxSize())
                }
            }

            // Learnings Popup (Overlay)
            LearningsPopup(
                showLearnings = showLearnings,
                learningsValue = learningsValue,
                onDismiss = { showLearnings = false },
                onValueChange = {
                    learningsValue = it
                    state.saved = false
                }
            )
        }
    }
}
