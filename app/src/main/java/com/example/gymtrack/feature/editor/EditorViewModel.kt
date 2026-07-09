package com.example.gymtrack.feature.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.gymtrack.core.data.Category
import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.data.WeightUnit
import com.example.gymtrack.core.data.WorkoutRepository
import com.example.gymtrack.core.data.repository.NoteRepository
import com.example.gymtrack.core.data.repository.toEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditorViewModel(
    private val initialId: Long,
    private val noteRepo: NoteRepository,
    private val workoutRepo: WorkoutRepository,
    private val suggestedCategoryName: String? = null,
    private val suggestedDraftText: String = "",
) : ViewModel() {

    private val _uiState = MutableStateFlow<NoteLine?>(null)
    val uiState = _uiState.asStateFlow()

    private val _saveError = MutableStateFlow<String?>(null)
    val saveError = _saveError.asStateFlow()

    private val _exerciseSuggestions = MutableStateFlow<List<String>>(emptyList())
    val exerciseSuggestions = _exerciseSuggestions.asStateFlow()

    var currentDefaultWeightUnit: WeightUnit = WeightUnit.KG

    private val saveCoordinator = EditorSaveCoordinator(
        persistDraft = noteRepo::saveNote,
        finalizeWorkout = { note ->
            workoutRepo.saveCompletedWorkout(note.toEntity(), currentDefaultWeightUnit)
        },
    )

    private val idLock = Any()

    var currentId: Long = initialId
        private set

    var currentTitle = ""
    var currentCategory: Category? = null
    var currentLearnings = ""

    init {
        if (initialId != -1L) {
            viewModelScope.launch {
                val note = noteRepo.getNoteById(initialId)
                initialize(note)
            }
        } else {
            initialize(null)
            currentCategory?.name?.let(::refreshExerciseSuggestionsForCategory)
        }
    }

    fun initialize(note: NoteLine?) {
        if (note != null) {
            currentTitle = note.title
            currentCategory = Category(
                note.categoryName ?: "Uncategorized",
                note.categoryColor ?: 0xFF808080,
            )
            currentLearnings = note.learnings
            _uiState.value = note
        } else {
            val suggestedCategory = suggestedCategoryName
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { name -> Category(name, categoryColorFor(name)) }
            currentCategory = suggestedCategory ?: Category("Push", 0xFFFF3B30)
            if (suggestedDraftText.isNotBlank()) {
                _uiState.value = NoteLine(
                    title = "",
                    text = suggestedDraftText,
                    timestamp = System.currentTimeMillis(),
                    categoryName = currentCategory?.name,
                    categoryColor = currentCategory?.color,
                )
            }
        }
    }

    fun refreshExerciseSuggestionsForCategory(categoryName: String) {
        if (initialId != -1L) return
        viewModelScope.launch(Dispatchers.IO) {
            val suggestions = runCatching {
                workoutRepo.getSuggestedExerciseOrder(categoryName)
            }.getOrNull()
                ?.exercises
                .orEmpty()
                .map { it.name }
            _exerciseSuggestions.value = suggestions
        }
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
                currentId = newNoteTimestamp ?: System.currentTimeMillis()
            }
            currentId
        }

        return NoteLine(
            title = currentTitle,
            text = finalText,
            timestamp = timestamp,
            categoryName = currentCategory?.name,
            categoryColor = currentCategory?.color,
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
        private val suggestedCategoryName: String? = null,
        private val suggestedDraftText: String = "",
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return EditorViewModel(
                initialId = noteId,
                noteRepo = noteRepo,
                workoutRepo = workoutRepo,
                suggestedCategoryName = suggestedCategoryName,
                suggestedDraftText = suggestedDraftText,
            ) as T
        }
    }

    private fun categoryColorFor(name: String): Long = when (name.trim().lowercase()) {
        "push" -> 0xFFFF3B30
        "pull" -> 0xFFAF52DE
        "legs" -> 0xFF34C759
        else -> 0xFF808080
    }
}
