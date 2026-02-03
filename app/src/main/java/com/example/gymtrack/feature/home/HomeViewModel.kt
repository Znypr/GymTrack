// FILE PATH: C:\Users\znypr\AndroidStudioProjects\GymTrack\app\src\main\java\com\example\gymtrack\feature\home\HomeViewModel.kt

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
import com.example.gymtrack.core.data.WorkoutRepository
import com.example.gymtrack.core.data.repository.toEntity
import java.util.UUID

class HomeViewModel(
    private val repository: NoteRepository,
    private val workoutRepository: WorkoutRepository
) : ViewModel() {

    val notes = repository.getAllNotes()
        .stateIn(viewModelScope, SharingStarted.Companion.WhileSubscribed(5000), emptyList())

    fun deleteNotes(notes: Set<NoteLine>) {
        viewModelScope.launch {
            // 1. Delete the Text Notes
            repository.deleteNotes(notes)

            // 2. Delete the associated Stats
            notes.forEach { note ->
                workoutRepository.deleteWorkout(note.timestamp)
            }
        }
    }

    suspend fun exportNotes(context: Context, notes: Set<NoteLine>, settings: Settings): List<File> {
        return withContext(Dispatchers.IO) {
            notes.map { exportNote(context, it, settings) }
        }
    }

    // [FIX] Updated to support multiple files
    fun importNotesFromUris(context: Context, uris: List<Uri>, settings: Settings) {
        viewModelScope.launch(Dispatchers.IO) {
            uris.forEach { uri ->
                try {
                    // 1. Copy content from Uri to a unique temporary file
                    // We use UUID to ensure no conflicts during the loop
                    val uniqueName = "temp_import_${UUID.randomUUID()}.csv"
                    val tempFile = File(context.cacheDir, uniqueName)

                    val inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        tempFile.outputStream().use { output ->
                            inputStream.copyTo(output)
                        }
                        inputStream.close()

                        // 2. Parse using your existing ImportUtils
                        val note = importNote(tempFile, settings)

                        if (note != null) {
                            // 3. Save Text
                            repository.saveNote(note)
                            // 4. Generate Stats immediately
                            workoutRepository.syncNoteToWorkout(note.toEntity())
                        }
                    }

                    // 5. Cleanup
                    if (tempFile.exists()) {
                        tempFile.delete()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    class Factory(
        private val repository: NoteRepository,
        private val workoutRepository: WorkoutRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(repository, workoutRepository) as T
        }
    }
}