package com.example.gymtrack.feature.editor

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
import kotlinx.coroutines.launch

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
    note: NoteLine?,
    private val onSaveSuccess: () -> Unit
) {
    var nextId by mutableStateOf(0L)
    val lines = mutableStateListOf<NoteRow>()
    val timestamps = mutableStateListOf<String>()
    val flags = mutableStateListOf<ExerciseFlag>()

    private var startTime: Long? = note?.timestamp
    private var lastEnter: Long = System.currentTimeMillis()
    private val noteTimestamp = note?.timestamp ?: System.currentTimeMillis()
    var saved by mutableStateOf(false)

    init {
        val parsed = parseNoteText(note?.text ?: "")

        if (parsed.first.isEmpty()) {
            addLine(NoteRow(nextId++, mutableStateOf(TextFieldValue(""))))
            timestamps.add("")
            flags.add(ExerciseFlag.BILATERAL)
        } else {
            parsed.first.forEachIndexed { idx, txt ->
                // [FIX] If it's a main line (no indentation), remove the old relative time suffix
                val cleanTxt = if (txt.isNotBlank() && !txt.startsWith(" ")) {
                    txt.replace(Regex("""\s*\(\d+'\d{2}''\)$"""), "")
                } else txt

                addLine(
                    NoteRow(
                        nextId++,
                        mutableStateOf(TextFieldValue(cleanTxt)), // Use cleanTxt here
                        mutableStateOf(parsed.third.getOrNull(idx) ?: ExerciseFlag.BILATERAL)
                    )
                )
            }
            timestamps.addAll(parsed.second)
            flags.addAll(parsed.third)
        }



        cleanLists()

        if (note != null) {
            val list = parsed.second
            val lastIdx = list.indexOfLast { it.isNotBlank() }
            val lastSec = if (lastIdx >= 0) parseDurationSeconds(list[lastIdx]).toLong() else 0L
            startTime = note.timestamp
            lastEnter = note.timestamp + lastSec * 1000
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

        if (newValue.text.endsWith("\n")) {
            val content = newValue.text.dropLast(1)
            val now = System.currentTimeMillis()

            val diffSec = (now - lastEnter) / 1000

            if (content.isNotBlank()) lastEnter = now

            val rel = formatSecondsToMinutesSeconds(diffSec)
            val isMain = index == 0 || lines.getOrNull(index - 1)?.text?.value?.text?.isBlank() != false
            if (startTime == null && content.isNotBlank() && isMain) startTime = now

            val effectiveStart = startTime ?: now
            val abs = formatElapsedMinutesSeconds(effectiveStart, now, settings)

            val formatted = if (content.isNotBlank()) {
                if (isMain) content else "     $content ($rel)"
            } else ""

            row.text.value = TextFieldValue(formatted, TextRange(formatted.length))

            ensureListSize(index)
            if (content.isNotBlank()) timestamps[index] = abs

            val nextIdx = index + 1
            val newRow = NoteRow(nextId++, mutableStateOf(TextFieldValue("")), mutableStateOf(row.flag.value))

            lines.add(nextIdx, newRow)

            if (flags.size <= nextIdx) flags.add(row.flag.value) else flags.add(nextIdx, row.flag.value)
            if (timestamps.size <= nextIdx) timestamps.add("") else timestamps.add(nextIdx, "")

            scope.launch {
                listState.animateScrollToItem(nextIdx)
                newRow.focusRequester.requestFocus()
            }
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
    }

    fun saveNote(isLastNote: Boolean, exit: Boolean) {
        if (saved && !exit) return

        val lastContentIndex = lines.indexOfLast { it.text.value.text.isNotBlank() }

        val range = if (lastContentIndex >= 0) 0..lastContentIndex else IntRange.EMPTY

        val finalStartTime = startTime ?: noteTimestamp

        range.forEach { i ->
            val row = lines[i]

            if (row.text.value.text.isNotBlank() && timestamps.getOrElse(i) { "" }.isBlank()) {
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

        viewModel.saveNote(combined, settings) {
            if (exit) {
                onSaveSuccess()
            }
        }

        saved = true

        if (isLastNote) {
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

    return remember(note) {
        NoteEditorState(viewModel, settings, context, scope, listState, note, onSaveSuccess)
    }
}