package com.example.gymtrack.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.gymtrack.R
import com.example.gymtrack.data.Category
import com.example.gymtrack.data.ExerciseFlag
import com.example.gymtrack.data.Settings
import com.example.gymtrack.ui.components.*
import com.example.gymtrack.util.*
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditor(
    viewModel: EditorViewModel,
    settings: Settings,
    isLastNote: Boolean,
    onCancel: () -> Unit,
    onSaveSuccess: () -> Unit
) {
    // 1. Load Data
    val note by viewModel.uiState.collectAsState()
    val noteTimestamp = note?.timestamp ?: System.currentTimeMillis()

    // 2. Initialize State Holder
    val state = rememberNoteEditorState(viewModel, settings, note, onSaveSuccess)

    // 3. Header State
    var selectedCategory by remember(note) { mutableStateOf<Category?>(settings.categories.find { it.name == note?.categoryName } ?: Category("Push", 0xFFFF3B30)) }
    var learningsValue by remember(note) { mutableStateOf(TextFieldValue(note?.learnings ?: "")) }
    var showLearnings by remember { mutableStateOf(false) }

    LaunchedEffect(selectedCategory) { viewModel.currentCategory = selectedCategory }
    LaunchedEffect(learningsValue.text) { viewModel.currentLearnings = learningsValue.text }
    LaunchedEffect(note) { note?.let { viewModel.currentTitle = it.title } }

    // 4. Lifecycle Handlers
    BackHandler {
        // We do NOT call onCancel() here. We let the save process trigger the exit.
        // We pass 'exit = true' to tell the state we want to leave after saving.
        state.saveNote(isLastNote, exit = true)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                // When app goes background, save but DO NOT exit/pop.
                state.saveNote(isLastNote, exit = false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Status Bar Style
    val view = LocalView.current
    SideEffect {
        val window = (view.context as Activity).window
        val controller = WindowInsetsControllerCompat(window, view)
        val light = !settings.darkMode
        controller.isAppearanceLightStatusBars = light
        controller.isAppearanceLightNavigationBars = light
    }

    // 5. UI Render
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                navigationIcon = { Image(painter = painterResource(id = R.drawable.ic_gymtrack_logo), contentDescription = null, modifier = Modifier.size(45.dp)) },
                title = { Text("GymTrack", fontSize = 24.sp) },
                actions = { IconButton(onClick = { showLearnings = true }) { Icon(Icons.Default.Menu, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurface) } }
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 15.dp)
                ) {
                    EditorHeaderSection(noteTimestamp, settings, selectedCategory) {
                        selectedCategory = it
                        state.saved = false
                    }

                    Spacer(Modifier.height(8.dp))
                    if (isLastNote) {
                        NoteTimer(noteTimestamp = noteTimestamp, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                    }
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))

                    EditorListSection(state, modifier = Modifier.weight(1f))
                }

                LearningsPopup(
                    showLearnings = showLearnings,
                    learningsValue = learningsValue,
                    onDismiss = { showLearnings = false },
                    onValueChange = { learningsValue = it; state.saved = false }
                )
            }
        }
    }
}

// --- SUB-COMPONENTS ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorHeaderSection(
    noteTimestamp: Long,
    settings: Settings,
    selectedCategory: Category?,
    onCategorySelect: (Category) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatDate(noteTimestamp, settings), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text(formatTime(noteTimestamp, settings), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 20.sp)
    }
    Spacer(Modifier.height(12.dp))

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            readOnly = true,
            value = selectedCategory?.name ?: "None",
            onValueChange = {},
            label = { Text("Category") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.surface,
                unfocusedBorderColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                cursorColor = MaterialTheme.colorScheme.onSurface
            ),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            settings.categories.forEach { cat ->
                DropdownMenuItem(
                    text = { Text(cat.name) },
                    leadingIcon = { Box(Modifier.size(12.dp).background(Color(cat.color.toInt()))) },
                    onClick = { onCategorySelect(cat); expanded = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditorListSection(state: NoteEditorState, modifier: Modifier = Modifier) {
    val coroutineScope = rememberCoroutineScope()

    Surface(modifier = modifier.fillMaxWidth().imePadding(), color = MaterialTheme.colorScheme.surfaceVariant) {
        LazyColumn(
            state = state.listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 20.dp),
        ) {
            itemsIndexed(state.lines, key = { _, row -> row.id }) { index, row ->
                val fr = row.focusRequester
                val bringIntoViewRequester = remember { BringIntoViewRequester() }
                var rowFocused by remember { mutableStateOf(false) }
                val isMain = index == 0 || state.lines.getOrNull(index - 1)?.text?.value?.text?.isBlank() != false

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (rowFocused) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Flag Button
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val relColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isSystemInDarkTheme()) 0.75f else 0.45f)
                        if (row.text.value.text.isNotBlank()) {
                            Box(Modifier.width(36.dp).height(28.dp), contentAlignment = Alignment.Center) {
                                if (isMain) {
                                    ExerciseFlagButton(flag = row.flag.value, relColor = relColor, onToggle = { state.toggleFlag(index) })
                                } else {
                                    var p = index - 1
                                    while (p >= 0 && (state.lines.getOrNull(p - 1)?.text?.value?.text?.isNotBlank() == true)) p--
                                    val parentFlag = state.lines.getOrNull(p)?.flag?.value ?: ExerciseFlag.BILATERAL
                                    ExerciseFlagTag(flag = parentFlag, relColor = relColor)
                                }
                            }
                        }
                        Spacer(Modifier.width(8.dp))

                        // Text Input
                        BasicTextField(
                            value = row.text.value,
                            onValueChange = { state.onTextChange(index, it) },
                            textStyle = LocalTextStyle.current.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = if (isMain) 20.sp else 16.sp,
                                fontWeight = if (isMain) FontWeight.Bold else null,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                            visualTransformation = rememberRelativeTimeVisualTransformation(if (isMain) 20.sp else 16.sp),
                            modifier = Modifier
                                .focusRequester(fr)
                                .bringIntoViewRequester(bringIntoViewRequester)
                                .onFocusChanged {
                                    rowFocused = it.isFocused
                                    if (it.isFocused) coroutineScope.launch { bringIntoViewRequester.bringIntoView() }
                                }
                                .weight(1f)
                        )
                    }

                    // Timestamp
                    val absText = state.timestamps.getOrElse(index) { "" }
                    val absAnnotated = SmallSecondsVisualTransformation(if (isMain) 20.sp else 16.sp).filter(AnnotatedString(absText)).text
                    Text(
                        text = absAnnotated,
                        fontSize = if (isMain) 20.sp else 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isMain) FontWeight.Bold else null
                    )
                }
            }
        }
    }
}