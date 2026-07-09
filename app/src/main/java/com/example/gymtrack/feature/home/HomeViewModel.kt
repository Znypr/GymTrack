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
import com.example.gymtrack.domain.recommendation.NextWorkoutSuggestion
import com.example.gymtrack.domain.recommendation.SuggestionConfidence
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    val skippedSupersededSnapshots: Int,
    val skippedExistingTimestamps: Int,
    val failed: Int,
    val failedNames: List<String> = emptyList(),
) {
    fun toUserMessage(): String = buildString {
        append("CSV import: $imported/$selected imported")
        if (skippedExactDuplicates > 0) {
            append(", $skippedExactDuplicates exact duplicates skipped")
        }
        if (skippedSupersededSnapshots > 0) {
            append(", $skippedSupersededSnapshots older snapshots skipped")
        }
        if (skippedExistingTimestamps > 0) {
            append(", $skippedExistingTimestamps existing timestamps skipped")
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

data class LegacyCsvImportProgress(
    val phase: String,
    val selected: Int,
    val processed: Int,
    val failed: Int = 0,
    val currentWorkoutLabel: String? = null,
) {
    val progressFraction: Float
        get() = if (selected <= 0) 0f else (processed.toFloat() / selected).coerceIn(0f, 1f)

    fun detailMessage(): String = buildString {
        append("$processed/$selected processed")
        append(" • $failed failed")
    }
}

data class NextWorkoutHomeSuggestion(
    val workoutLabel: String,
    val confidenceLabel: String,
    val reason: String,
    val suggestedExercises: List<String> = emptyList(),
)

class HomeViewModel(
    private val repository: NoteRepository,
    private val workoutRepository: WorkoutRepository,
) : ViewModel() {

    val notes = repository.getAllNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _legacyCsvImportSummary = MutableStateFlow<LegacyCsvImportSummary?>(null)
    val legacyCsvImportSummary = _legacyCsvImportSummary.asStateFlow()

    private val _legacyCsvImportProgress = MutableStateFlow<LegacyCsvImportProgress?>(null)
    val legacyCsvImportProgress = _legacyCsvImportProgress.asStateFlow()

    private val _nextWorkoutSuggestion = MutableStateFlow<NextWorkoutHomeSuggestion?>(null)
    val nextWorkoutSuggestion = _nextWorkoutSuggestion.asStateFlow()

    private var nextWorkoutSuggestionDismissed = false

    init {
        refreshNextWorkoutSuggestion()
    }

    fun clearLegacyCsvImportSummary() {
        _legacyCsvImportSummary.value = null
    }

    fun dismissNextWorkoutSuggestion() {
        nextWorkoutSuggestionDismissed = true
        _nextWorkoutSuggestion.value = null
    }

    fun refreshNextWorkoutSuggestion() {
        if (nextWorkoutSuggestionDismissed) return
        viewModelScope.launch(Dispatchers.IO) {
            val suggestion = runCatching {
                workoutRepository.getNextWorkoutSuggestion(System.currentTimeMillis())
            }.getOrNull()
            val exerciseOrder = suggestion?.let {
                runCatching { workoutRepository.getSuggestedExerciseOrder(it.workoutLabel) }.getOrNull()
            }
            _nextWorkoutSuggestion.value = suggestion?.toHomeSuggestion(
                suggestedExercises = exerciseOrder?.exercises.orEmpty().map { it.name },
            )
        }
    }

    fun deleteNotes(notes: Set<NoteLine>) {
        viewModelScope.launch(Dispatchers.IO) {
            notes.forEach { note ->
                workoutRepository.deleteWorkout(note.toEntity())
            }
            refreshNextWorkoutSuggestion()
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
        if (_legacyCsvImportProgress.value != null) return
        viewModelScope.launch(Dispatchers.IO) {
            _legacyCsvImportProgress.value = LegacyCsvImportProgress(
                phase = "Starting CSV import",
                selected = uris.size,
                processed = 0,
            )

            val existingNotes = notes.value
            val usedTimestamps = existingNotes.mapTo(mutableSetOf()) { it.timestamp }
            val knownFullFingerprints = existingNotes.mapTo(mutableSetOf()) { note ->
                legacyCsvFullFingerprint(note)
            }
            val knownContentFingerprints = existingNotes.mapTo(mutableSetOf()) { note ->
                legacyCsvContentFingerprint(note)
            }
            var failed = 0
            val failedNames = mutableListOf<String>()
            val candidates = mutableListOf<LegacyCsvImportCandidate>()

            try {
                val orderedUris = uris
                    .map { uri -> displayName(context, uri) to uri }
                    .sortedBy { (displayName, _) -> displayName.lowercase() }

                orderedUris.forEachIndexed { index, (displayName, uri) ->
                    _legacyCsvImportProgress.value = LegacyCsvImportProgress(
                        phase = "Reading CSV files",
                        selected = uris.size,
                        processed = index,
                        failed = failed,
                        currentWorkoutLabel = workoutLabelFromLegacyFileName(displayName),
                    )

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

                        candidates += LegacyCsvImportCandidate(
                            displayName = displayName,
                            note = parsedNote,
                            fileSizeBytes = tempFile.length(),
                        )
                    } catch (error: Exception) {
                        error.printStackTrace()
                        failed++
                        failedNames += displayName
                    } finally {
                        tempFile.delete()
                        _legacyCsvImportProgress.value = LegacyCsvImportProgress(
                            phase = "Reading CSV files",
                            selected = uris.size,
                            processed = index + 1,
                            failed = failed,
                            currentWorkoutLabel = workoutLabelFromLegacyFileName(displayName),
                        )
                    }
                }

                _legacyCsvImportProgress.value = LegacyCsvImportProgress(
                    phase = "Selecting best CSV snapshots",
                    selected = uris.size,
                    processed = uris.size,
                    failed = failed,
                )

                val selection = selectBestLegacyCsvCandidates(candidates)
                var imported = 0
                var skippedExistingDuplicates = 0
                var skippedExistingTimestamps = 0
                val saveTotal = selection.selected.size.coerceAtLeast(1)

                selection.selected.forEachIndexed { index, candidate ->
                    val parsedNote = candidate.note
                    val fullFingerprint = legacyCsvFullFingerprint(parsedNote)
                    val contentFingerprint = legacyCsvContentFingerprint(parsedNote)
                    val timestampAlreadyUsed = parsedNote.timestamp in usedTimestamps
                    val workoutLabel = parsedNote.importProgressLabel()

                    when {
                        fullFingerprint in knownFullFingerprints ||
                            contentFingerprint in knownContentFingerprints -> {
                            skippedExistingDuplicates++
                        }
                        timestampAlreadyUsed -> {
                            skippedExistingTimestamps++
                        }
                        else -> {
                            workoutRepository.saveCompletedWorkout(
                                note = parsedNote.toEntity(),
                                defaultWeightUnit = settings.defaultWeightUnit,
                            )
                            usedTimestamps += parsedNote.timestamp
                            knownFullFingerprints += legacyCsvFullFingerprint(parsedNote)
                            knownContentFingerprints += legacyCsvContentFingerprint(parsedNote)
                            imported++
                        }
                    }

                    _legacyCsvImportProgress.value = LegacyCsvImportProgress(
                        phase = "Saving imported workouts",
                        selected = saveTotal,
                        processed = index + 1,
                        failed = failed,
                        currentWorkoutLabel = workoutLabel,
                    )
                }

                _legacyCsvImportSummary.value = LegacyCsvImportSummary(
                    selected = uris.size,
                    imported = imported,
                    skippedExactDuplicates = selection.exactDuplicates + skippedExistingDuplicates,
                    skippedSupersededSnapshots = selection.supersededSnapshots,
                    skippedExistingTimestamps = skippedExistingTimestamps,
                    failed = failed,
                    failedNames = failedNames,
                )
            } finally {
                _legacyCsvImportProgress.value = null
                refreshNextWorkoutSuggestion()
            }
        }
    }

    private fun NextWorkoutSuggestion.toHomeSuggestion(
        suggestedExercises: List<String>,
    ): NextWorkoutHomeSuggestion = NextWorkoutHomeSuggestion(
        workoutLabel = workoutLabel,
        confidenceLabel = when (confidence) {
            SuggestionConfidence.LOW -> "Low confidence"
            SuggestionConfidence.MEDIUM -> "Medium confidence"
            SuggestionConfidence.HIGH -> "High confidence"
        },
        reason = reason,
        suggestedExercises = suggestedExercises,
    )

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

    private fun NoteLine.importProgressLabel(): String {
        val titleLabel = title.trim().ifBlank { categoryName?.trim().orEmpty() }.ifBlank { "Workout" }
        val dateLabel = importDateFormatter.format(Date(timestamp))
        return "$titleLabel · $dateLabel"
    }

    private fun workoutLabelFromLegacyFileName(fileName: String): String? {
        val match = legacyFileNamePattern.find(fileName) ?: return null
        val month = match.groupValues[1]
        val day = match.groupValues[2]
        val year = match.groupValues[3]
        val hour = match.groupValues[4]
        val minute = match.groupValues[5]
        return "Workout · $day/$month/20$year $hour:$minute"
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

    private companion object {
        val importDateFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val legacyFileNamePattern = Regex("note-(\\d{2})-(\\d{2})-(\\d{2})-(\\d{2})-(\\d{2})", RegexOption.IGNORE_CASE)
    }
}
