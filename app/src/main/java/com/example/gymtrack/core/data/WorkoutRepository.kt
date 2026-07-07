package com.example.gymtrack.core.data

import androidx.room.withTransaction
import com.example.gymtrack.core.data.canonical.toDomain
import com.example.gymtrack.core.data.transition.CanonicalExerciseCatalog
import com.example.gymtrack.core.data.transition.CanonicalKeys
import com.example.gymtrack.core.data.transition.CanonicalWorkoutProjection
import com.example.gymtrack.core.data.transition.LegacyWorkoutProjector
import com.example.gymtrack.core.util.ParsedSetDTO
import com.example.gymtrack.core.util.WorkoutParser
import com.example.gymtrack.domain.model.WorkoutDetails
import com.example.gymtrack.domain.model.WorkoutRecord
import com.example.gymtrack.domain.summary.TRAINING_SUMMARY_SCHEMA_VERSION
import com.example.gymtrack.domain.summary.TrainingSummaryBuilder
import com.example.gymtrack.domain.summary.TrainingSummaryJson
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class WorkoutRepository(
    private val database: NoteDatabase,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val summaryZoneId: ZoneId = ZoneId.systemDefault(),
) {
    private val noteDao = database.noteDao()
    private val exerciseDao = database.exerciseDao()
    private val setDao = database.setDao()
    private val parser = WorkoutParser()
    private val projector = LegacyWorkoutProjector(parser)
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

    fun getWeightHistory(exerciseId: Long): Flow<List<GraphPoint>> {
        return setDao.getAverageWeightHistory(exerciseId)
    }

    suspend fun cleanUpOrphans() {
        setDao.deleteOrphanedSets()
    }

    suspend fun deleteWorkout(timestamp: Long) {
        setDao.deleteSetsForWorkout(timestamp)
    }

    suspend fun saveParsedSets(sets: List<ParsedSetDTO>, workoutId: Long) {
        val setEntities = sets.map { set ->
            val exerciseId = resolveExerciseId(set.exerciseName)
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
     * Persists the completed compatibility workout, derived statistics rows,
     * canonical projection, and local TrainingSummary outbox entry as one Room
     * transaction. Draft autosave intentionally does not call this method.
     */
    suspend fun saveCompletedWorkout(
        note: NoteEntity,
        defaultWeightUnit: WeightUnit = WeightUnit.KG,
    ) {
        val completedAt = maxOf(note.timestamp, clockMillis())
        database.withTransaction {
            noteDao.insert(note)
            val parsedSets = parser.parseWorkout(note.text, defaultWeightUnit.storageValue)
            saveParsedSets(parsedSets, note.timestamp)
            saveCanonicalWorkoutAndSummary(note, defaultWeightUnit, completedAt)
        }
    }

    suspend fun syncNoteToWorkout(note: NoteEntity) {
        val parsedSets = parser.parseWorkout(note.text)
        saveParsedSets(parsedSets, note.timestamp)
    }

    suspend fun forceUpdateStats() {
        val allNotes = noteDao.getAll().first()
        allNotes.forEach { note ->
            syncNoteToWorkout(note)
        }
    }

    suspend fun checkAndMigrate() {
        val noteCount = noteDao.getCount()
        val setCount = setDao.getCount()
        if (noteCount > 0 && setCount == 0) {
            forceUpdateStats()
        }
    }

    private suspend fun saveCanonicalWorkoutAndSummary(
        note: NoteEntity,
        defaultWeightUnit: WeightUnit,
        completedAt: Long,
    ) {
        val exerciseCatalog = CanonicalExerciseCatalog(exerciseDao.getAllForBackup())
        val category = note.toCanonicalCategory()
        val projection = projector.project(
            note = note,
            category = category,
            exercises = exerciseCatalog,
            defaultWeightUnit = defaultWeightUnit,
        ).asCompleted(completedAt)

        category?.let(::upsertCategory)
        upsertExerciseCatalog(exerciseCatalog, completedAt)
        upsertWorkout(projection.workout)
        database.canonicalWorkoutExerciseDao().deleteForWorkout(projection.workout.id)
        if (projection.workoutExercises.isNotEmpty()) {
            database.canonicalWorkoutExerciseDao().insertAll(projection.workoutExercises)
        }
        if (projection.workoutSets.isNotEmpty()) {
            database.canonicalWorkoutSetDao().insertAll(projection.workoutSets)
        }

        val details = projection.toWorkoutDetails(
            exerciseDefinitions = exerciseCatalog.exerciseEntities(),
            category = category,
        )
        val summary = summaryBuilder.build(details, summaryZoneId)
        val existing = database.trainingSummaryOutboxDao().get(
            workoutId = summary.workoutId,
            schemaVersion = summary.schemaVersion,
        )
        database.trainingSummaryOutboxDao().upsert(
            TrainingSummaryOutboxEntity(
                workoutId = summary.workoutId,
                schemaVersion = summary.schemaVersion,
                summaryJson = TrainingSummaryJson.encode(summary),
                state = "PENDING",
                attemptCount = 0,
                lastError = null,
                createdAt = existing?.createdAt ?: completedAt,
                updatedAt = completedAt,
            ),
        )
    }

    private suspend fun upsertCategory(category: CanonicalCategoryEntity) {
        val inserted = database.canonicalCategoryDao().insert(category)
        if (inserted == -1L) database.canonicalCategoryDao().update(category)
    }

    private suspend fun upsertExerciseCatalog(
        catalog: CanonicalExerciseCatalog,
        completedAt: Long,
    ) {
        val dao = database.canonicalExerciseDao()
        val exercises = catalog.exerciseEntities()

        exercises.forEach { incoming ->
            val existing = dao.getById(incoming.id)
            val entity = incoming.copy(
                parentExerciseId = null,
                createdAt = existing?.createdAt ?: completedAt,
                updatedAt = completedAt,
            )
            val inserted = dao.insert(entity)
            if (inserted == -1L) dao.update(entity)
        }

        exercises.forEach { incoming ->
            val existing = dao.getById(incoming.id) ?: return@forEach
            val entity = existing.copy(
                parentExerciseId = incoming.parentExerciseId,
                muscleGroup = existing.muscleGroup ?: incoming.muscleGroup,
                updatedAt = completedAt,
            )
            if (entity != existing) dao.update(entity)
        }

        catalog.aliasEntities().forEach { alias -> dao.insertAlias(alias) }
    }

    private suspend fun upsertWorkout(workout: CanonicalWorkoutEntity) {
        val inserted = database.canonicalWorkoutDao().insert(workout)
        if (inserted == -1L) database.canonicalWorkoutDao().update(workout)
    }

    private fun CanonicalWorkoutProjection.asCompleted(completedAt: Long): CanonicalWorkoutProjection = copy(
        workout = workout.copy(
            status = "COMPLETED",
            endedAt = workout.endedAt ?: completedAt,
            updatedAt = completedAt,
        ),
    )

    private fun CanonicalWorkoutProjection.toWorkoutDetails(
        exerciseDefinitions: List<CanonicalExerciseEntity>,
        category: CanonicalCategoryEntity?,
    ): WorkoutDetails = WorkoutDetails(
        record = WorkoutRecord(
            workout = workout.toDomain(),
            exercises = workoutExercises.map { it.toDomain() },
            sets = workoutSets.map { it.toDomain() },
        ),
        exerciseDefinitions = exerciseDefinitions.associate { entity ->
            entity.id to entity.toDomain(emptyList())
        },
        category = category?.toDomain(),
    )

    private fun NoteEntity.toCanonicalCategory(): CanonicalCategoryEntity? {
        val name = categoryName?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val color = categoryColor ?: 0L
        return CanonicalCategoryEntity(
            id = CanonicalKeys.category(name, color),
            name = name,
            colorArgb = color,
            position = 0,
            isBuiltIn = false,
            isArchived = false,
        )
    }

    private suspend fun resolveExerciseId(rawName: String): Long {
        if (rawName.isBlank()) return -1L
        val normalizedName = rawName.trim()
        val existing = exerciseDao.getByName(normalizedName)
        if (existing != null) return existing.exerciseId
        val aliasMatch = exerciseDao.findByAlias(normalizedName).firstOrNull()
        if (aliasMatch != null) return aliasMatch.exerciseId
        val newExercise = ExerciseEntity(
            name = normalizedName,
            aliases = "",
            muscleGroup = null,
        )
        return exerciseDao.insert(newExercise)
    }
}