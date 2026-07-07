package com.example.gymtrack.feature.home

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LegacyCsvImportSummary(
    val selected: Int,
    val imported: Int,
    val skippedExactDuplicates: Int,
    val adjustedTimestampCollisions: Int,
    val failed: Int,
    val failedNames: List<String> = emptyList(),
) {
    fun toUserMessage(): String = buildString {
        append("CSV import: $imported/$selected imported")
        if (skippedExactDuplicates > 0) {
            append(", $skippedExactDuplicates exact duplicates skipped")
        }
        if (adjustedTimestampCollisions > 0) {
            append(", $adjustedTimestampCollisions timestamp collisions preserved")
        }
        if (failed > 0) {
            append(", $failed failed")
            failedNames.take(3).takeIf { it.isNotEmpty() }?.let { names ->
                append(". First failed: ")
                append(names.joinToString())
            }
        }
    }
}

class HomeViewModel(
    private val repository: NoteRepository,
    private val workoutRepository: WorkoutRepository,
) : ViewModel() {

    val notes = repository.getAllNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _legacyCsvImportSummary = MutableStateFlow<LegacyCsvImportSummary?>(null)
    val legacyCsvImportSummary = _legacyCsvImportSummary.asStateFlow()

    fun clearLegacyCsvImportSummary() {
        _legacyCsvImportSummary.value = null
    }

    fun deleteNotes(notes: Set<NoteLine>) {
        viewModelScope.launch(Dispatchers.IO) {
            notes.forEach { note ->
                workoutRepository.deleteWorkout(note.toEntity())
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
            val existingNotes = notes.value
            val usedTimestamps = existingNotes.mapTo(mutableSetOf()) { it.timestamp }
            val knownFullFingerprints = existingNotes.mapTo(mutableSetOf()) { note ->
                legacyCsvFullFingerprint(note)
            }
            val knownContentFingerprints = existingNotes.mapTo(mutableSetOf()) { note ->
                legacyCsvContentFingerprint(note)
            }
            var imported = 0
            var skippedExactDuplicates = 0
            var adjustedTimestampCollisions = 0
            var failed = 0
            val failedNames = mutableListOf<String>()

            uris
                .map { uri -> displayName(context, uri) to uri }
                .sortedBy { (displayName, _) -> displayName.lowercase() }
                .forEach { (displayName, uri) ->
                    val uniqueName = "temp_import_${UUID.randomUUID()}.csv"
                    val tempFile = File(context.cacheDir, uniqueName)

                    try {
                        val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                            true
                        } ?: false
                        if (!copied) {
                            throw IOException("Could not read $displayName")
                        }

                        val parsedNote = importNote(tempFile, settings)
                            ?: throw IOException("Could not parse $displayName")
                        if (parsedNote.timestamp <= 0L) {
                            throw IOException("Invalid timestamp in $displayName")
                        }

                        val fullFingerprint = legacyCsvFullFingerprint(parsedNote)
                        val contentFingerprint = legacyCsvContentFingerprint(parsedNote)
                        val timestampAlreadyUsed = parsedNote.timestamp in usedTimestamps

                        if (fullFingerprint in knownFullFingerprints ||
                            timestampAlreadyUsed && contentFingerprint in knownContentFingerprints
                        ) {
                            skippedExactDuplicates++
                        } else {
                            val timestampAllocation = allocateLegacyCsvTimestamp(
                                originalTimestamp = parsedNote.timestamp,
                                usedTimestamps = usedTimestamps,
                            )
                            val note = if (timestampAllocation.adjusted) {
                                adjustedTimestampCollisions++
                                parsedNote.copy(timestamp = timestampAllocation.timestamp)
                            } else {
                                parsedNote
                            }
                            workoutRepository.saveCompletedWorkout(
                                note = note.toEntity(),
                                defaultWeightUnit = settings.defaultWeightUnit,
                            )
                            knownFullFingerprints += legacyCsvFullFingerprint(note)
                            knownContentFingerprints += legacyCsvContentFingerprint(note)
                            imported++
                        }
                    } catch (error: Exception) {
                        error.printStackTrace()
                        failed++
                        failedNames += displayName
                    } finally {
                        tempFile.delete()
                    }
                }

            _legacyCsvImportSummary.value = LegacyCsvImportSummary(
                selected = uris.size,
                imported = imported,
                skippedExactDuplicates = skippedExactDuplicates,
                adjustedTimestampCollisions = adjustedTimestampCollisions,
                failed = failed,
                failedNames = failedNames,
            )
        }
    }

    private fun displayName(context: Context, uri: Uri): String {
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment
            ?: uri.toString()
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
