package com.example.gymtrack.core.data

import com.example.gymtrack.core.util.ParsedSetDTO
import com.example.gymtrack.core.util.WorkoutParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class WorkoutRepository(
    private val noteDao: NoteDao,
    private val exerciseDao: ExerciseDao,
    private val setDao: SetDao
) {
    private val parser = WorkoutParser()

    fun getAllExercises(): Flow<List<ExerciseEntity>> {
        return exerciseDao.getAllExercises()
    }

    // Support both All-Time (0) and Time-Range filtering
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
                absoluteTime = set.absoluteTime
            )
        }
        setDao.replaceSetsForWorkout(workoutId, setEntities)
    }

    suspend fun syncNoteToWorkout(note: NoteEntity) {
        val parsedSets = parser.parseWorkout(note.text)
        saveParsedSets(parsedSets, note.timestamp)
    }

    // [FIX] New function to forcefully rebuild stats from Notes
    suspend fun forceUpdateStats() {
        val allNotes = noteDao.getAll().first()
        allNotes.forEach { note ->
            syncNoteToWorkout(note)
        }
    }

    // Keeps old check for first-run migration
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
        val newExercise = ExerciseEntity(name = normalizedName, aliases = "", muscleGroup = null)
        return exerciseDao.insert(newExercise)
    }
}