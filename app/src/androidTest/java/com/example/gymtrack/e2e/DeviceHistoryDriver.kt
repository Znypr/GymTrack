package com.example.gymtrack.e2e

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.example.gymtrack.core.backup.BackupRepository
import com.example.gymtrack.core.data.Category
import com.example.gymtrack.core.data.NoteDatabase
import com.example.gymtrack.core.data.SettingsStore
import com.example.gymtrack.core.data.WeightUnit
import com.example.gymtrack.core.data.WorkoutRepository
import com.example.gymtrack.core.data.repository.NoteRepository
import com.example.gymtrack.core.data.repository.toDomainModel
import com.example.gymtrack.core.data.transition.CanonicalImportRunner
import com.example.gymtrack.core.timer.NoteTimerStore
import com.example.gymtrack.core.util.WorkoutParser
import com.example.gymtrack.feature.editor.EditorViewModel
import com.example.gymtrack.feature.home.HomeViewModel
import com.example.gymtrack.feature.stats.StatsViewModel
import com.example.gymtrack.simulation.CanonicalRows
import com.example.gymtrack.simulation.EditorSession
import com.example.gymtrack.simulation.EditorTranscript
import com.example.gymtrack.simulation.HistoryInvariants
import com.example.gymtrack.simulation.Severity
import com.example.gymtrack.simulation.SimEvent
import com.example.gymtrack.simulation.SimEventType
import com.example.gymtrack.simulation.SimHistory
import com.example.gymtrack.simulation.SimWorkout
import com.example.gymtrack.simulation.WorkoutHistoryGenerator
import com.example.gymtrack.simulation.SimulationFindings
import java.io.File
import java.util.TimeZone
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** Device budgets (ms) for a mid-range phone; a breach is a finding, not a test failure. */
object PerfBudget {
    const val HOME_LOAD_MS = 500L
    const val EDITOR_OPEN_MS = 300L
    const val STATS_FIRST_MS = 2_000L
    const val STATS_CACHED_MS = 300L
    const val STARTUP_IMPORT_MS = 1_500L
    const val BACKUP_MS = 3_000L
    const val BACKUP_BYTES = 5L * 1024L * 1024L
}

/**
 * Pushes a generated history through the app's real persistence paths, in simulated time:
 * draft save (NoteRepository.saveNote, what Back and ON_STOP call), finalization
 * (WorkoutRepository.saveCompletedWorkout, what Finish calls), the startup path
 * (CanonicalImportRunner + checkAndMigrate, what MainActivity runs on every launch), timer
 * state, settings events and backup → restore. Checks invariants after every workout and week,
 * and performance budgets at months 3, 6 and 12.
 *
 * [uiDriver] may take over a workout (real editor UI); it returns true when it did.
 */
class DeviceHistoryDriver(
    private val context: Context,
    private val database: NoteDatabase,
    private val clock: FakeClock,
    val history: SimHistory,
    val findings: SimulationFindings = SimulationFindings(),
    /** Workouts already in the database before the simulation (e.g. a migrated old schema). */
    private val preexistingWorkouts: Int = 0,
) {
    private val noteRepository = NoteRepository(database.noteDao())
    private val workoutRepository = WorkoutRepository(database)
    private val backupRepository = BackupRepository(database)
    private val parser = WorkoutParser()
    private val invariants = HistoryInvariants(history, findings)
    private val homeZone = TimeZone.getTimeZone(history.homeZone)

    val sessions = mutableListOf<EditorSession>()
    private val finishedTimestamps = mutableSetOf<Long>()

    fun run(
        perfDays: Set<Int> = setOf(91, 182, 364),
        onEvent: (SimEvent) -> Unit = {},
        uiDriver: (SimWorkout, EditorSession) -> Boolean = { _, _ -> false },
    ) {
        val originalZone = TimeZone.getDefault()
        TimeZone.setDefault(homeZone)
        try {
            val lastDay = history.workouts.maxOf { it.day }
            val workoutsByDay = history.workouts.groupBy { it.day }
            val eventsByDay = history.events.groupBy { it.day }
            for (day in 0..lastDay) {
                eventsByDay[day].orEmpty().forEach { event ->
                    applyEvent(event)
                    onEvent(event)
                }
                workoutsByDay[day].orEmpty().forEach { workout ->
                    val session = EditorTranscript.build(workout)
                    sessions += session
                    TimeZone.setDefault(TimeZone.getTimeZone(workout.zoneId))
                    val byUi = uiDriver(workout, session)
                    if (!byUi) saveThroughRepositories(session)
                    TimeZone.setDefault(homeZone)
                    checkWorkout(session, byUi)
                }
                if (day % 7 == 6 || day == lastDay) checkWeek(day)
                if (day in perfDays || day == lastDay) measureBudgets(day)
            }
        } finally {
            TimeZone.setDefault(originalZone)
        }
    }

    private fun saveThroughRepositories(session: EditorSession) = runBlocking {
        val w = session.workout
        clock.setTo(w.startMillis)
        NoteTimerStore.startOrRestore(context, w.startMillis)
        session.draftAtBack?.let { draft ->
            clock.setTo(session.truth.lastOrNull()?.enteredAtMillis ?: w.startMillis)
            noteRepository.saveNote(draft.toDomainModel())
        }
        val finished = session.finishedAtMillis ?: return@runBlocking
        clock.setTo(finished)
        workoutRepository.saveCompletedWorkout(session.finalNote, w.unit)
        NoteTimerStore.stop(context, w.startMillis)
        finishedTimestamps += w.startMillis
    }

    fun markFinishedByUi(timestamp: Long) {
        finishedTimestamps += timestamp
    }

    private fun checkWorkout(session: EditorSession, byUi: Boolean) = runBlocking {
        val w = session.workout
        val note = database.noteDao().getById(w.startMillis)
        fun report(id: String, severity: Severity, title: String, example: String, issue: String? = null) =
            findings.report(id, severity, title, history, w.day, w.index, example, issue)

        if (note == null) {
            report("WORKOUT-LOST", Severity.HIGH, "Workout missing from the database after save", "workout ${w.index} ${w.date}")
            return@runBlocking
        }
        val expected = session.draftAtBack.takeIf { session.finishedAtMillis == null } ?: session.finalNote
        if (note.text != expected.text || note.rowMetadata != expected.rowMetadata) {
            report(
                if (byUi) "UI-NOTE-DIFFERS-FROM-MODEL" else "NOTE-ROUND-TRIP",
                Severity.HIGH,
                if (byUi) "Note written by the real editor differs from the editor model used by the JVM simulation" else "Saved note differs from what was saved",
                "workout ${w.index} ${w.date} tags=${w.tags}\nexpected: ${expected.text.lines().take(4)}\nactual:   ${note.text.lines().take(4)}",
            )
        }
        if (session.finishedAtMillis == null) return@runBlocking

        val canonicalWorkout = database.canonicalWorkoutDao().getByLegacyTimestamp(w.startMillis)
        val canonical = CanonicalRows(
            canonicalWorkout,
            canonicalWorkout?.let { database.canonicalWorkoutExerciseDao().getForWorkout(it.id) }.orEmpty(),
            canonicalWorkout?.let { database.canonicalWorkoutSetDao().getForWorkout(it.id) }.orEmpty(),
        )
        val parsed = parser.parseWorkout(note.text, w.unit.storageValue, note.rowMetadata)
        invariants.checkFinishedWorkout(session, parsed, canonical)

        val legacy = database.setDao().getAllForBackup().filter { it.workoutId == w.startMillis }
        if (legacy.size != parsed.size) {
            report("LEGACY-SET-COUNT", Severity.HIGH, "sets table row count differs from the saved note", "workout ${w.index}: sets ${legacy.size}, note ${parsed.size}")
        }
        if (canonicalWorkout?.status != "COMPLETED") {
            report("NOT-COMPLETED-AFTER-FINISH", Severity.HIGH, "Workout not COMPLETED right after Finish", "workout ${w.index}: ${canonicalWorkout?.status}")
        }
        val timer = NoteTimerStore.observe(context).first()
        if (timer.activeNoteTimestamp == w.startMillis) {
            report("TIMER-NOT-STOPPED", Severity.MEDIUM, "Timer still active after Finish", "workout ${w.index}")
        }
    }

    private fun checkWeek(day: Int) = runBlocking {
        val notes = noteRepository.getAllNotes().first()
        fun report(id: String, severity: Severity, title: String, example: String, issue: String? = null) =
            findings.report(id, severity, title, history, day, null, example, issue)

        if (notes.size != sessions.size + preexistingWorkouts) {
            report("WORKOUT-COUNT", Severity.HIGH, "Workout count in DB differs from workouts done", "db ${notes.size}, done ${sessions.size} + $preexistingWorkouts pre-existing")
        }
        if (notes.map { it.timestamp }.toSet().size != notes.size) {
            report("WORKOUT-DUPLICATED", Severity.HIGH, "Duplicate workouts in DB", "${notes.size} notes")
        }
        invariants.checkStats(day, notes, sessions, noteRepository.getStatsWorkoutFacts().first())

        // Canonical vs legacy totals for workouts the user finished.
        val legacyBySession = database.setDao().getAllForBackup().groupBy { it.workoutId }
        sessions.filter { it.isFinished }.forEach { session ->
            val ts = session.workout.startMillis
            val note = notes.firstOrNull { it.timestamp == ts } ?: return@forEach
            val fromText = parser.parseWorkout(note.text, session.workout.unit.storageValue, note.rowMetadata)
            val legacy = legacyBySession[ts].orEmpty()
            val sameValues = legacy.size == fromText.size &&
                legacy.zip(fromText).all { (row, dto) -> row.reps == dto.reps && row.weight == dto.weight }
            if (!sameValues) {
                report(
                    "LEGACY-STALE-AFTER-EDIT", Severity.HIGH,
                    "Editing a finished workout later does not update the sets table (Stats progress charts keep the old numbers)",
                    "workout ${session.workout.index} ${session.workout.date}: note says ${fromText.take(2).map { "${it.reps}x${it.weight}" }}, sets table ${legacy.take(2).map { "${it.reps}x${it.weight}" }}",
                )
            }
        }

        // Stats ViewModel counts what the generator logged.
        val stats = withStatsViewModel { vm ->
            withTimeout(30_000L) { vm.uiState.first { !it.isLoading && it.sourceNoteCount == notes.size } }
        }
        if (stats.totalNotes != sessions.size + preexistingWorkouts) {
            report("STATS-WORKOUT-COUNT", Severity.MEDIUM, "Stats 'Workouts' differs from workouts done", "stats ${stats.totalNotes}, done ${sessions.size}")
        }
        val finishedCount = sessions.count { it.isFinished } + preexistingWorkouts
        if (stats.totalNotes != finishedCount) {
            report("STATS-COUNTS-UNFINISHED", Severity.LOW, "Stats 'Workouts' counts unfinished workouts", "stats ${stats.totalNotes}, finished $finishedCount")
        }
    }

    private fun applyEvent(event: SimEvent) = runBlocking {
        fun report(id: String, severity: Severity, title: String, example: String, issue: String? = null) =
            findings.report(id, severity, title, history, event.day, null, example, issue)
        clock.setTo(history.dateOf(event.day).atStartOfDay(history.homeZone).toInstant().toEpochMilli() + 12 * 3_600_000L)
        val settings = SettingsStore.load(context)
        when (event.type) {
            SimEventType.UNIT_SWITCH_TO_LB -> SettingsStore.save(context, settings.copy(defaultWeightUnit = WeightUnit.LB))
            SimEventType.CATEGORY_ADD -> SettingsStore.save(context, settings.copy(categories = settings.categories + Category(event.detail, 0xFF007AFF)))
            SimEventType.CATEGORY_RENAME -> {
                val (from, to) = event.detail.split("->")
                SettingsStore.save(context, settings.copy(categories = settings.categories.map { if (it.name == from) it.copy(name = to) else it }))
                val reloaded = SettingsStore.load(context).categories.map { it.name }
                if (from in reloaded) {
                    report(
                        "CATEGORY-RENAME-KEEPS-OLD", Severity.LOW,
                        "Renaming a default category keeps the old name too (defaults are always re-added)",
                        "after renaming $from -> $to the list is $reloaded", issue = "#296",
                    )
                }
            }
            SimEventType.CATEGORY_DELETE -> {
                SettingsStore.save(context, settings.copy(categories = settings.categories.filterNot { it.name == event.detail }))
                val orphaned = noteRepository.getAllNotes().first().count { it.categoryName == event.detail }
                if (orphaned > 0) {
                    report(
                        "CATEGORY-DELETE-ORPHANS", Severity.MEDIUM,
                        "Deleting a category leaves its workouts with a category the editor no longer knows (opening one falls back to Push)",
                        "$orphaned workouts still say '${event.detail}'", issue = "#296",
                    )
                }
            }
            SimEventType.BACKUP_RESTORE -> backupRestoreRoundTrip(event)
            SimEventType.EDIT_OLD_WORKOUT -> {
                val target = sessions.getOrNull(event.targetWorkout ?: -1) ?: return@runBlocking
                val note = database.noteDao().getById(target.workout.startMillis) ?: return@runBlocking
                val lines = note.text.split('\n').toMutableList()
                val i = lines.indexOfFirst { it.startsWith("      ") && it.contains('x') }
                if (i < 0) return@runBlocking
                lines[i] = lines[i].replace(Regex("""x (\d+(?:\.\d+)?)""")) { m ->
                    "x " + WorkoutHistoryGenerator.fmt(m.groupValues[1].toDouble() + 5.0)
                }
                // Editor on a non-active workout: Back saves a draft (NoteEditor -> saveNote(exit = true)).
                noteRepository.saveNote(note.copy(text = lines.joinToString("\n")).toDomainModel())
            }
            SimEventType.RELAUNCH -> relaunchStartupPath(event)
            SimEventType.RECREATE -> Unit // UI-level; the UI driver handles it
        }
    }

    /** What MainActivity runs on every launch, timed. */
    fun relaunchStartupPath(event: SimEvent) = runBlocking {
        val before = database.canonicalWorkoutDao().getLegacyBackedWorkoutIds().size
        val ms = measureTimeMillis {
            workoutRepository.checkAndMigrate()
            CanonicalImportRunner(database).run()
        }
        val downgraded = finishedTimestamps.mapNotNull { database.canonicalWorkoutDao().getByLegacyTimestamp(it) }
            .count { it.status != "COMPLETED" }
        if (downgraded > 0) {
            findings.report(
                "RELAUNCH-DOWNGRADES-COMPLETED", Severity.HIGH,
                "App start re-imports every workout and turns finished (COMPLETED) workouts back into PARTIAL",
                history, event.day, null, "$downgraded of ${finishedTimestamps.size} finished workouts are no longer COMPLETED",
            )
        }
        if (ms > PerfBudget.STARTUP_IMPORT_MS) {
            findings.report(
                "PERF-STARTUP-IMPORT", Severity.MEDIUM, "Startup canonical import exceeds ${PerfBudget.STARTUP_IMPORT_MS} ms",
                history, event.day, null, "$ms ms for $before workouts",
            )
        }
        findings.metric("day ${event.day}: startup import ${ms} ms over $before canonical workouts")
    }

    private suspend fun backupRestoreRoundTrip(event: SimEvent) {
        val settings = SettingsStore.load(context)
        val before = backupRepository.snapshot(settings)
        val file = File(context.cacheDir, "sim-backup-${history.seed}-${event.day}.gymtrack-backup")
        val uri = Uri.fromFile(file)
        var manifestRecords = 0
        val backupMs = measureTimeMillis {
            manifestRecords = backupRepository.createBackup(context.contentResolver, uri, settings, "sim", 12).manifest.counts.totalRecords
        }
        val restoreMs = measureTimeMillis { backupRepository.restoreBackup(context, context.contentResolver, uri) }
        val after = backupRepository.snapshot(SettingsStore.load(context))
        fun report(id: String, severity: Severity, title: String, example: String) =
            findings.report(id, severity, title, history, event.day, null, example)
        if (before.copy(settings = after.settings) != after) {
            report("BACKUP-ROUND-TRIP-LOSS", Severity.HIGH, "Backup → restore changes data", "notes ${before.legacyNotes.size}->${after.legacyNotes.size}, sets ${before.legacySets.size}->${after.legacySets.size}, canonical sets ${before.canonicalWorkoutSets.size}->${after.canonicalWorkoutSets.size}")
        }
        if (before.settings != after.settings) {
            report("BACKUP-SETTINGS-CHANGED", Severity.MEDIUM, "Settings differ after restoring a fresh backup", "${before.settings} -> ${after.settings}")
        }
        val counted = before.legacyNotes.size + before.legacySets.size + before.legacyExercises.size +
            before.canonicalCategories.size + before.canonicalExercises.size + before.canonicalExerciseAliases.size +
            before.canonicalWorkouts.size + before.canonicalWorkoutExercises.size + before.canonicalWorkoutSets.size
        if (manifestRecords != counted) {
            report("BACKUP-COUNT-MISMATCH", Severity.MEDIUM, "Backup manifest record count differs from the data", "manifest $manifestRecords, data $counted")
        }
        val active = NoteTimerStore.observe(context).first().activeNoteTimestamp
        if (active == null && sessions.lastOrNull()?.isFinished == false) {
            report("RESTORE-STOPS-ACTIVE-WORKOUT", Severity.LOW, "Restoring a backup stops the running workout's timer", "last workout ${sessions.last().workout.index} unfinished")
        }
        if (backupMs > PerfBudget.BACKUP_MS) report("PERF-BACKUP", Severity.MEDIUM, "Backup exceeds ${PerfBudget.BACKUP_MS} ms", "$backupMs ms")
        if (file.length() > PerfBudget.BACKUP_BYTES) report("PERF-BACKUP-SIZE", Severity.LOW, "Backup file larger than 5 MB", "${file.length()} bytes")
        findings.metric("day ${event.day}: backup ${backupMs} ms, restore ${restoreMs} ms, ${file.length() / 1024} KiB, $manifestRecords records")
        file.delete()
    }

    private fun measureBudgets(day: Int) = runBlocking {
        val notes = noteRepository.getAllNotes().first()
        val n = notes.size
        fun budget(id: String, title: String, ms: Long, limit: Long) {
            if (ms > limit) findings.report(id, Severity.MEDIUM, "$title exceeds $limit ms", history, day, null, "$ms ms with $n workouts")
        }
        val homeMs = measureTimeMillis {
            withViewModel(HomeViewModel(noteRepository, workoutRepository)) { vm -> withTimeout(30_000L) { vm.notes.first { it.size == n } } }
        }
        val latest = notes.maxOf { it.timestamp }
        val editorMs = measureTimeMillis {
            withViewModel(EditorViewModel(latest, noteRepository, workoutRepository)) { vm -> withTimeout(30_000L) { vm.uiState.first { it != null } } }
        }
        context.getSharedPreferences("stats_cache", Context.MODE_PRIVATE).edit().clear().commit()
        val statsFirstMs = measureTimeMillis {
            withStatsViewModel { vm -> withTimeout(60_000L) { vm.uiState.first { !it.isLoading && it.sourceNoteCount == n } } }
        }
        val statsCachedMs = measureTimeMillis {
            withStatsViewModel { vm -> withTimeout(60_000L) { vm.uiState.first { !it.isLoading && it.sourceNoteCount == n } } }
        }
        budget("PERF-HOME-LOAD", "Home grid data load", homeMs, PerfBudget.HOME_LOAD_MS)
        budget("PERF-EDITOR-OPEN", "Editor open (load newest workout)", editorMs, PerfBudget.EDITOR_OPEN_MS)
        budget("PERF-STATS-FIRST", "Stats first build", statsFirstMs, PerfBudget.STATS_FIRST_MS)
        budget("PERF-STATS-CACHED", "Stats cached build", statsCachedMs, PerfBudget.STATS_CACHED_MS)
        val line = "day $day (${history.dateOf(day)}), $n workouts: home ${homeMs} ms, editor ${editorMs} ms, " +
            "stats first ${statsFirstMs} ms, stats cached ${statsCachedMs} ms"
        findings.metric(line)
        Log.i(WorkoutRobot.TAG, line)
    }

    private suspend fun <T> withStatsViewModel(block: suspend (StatsViewModel) -> T): T =
        withViewModel(StatsViewModel(noteRepository, context), block)

    /** Owns a ViewModel in a throwaway store so its coroutines are cancelled afterwards. */
    private suspend fun <VM : ViewModel, T> withViewModel(viewModel: VM, block: suspend (VM) -> T): T {
        val store = ViewModelStore()
        val owned = ViewModelProvider(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <V : ViewModel> create(modelClass: Class<V>): V = viewModel as V
        })[viewModel.javaClass]
        return try {
            block(owned)
        } finally {
            store.clear()
        }
    }

    fun writeReport(name: String): File {
        val dir = File(context.getExternalFilesDir(null), "simulation").apply { mkdirs() }
        val file = File(dir, "$name.md")
        val text = buildString {
            appendLine("# $name")
            appendLine()
            appendLine("seed ${history.seed}, ${history.months} months, ${history.workouts.size} workouts, ${sessions.size} simulated")
            appendLine()
            append(findings.toMarkdown("Findings"))
        }
        file.writeText(text)
        text.lines().forEach { Log.i(WorkoutRobot.TAG, it) }
        return file
    }
}
