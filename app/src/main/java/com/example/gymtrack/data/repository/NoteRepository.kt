package com.example.gymtrack.data.repository

import com.example.gymtrack.data.NoteDao
import com.example.gymtrack.data.NoteEntity
import com.example.gymtrack.data.NoteLine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// REMOVED: @Inject annotation
class NoteRepository(private val dao: NoteDao) {

    fun getAllNotes(): Flow<List<NoteLine>> = dao.getAll().map { entities ->
        entities.map { it.toDomainModel() }
    }

    suspend fun saveNote(note: NoteLine) {
        dao.insert(note.toEntity())
    }

    suspend fun deleteNotes(notes: Set<NoteLine>) {
        notes.forEach { dao.delete(it.toEntity()) }
    }
}

// Mappers
fun NoteEntity.toDomainModel() = NoteLine(title, text, timestamp, categoryName, categoryColor, learnings ?: "")
fun NoteLine.toEntity() = NoteEntity(timestamp, title, text, categoryName, categoryColor, learnings)