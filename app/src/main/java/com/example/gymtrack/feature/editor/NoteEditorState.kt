package com.example.gymtrack.feature.editor

import android.content.Context
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.example.gymtrack.core.data.ExerciseFlag
import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.data.Settings
import com.example.gymtrack.core.timer.NoteTimerStore
import com.example.gymtrack.core.util.combineTextAndTimes
import com.example.gymtrack.core.util.formatElapsedMinutesSeconds
import com.example.gymtrack.core.util.formatSecondsToMinutesSeconds
import com.example.gymtrack.core.util.parseDurationSeconds
import com.example.gymtrack.core.util.parseNoteText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class NoteRow(
    val id: Long,
    val text: MutableState<TextFieldValue>,
    val flag: MutableState<ExerciseFlag> = mutableStateOf(ExerciseFlag.BILATERAL),
    val focusRequester: FocusRequester = FocusRequester(),
)

class NoteEditorState(
    private val viewModel: EditorViewModel,
    private val settings: Settings,
    private val context: Context,
    private val scope: CoroutineScope,
    val listState: LazyListState,
    initialNote: NoteLine?,
    private val onSaveSuccess: () -> Unit,
) {
    var nextId by mutableStateOf(0L)
    val lines = mutableStateListOf<NoteRow>()
    val timestamps = mutableStateListOf<String>()
    val flags = mutableStateListOf<ExerciseFlag>()

    private var startTime: Long? = initialNote?.timestamp
    private var lastEnter: Long = System.currentTimeMillis()
    val noteTimestamp: Long = initialNote?.timestamp ?: System.currentTimeMillis()
    private var editRevision = 0L
    private var finishing = false

    var saved by mutableStateOf(false)
        private set

    init {
        val parsed = parseNoteText(initialNote?.text ?: "")
        if (parsed.first.isEmpty()) {
            addLine(NoteRow(nextId++, mutableStateOf(TextFieldValue(""))))
            timestamps.add("")
            flags.add(ExerciseFlag.BILATERAL)
        } else {
            parsed.first.forEachIndexed { index, text ->
                val cleanText = if (text.isNotBlank() && !text.startsWith(" ")) {
                    text.replace(Regex("""\s*\(\d+'\d{2}''\)$"""), "")
                } else text
                addLine(
                    NoteRow(
                        id = nextId++,
                        text = mutableStateOf(TextFieldValue(cleanText)),
                        flag = mutableStateOf(parsed.third.getOrNull(index) ?: ExerciseFlag.BILATERAL),
                    ),
                )
            }
            timestamps.addAll(parsed.second)
            flags.addAll(parsed.third)
        }
        cleanLists()
        if (initialNote != null) {
            val lastIndex = parsed.second.indexOfLast(String::isNotBlank)
            val lastSeconds = if (lastIndex >= 0) parseDurationSeconds(parsed.second[lastIndex]).toLong() else 0L
            startTime = initialNote.timestamp
            lastEnter = initialNote.timestamp + lastSeconds * 1_000L
        }
    }

    fun markDirty() {
        if (finishing) return
        editRevision += 1L
        saved = false
    }

    fun onTextChange(index: Int, newValue: TextFieldValue) {
        if (finishing) return
        markDirty()
        val row = lines[index]
        if (newValue.text.endsWith("\n")) {
            val content = newValue.text.dropLast(1)
            val hasTimestamp = content.trim().matches(Regex(".*\\(\\d+'\\d{2}''\\)$"))
            val now = System.currentTimeMillis()
            val differenceSeconds = (now - lastEnter) / 1_000L
            if (content.isNotBlank()) lastEnter = now
            val relative = formatSecondsToMinutesSeconds(differenceSeconds)
            val isMain = index == 0 || lines.getOrNull(index - 1)?.text?.value?.text?.isBlank() != false
            if (startTime == null && content.isNotBlank() && isMain) startTime = now
            val absolute = formatElapsedMinutesSeconds(startTime ?: now, now, settings)
            val formatted = when {
                content.isBlank() -> ""
                isMain || hasTimestamp -> content
                else -> "      $content ($relative)"
            }
            row.text.value = TextFieldValue(formatted, TextRange(formatted.length))
            ensureListSize(index)
            if (content.isNotBlank()) timestamps[index] = absolute

            val nextIndex = index + 1
            val newRow = NoteRow(
                id = nextId++,
                text = mutableStateOf(TextFieldValue("")),
                flag = mutableStateOf(row.flag.value),
            )
            lines.add(nextIndex, newRow)
            if (flags.size <= nextIndex) flags.add(row.flag.value) else flags.add(nextIndex, row.flag.value)
            if (timestamps.size <= nextIndex) timestamps.add("") else timestamps.add(nextIndex, "")
            scope.launch {
                delay(100L)
                if (nextIndex < lines.size) {
                    listState.scrollToItem(nextIndex)
                    newRow.focusRequester.requestFocus()
                }
            }
            saveNote(isLastNote = false, exit = false)
        } else {
            row.text.value = newValue
        }
    }

    fun toggleFlag(index: Int) {
        if (finishing) return
        markDirty()
        val row = lines[index]
        val newFlag = row.flag.value.next()
        row.flag.value = newFlag
        ensureListSize(index)
        flags[index] = newFlag
        saveNote(isLastNote = false, exit = false)
    }

    fun saveNote(isLastNote: Boolean, exit: Boolean) {
        if (finishing || (saved && !exit)) return
        if (exit) finishing = true
        val range = lines.indices
        val finalStartTime = startTime ?: noteTimestamp
        range.forEach { index ->
            val row = lines[index]
            if (row.text.value.text.isNotBlank() && timestamps.getOrElse(index) { "" }.isBlank()) {
                ensureListSize(index)
                timestamps[index] = formatElapsedMinutesSeconds(finalStartTime, System.currentTimeMillis(), settings)
            }
            ensureListSize(index)
            flags[index] = row.flag.value
        }
        val plainContent = range.joinToString("\n") { lines[it].text.value.text }
        val validTimestamps = range.map { timestamps.getOrElse(it) { "" } }
        val validFlags = range.map { flags.getOrElse(it) { ExerciseFlag.BILATERAL } }
        val combined = combineTextAndTimes(plainContent, validTimestamps, validFlags)
        val isNew = viewModel.currentId == -1L
        val isEmpty = combined.isBlank() && viewModel.currentTitle.isBlank() && viewModel.currentLearnings.isBlank()
        if (isNew && isEmpty) {
            if (exit) finishExit(isLastNote)
            return
        }

        val requestedRevision = editRevision
        val onPersisted = {
            if (requestedRevision == editRevision) saved = true
            if (exit) finishExit(isLastNote)
        }
        val onFailure: (String) -> Unit = {
            saved = false
            if (exit) finishing = false
        }
        if (exit) {
            viewModel.finalizeWorkout(combined, noteTimestamp, onPersisted, onFailure)
        } else {
            viewModel.saveDraft(combined, noteTimestamp, onPersisted, onFailure)
        }
    }

    private fun finishExit(isLastNote: Boolean) {
        if (!isLastNote) {
            onSaveSuccess()
            return
        }
        scope.launch {
            NoteTimerStore.stop(context, noteTimestamp)
            onSaveSuccess()
        }
    }

    private fun addLine(row: NoteRow, index: Int = lines.size) = lines.add(index, row)

    private fun ensureListSize(index: Int) {
        while (timestamps.size <= index) timestamps.add("")
        while (flags.size <= index) flags.add(ExerciseFlag.BILATERAL)
    }

    private fun cleanLists() {
        while (timestamps.size < lines.size) timestamps.add("")
        while (timestamps.size > lines.size) timestamps.removeAt(timestamps.lastIndex)
        while (flags.size < lines.size) flags.add(ExerciseFlag.BILATERAL)
        while (flags.size > lines.size) flags.removeAt(flags.lastIndex)
    }
}

@Composable
fun rememberNoteEditorState(
    viewModel: EditorViewModel,
    settings: Settings,
    note: NoteLine?,
    onSaveSuccess: () -> Unit,
): NoteEditorState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    return remember(viewModel) {
        NoteEditorState(viewModel, settings, context, scope, listState, note, onSaveSuccess)
    }
}
