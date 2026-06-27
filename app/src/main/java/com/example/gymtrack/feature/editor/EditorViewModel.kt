package com.example.gymtrack.feature.editor

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.gymtrack.core.data.Category
import com.example.gymtrack.core.data.NoteEntity
import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.data.Settings
import com.example.gymtrack.core.data.WorkoutRepository
import com.example.gymtrack.core.data.repository.NoteRepository
import com.example.gymtrack.core.util.exportNote
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
    private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow<NoteLine?>(null)
    val uiState = _uiState.asStateFlow()

    var currentId: Long = initialId

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
            currentCategory = Category("Push", 0xFFFF3B30)
        }
    }

    fun saveNote(
        finalText: String,
        settings: Settings,
        newNoteTimestamp: Long? = null,
        onComplete: () -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(NonCancellable) {
                val timestamp = if (currentId != -1L) {
                    currentId
                } else {
                    newNoteTimestamp ?: System.currentTimeMillis()
                }
                currentId = timestamp

                val updatedNote = NoteLine(
                    title = currentTitle,
                    text = finalText,
                    timestamp = timestamp,
                    categoryName = currentCategory?.name,
                    categoryColor = currentCategory?.color,
                    learnings = currentLearnings,
                )

                noteRepo.saveNote(updatedNote)
                _uiState.value = updatedNote

                val entity = NoteEntity(
                    timestamp,
                    updatedNote.title,
                    updatedNote.text,
                    updatedNote.categoryName,
                    updatedNote.categoryColor,
                    updatedNote.learnings,
                )
                workoutRepo.syncNoteToWorkout(entity)
                exportNote(context, updatedNote, settings)
            }
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    class Factory(
        private val noteId: Long,
        private val noteRepo: NoteRepository,
        private val workoutRepo: WorkoutRepository,
        private val context: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return EditorViewModel(noteId, noteRepo, workoutRepo, context) as T
        }
    }
}
