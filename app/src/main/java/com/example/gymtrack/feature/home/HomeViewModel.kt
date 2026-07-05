package com.example.gymtrack.feature.home

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.data.Settings
import com.example.gymtrack.core.data.WorkoutRepository
import com.example.gymtrack.core.data.repository.NoteRepository
import com.example.gymtrack.core.data.repository.toEntity
import com.example.gymtrack.core.util.exportNote
import com.example.gymtrack.core.util.importNote
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(
    private val repository: NoteRepository,
    private val workoutRepository: WorkoutRepository,
) : ViewModel() {

    val notes = repository.getAllNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteNotes(notes: Set<NoteLine>) {
        viewModelScope.launch {
            repository.deleteNotes(notes)
            notes.forEach { note ->
                workoutRepository.deleteWorkout(note.timestamp)
            }
        }
    }

    suspend fun exportNotes(
        context: Context,
        notes: Set<NoteLine>,
        settings: Settings,
    ): List<File> = withContext(Dispatchers.IO) {
        notes.map { note ->
            val file = exportNote(context, note, settings)
            if (!file.exists() || file.name == "export_failed.log") {
                val displayName = note.title.ifBlank { "workout" }
                throw IOException("Could not export $displayName")
            }
            file
        }
    }

    fun importNotesFromUris(context: Context, uris: List<Uri>, settings: Settings) {
        viewModelScope.launch(Dispatchers.IO) {
            uris.forEach { uri ->
                val uniqueName = "temp_import_${UUID.randomUUID()}.csv"
                val tempFile = File(context.cacheDir, uniqueName)

                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use(input::copyTo)
                    }

                    importNote(tempFile, settings)?.let { note ->
                        workoutRepository.saveCompletedWorkout(note.toEntity())
                    }
                } catch (error: Exception) {
                    error.printStackTrace()
                } finally {
                    tempFile.delete()
                }
            }
        }
    }

    class Factory(
        private val repository: NoteRepository,
        private val workoutRepository: WorkoutRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(repository, workoutRepository) as T
        }
    }
}
