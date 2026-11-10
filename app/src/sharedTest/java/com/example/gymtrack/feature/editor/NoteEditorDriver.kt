package com.example.gymtrack.feature.editor

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.data.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

/** What the editor last handed to persistence. */
data class SavedEditorNote(val text: String, val rowMetadata: String, val timestamp: Long, val finalized: Boolean) {
    fun toNoteLine(): NoteLine = NoteLine(title = "", text = text, timestamp = timestamp, rowMetadata = rowMetadata)
}

/**
 * Drives the real [NoteEditorState] without Compose or Android: a fake clock, saves captured in
 * memory, and a cancelled scope so UI-only coroutines (scroll, timer stop) never run.
 */
class NoteEditorDriver(
    initialNote: NoteLine? = null,
    startMillis: Long = 1_000_000L,
    settings: Settings = Settings(),
) {
    var now: Long = startMillis
    val saves = mutableListOf<SavedEditorNote>()
    private var id: Long = initialNote?.timestamp ?: -1L

    private val persistence = object : NoteEditorPersistence {
        override val currentId: Long get() = id
        override val currentTitle: String get() = ""
        override val currentLearnings: String get() = ""

        override fun saveDraft(text: String, rowMetadata: String, noteTimestamp: Long, onPersisted: () -> Unit, onFailure: (String) -> Unit) {
            id = noteTimestamp
            saves += SavedEditorNote(text, rowMetadata, noteTimestamp, finalized = false)
            onPersisted()
        }

        override fun finalizeWorkout(text: String, rowMetadata: String, noteTimestamp: Long, onPersisted: () -> Unit, onFailure: (String) -> Unit) {
            id = noteTimestamp
            saves += SavedEditorNote(text, rowMetadata, noteTimestamp, finalized = true)
            onPersisted()
        }

        override suspend fun stopTimer(noteTimestamp: Long) = Unit
    }

    val state = NoteEditorState(
        persistence = persistence,
        settings = settings,
        scope = CoroutineScope(Job().apply { cancel() }),
        listState = LazyListState(),
        initialNote = initialNote,
        onSaveSuccess = {},
        clock = { now },
    )

    val lastSave: SavedEditorNote get() = saves.last()
    val texts: List<String> get() = state.lines.map { it.text.value.text }
    val lastIndex: Int get() = state.lines.lastIndex

    fun advance(seconds: Long) {
        now += seconds * 1_000L
    }

    /** Replaces the row's text, cursor at the end (IME typing or keypad keys). */
    fun type(index: Int, text: String) = state.onTextChange(index, TextFieldValue(text, TextRange(text.length)))

    /** Enter with the cursor at the end of the row. */
    fun enter(index: Int) {
        val current = state.lines[index].text.value.text
        state.onTextChange(index, TextFieldValue(current + "\n", TextRange(current.length + 1)))
    }

    fun typeAndEnter(index: Int, text: String) {
        type(index, text)
        enter(index)
    }

    /** ON_STOP autosave. */
    fun stop() = state.saveNote(isLastNote = false, exit = false)

    fun back() = state.saveNote(isLastNote = false, exit = true)

    fun finish() = state.saveNote(isLastNote = false, exit = true, finishWorkout = true)
}
