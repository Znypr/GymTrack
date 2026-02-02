package com.example.gymtrack.data

import android.util.Log
import com.example.gymtrack.util.ParsedSetDTO
import com.example.gymtrack.util.WorkoutParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class WorkoutRepository(
    private val noteDao: NoteDao,
    private val exerciseDao: ExerciseDao,
    private val setDao: SetDao
) {
    private val parser = WorkoutParser()

    // --- READ FUNCTIONS FOR UI ---
    fun getAllExercises(): Flow<List<ExerciseEntity>> {
        return exerciseDao.getAllExercises()
    }

    fun getExercisesSortedByCount(): Flow<List<ExerciseWithCount>> {
        // You will need to inject/access the ExerciseDao here.
        // Assuming your repository constructor provides access to exerciseDao:
        return exerciseDao.getExercisesSortedByCount()
    }

    fun getWeightHistory(exerciseId: Long): Flow<List<GraphPoint>> {
        return setDao.getAverageWeightHistory(exerciseId)
    }

    // --- NEW WRITE FUNCTION FOR CSV/STATEFUL IMPORTS ---

    /**
     * Converts a list of DTOs (from a CSV or other structured source) into
     * SetEntities and resolves exercise IDs before inserting into the database.
     */
    suspend fun saveParsedSets(sets: List<ParsedSetDTO>, workoutId: Long) {

        val setEntities = sets.map { set ->
            val exerciseId = resolveExerciseId(set.exerciseName)
            SetEntity(
                // setId must remain auto-generated (0L) for new inserts
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

    // --- WRITE / MIGRATION LOGIC (Main UI Entry Point) ---

    // This function is fine for UI text input, as the parser handles the carry-over internally.
    suspend fun syncNoteToWorkout(note: NoteEntity) {
        val parsedSets = parser.parseWorkout(note.text)
        saveParsedSets(parsedSets, note.timestamp) // Use the new helper
    }

    suspend fun checkAndMigrate() {
        val noteCount = noteDao.getCount()
        val setCount = setDao.getCount()

        if (noteCount > 0 && setCount == 0) {
            Log.d("GymTrack", "Migrating legacy notes...")
            migrateAllLegacyNotes()
        }
    }

    suspend fun migrateAllLegacyNotes() {
        val allNotes = noteDao.getAll().first()
        var count = 0
        allNotes.forEach { note ->
            syncNoteToWorkout(note)
            count++
        }
        Log.d("GymTrack", "Migration Complete. Processed $count notes.")
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