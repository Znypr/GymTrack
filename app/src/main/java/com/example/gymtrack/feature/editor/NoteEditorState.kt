package com.example.gymtrack.feature.editor

import android.R.attr.delay
import android.content.Context
import android.content.Intent
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
import com.example.gymtrack.core.util.combineTextAndTimes
import com.example.gymtrack.core.util.formatElapsedMinutesSeconds
import com.example.gymtrack.core.util.formatSecondsToMinutesSeconds
import com.example.gymtrack.core.util.parseDurationSeconds
import com.example.gymtrack.core.util.parseNoteText
import com.example.gymtrack.core.services.NoteTimerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.delay

data class NoteRow(
    val id: Long,
    val text: MutableState<TextFieldValue>,
    val flag: MutableState<ExerciseFlag> = mutableStateOf(ExerciseFlag.BILATERAL),
    val focusRequester: FocusRequester = FocusRequester()
)

class NoteEditorState(
    private val viewModel: EditorViewModel,
    private val settings: Settings,
    private val context: Context,
    private val scope: CoroutineScope,
    val listState: LazyListState,
    initialNote: NoteLine?, // Renamed to initialNote to avoid confusion
    private val onSaveSuccess: () -> Unit
) {
    var nextId by mutableStateOf(0L)
    val lines = mutableStateListOf<NoteRow>()
    val timestamps = mutableStateListOf<String>()
    val flags = mutableStateListOf<ExerciseFlag>()

    // Keep track of the latest saved version to prevent overwriting with old data
    private var currentNoteVersion: NoteLine? = initialNote

    private var startTime: Long? = initialNote?.timestamp
    private var lastEnter: Long = System.currentTimeMillis()
    private val noteTimestamp = initialNote?.timestamp ?: System.currentTimeMillis()
    var saved by mutableStateOf(false)

    init {
        val parsed = parseNoteText(initialNote?.text ?: "")

        if (parsed.first.isEmpty()) {
            addLine(NoteRow(nextId++, mutableStateOf(TextFieldValue(""))))
            timestamps.add("")
            flags.add(ExerciseFlag.BILATERAL)
        } else {
            parsed.first.forEachIndexed { idx, txt ->
                // [FIX] Clean regex to remove existing relative times to prevent duplication
                val cleanTxt = if (txt.isNotBlank() && !txt.startsWith(" ")) {
                    txt.replace(Regex("""\s*\(\d+'\d{2}''\)$"""), "")
                } else txt

                addLine(
                    NoteRow(
                        nextId++,
                        mutableStateOf(TextFieldValue(cleanTxt)),
                        mutableStateOf(parsed.third.getOrNull(idx) ?: ExerciseFlag.BILATERAL)
                    )
                )
            }
            timestamps.addAll(parsed.second)
            flags.addAll(parsed.third)
        }

        cleanLists()

        if (initialNote != null) {
            val list = parsed.second
            val lastIdx = list.indexOfLast { it.isNotBlank() }
            val lastSec = if (lastIdx >= 0) parseDurationSeconds(list[lastIdx]).toLong() else 0L
            startTime = initialNote.timestamp
            lastEnter = initialNote.timestamp + lastSec * 1000
        } else {
            lastEnter = System.currentTimeMillis()
        }
    }

    private fun addLine(row: NoteRow, index: Int = lines.size) {
        lines.add(index, row)
    }

    fun onTextChange(index: Int, newValue: TextFieldValue) {
        saved = false
        val row = lines[index]

        // [FIX] Logic to handle "Enter" press
        if (newValue.text.endsWith("\n")) {
            val content = newValue.text.dropLast(1)

            // [FIX] Prevent double timestamps: Check if line already ends with (...)
            // matches (m'ss'') pattern
            val hasTimestamp = content.trim().matches(Regex(".*\\(\\d+'\\d{2}''\\)$"))

            val now = System.currentTimeMillis()
            val diffSec = (now - lastEnter) / 1000

            if (content.isNotBlank()) lastEnter = now

            val rel = formatSecondsToMinutesSeconds(diffSec)
            val isMain = index == 0 || lines.getOrNull(index - 1)?.text?.value?.text?.isBlank() != false

            if (startTime == null && content.isNotBlank() && isMain) startTime = now
            val effectiveStart = startTime ?: now
            val abs = formatElapsedMinutesSeconds(effectiveStart, now, settings)

            // Only append relative time if it doesn't already have one and is a sub-entry
            val formatted = if (content.isNotBlank()) {
                if (isMain) content
                else if (hasTimestamp) content // Keep existing
                else "      $content ($rel)" // Append new
            } else ""

            row.text.value = TextFieldValue(formatted, TextRange(formatted.length))

            ensureListSize(index)
            if (content.isNotBlank()) timestamps[index] = abs

            // Create new line
            val nextIdx = index + 1
            val newRow = NoteRow(nextId++, mutableStateOf(TextFieldValue("")), mutableStateOf(row.flag.value))

            lines.add(nextIdx, newRow)
            if (flags.size <= nextIdx) flags.add(row.flag.value) else flags.add(nextIdx, row.flag.value)
            if (timestamps.size <= nextIdx) timestamps.add("") else timestamps.add(nextIdx, "")

            scope.launch {
                // [CRITICAL FIX] Small delay to allow Compose to render the new row before focusing
                delay(50)
                listState.animateScrollToItem(nextIdx)
                newRow.focusRequester.requestFocus()
            }

            // [CRITICAL FIX] AUTOSAVE on new line to prevent data loss if app dies
            saveNote(isLastNote = false, exit = false)

        } else {
            row.text.value = newValue
        }
    }

    fun toggleFlag(index: Int) {
        val row = lines[index]
        val newFlag = row.flag.value.next()
        row.flag.value = newFlag
        ensureListSize(index)
        flags[index] = newFlag
        saved = false
        // [Optional] Autosave on flag toggle too
        saveNote(isLastNote = false, exit = false)
    }

    fun saveNote(isLastNote: Boolean, exit: Boolean) {
        if (saved && !exit) return

        val range = lines.indices

        val finalStartTime = startTime ?: noteTimestamp

        range.forEach { i ->
            val row = lines[i]
            if (row.text.value.text.isNotBlank() &&
                timestamps.getOrElse(i) { "" }.isBlank()) {

                val nowAbs = formatElapsedMinutesSeconds(
                    finalStartTime,
                    System.currentTimeMillis(),
                    settings
                )
                ensureListSize(i)
                timestamps[i] = nowAbs
            }
            ensureListSize(i)
            flags[i] = row.flag.value
        }

        val plainContent = range.joinToString("\n") { lines[it].text.value.text }
        val validTimestamps = range.map { timestamps.getOrElse(it) { "" } }
        val validFlags = range.map { flags.getOrElse(it) { ExerciseFlag.BILATERAL } }
        val combined = combineTextAndTimes(plainContent, validTimestamps, validFlags)

        val isNew = currentNoteVersion == null
        val isEmpty = combined.isBlank() &&
                viewModel.currentTitle.isBlank() &&
                viewModel.currentLearnings.isBlank()

        if (isNew && isEmpty) {
            if (exit) onSaveSuccess()
            return
        }

        viewModel.saveNote(combined, settings) {
            if (exit) {
                onSaveSuccess()
            }
            saved = true
        }

        if (isLastNote && exit) {
            context.stopService(Intent(context, NoteTimerService::class.java))
        }
    }

    private fun ensureListSize(index: Int) {
        while (timestamps.size <= index + 1) timestamps.add("")
        while (flags.size <= index + 1) flags.add(ExerciseFlag.BILATERAL)
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
    onSaveSuccess: () -> Unit
): NoteEditorState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    return remember(viewModel) {
        NoteEditorState(viewModel, settings, context, scope, listState, note, onSaveSuccess)
    }
}