package com.example.gymtrack.feature.home

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.gymtrack.core.data.Category
import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.data.Settings
import com.example.gymtrack.core.data.WeightUnit
import com.example.gymtrack.core.data.WorkoutRepository
import com.example.gymtrack.core.data.repository.NoteRepository
import com.example.gymtrack.core.data.repository.toEntity
import com.example.gymtrack.core.time.AppClock
import com.example.gymtrack.core.util.WorkoutParser
import com.example.gymtrack.core.util.exportNote
import com.example.gymtrack.core.util.importNote
import com.example.gymtrack.feature.history.TopSet
import com.example.gymtrack.feature.history.topSet
import com.example.gymtrack.feature.history.workoutCardMinutes
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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

class HomeViewModel(
    private val repository: NoteRepository,
    private val workoutRepository: WorkoutRepository,
) : ViewModel() {

    /**
     * Deletes outlive this ViewModel: a pending delete is committed in [onCleared], after
     * viewModelScope is already cancelled.
     */
    private val deletionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val pendingDeletion = PendingWorkoutDeletion(
        scope = deletionScope,
        delete = { notes ->
            notes.forEach { note -> workoutRepository.deleteWorkout(note.toEntity()) }
            // Keep the rows hidden until Room has emitted the list without them.
            val timestamps = notes.mapTo(mutableSetOf()) { it.timestamp }
            withTimeoutOrNull(2_000) {
                repository.getAllNotes().first { list -> list.none { it.timestamp in timestamps } }
            }
        },
        onDeleteError = { error -> Log.e("HomeViewModel", "Deleting workouts failed", error) },
    )

    /** Deleted workouts the Undo snackbar can still restore. */
    val pendingDelete = pendingDeletion.pending

    /** Visible workouts; null until the database answered once (no empty-state flash). */
    val loadedNotes = combine(repository.getAllNotes(), pendingDeletion.hiddenTimestamps) { notes, hidden ->
        if (hidden.isEmpty()) notes else notes.filter { it.timestamp !in hidden }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val notes = loadedNotes.map { it.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Finish time per workout timestamp (completed workouts only), for the row durations. */
    val finishTimes = workoutRepository.observeFinishTimes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * Timestamps of finished workouts, by the same rule Stats uses (completed, or old workouts
     * that already have derived set rows; docs/STATS_RULES.md). Everything else is "Unfinished".
     */
    val finishedTimestamps = repository.getStatsWorkoutFacts()
        .map { facts -> facts.completedTimestamps + facts.storedSetUnits.keys }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val parser = WorkoutParser()
    private val rowFactsCache = HashMap<Long, Pair<Int, WorkoutRowFacts>>()

    /**
     * Duration and top set per workout for the History and Recent rows. Parsed off the main
     * thread and cached per workout text, so only changed workouts are parsed again.
     */
    val rowFacts = combine(repository.getAllNotes(), finishTimes) { notes, finishes ->
        synchronized(rowFactsCache) {
            val alive = notes.mapTo(HashSet()) { it.timestamp }
            rowFactsCache.keys.retainAll(alive)
            notes.associate { note ->
                val finishedAt = finishes[note.timestamp]
                val key = 31 * (31 * note.text.hashCode() + note.rowMetadata.hashCode()) + finishedAt.hashCode()
                val cached = rowFactsCache[note.timestamp]?.takeIf { it.first == key }?.second
                val facts = cached ?: WorkoutRowFacts(
                    minutes = workoutCardMinutes(note, finishedAt),
                    topSet = runCatching {
                        topSet(parser.parseWorkout(note.text, rowMetadata = note.rowMetadata))
                    }.getOrNull(),
                ).also { rowFactsCache[note.timestamp] = key to it }
                note.timestamp to facts
            }
        }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * Saves an empty workout of [category] that the editor then opens by id, so Start <Category>
     * needs no editor change. Returns its timestamp (also the workout start).
     *
     * [text] is the prefill of "Start from last" (#305): exercise names only, never set values
     * ([WorkoutPrefill]). With no set logged the start rule cleans it up like an empty workout.
     */
    suspend fun createWorkout(category: Category, text: String = ""): Long = withContext(Dispatchers.IO) {
        // The list Today already shows is enough to avoid a taken timestamp; no extra query.
        val known = loadedNotes.value ?: repository.getAllNotes().first()
        val taken = known.mapTo(HashSet()) { it.timestamp }
        val timestamp = freeWorkoutTimestamp(AppClock.nowMillis(), taken)
        repository.saveNote(
            NoteLine(
                title = "",
                text = text,
                timestamp = timestamp,
                categoryName = category.name,
                categoryColor = category.color,
            ),
        )
        timestamp
    }

    /**
     * Finishes workouts that were left unfinished (#293). [finishedAtMillis] null ends each one
     * at its last logged row ("Discard timer", History "Mark finished"); a time ends it there,
     * subject to the duration rule in docs/WORKOUT_DURATION.md.
     */
    suspend fun markFinished(notes: Collection<NoteLine>, defaultUnit: WeightUnit, finishedAtMillis: Long?) {
        withContext(Dispatchers.IO) {
            notes.forEach { note ->
                workoutRepository.saveCompletedWorkout(note.toEntity(), defaultUnit, finishedAtMillis)
            }
        }
    }

    /** Removes a workout that has nothing in it (Start, then Back) without an Undo. */
    suspend fun deleteEmptyWorkout(note: NoteLine) {
        withContext(Dispatchers.IO) { workoutRepository.deleteWorkout(note.toEntity()) }
    }

    private val _legacyCsvImportSummary = MutableStateFlow<LegacyCsvImportSummary?>(null)
    val legacyCsvImportSummary = _legacyCsvImportSummary.asStateFlow()

    private val _legacyCsvImportProgress = MutableStateFlow<LegacyCsvImportProgress?>(null)
    val legacyCsvImportProgress = _legacyCsvImportProgress.asStateFlow()

    fun clearLegacyCsvImportSummary() {
        _legacyCsvImportSummary.value = null
    }

    /** Hides [notes] and offers Undo; the rows are only deleted by [commitPendingDelete]. */
    fun deleteNotes(notes: Set<NoteLine>) {
        pendingDeletion.request(notes)
    }

    fun undoDelete() {
        pendingDeletion.undo()
    }

    fun commitPendingDelete() {
        pendingDeletion.commit()
    }

    override fun onCleared() {
        pendingDeletion.commit()
        super.onCleared()
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

            // Finish any pending delete first so the duplicate checks see the real database.
            pendingDeletion.commit().join()
            val existingNotes = repository.getAllNotes().first()
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
                                // An import has no Finish moment; the duration ends at the last row.
                                finishedAtMillis = null,
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
            }
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

/** What a History/Recent row shows besides the date: duration and top set. */
data class WorkoutRowFacts(val minutes: Int, val topSet: TopSet?)
