package com.example.gymtrack.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.gymtrack.core.data.canonical.RoomCanonicalWorkoutRepository
import com.example.gymtrack.domain.model.Category
import com.example.gymtrack.domain.model.LegacyMigrationStatus
import com.example.gymtrack.domain.model.LegacyWorkoutCompatibility
import com.example.gymtrack.domain.model.Workout
import com.example.gymtrack.domain.model.WorkoutDetails
import com.example.gymtrack.domain.model.WorkoutRecord
import com.example.gymtrack.domain.model.WorkoutStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanonicalPredictionHistoryRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun cleanUp() = context.deleteMigrationTestDatabase()

    @Test
    fun loadsCompletedAndMigratedLegacyWorkoutsForPredictionHistory() {
        context.createLegacyDatabase(8, VERSION_8_SCHEMA, emptyList())

        context.openMigratedDatabase().use { database ->
            val repository = RoomCanonicalWorkoutRepository(database)

            runBlocking {
                repository.save(workoutDetails("ignored-draft", "Draft", 1, WorkoutStatus.DRAFT))
                repository.save(
                    workoutDetails(
                        id = "pending-partial",
                        label = "Pending",
                        day = 2,
                        status = WorkoutStatus.PARTIAL,
                        legacyMigrationStatus = LegacyMigrationStatus.PENDING,
                    ),
                )
                repository.save(
                    workoutDetails(
                        id = "migrated-partial",
                        label = "Push",
                        day = 3,
                        status = WorkoutStatus.PARTIAL,
                        legacyMigrationStatus = LegacyMigrationStatus.MIGRATED,
                    ),
                )
                repository.save(
                    workoutDetails(
                        id = "review-partial",
                        label = "Pull",
                        day = 4,
                        status = WorkoutStatus.PARTIAL,
                        legacyMigrationStatus = LegacyMigrationStatus.NEEDS_REVIEW,
                    ),
                )
                repository.save(workoutDetails("completed", "Legs", 5, WorkoutStatus.COMPLETED))

                val history = repository.getRecentPredictionHistory(limit = 10)

                assertEquals(
                    listOf("Legs", "Pull", "Push"),
                    history.mapNotNull { it.category?.name },
                )
            }
        }
    }

    private fun workoutDetails(
        id: String,
        label: String,
        day: Int,
        status: WorkoutStatus,
        legacyMigrationStatus: LegacyMigrationStatus? = null,
    ): WorkoutDetails {
        val start = day * MILLIS_PER_DAY
        val category = Category(
            id = "category-$label",
            name = label,
            colorArgb = 0L,
            position = day,
            isBuiltIn = false,
        )
        return WorkoutDetails(
            record = WorkoutRecord(
                workout = Workout(
                    id = id,
                    startedAtEpochMillis = start,
                    categoryId = category.id,
                    title = label,
                    status = status,
                    createdAtEpochMillis = start,
                    updatedAtEpochMillis = start,
                    legacyCompatibility = legacyMigrationStatus?.let { migrationStatus ->
                        LegacyWorkoutCompatibility(
                            legacyTimestamp = start,
                            rawDraftText = label,
                            migrationStatus = migrationStatus,
                        )
                    },
                ),
                exercises = emptyList(),
                sets = emptyList(),
            ),
            exerciseDefinitions = emptyMap(),
            category = category,
        )
    }

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
    }
}
