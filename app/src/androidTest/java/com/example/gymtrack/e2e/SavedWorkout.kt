package com.example.gymtrack.e2e

import android.content.Context
import com.example.gymtrack.core.data.CanonicalExerciseEntity
import com.example.gymtrack.core.data.CanonicalWorkoutEntity
import com.example.gymtrack.core.data.CanonicalWorkoutExerciseEntity
import com.example.gymtrack.core.data.CanonicalWorkoutSetEntity
import com.example.gymtrack.core.data.NoteDatabase
import com.example.gymtrack.core.data.NoteEntity
import com.example.gymtrack.core.data.SetEntity
import com.example.gymtrack.core.timer.NoteTimerState
import com.example.gymtrack.core.timer.NoteTimerStore
import com.example.gymtrack.core.util.parseNoteRowMetadata
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Everything the app persisted for one workout, read straight from Room and DataStore. */
data class SavedWorkout(
    val note: NoteEntity?,
    val legacySets: List<SetEntity>,
    val workout: CanonicalWorkoutEntity?,
    val exercises: List<CanonicalWorkoutExerciseEntity>,
    val sets: List<CanonicalWorkoutSetEntity>,
    val exerciseDefinitions: Map<String, CanonicalExerciseEntity>,
    val timer: NoteTimerState,
) {
    val noteLines: List<String> get() = note?.text?.split('\n').orEmpty()

    val elapsedColumn: List<String> get() = parseNoteRowMetadata(note?.rowMetadata).map { it.absoluteTime }

    fun exerciseName(exercise: CanonicalWorkoutExerciseEntity): String =
        exerciseDefinitions[exercise.exerciseId]?.canonicalName.orEmpty()

    fun setsOf(exercise: CanonicalWorkoutExerciseEntity): List<CanonicalWorkoutSetEntity> =
        sets.filter { it.workoutExerciseId == exercise.id }.sortedBy { it.position }

    companion object {
        fun read(context: Context, timestamp: Long): SavedWorkout = runBlocking {
            val db = NoteDatabase.getDatabase(context)
            val workout = db.canonicalWorkoutDao().getByLegacyTimestamp(timestamp)
            val exercises = workout?.let { db.canonicalWorkoutExerciseDao().getForWorkout(it.id) }.orEmpty()
            val sets = workout?.let { db.canonicalWorkoutSetDao().getForWorkout(it.id) }.orEmpty()
            val definitions = exercises.map { it.exerciseId }.distinct()
                .takeIf { it.isNotEmpty() }
                ?.let { db.canonicalExerciseDao().getByIds(it) }
                .orEmpty()
                .associateBy { it.id }
            SavedWorkout(
                note = db.noteDao().getById(timestamp),
                legacySets = db.setDao().getAllForBackup().filter { it.workoutId == timestamp },
                workout = workout,
                exercises = exercises,
                sets = sets,
                exerciseDefinitions = definitions,
                timer = NoteTimerStore.observe(context).first(),
            )
        }

        fun noteCount(context: Context): Int = runBlocking {
            NoteDatabase.getDatabase(context).noteDao().getAllForBackup().size
        }

        /** Polls (off the main thread) until [condition] holds for the saved state. */
        fun awaitSaved(
            context: Context,
            timestamp: Long,
            timeoutMs: Long = 5_000L,
            condition: (SavedWorkout) -> Boolean,
        ): SavedWorkout {
            val deadline = System.nanoTime() + timeoutMs * 1_000_000L
            var latest = read(context, timestamp)
            while (!condition(latest) && System.nanoTime() < deadline) {
                Thread.sleep(50L)
                latest = read(context, timestamp)
            }
            return latest
        }
    }
}
