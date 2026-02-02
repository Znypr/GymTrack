package com.example.gymtrack.feature.home

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.data.Settings
import com.example.gymtrack.core.data.repository.NoteRepository
import com.example.gymtrack.core.util.exportNote
import com.example.gymtrack.core.util.importNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class HomeViewModel(
    private val repository: NoteRepository
) : ViewModel() {

    val notes = repository.getAllNotes()
        .stateIn(viewModelScope, SharingStarted.Companion.WhileSubscribed(5000), emptyList())

    fun deleteNotes(notes: Set<NoteLine>) {
        viewModelScope.launch {
            repository.deleteNotes(notes)
        }
    }

    suspend fun exportNotes(context: Context, notes: Set<NoteLine>, settings: Settings): List<File> {
        return withContext(Dispatchers.IO) {
            notes.map { exportNote(context, it, settings) }
        }
    }

    // [FIX] Added Import Functionality
    fun importNoteFromUri(context: Context, uri: Uri, settings: Settings) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Copy content from Uri to a temporary file
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@launch
                val tempFile = File(context.cacheDir, "temp_import.csv")

                tempFile.outputStream().use { output ->
                    inputStream.copyTo(output)
                }
                inputStream.close()

                // 2. Parse using your existing ImportUtils
                val note = importNote(tempFile, settings)

                // 3. Save to Database
                if (note != null) {
                    repository.saveNote(note)
                }

                // 4. Cleanup
                tempFile.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    class Factory(private val repository: NoteRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(repository) as T
        }
    }
}