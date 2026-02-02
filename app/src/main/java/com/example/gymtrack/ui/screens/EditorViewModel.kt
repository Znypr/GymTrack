package com.example.gymtrack.ui.screens

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.gymtrack.data.Category
import com.example.gymtrack.data.NoteEntity
import com.example.gymtrack.data.NoteLine
import com.example.gymtrack.data.Settings
import com.example.gymtrack.data.repository.NoteRepository
import com.example.gymtrack.data.WorkoutRepository
import com.example.gymtrack.util.exportNote
import com.example.gymtrack.util.formatRoundedTime
import com.example.gymtrack.util.getRelativeTimeDiffString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi

class EditorViewModel(
    private val initialId: Long,
    private val noteRepo: NoteRepository,
    private val workoutRepo: WorkoutRepository,
    private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<NoteLine?>(null)
    val uiState = _uiState.asStateFlow()

    var currentId: Long = initialId // This can now change from -1 to a real timestamp

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
            initialize(null) // New note
        }
    }


    // Initialize the editor with data (Called once when UI loads)
    fun initialize(note: NoteLine?) {
        if (note != null) {
            currentTitle = note.title
            currentCategory = Category(note.categoryName ?: "Uncategorized", note.categoryColor ?: 0xFF808080)
            currentLearnings = note.learnings
            _uiState.value = note
        } else {
            // Defaults for new note
            currentCategory = Category("Push", 0xFFFF3B30) // Default
        }
    }

    fun saveNote(finalText: String, settings: Settings, onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(kotlinx.coroutines.NonCancellable) {
                val timestamp = if (currentId != -1L) currentId else System.currentTimeMillis()
                currentId = timestamp

                val updatedNote = NoteLine(
                    title = currentTitle,
                    text = finalText,
                    timestamp = timestamp,
                    categoryName = currentCategory?.name,
                    categoryColor = currentCategory?.color,
                    learnings = currentLearnings
                )

                noteRepo.saveNote(updatedNote)

                // CRITICAL: Update the internal state so the ViewModel knows it's saved
                _uiState.value = updatedNote

                val entity = NoteEntity(timestamp, updatedNote.title, updatedNote.text,
                    updatedNote.categoryName, updatedNote.categoryColor, updatedNote.learnings)
                workoutRepo.syncNoteToWorkout(entity)
                exportNote(context, updatedNote, settings)
            }
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    // Factory
    class Factory(
        private val noteId: Long,
        private val noteRepo: NoteRepository,
        private val workoutRepo: WorkoutRepository,
        private val context: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return EditorViewModel(noteId, noteRepo, workoutRepo, context) as T
        }
    }
}