package com.example.gymtrack.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.gymtrack.core.time.AppClock
import com.example.gymtrack.core.time.Clock
import com.example.gymtrack.core.time.SystemWallClock
import com.example.gymtrack.core.util.buildNoteRowMetadata
import com.example.gymtrack.domain.model.WorkoutStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** #325 and #323 on real Room: editing a finished workout refreshes Stats data and keeps Finish. */
@RunWith(AndroidJUnit4::class)
class WorkoutRepositoryEditedWorkoutTest {
    private lateinit var database: NoteDatabase
    private var now = START + 60L * 60_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, NoteDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        AppClock.current = Clock { now }
    }

    @After
    fun tearDown() {
        AppClock.current = SystemWallClock
        database.close()
    }

    @Test
    fun finish_storesFinishTime_andEditLaterRefreshesSetsWithoutMovingIt() = runBlocking<Unit> {
        val repository = WorkoutRepository(database)
        val finishedAt = START + 58L * 60_000L
        repository.saveCompletedWorkout(note(weight = "80"), WeightUnit.KG, finishedAtMillis = finishedAt)

        val completed = database.canonicalWorkoutDao().getByLegacyTimestamp(START)!!
        assertEquals(WorkoutStatus.COMPLETED.name, completed.status)
        assertEquals(finishedAt, completed.endedAt)
        assertEquals(mapOf(START to finishedAt), repository.observeFinishTimes().first())

        // Weeks later the user corrects the weight and leaves with Back.
        now += 14L * 24L * 3_600_000L
        repository.saveEditedWorkout(note(weight = "85"), WeightUnit.KG)

        val sets = database.setDao().getAllForBackup()
        assertEquals(listOf(85f, 85f), sets.filter { it.workoutId == START }.map { it.weight })
        val edited = database.canonicalWorkoutDao().getByLegacyTimestamp(START)!!
        assertEquals(WorkoutStatus.COMPLETED.name, edited.status)
        assertEquals(finishedAt, edited.endedAt)
        assertEquals(completed.createdAt, edited.createdAt)
        assertEquals(now, edited.updatedAt)
        assertEquals(1, database.trainingSummaryOutboxDao().getPending().size)
    }

    @Test
    fun editOfAnUnfinishedPastWorkout_writesItsSetsAndStaysPartial() = runBlocking<Unit> {
        val repository = WorkoutRepository(database)
        database.noteDao().insert(note(weight = "80"))

        repository.saveEditedWorkout(note(weight = "82.5"), WeightUnit.KG)

        assertEquals(listOf(82.5f, 82.5f), database.setDao().getAllForBackup().map { it.weight })
        assertEquals(WorkoutStatus.PARTIAL.name, database.canonicalWorkoutDao().getByLegacyTimestamp(START)!!.status)
    }

    private fun note(weight: String) = NoteEntity(
        timestamp = START,
        title = "Push",
        text = "Bench Press\n      8x $weight\n      8x $weight",
        categoryName = "Push",
        categoryColor = 55L,
        learnings = null,
        rowMetadata = buildNoteRowMetadata(
            times = listOf("0'00''", "20'00''", "51'10''"),
            flags = List(3) { ExerciseFlag.BILATERAL },
        ),
    )

    private companion object {
        const val START = 1_800_000_000_000L
    }
}
