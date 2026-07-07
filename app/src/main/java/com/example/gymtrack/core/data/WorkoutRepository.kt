package com.example.gymtrack.core.data

import androidx.room.withTransaction
import com.example.gymtrack.core.util.ParsedSetDTO
import com.example.gymtrack.core.util.WorkoutParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class WorkoutRepository(
    private val database: NoteDatabase,
) {
    private val noteDao = database.noteDao()
    private val exerciseDao = database.exerciseDao()
    private val setDao = database.setDao()
    private val parser = WorkoutParser()

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
            )
        }
        setDao.replaceSetsForWorkout(workoutId, setEntities)
    }

    /**
     * Persists the completed compatibility workout and its derived statistics
     * rows as one Room transaction. Draft autosave intentionally does not call
     * this method.
     *
     * The compatibility `sets` table does not yet have a unit column. The parser
     * still receives the selected default so the derived DTOs already model the
     * correct unit boundary before typed set persistence is introduced.
     */
    suspend fun saveCompletedWorkout(
        note: NoteEntity,
        defaultWeightUnit: WeightUnit = WeightUnit.KG,
    ) {
        database.withTransaction {
            noteDao.insert(note)
            val parsedSets = parser.parseWorkout(note.text, defaultWeightUnit.storageValue)
            saveParsedSets(parsedSets, note.timestamp)
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