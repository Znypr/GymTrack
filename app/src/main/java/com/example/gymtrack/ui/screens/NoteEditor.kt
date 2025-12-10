package com.example.gymtrack.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.gymtrack.data.Category
import com.example.gymtrack.data.NoteLine
import com.example.gymtrack.data.Settings
import com.example.gymtrack.ui.components.LearningsPopup
import com.example.gymtrack.ui.components.NoteEditorRow
import com.example.gymtrack.ui.components.NoteEditorHeader
import com.example.gymtrack.util.formatRoundedTime
import com.example.gymtrack.util.getRelativeTimeDiffString
import kotlinx.coroutines.launch
import com.example.gymtrack.ui.screens.EditorViewModel


// Internal state holder for a single row
data class BodyRow(val id: Int, val text: MutableState<TextFieldValue>)

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun NoteEditor(
    viewModel: EditorViewModel, // <-- Input is now ViewModel
    settings: Settings,
    isLastNote: Boolean, // Helper for UI logic
    onCancel: () -> Unit,
    onSaveSuccess: () -> Unit
) {
    // --- STATE INITIALIZATION ---
    val title = remember { mutableStateOf(viewModel.currentTitle) }
    val category = remember { mutableStateOf(viewModel.currentCategory ?: Category("Uncategorized", 0xFF808080)) }
    val learningsValue = remember { mutableStateOf(TextFieldValue(viewModel.currentLearnings)) }

    var showLearnings by remember { mutableStateOf(false) }

    // Sync local changes back to ViewModel when they change
    LaunchedEffect(title.value) { viewModel.currentTitle = title.value }
    LaunchedEffect(category.value) { viewModel.currentCategory = category.value }
    LaunchedEffect(learningsValue.value.text) { viewModel.currentLearnings = learningsValue.value.text }

    // Editor Content State
    val bodyLines = remember { mutableStateListOf<BodyRow>() }
    val timestamps = remember { mutableStateListOf<String>() }
    val focusRequesters = remember { mutableStateListOf<FocusRequester>() }

    // Helper to generate unique IDs for LazyColumn keys
    var nextIdCounter by remember { mutableStateOf(1000) }
    fun getNextId(): Int = nextIdCounter++

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var pendingFocusId by remember { mutableStateOf<Int?>(null) }
    var saved by remember { mutableStateOf(true) }

    val uiState by viewModel.uiState.collectAsState() //

    // --- INITIAL DATA LOADING ---
    LaunchedEffect(uiState) { // Trigger when uiState loads
        if (bodyLines.isEmpty()) {
            // FIX: Use 'uiState' instead of the missing 'note' parameter
            val initialText = uiState?.text ?: ""

            if (initialText.isNotEmpty()) {
                initialText.split("\n").forEach { line ->
                    bodyLines.add(BodyRow(getNextId(), mutableStateOf(TextFieldValue(line))))
                    focusRequesters.add(FocusRequester())
                    timestamps.add("") // Placeholder
                }
            } else {
                // Initialize with one empty line for new notes
                bodyLines.add(BodyRow(getNextId(), mutableStateOf(TextFieldValue(""))))
                focusRequesters.add(FocusRequester())
                timestamps.add("")
            }
        }
    }
    // --- CORE LOGIC: ADD NEW LINE ---
    fun addNewLine(currentIndex: Int) {
        val nextId = getNextId()
        val now = System.currentTimeMillis()
        val formattedNow = formatRoundedTime(now, settings)

        // 1. Calculate Relative Time Text (e.g., " (1'30'')")
        var relativeTimeText = ""
        if (currentIndex >= 0 && currentIndex < timestamps.size) {
            val prevTime = timestamps[currentIndex]
            val diffStr = getRelativeTimeDiffString(prevTime, now)
            if (diffStr != null) {
                relativeTimeText = " $diffStr"
            }
        }

        // 2. Prepare Insertion
        val insertIndex = currentIndex + 1
        val newTextState = TextFieldValue(relativeTimeText, TextRange(relativeTimeText.length))
        val newRow = BodyRow(nextId, mutableStateOf(newTextState))
        val newFocusRequester = FocusRequester()

        // 3. Insert into Lists
        if (insertIndex <= bodyLines.size) {
            bodyLines.add(insertIndex, newRow)
            timestamps.add(insertIndex, formattedNow) // FIX: Add actual time, not ""
            focusRequesters.add(insertIndex, newFocusRequester)
        } else {
            bodyLines.add(newRow)
            timestamps.add(formattedNow)
            focusRequesters.add(newFocusRequester)
        }

        // 4. Handle Focus & Scrolling
        pendingFocusId = nextId
        coroutineScope.launch {
            // Animate scroll to the new item
            listState.animateScrollToItem(insertIndex)
        }
    }

    // --- UI STRUCTURE ---
    Scaffold(
        topBar = {
            NoteEditorHeader(
                title = title.value,
                onTitleChange = { title.value = it },
                category = category.value,
                onCategoryChange = { category.value = it },
                onSave = {
                    // EFFICIENCY FIX: Combine text and save via ViewModel on IO thread
                    val combinedText = bodyLines.joinToString("\n") { it.text.value.text }
                    viewModel.saveNote(combinedText, settings, onSaveSuccess)
                },
                onBack = onCancel,
                onLearningsClick = { showLearnings = true }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                itemsIndexed(bodyLines, key = { _, item -> item.id }) { index, row ->

                    // Handle Focus Request
                    if (pendingFocusId == row.id) {
                        LaunchedEffect(Unit) {
                            focusRequesters.getOrNull(index)?.requestFocus()
                            pendingFocusId = null
                        }
                    }

                    // Render Row Component
                    NoteEditorRow(
                        textValue = row.text.value,
                        onValueChange = { newValue ->
                            row.text.value = newValue
                            saved = false
                            // Optional: Capture timestamp on first keystroke if empty
                            if (timestamps.getOrNull(index)
                                    ?.isEmpty() == true && newValue.text.isNotEmpty()
                            ) {
                                timestamps[index] =
                                    formatRoundedTime(System.currentTimeMillis(), settings)
                            }
                        },
                        focusRequester = focusRequesters.getOrElse(index) { FocusRequester() },
                        onNextAction = { addNewLine(index) }
                    )
                }
            }

            // Learnings Popup
            LearningsPopup(
                showLearnings = showLearnings,
                learningsValue = learningsValue.value,
                onDismiss = { showLearnings = false },
                onValueChange = { newValue ->
                    learningsValue.value = newValue
                    saved = false
                }
            )
        }
    }
}