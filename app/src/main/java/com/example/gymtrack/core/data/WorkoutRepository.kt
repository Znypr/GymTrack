package com.example.gymtrack.core.data

import com.example.gymtrack.core.time.AppClock
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.ZoneId

class WorkoutRepository(
    private val database: NoteDatabase,
) {
    private val noteDao = database.noteDao()
    private val exerciseDao = database.exerciseDao()
    private val setDao = database.setDao()
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
        }

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
        setDao.deleteOrphanedSets()
    }

    suspend fun deleteWorkout(timestamp: Long) {
        setDao.deleteSetsForWorkout(timestamp)
    }

    suspend fun deleteWorkout(note: NoteEntity) {
        database.withTransaction {
            setDao.deleteSetsForWorkout(note.timestamp)
            database.canonicalWorkoutDao()
                .getByLegacyTimestamp(note.timestamp)
                ?.let { workout ->
                    database.trainingSummaryOutboxDao().deleteForWorkout(workout.id)
                    database.canonicalWorkoutDao().deleteById(workout.id)
                }
            noteDao.delete(note)
        }
    }

    suspend fun saveParsedSets(sets: List<ParsedSetDTO>, workoutId: Long) {
        val setEntities = sets.map { set ->
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
        setDao.replaceSetsForWorkout(workoutId, setEntities)
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
        /**
         * Canonical `ended_at` of a workout finished at [finishedAtMillis] (null: no real Finish),
         * per the duration rule in docs/WORKOUT_DURATION.md. Null when nothing gives a duration.
         */
        fun completedWorkoutEndedAt(note: NoteEntity, finishedAtMillis: Long?): Long? =
            noteWorkoutDuration(note.text, note.rowMetadata, note.timestamp, finishedAtMillis)
                ?.let { note.timestamp + it.seconds * 1_000L }
    }
}
