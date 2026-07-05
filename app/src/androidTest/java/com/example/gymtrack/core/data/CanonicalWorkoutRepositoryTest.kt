package com.example.gymtrack.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.gymtrack.core.data.canonical.RoomCanonicalWorkoutRepository
import com.example.gymtrack.domain.model.Category
import com.example.gymtrack.domain.model.Exercise
import com.example.gymtrack.domain.model.ExerciseMode
import com.example.gymtrack.domain.model.WeightUnit
import com.example.gymtrack.domain.model.Workout
import com.example.gymtrack.domain.model.WorkoutDetails
import com.example.gymtrack.domain.model.WorkoutExercise
import com.example.gymtrack.domain.model.WorkoutRecord
import com.example.gymtrack.domain.model.WorkoutSet
import com.example.gymtrack.domain.model.WorkoutStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanonicalWorkoutRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun cleanUp() = context.deleteMigrationTestDatabase()

    @Test
    fun savesAndLoadsCompleteAggregateInDeterministicOrder() {
        context.createLegacyDatabase(8, VERSION_8_SCHEMA, emptyList())

        context.openMigratedDatabase().use { database ->
            val repository = RoomCanonicalWorkoutRepository(database)
            val category = Category(
                id = "category-pull",
                name = "Pull",
                colorArgb = 123L,
                position = 0,
                isBuiltIn = true,
            )
            val row = Exercise(
                id = "exercise-row",
                canonicalName = "Row",
                muscleGroup = "Back",
                aliases = setOf("Cable Row"),
            )
            val pullup = Exercise(
                id = "exercise-pullup",
                canonicalName = "Pullup",
                muscleGroup = "Back",
            )
            val workout = Workout(
                id = "workout-1",
                startedAtEpochMillis = 1_000L,
                endedAtEpochMillis = 2_000L,
                categoryId = category.id,
                title = "Pull",
                status = WorkoutStatus.COMPLETED,
                createdAtEpochMillis = 1_000L,
                updatedAtEpochMillis = 2_000L,
            )
            val firstOccurrence = WorkoutExercise(
                id = "occurrence-row",
                workoutId = workout.id,
                exerciseId = row.id,
                position = 0,
                mode = ExerciseMode.BILATERAL,
            )
            val secondOccurrence = WorkoutExercise(
                id = "occurrence-pullup",
                workoutId = workout.id,
                exerciseId = pullup.id,
                position = 1,
                mode = ExerciseMode.UNILATERAL,
            )
            val rowSet = WorkoutSet(
                id = "set-row",
                workoutExerciseId = firstOccurrence.id,
                position = 0,
                repetitions = 10,
                weight = 70.0,
                weightUnit = WeightUnit.UNKNOWN,
            )
            val pullupSet = WorkoutSet(
                id = "set-pullup",
                workoutExerciseId = secondOccurrence.id,
                position = 0,
                repetitions = 6,
            )
            val details = WorkoutDetails(
                record = WorkoutRecord(
                    workout = workout,
                    exercises = listOf(secondOccurrence, firstOccurrence),
                    sets = listOf(pullupSet, rowSet),
                ),
                exerciseDefinitions = mapOf(row.id to row, pullup.id to pullup),
                category = category,
            )

            runBlocking {
                repository.save(details)
                val loaded = repository.getById(workout.id)

                assertNotNull(loaded)
                assertEquals(listOf(0, 1), loaded!!.record.exercises.map { it.position })
                assertEquals(listOf("set-row", "set-pullup"), loaded.record.sets.map { it.id })
                assertEquals(WeightUnit.UNKNOWN, loaded.record.sets.first().weightUnit)
                assertEquals(setOf("Cable Row"), loaded.exerciseDefinitions.getValue(row.id).aliases)
                assertEquals(category, loaded.category)

                val replacement = loaded.copy(
                    record = loaded.record.copy(
                        sets = listOf(
                            loaded.record.sets.first().copy(
                                weight = 72.5,
                                weightUnit = WeightUnit.KILOGRAM,
                            ),
                        ),
                    ),
                )
                repository.save(replacement)
                val replaced = repository.getById(workout.id)

                assertEquals(1, replaced!!.record.sets.size)
                assertEquals(72.5, replaced.record.sets.single().weight ?: 0.0, 0.0)
                assertEquals(WeightUnit.KILOGRAM, replaced.record.sets.single().weightUnit)
                assertEquals(1, database.canonicalWorkoutSetDao().getCount())
            }
        }
    }
}
