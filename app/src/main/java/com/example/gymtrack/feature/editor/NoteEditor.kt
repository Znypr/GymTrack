package com.example.gymtrack.feature.editor

import android.annotation.SuppressLint
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    onCancel: () -> Unit,
    onSaveSuccess: () -> Unit,
) {
    val note by viewModel.uiState.collectAsState()
    val saveError by viewModel.saveError.collectAsState()
    val isEditingExisting = viewModel.currentId != -1L

    if (isEditingExisting && note == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val state = rememberNoteEditorState(viewModel, settings, note, onSaveSuccess)
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(saveError) {
        saveError?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSaveError()
        }
    }

    LaunchedEffect(Unit) {
        if (state.lines.isNotEmpty()) {
            val lastIndex = state.lines.lastIndex
            state.listState.scrollToItem(lastIndex)
        }
    }

    val noteTimestamp = note?.timestamp ?: System.currentTimeMillis()
    var selectedCategory by remember(note) {
        mutableStateOf(
            settings.categories.find { it.name == note?.categoryName }
                ?: Category("Push", 0xFFFF3B30),
        )
    }
    var learningsValue by remember(note) {
        mutableStateOf(TextFieldValue(note?.learnings ?: ""))
    }
    var showLearnings by remember { mutableStateOf(false) }

    LaunchedEffect(selectedCategory) {
        viewModel.currentCategory = selectedCategory
    }
    LaunchedEffect(learningsValue.text) {
        viewModel.currentLearnings = learningsValue.text
    }
    LaunchedEffect(note) {
        note?.let { viewModel.currentTitle = it.title }
    }

    BackHandler {
        state.saveNote(isLastNote = isLastNote, exit = true)
    }

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
                    IconButton(
                        onClick = {
                            state.saveNote(isLastNote = isLastNote, exit = true)
                        },
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                actions = {
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
                        state.markDirty()
                    },
                    topPadding = padding.calculateTopPadding(),
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                ) {
                    Spacer(Modifier.height(16.dp))
                    EditorListSection(state, modifier = Modifier.fillMaxSize())
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
