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
    private val noteId: Long?, // If null, we are creating a new note
    private val noteRepo: NoteRepository,
    private val workoutRepo: WorkoutRepository,
    private val context: Context // Needed for export, pass Application context in real apps
) : ViewModel() {

    // UI State
    private val _uiState = MutableStateFlow<NoteLine?>(null)
    val uiState = _uiState.asStateFlow()

    // Temporary state for the editor fields
    // We keep these public so the UI can bind to them directly
    var currentTitle = ""
    var currentCategory: Category? = null
    var currentLearnings = ""

    // We load data immediately
    init {
        if (noteId != null) {
            viewModelScope.launch {
                // Fetch the latest version from DB to ensure robustness
                // Note: You might need to add a 'getById' to your Repository/DAO.
                // For now, we rely on the object passed or standard flow.
                // ideally: val note = noteRepo.getById(noteId)
            }
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
            val timestamp = noteId ?: System.currentTimeMillis()

            // 1. Construct the Domain Object
            val updatedNote = NoteLine(
                title = currentTitle,
                text = finalText,
                timestamp = timestamp,
                categoryName = currentCategory?.name,
                categoryColor = currentCategory?.color,
                learnings = currentLearnings
            )

            // 2. Save to UI Database
            noteRepo.saveNote(updatedNote)

            // 3. Parse & Save for Graphs (The "Robust" step)
            // We map to Entity temporarily for the legacy helper
            val entity = NoteEntity(
                timestamp, updatedNote.title, updatedNote.text,
                updatedNote.categoryName, updatedNote.categoryColor, updatedNote.learnings
            )
            workoutRepo.syncNoteToWorkout(entity)

            // 4. Export to CSV
            exportNote(context, updatedNote, settings)

            // 5. Notify UI
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    // Factory
    class Factory(
        private val noteId: Long?,
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