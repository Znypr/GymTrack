package com.example.gymtrack.feature.editor

import com.example.gymtrack.core.time.AppClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.data.WeightUnit
import com.example.gymtrack.core.data.WorkoutRepository
import com.example.gymtrack.core.data.repository.NoteRepository
import com.example.gymtrack.core.data.repository.toEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import com.example.gymtrack.feature.editor.guardrail.SetHistory
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditorViewModel(
    private val initialId: Long,
    private val noteRepo: NoteRepository,
    private val workoutRepo: WorkoutRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<NoteLine?>(null)
    val uiState = _uiState.asStateFlow()

    private val _saveError = MutableStateFlow<String?>(null)
    val saveError = _saveError.asStateFlow()

    var currentDefaultWeightUnit: WeightUnit = WeightUnit.KG

    /**
     * Whether this editor shows the running workout (the one with Finish). Set by the editor
     * screen; true until then, which keeps the cheap draft-only path as the default.
     */
    @Volatile
    var isActiveWorkout: Boolean = true

    private val saveCoordinator = EditorSaveCoordinator<NoteLine>(
        persistDraft = { note ->
            val refresh = shouldRefreshDerivedDataOnSave(
                isActiveWorkout = isActiveWorkout,
                isCompleted = { workoutRepo.isCompleted(note.timestamp) },
            )
            if (refresh) persistHistoryEdit(note) else noteRepo.saveNote(note)
        },
        finalizeWorkout = { note ->
            workoutRepo.saveCompletedWorkout(note.toEntity(), currentDefaultWeightUnit)
        },
    )

    /**
     * An edit to a past workout updates its derived Stats data (#325). If that fails, the text is
     * still saved as a plain draft so nothing typed is lost. The failure is not rethrown: Back
     * would then fail every time and trap the user in the editor. The derived rows stay as they
     * were and are re-derived on the next successful save of this workout.
     */
    private suspend fun persistHistoryEdit(note: NoteLine) {
        try {
            workoutRepo.saveEditedWorkout(note.toEntity(), currentDefaultWeightUnit)
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            noteRepo.saveNote(note)
        }
    }

    private val idLock = Any()

    var currentId: Long = initialId
        private set

    var currentTitle = ""
    /** The workout's category: stored values kept verbatim, changed only by a tap (#296). */
    val category = EditorCategory()
    var currentLearnings = ""

    init {
        if (initialId != -1L) {
            viewModelScope.launch {
                val note = noteRepo.getNoteById(initialId)
                initialize(note)
            }
        } else {
            initialize(null)
        }
    }

    fun initialize(note: NoteLine?) {
        if (note != null) {
            currentTitle = note.title
            category.loadStored(note.categoryName, note.categoryColor)
            currentLearnings = note.learnings
            _uiState.value = note
        }
        // A new workout gets its category from NoteEditor (category.startNew), which knows the
        // settings and the category chosen on Today. No hard-coded fallback (#296).
    }

    fun saveDraft(
        finalText: String,
        rowMetadata: String = "",
        newNoteTimestamp: Long? = null,
        onComplete: () -> Unit = {},
        onError: (String) -> Unit = {},
    ) {
        requestSave(
            note = snapshot(finalText, rowMetadata, newNoteTimestamp),
            kind = EditorSaveKind.DRAFT,
            onComplete = onComplete,
            onError = onError,
        )
    }

    fun finalizeWorkout(
        finalText: String,
        rowMetadata: String = "",
        newNoteTimestamp: Long? = null,
        onComplete: () -> Unit = {},
        onError: (String) -> Unit = {},
    ) {
        requestSave(
            note = snapshot(finalText, rowMetadata, newNoteTimestamp),
            kind = EditorSaveKind.FINALIZE,
            onComplete = onComplete,
            onError = onError,
        )
    }

    private val _setHistory = MutableStateFlow<SetHistory?>(null)

    /** The user's per-exercise history for the input guardrails and rest default (#304, #349). */
    val setHistory = _setHistory.asStateFlow()
    private var historyRequested = false

    /**
     * Loads every other workout once per editor, parsed off the main thread, so Enter only does a
     * map lookup. [excludeTimestamp] is this workout: an edited old workout is not its own history.
     * A failure leaves the history empty: only the history-free hints run.
     */
    fun loadSetHistory(defaultUnit: WeightUnit, excludeTimestamp: Long) {
        if (historyRequested) return
        historyRequested = true
        viewModelScope.launch {
            val history = runCatching {
                val notes = noteRepo.getAllNotes().first()
                withContext(Dispatchers.Default) { SetHistory.build(notes, excludeTimestamp, defaultUnit.storageValue) }
            }.getOrElse { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                SetHistory.EMPTY
            }
            _setHistory.value = history
        }
    }

    fun clearSaveError() {
        _saveError.value = null
    }

    private fun snapshot(
        finalText: String,
        rowMetadata: String,
        newNoteTimestamp: Long?,
    ): NoteLine {
        val timestamp = synchronized(idLock) {
            if (currentId == -1L) {
                currentId = newNoteTimestamp ?: AppClock.nowMillis()
            }
            currentId
        }

        return NoteLine(
            title = currentTitle,
            text = finalText,
            timestamp = timestamp,
            categoryName = category.name,
            categoryColor = category.color,
            learnings = currentLearnings,
            rowMetadata = rowMetadata,
        )
    }

    private fun requestSave(
        note: NoteLine,
        kind: EditorSaveKind,
        onComplete: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val revision = saveCoordinator.reserveRevision()
        _saveError.value = null

        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                withContext(NonCancellable) {
                    saveCoordinator.persist(revision, kind, note)
                }
            }

            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = { outcome ->
                        if (outcome == EditorSaveOutcome.Persisted) {
                            _uiState.value = note
                            onComplete()
                        }
                    },
                    onFailure = { error ->
                        val message = error.message?.takeIf(String::isNotBlank)
                            ?: "Workout save failed"
                        _saveError.value = message
                        onError(message)
                    },
                )
            }
        }
    }

    class Factory(
        private val noteId: Long,
        private val noteRepo: NoteRepository,
        private val workoutRepo: WorkoutRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return EditorViewModel(noteId, noteRepo, workoutRepo) as T
        }
    }
}

/**
 * Draft saves of a workout that is not the running one, or that is already finished, also
 * re-derive its sets and canonical rows, because such a workout has no Finish button and would
 * otherwise never reach Stats (#325). The running workout keeps the cheap draft-only save until
 * Finish. [isCompleted] is only queried when the workout is the running one.
 */
internal suspend fun shouldRefreshDerivedDataOnSave(
    isActiveWorkout: Boolean,
    isCompleted: suspend () -> Boolean,
): Boolean = !isActiveWorkout || isCompleted()
