package com.example.gymtrack.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.gymtrack.core.data.transition.CanonicalImportRunner
import com.example.gymtrack.domain.model.WorkoutStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Bug #299 on real Room: completion followed by two startup imports keeps the row untouched. */
@RunWith(AndroidJUnit4::class)
class CanonicalImportCompletionTest {
    @Test
    fun completedWorkoutSurvivesTwoStartupImports() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, NoteDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val note = NoteEntity(
                timestamp = 1_800_000_000_000L,
                title = "Push",
                text = "Bench Press\n    5x 100\n    8x 90 kg",
                categoryName = "Push",
                categoryColor = 55L,
                learnings = null,
            )
            WorkoutRepository(database).saveCompletedWorkout(note, WeightUnit.KG)
            val completed = database.canonicalWorkoutDao().getByLegacyTimestamp(note.timestamp)!!
            assertEquals(WorkoutStatus.COMPLETED.name, completed.status)

            repeat(2) {
                val report = CanonicalImportRunner(database).run()
                assertEquals(1, report.skipped)
                assertEquals(0, report.migrated)
                assertEquals(0, report.restamped)
            }

            assertEquals(completed, database.canonicalWorkoutDao().getByLegacyTimestamp(note.timestamp))
            assertEquals(2, database.canonicalWorkoutSetDao().getCount())
        } finally {
            database.close()
        }
    }
}
