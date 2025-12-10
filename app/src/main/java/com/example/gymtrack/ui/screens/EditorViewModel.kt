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

class EditorViewModel(
    private val initialNote: NoteLine?,
    private val noteRepo: NoteRepository,
    private val workoutRepo: WorkoutRepository,
    private val context: Context
) : ViewModel() {

    // Initialize immediately with the passed note
    private val _uiState = MutableStateFlow<NoteLine?>(initialNote)
    val uiState = _uiState.asStateFlow()

    var currentTitle = initialNote?.title ?: ""
    var currentCategory: Category? = initialNote?.let {
        Category(it.categoryName ?: "Uncategorized", it.categoryColor ?: 0xFF808080)
    }
    var currentLearnings = initialNote?.learnings ?: ""

    init {
        // Defaults for new note if null
        if (initialNote == null) {
            currentCategory = Category("Push", 0xFFFF3B30)
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

    // Heavy Logic: Save Note
    fun saveNote(
        finalText: String,
        settings: Settings,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(kotlinx.coroutines.NonCancellable) {
                val timestamp = initialNote?.timestamp ?: System.currentTimeMillis()

                // 1. Construct Domain Object
                val updatedNote = NoteLine(
                    title = currentTitle,
                    text = finalText,
                    timestamp = timestamp,
                    categoryName = currentCategory?.name,
                    categoryColor = currentCategory?.color,
                    learnings = currentLearnings
                )

                // 2. Save to DB [cite: 585]
                noteRepo.saveNote(updatedNote)

                // 3. Parse for Graphs
                val entity = NoteEntity(
                    timestamp, updatedNote.title, updatedNote.text,
                    updatedNote.categoryName, updatedNote.categoryColor, updatedNote.learnings
                )
                workoutRepo.syncNoteToWorkout(entity)

                // 4. Export CSV
                exportNote(context, updatedNote, settings)
            }

            // 5. Notify UI (Main Thread)
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    // Factory
    class Factory(
        private val note: NoteLine?,
        private val noteRepo: NoteRepository,
        private val workoutRepo: WorkoutRepository,
        private val context: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return EditorViewModel(note, noteRepo, workoutRepo, context) as T
        }
    }
}