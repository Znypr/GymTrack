package com.example.gymtrack.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.gymtrack.data.NoteLine
import com.example.gymtrack.data.repository.NoteRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.content.Context
import com.example.gymtrack.data.Settings
import com.example.gymtrack.util.exportNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class HomeViewModel(
    private val repository: NoteRepository
) : ViewModel() {

    val notes = repository.getAllNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    class Factory(private val repository: NoteRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(repository) as T
        }
    }
}