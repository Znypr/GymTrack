package com.example.gymtrack.core.data

import com.example.gymtrack.core.time.AppClock
import com.example.gymtrack.core.muscle.Muscle
import com.example.gymtrack.core.muscle.MuscleExerciseRef
import com.example.gymtrack.core.muscle.MuscleLevel
import com.example.gymtrack.core.muscle.MuscleMapping
import com.example.gymtrack.core.muscle.MuscleMappingSeed
import com.example.gymtrack.core.muscle.MuscleResolver
import com.example.gymtrack.core.muscle.MuscleRole
import com.example.gymtrack.core.muscle.MuscleSeedEntry
import com.example.gymtrack.core.muscle.MuscleWeekRow
import com.example.gymtrack.core.muscle.WeeklyMuscleSummary
import androidx.room.withTransaction
import com.example.gymtrack.core.data.canonical.toDomain
import com.example.gymtrack.core.data.transition.CanonicalExerciseCatalog
import com.example.gymtrack.core.data.transition.CanonicalImportSource
import com.example.gymtrack.core.data.transition.CanonicalKeys
import com.example.gymtrack.core.data.transition.LegacyWorkoutProjector
import com.example.gymtrack.core.util.ExerciseIdentity
import com.example.gymtrack.core.util.ExerciseIdentityResolver
import com.example.gymtrack.core.util.ParsedSetDTO
import com.example.gymtrack.core.util.WorkoutParser
import com.example.gymtrack.core.util.noteWorkoutDuration
import com.example.gymtrack.core.util.variantLabels
import com.example.gymtrack.domain.model.WorkoutDetails
import com.example.gymtrack.domain.model.WorkoutRecord
import com.example.gymtrack.domain.model.WorkoutStatus
import com.example.gymtrack.domain.summary.TrainingSummaryBuilder
import com.example.gymtrack.domain.summary.TrainingSummaryJson
import com.example.gymtrack.domain.summary.TrainingSummaryOutboxEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.time.ZoneId

class WorkoutRepository(
    private val database: NoteDatabase,
) {
    private val noteDao = database.noteDao()
    private val exerciseDao = database.exerciseDao()
    private val setDao = database.setDao()
    private val derivedDao = database.derivedIndexDao()
    private val parser = WorkoutParser()
    private val canonicalProjector = LegacyWorkoutProjector()
    private val summaryBuilder = TrainingSummaryBuilder()

    fun getAllExercises(): Flow<List<ExerciseEntity>> {
        return exerciseDao.getAllExercises()
    }

    fun getExercisesSortedByCount(minTimestamp: Long = 0): Flow<List<ExerciseWithCount>> {
        return if (minTimestamp == 0L) {
            exerciseDao.getExercisesSortedByCount()
        } else {
            exerciseDao.getExercisesWithCountAfter(minTimestamp)
        }
    }

    fun getExerciseGroupsSortedByCount(minTimestamp: Long = 0): Flow<List<ExerciseGroupWithCount>> {
        val rowsFlow = if (minTimestamp == 0L) {
            exerciseDao.getExerciseProgressOptions()
        } else {
            exerciseDao.getExerciseProgressOptionsAfter(minTimestamp)
        }.withLazyDerivedRebuild()

        return rowsFlow.map { rows ->
            rows
                .groupBy { row -> identityFor(row).baseComparisonKey }
                .values
                .map { group ->
                    val rowsWithIdentity = group.map { row -> row to identityFor(row) }
                    val primary = rowsWithIdentity.maxBy { it.first.setTotalCount }.second
                    val variants = rowsWithIdentity
                        .groupBy { it.second.progressComparisonKey }
                        .map { (key, variantRows) ->
                            val primaryVariant = variantRows.maxBy { it.first.setTotalCount }.second
                            val labels = primaryVariant.variantLabels()
                            ExerciseProgressVariant(
                                key = key,
                                label = variantDisplayLabel(labels),
                                labels = labels,
                                setTotalCount = variantRows.sumOf { it.first.setTotalCount },
                            )
                        }
                        .sortedWith(
                            compareByDescending<ExerciseProgressVariant> { it.setTotalCount }
                                .thenBy { it.label },
                        )

                    ExerciseGroupWithCount(
                        exerciseIds = group.map { it.exerciseId }.distinct(),
                        name = primary.canonicalName,
                        setTotalCount = group.sumOf { it.setTotalCount },
                        variants = variants,
                    )
                }
                .sortedWith(
                    compareByDescending<ExerciseGroupWithCount> { it.setTotalCount }
                        .thenBy { it.name },
                )
        }
    }

    fun getWeightHistory(exerciseId: Long): Flow<List<GraphPoint>> {
        return setDao.getAverageWeightHistory(exerciseId)
    }

    fun getWeightHistory(exerciseIds: List<Long>): Flow<List<GraphPoint>> {
        return if (exerciseIds.isEmpty()) {
            flowOf(emptyList())
        } else {
            setDao.getAverageWeightHistoryForExercises(exerciseIds)
        }
    }

    fun getWeightHistoryForExerciseGroup(group: ExerciseGroupWithCount): Flow<List<ExerciseProgressSeries>> {
        if (group.exerciseIds.isEmpty()) return flowOf(emptyList())
        val variantByKey = group.variants.associateBy { it.key }
        return setDao.getProgressHistoryRowsForExercises(group.exerciseIds).map { rows ->
            rows
                .groupBy { row -> identityFor(row).progressComparisonKey }
                .map { (key, variantRows) ->
                    val fallbackIdentity = identityFor(variantRows.first())
                    val variant = variantByKey[key]
                    ExerciseProgressSeries(
                        key = key,
                        label = variant?.label ?: variantDisplayLabel(fallbackIdentity.variantLabels()),
                        labels = variant?.labels ?: fallbackIdentity.variantLabels(),
                        points = variantRows
                            .groupBy { it.originTimestamp }
                            // A workout with no reps for this variant (only timed sets, #283) has
                            // no working weight: no point, never a 0 kg point.
                            .mapNotNull { (timestamp, timestampRows) ->
                                val totalVolume = timestampRows.sumOf { it.totalVolume.toDouble() }
                                val totalReps = timestampRows.sumOf { it.totalReps }
                                if (totalReps <= 0) return@mapNotNull null
                                GraphPoint(
                                    originTimestamp = timestamp,
                                    avgVal = (totalVolume / totalReps).toFloat(),
                                )
                            }
                            .sortedBy { it.originTimestamp },
                    )
                }
                .sortedWith(
                    compareByDescending<ExerciseProgressSeries> { variantByKey[it.key]?.setTotalCount ?: 0 }
                        .thenBy { it.label },
                )
        }
    }

    suspend fun cleanUpOrphans() {
        database.withTransaction {
            setDao.deleteOrphanedSets()
            derivedDao.deleteOrphanedRecent()
            derivedDao.deleteOrphanedMuscleWeek()
        }
    }

    suspend fun deleteWorkout(timestamp: Long) {
        database.withTransaction {
            setDao.deleteSetsForWorkout(timestamp)
            deleteDerivedRows(timestamp)
        }
    }

    suspend fun deleteWorkout(note: NoteEntity) {
        database.withTransaction {
            setDao.deleteSetsForWorkout(note.timestamp)
            deleteDerivedRows(note.timestamp)
            database.canonicalWorkoutDao()
                .getByLegacyTimestamp(note.timestamp)
                ?.let { workout ->
                    database.trainingSummaryOutboxDao().deleteForWorkout(workout.id)
                    database.canonicalWorkoutDao().deleteById(workout.id)
                }
            noteDao.delete(note)
        }
    }

    /**
     * Replaces the `sets` rows of [workoutId] with [sets], classified into warm-up / working with
     * rest (#306), and rewrites the workout's derived rows (#307), in one transaction. Set types
     * the user chose (source USER) survive the re-derive: they are matched by exercise and by the
     * set's position within that exercise.
     */
    suspend fun saveParsedSets(sets: List<ParsedSetDTO>, workoutId: Long) {
        val unclassified = sets.map { set ->
            val exerciseId = resolveExerciseId(set)
            SetEntity(
                workoutId = workoutId,
                exerciseId = exerciseId,
                weight = set.weight,
                reps = set.reps,
                isUnilateral = set.isUnilateral,
                modifier = set.modifier,
                brand = set.brand,
                relativeTime = set.relativeTime,
                absoluteTime = set.absoluteTime,
                weightUnit = WorkoutParser.normalizeWeightUnit(set.weightUnit),
            )
        }
        database.withTransaction {
            val userTypes = userOverridesByPosition(setDao.getForWorkout(workoutId))
            val classified = DerivedIndexWriter.classify(
                rows = unclassified,
                offsets = sets.map { it.rowOffsetSeconds },
                overrides = positionKeys(unclassified).map { userTypes[it] },
            )
            setDao.replaceSetsForWorkout(workoutId, classified)
            writeDerivedRows(workoutId, classified)
        }
    }

    /**
     * Sets or clears the user's set type for the [setOrdinal]-th saved set (0-based, logged order)
     * of the workout started at [workoutTimestamp] (#306). Null puts the set back on inference.
     * Rewrites the workout's derived rows in the same transaction. Returns false when there is no
     * such set (the workout is unfinished, or the ordinal is out of range).
     *
     * TODO(#306): the editor session wires this to a long-press on a set row ("Warm-up" /
     * "Working" / "Auto"); the ordinal is the row's index among the note's parsed sets.
     */
    suspend fun setSetTypeOverride(workoutTimestamp: Long, setOrdinal: Int, type: SetType?): Boolean =
        database.withTransaction {
            val rows = setDao.getForWorkout(workoutTimestamp)
            if (setOrdinal !in rows.indices) return@withTransaction false
            val overrides = rows.mapIndexed { index, row ->
                when {
                    index == setOrdinal -> type
                    SetTypeSource.fromStorage(row.setTypeSource) == SetTypeSource.USER -> SetType.fromStorage(row.setType)
                    else -> null
                }
            }
            // Rest does not depend on the type; keep what was stored.
            val reclassified = DerivedIndexWriter.classify(rows = rows, overrides = overrides)
                .zip(rows) { new, old -> new.copy(restSeconds = old.restSeconds) }
            updateChangedRows(rows, reclassified)
            writeDerivedRows(workoutTimestamp, reclassified)
            true
        }

    /**
     * The newest [perExercise] finished sessions (working sets only) of each exercise in
     * [exerciseIds], newest first (#307). For suggestions: load once per note, off the main
     * thread, and keep in memory; never on Enter.
     */
    suspend fun recentSessions(
        exerciseIds: Collection<Long>,
        perExercise: Int = RECENT_SESSIONS,
    ): Map<Long, List<ExerciseRecentEntity>> {
        if (exerciseIds.isEmpty()) return emptyMap()
        ensureDerivedIndexes()
        return derivedDao.recentForExercises(exerciseIds.distinct(), perExercise).groupBy { it.exerciseId }
    }

    /**
     * [recentSessions] by exercise name as typed in a note (resolved like the parser does), keyed
     * by the given name. Names without saved history are left out.
     */
    suspend fun recentSessionsForNames(
        names: Collection<String>,
        perExercise: Int = RECENT_SESSIONS,
    ): Map<String, List<ExerciseRecentEntity>> = withContext(Dispatchers.IO) {
        val idsByName = names.distinct().mapNotNull { name ->
            val canonical = ExerciseIdentityResolver.resolve(rawName = name).canonicalName.trim()
            val exercise = exerciseDao.getByName(canonical.ifBlank { name.trim() })
                ?: exerciseDao.getByName(name.trim())
            exercise?.let { name to it.exerciseId }
        }
        val sessions = recentSessions(idsByName.map { it.second }, perExercise)
        idsByName.mapNotNull { (name, id) -> sessions[id]?.let { name to it } }.toMap()
    }

    /**
     * Effective working sets per muscle key in the ISO week [weekKey] (`2026-W45`), most first
     * (#307). Keys are [Muscle.id]s plus [WeeklyMuscleSummary.UNMAPPED_KEY]; prefer
     * [weeklyMuscleSummary], which also gives levels and the exercises behind each muscle.
     */
    suspend fun muscleWeek(weekKey: String = DerivedIndexWriter.weekKey(AppClock.nowMillis())): List<MuscleWeekTotal> {
        ensureDerivedIndexes()
        return derivedDao.muscleWeek(weekKey)
    }

    /**
     * All 26 regions for the ISO week [weekKey] (#309): fractional working sets, level and the
     * exercises behind them, plus the trained exercises that have no mapping.
     */
    suspend fun weeklyMuscleSummary(weekKey: String = DerivedIndexWriter.weekKey(AppClock.nowMillis())): WeeklyMuscleSummary =
        withContext(Dispatchers.IO) {
            ensureDerivedIndexes()
            val rows = derivedDao.muscleWeekByExercise(weekKey).map {
                MuscleWeekRow(it.muscle, it.exerciseId, it.name, it.effectiveSets)
            }
            WeeklyMuscleSummary.from(weekKey, rows)
        }

    /** Level of each of the 26 regions in [weekKey]; [MuscleLevel.UNMAPPED] is never zero (#309). */
    suspend fun weeklyMuscleLevels(weekKey: String = DerivedIndexWriter.weekKey(AppClock.nowMillis())): Map<Muscle, MuscleLevel> =
        weeklyMuscleSummary(weekKey).levels

    /**
     * The muscles [exerciseIds] count for (#310): the first id's resolved mapping (they are one
     * exercise group on the Progress tab), whether it is the user's edit, and the seed behind it.
     */
    suspend fun exerciseMuscleMapping(exerciseIds: List<Long>): ExerciseMuscleMappingState = withContext(Dispatchers.IO) {
        val id = exerciseIds.firstOrNull() ?: return@withContext ExerciseMuscleMappingState(null, null, isUserEdit = false)
        val resolver = muscleResolver()
        val exercise = derivedDao.exerciseMuscleRefs().firstOrNull { it.exerciseId == id }
        ExerciseMuscleMappingState(
            mapping = resolver.resolve(id),
            seed = exercise?.let { MuscleMappingSeed.lookup(it.name) },
            isUserEdit = database.exerciseMuscleDao().getForExercises(listOf(id)).isNotEmpty(),
        )
    }

    /**
     * Stores the user's muscles for every id in [exerciseIds] (#310) and re-renders the muscle rows
     * of every workout with those exercises. Logged sets are never rewritten. [primary] must not
     * be empty; a muscle in both sets counts as primary.
     */
    suspend fun setExerciseMuscles(exerciseIds: List<Long>, primary: Set<Muscle>, secondary: Set<Muscle>) {
        require(primary.isNotEmpty()) { "An exercise needs at least one primary muscle" }
        val rows = exerciseIds.distinct().flatMap { id ->
            primary.map { ExerciseMuscleEntity(id, it.id, MuscleRole.PRIMARY.storageValue) } +
                (secondary - primary).map { ExerciseMuscleEntity(id, it.id, MuscleRole.SECONDARY.storageValue) }
        }
        replaceExerciseMuscles(exerciseIds, rows)
    }

    /** Deletes the user's muscles of [exerciseIds]: they use the seed again (#310). */
    suspend fun resetExerciseMusclesToSeed(exerciseIds: List<Long>) = replaceExerciseMuscles(exerciseIds, emptyList())

    private suspend fun replaceExerciseMuscles(exerciseIds: List<Long>, rows: List<ExerciseMuscleEntity>) {
        if (exerciseIds.isEmpty()) return
        withContext(Dispatchers.IO) {
            database.withTransaction {
                database.exerciseMuscleDao().replaceForExercises(exerciseIds.distinct(), rows)
                val resolver = muscleResolver()
                derivedDao.workoutIdsForExercises(exerciseIds.distinct()).forEach { workoutId ->
                    writeMuscleRows(workoutId, setDao.getForWorkout(workoutId), resolver)
                }
            }
        }
    }

    private suspend fun muscleResolver(): MuscleResolver = MuscleResolver(
        exercises = derivedDao.exerciseMuscleRefs().associate { it.exerciseId to MuscleExerciseRef(it.exerciseId, it.name, it.parentId) },
        overrides = database.exerciseMuscleDao().getAll().toOverrides(),
    )

    /**
     * Brings the derived rows and the inferred set types of saved workouts up to date when the
     * version stamp is missing or old (after the v14 migration, a restore, or a
     * [DerivedIndexWriter.VERSION] bump). Lazy: only readers call it, never startup (#126).
     * Chunked: [chunkSize] workouts per transaction, yielding between chunks; it resumes where it
     * stopped. Rows the user set (USER) keep their type; units are never touched.
     */
    suspend fun ensureDerivedIndexes(chunkSize: Int = REBUILD_CHUNK) {
        withContext(Dispatchers.IO) {
            rebuildMutex.withLock {
                var state = derivedDao.state(DerivedIndexWriter.INDEX_NAME)
                if (state != null && state.version == DerivedIndexWriter.VERSION && state.complete) {
                    return@withLock
                }
                if (state == null || state.version != DerivedIndexWriter.VERSION) {
                    val fresh = DerivedIndexStateEntity(
                        name = DerivedIndexWriter.INDEX_NAME,
                        version = DerivedIndexWriter.VERSION,
                        rebuiltThrough = Long.MIN_VALUE,
                        complete = false,
                    )
                    database.withTransaction {
                        derivedDao.deleteAllRecent()
                        derivedDao.deleteAllMuscleWeek()
                        derivedDao.putState(fresh)
                    }
                    state = fresh
                }
                var cursor: DerivedIndexStateEntity = state
                while (true) {
                    val workoutIds = derivedDao.workoutIdsWithSetsAfter(cursor.rebuiltThrough, chunkSize)
                    if (workoutIds.isEmpty()) {
                        derivedDao.putState(cursor.copy(complete = true))
                        break
                    }
                    database.withTransaction {
                        val resolver = muscleResolver()
                        workoutIds.forEach { rederiveStoredWorkout(it, resolver) }
                        cursor = cursor.copy(rebuiltThrough = workoutIds.last())
                        derivedDao.putState(cursor)
                    }
                    yield()
                }
            }
        }
    }

    /** Marks the derived rows stale (a restore replaced `sets`); the next reader rebuilds them. */
    suspend fun invalidateDerivedIndexes() {
        database.withTransaction {
            derivedDao.clearStates()
            derivedDao.deleteAllRecent()
            derivedDao.deleteAllMuscleWeek()
        }
    }

    /** Re-classifies one workout's stored rows in place (units untouched) and rewrites its derived rows. */
    private suspend fun rederiveStoredWorkout(workoutId: Long, resolver: MuscleResolver) {
        val rows = setDao.getForWorkout(workoutId)
        if (rows.isEmpty()) {
            deleteDerivedRows(workoutId)
            return
        }
        // Rest needs each row's own logging time, which only the note has. Rows line up with the
        // parsed sets when the counts match (the same rule Stats uses for units).
        val offsets = noteDao.getById(workoutId)?.let { note ->
            parser.parseWorkout(note.text, rowMetadata = note.rowMetadata)
                .takeIf { it.size == rows.size }
                ?.map { it.rowOffsetSeconds }
        }
        val overrides = rows.map { row ->
            if (SetTypeSource.fromStorage(row.setTypeSource) == SetTypeSource.USER) SetType.fromStorage(row.setType) else null
        }
        val classified = DerivedIndexWriter.classify(rows, offsets, overrides = overrides)
        updateChangedRows(rows, classified)
        writeDerivedRows(workoutId, classified, resolver)
    }

    private suspend fun updateChangedRows(old: List<SetEntity>, new: List<SetEntity>) {
        old.zip(new).forEach { (before, after) ->
            if (before.setType != after.setType ||
                before.setTypeSource != after.setTypeSource ||
                before.restSeconds != after.restSeconds
            ) {
                setDao.updateSetType(before.setId, after.setType, after.setTypeSource, after.restSeconds)
            }
        }
    }

    private suspend fun writeDerivedRows(workoutId: Long, rows: List<SetEntity>, resolver: MuscleResolver? = null) {
        derivedDao.deleteRecentForWorkout(workoutId)
        val recent = DerivedIndexWriter.recentRows(workoutId, rows)
        if (recent.isNotEmpty()) derivedDao.insertRecent(recent)
        writeMuscleRows(workoutId, rows, resolver ?: muscleResolver())
    }

    /** Rewrites one workout's `muscle_week` rows from its stored, classified [rows] (#309). */
    private suspend fun writeMuscleRows(workoutId: Long, rows: List<SetEntity>, resolver: MuscleResolver) {
        derivedDao.deleteMuscleWeekForWorkout(workoutId)
        val muscles = DerivedIndexWriter.muscleWeekRows(workoutId, rows, resolver::resolve)
        if (muscles.isNotEmpty()) derivedDao.insertMuscleWeek(muscles)
    }

    private suspend fun deleteDerivedRows(workoutId: Long) {
        derivedDao.deleteRecentForWorkout(workoutId)
        derivedDao.deleteMuscleWeekForWorkout(workoutId)
    }

    /** Lazily starts the derived rebuild when [this] is collected, without delaying its first value. */
    private fun <T> Flow<T>.withLazyDerivedRebuild(): Flow<T> {
        val source = this
        return channelFlow {
            launch { ensureDerivedIndexes() }
            source.collect { send(it) }
        }
    }

    /**
     * Persists the completed compatibility workout, canonical projection, derived statistics,
     * and local training-summary outbox record as one local persistence boundary. Draft autosave
     * intentionally does not call this method, so summary generation is tied only to explicit
     * workout completion.
     *
     * [finishedAtMillis] is the moment the user tapped Finish (default: now). The canonical
     * `ended_at` follows the duration rule in docs/WORKOUT_DURATION.md: it is the Finish time,
     * unless Finish (or a row) comes after a pause longer than [com.example.gymtrack.core.util.WORKOUT_SESSION_GAP_SECONDS].
     * Pass null when there is no real Finish moment (CSV import); `ended_at` then ends at the
     * last logged row.
     */
    suspend fun saveCompletedWorkout(
        note: NoteEntity,
        defaultWeightUnit: WeightUnit = WeightUnit.KG,
        finishedAtMillis: Long? = AppClock.nowMillis(),
    ) {
        val completedAt = maxOf(AppClock.nowMillis(), note.timestamp)
        val endedAt = completedWorkoutEndedAt(note, finishedAtMillis?.let { maxOf(it, note.timestamp) })
        database.withTransaction {
            noteDao.insert(note)
            replaceDerivedSets(note, defaultWeightUnit)

            val details = saveCanonicalAndBuildDetails(note, completedAt, defaultWeightUnit) { projected, _ ->
                projected.copy(
                    status = WorkoutStatus.COMPLETED.name,
                    endedAt = endedAt,
                    updatedAt = completedAt,
                )
            }
            val summary = summaryBuilder.build(details, ZoneId.systemDefault())
            database.trainingSummaryOutboxDao().upsert(
                TrainingSummaryOutboxEntry.pending(
                    summary = summary,
                    payloadJson = TrainingSummaryJson.encode(summary),
                    nowEpochMillis = completedAt,
                ).toEntity(),
            )
        }
    }

    /**
     * Saves an edit to a workout that is not the running one (a finished workout, or one left
     * with Back and replaced by a newer workout) and re-derives everything Stats reads from it:
     * the legacy `sets` rows and the canonical workout, exercises and sets, in one transaction.
     *
     * It never changes when the workout was completed: status, `ended_at` and `created_at` of an
     * existing canonical row are kept (a `COMPLETED` row stays `COMPLETED` with its Finish time).
     * It does not enqueue a training summary; that stays tied to the explicit Finish.
     *
     * Legacy `sets` rows are re-derived only for workouts Stats already counts: completed ones, or
     * old ones that already have rows (repaired databases from before Finish existed). A workout
     * that was never finished gets no rows here, so it stays "unfinished, not counted" (#322)
     * instead of silently turning into a finished workout because it was edited.
     */
    suspend fun saveEditedWorkout(
        note: NoteEntity,
        defaultWeightUnit: WeightUnit = WeightUnit.KG,
    ) {
        val editedAt = maxOf(AppClock.nowMillis(), note.timestamp)
        database.withTransaction {
            noteDao.insert(note)
            val countedByStats = isCompleted(note.timestamp) ||
                database.setDao().countForWorkout(note.timestamp) > 0
            if (countedByStats) replaceDerivedSets(note, defaultWeightUnit)
            saveCanonicalAndBuildDetails(note, editedAt, defaultWeightUnit) { projected, existing ->
                CanonicalImportSource.merge(existing, projected.copy(updatedAt = editedAt))
            }
        }
    }

    /**
     * Finish time per workout start timestamp, for completed workouts only. The Home card uses it
     * so its duration matches the timer at Finish instead of stopping at the last set (#323).
     */
    fun observeFinishTimes(): Flow<Map<Long, Long>> =
        database.canonicalWorkoutDao().observeCompletedEndTimes()
            .map { rows -> rows.associate { it.legacyTimestamp to it.endedAt } }

    /** True when the workout started at [timestamp] was finished (canonical status COMPLETED). */
    suspend fun isCompleted(timestamp: Long): Boolean =
        database.canonicalWorkoutDao().getByLegacyTimestamp(timestamp)?.status == WorkoutStatus.COMPLETED.name

    private suspend fun replaceDerivedSets(note: NoteEntity, defaultWeightUnit: WeightUnit) {
        val parsedSets = parser.parseWorkout(
            rawText = note.text,
            defaultWeightUnit = defaultWeightUnit.storageValue,
            rowMetadata = note.rowMetadata,
        )
        saveParsedSets(parsedSets, note.timestamp)
    }

    suspend fun syncNoteToWorkout(note: NoteEntity) {
        val parsedSets = parser.parseWorkout(
            rawText = note.text,
            rowMetadata = note.rowMetadata,
        )
        saveParsedSets(parsedSets, note.timestamp)
    }

    /**
     * Manual compatibility repair for legacy rows. Normal startup must not call this
     * unconditionally because it reparses every stored workout.
     */
    suspend fun forceUpdateStats() {
        val allNotes = noteDao.getAll().first()
        allNotes.forEach { note ->
            syncNoteToWorkout(note)
        }
    }

    /**
     * One-time compatibility repair for old databases where notes exist but derived
     * set rows were never created. This keeps normal startup cheap after the repair.
     */
    suspend fun checkAndMigrate() {
        val noteCount = noteDao.getCount()
        val setCount = setDao.getCount()
        if (noteCount > 0 && setCount == 0) {
            forceUpdateStats()
        }
    }

    private suspend fun saveCanonicalAndBuildDetails(
        note: NoteEntity,
        writtenAt: Long,
        // Shared by Finish and the history-edit save, so the projector unit (#326) reaches both.
        defaultWeightUnit: WeightUnit,
        finalizeRow: (projected: CanonicalWorkoutEntity, existing: CanonicalWorkoutEntity?) -> CanonicalWorkoutEntity,
    ): WorkoutDetails {
        val category = completedCategoryFor(note)
        category?.let { upsertCategory(it) }

        val catalog = CanonicalExerciseCatalog(exerciseDao.getAllForBackup())
        val projection = canonicalProjector.project(note, category, catalog, defaultWeightUnit)
        upsertExercises(catalog, writtenAt)

        val existing = database.canonicalWorkoutDao().getById(projection.workout.id)
        val workout = finalizeRow(projection.workout, existing)
        upsertWorkout(workout)

        database.canonicalWorkoutExerciseDao().deleteForWorkout(workout.id)
        if (projection.workoutExercises.isNotEmpty()) {
            database.canonicalWorkoutExerciseDao().insertAll(projection.workoutExercises)
        }
        if (projection.workoutSets.isNotEmpty()) {
            database.canonicalWorkoutSetDao().insertAll(projection.workoutSets)
        }

        return buildDetails(
            workout = workout,
            workoutExercises = projection.workoutExercises,
            workoutSets = projection.workoutSets,
            category = category,
        )
    }

    private suspend fun completedCategoryFor(note: NoteEntity): CanonicalCategoryEntity? {
        val name = note.categoryName?.trim().orEmpty()
        if (name.isEmpty()) return null
        val color = note.categoryColor ?: 0L
        val id = CanonicalKeys.category(name, color)
        return database.canonicalCategoryDao().getById(id) ?: CanonicalCategoryEntity(
            id = id,
            name = name,
            colorArgb = color,
            position = database.canonicalCategoryDao().getCount(),
            isBuiltIn = false,
            isArchived = false,
        )
    }

    private suspend fun upsertCategory(category: CanonicalCategoryEntity) {
        val inserted = database.canonicalCategoryDao().insert(category)
        if (inserted == -1L) database.canonicalCategoryDao().update(category)
    }

    private suspend fun upsertWorkout(workout: CanonicalWorkoutEntity) {
        val inserted = database.canonicalWorkoutDao().insert(workout)
        if (inserted == -1L) database.canonicalWorkoutDao().update(workout)
    }

    private suspend fun upsertExercises(
        catalog: CanonicalExerciseCatalog,
        updatedAt: Long,
    ) {
        val dao = database.canonicalExerciseDao()
        val exercises = catalog.exerciseEntities().map { incoming ->
            val existing = dao.getById(incoming.id)
            incoming.copy(
                createdAt = existing?.createdAt ?: updatedAt,
                updatedAt = updatedAt,
            )
        }

        exercises.forEach { entity -> upsertExercise(entity.copy(parentExerciseId = null)) }
        exercises.forEach { entity -> upsertExercise(entity) }
        catalog.aliasEntities().forEach { alias -> dao.insertAlias(alias) }
    }

    private suspend fun upsertExercise(entity: CanonicalExerciseEntity) {
        val inserted = database.canonicalExerciseDao().insert(entity)
        if (inserted == -1L) database.canonicalExerciseDao().update(entity)
    }

    private suspend fun buildDetails(
        workout: CanonicalWorkoutEntity,
        workoutExercises: List<CanonicalWorkoutExerciseEntity>,
        workoutSets: List<CanonicalWorkoutSetEntity>,
        category: CanonicalCategoryEntity?,
    ): WorkoutDetails {
        val exerciseIds = workoutExercises.map { it.exerciseId }.distinct()
        val exerciseEntities = if (exerciseIds.isEmpty()) {
            emptyList()
        } else {
            database.canonicalExerciseDao().getByIds(exerciseIds)
        }
        val aliases = if (exerciseIds.isEmpty()) {
            emptyList()
        } else {
            database.canonicalExerciseDao().getAliasesForExercises(exerciseIds)
        }

        return WorkoutDetails(
            record = WorkoutRecord(
                workout = workout.toDomain(),
                exercises = workoutExercises.map { it.toDomain() },
                sets = workoutSets.map { it.toDomain() },
            ),
            exerciseDefinitions = exerciseEntities.associate { entity ->
                entity.id to entity.toDomain(aliases)
            },
            category = category?.toDomain(),
        )
    }

    private suspend fun resolveExerciseId(set: ParsedSetDTO): Long {
        val identity = set.exerciseIdentity
        val normalizedName = identity.canonicalName.trim().ifBlank { set.exerciseName.trim() }
        if (normalizedName.isBlank()) return -1L

        val existing = exerciseDao.getByName(normalizedName)
        if (existing != null) return existing.exerciseId

        val aliasCandidates = (identity.aliases + set.exerciseName + identity.rawName)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinctBy { it.lowercase() }

        aliasCandidates.forEach { alias ->
            val aliasMatch = exerciseDao.findByAlias(alias).firstOrNull()
            if (aliasMatch != null) return aliasMatch.exerciseId
        }

        val newExercise = ExerciseEntity(
            name = normalizedName,
            aliases = aliasCandidates
                .filterNot { it.equals(normalizedName, ignoreCase = true) }
                .joinToString(","),
            muscleGroup = null,
        )
        return exerciseDao.insert(newExercise)
    }

    private fun identityFor(row: ExerciseProgressOptionRow): ExerciseIdentity = ExerciseIdentityResolver.resolve(
        rawName = row.name,
        parsedName = row.name,
        modifier = row.modifier,
        brand = row.brand,
        isUnilateral = row.isUnilateral,
    )

    private fun identityFor(row: ExerciseProgressHistoryRow): ExerciseIdentity = ExerciseIdentityResolver.resolve(
        rawName = row.exerciseName,
        parsedName = row.exerciseName,
        modifier = row.modifier,
        brand = row.brand,
        isUnilateral = row.isUnilateral,
    )

    private fun variantDisplayLabel(labels: List<String>): String = labels
        .filter(String::isNotBlank)
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" · ")
        ?: "Default"

    companion object {
        const val RECENT_SESSIONS = 5
        const val REBUILD_CHUNK = 50

        /** One rebuild at a time per process, whichever repository instance asks. */
        private val rebuildMutex = Mutex()

        /** (exerciseId, n-th set of that exercise in the workout) for each row, in order. */
        internal fun positionKeys(rows: List<SetEntity>): List<Pair<Long, Int>> {
            val seen = HashMap<Long, Int>()
            return rows.map { row ->
                val n = seen.getOrDefault(row.exerciseId, 0)
                seen[row.exerciseId] = n + 1
                row.exerciseId to n
            }
        }

        /** The user's set types of [rows] by [positionKeys]. */
        internal fun userOverridesByPosition(rows: List<SetEntity>): Map<Pair<Long, Int>, SetType> =
            positionKeys(rows).zip(rows)
                .filter { (_, row) -> SetTypeSource.fromStorage(row.setTypeSource) == SetTypeSource.USER }
                .associate { (key, row) -> key to SetType.fromStorage(row.setType) }

        /**
         * Canonical `ended_at` of a workout finished at [finishedAtMillis] (null: no real Finish),
         * per the duration rule in docs/WORKOUT_DURATION.md. Null when nothing gives a duration.
         */
        fun completedWorkoutEndedAt(note: NoteEntity, finishedAtMillis: Long?): Long? =
            noteWorkoutDuration(note.text, note.rowMetadata, note.timestamp, finishedAtMillis)
                ?.let { note.timestamp + it.seconds * 1_000L }
    }
}

/** What the "Edit muscles" sheet shows for one exercise (#310). */
data class ExerciseMuscleMappingState(
    /** The resolved mapping; null when the exercise is unmapped. */
    val mapping: MuscleMapping?,
    /** The built-in seed row for the exercise, if any (what "Reset" goes back to). */
    val seed: MuscleSeedEntry?,
    /** True when the user's own edit is stored. */
    val isUserEdit: Boolean,
)
