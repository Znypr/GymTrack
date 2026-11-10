package com.example.gymtrack.core.data.transition

import com.example.gymtrack.core.data.CanonicalCategoryEntity
import com.example.gymtrack.core.data.CanonicalWorkoutEntity
import com.example.gymtrack.core.data.CanonicalWorkoutExerciseEntity
import com.example.gymtrack.core.data.CanonicalWorkoutSetEntity
import com.example.gymtrack.core.data.ExerciseEntity
import com.example.gymtrack.core.data.ExerciseFlag
import com.example.gymtrack.core.data.NoteEntity
import com.example.gymtrack.core.util.combineTextAndTimes
import com.example.gymtrack.domain.model.WorkoutStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bug #299: the startup import must not downgrade completed workouts or rewrite unchanged ones. */
class CanonicalImportRunnerIdempotenceTest {
    private val projector = LegacyWorkoutProjector()

    @Test
    fun completedWorkout_importTwice_staysCompletedWithZeroWrites() = runBlocking<Unit> {
        val store = FakeImportStore(notes = listOf(NOTE))
        val completed = store.completeLikeRepository(NOTE, COMPLETED_AT)

        repeat(2) { run ->
            val report = CanonicalImportRunner(store, projector).run()
            assertEquals("launch ${run + 1}", 0, store.writes)
            assertEquals(1, report.skipped)
            assertEquals(0, report.migrated)
            assertEquals(0, report.restamped)
        }

        val workout = store.workout(NOTE.timestamp)
        assertEquals(WorkoutStatus.COMPLETED.name, workout.status)
        assertEquals(COMPLETED_AT, workout.updatedAt)
        assertEquals(completed, workout)
    }

    @Test
    fun preStampCompletedRow_isRestampedOnceWithoutDowngrade() = runBlocking<Unit> {
        val store = FakeImportStore(notes = listOf(NOTE))
        val completed = store.completeLikeRepository(NOTE, COMPLETED_AT)
        // A row written before source stamps existed carries only the version.
        store.seedWorkout(completed.copy(legacyMigrationMessage = CANONICAL_IMPORT_VERSION))

        val first = CanonicalImportRunner(store, projector).run()
        assertEquals(1, first.restamped)
        assertEquals(0, first.migrated)
        assertEquals(0, store.childReplaces)
        val restamped = store.workout(NOTE.timestamp)
        assertEquals(WorkoutStatus.COMPLETED.name, restamped.status)
        assertEquals(COMPLETED_AT, restamped.updatedAt)
        assertEquals(CanonicalImportSource.hash(NOTE), CanonicalImportSource.storedHash(restamped.legacyMigrationMessage))

        store.writes = 0
        val second = CanonicalImportRunner(store, projector).run()
        assertEquals(0, store.writes)
        assertEquals(1, second.skipped)
    }

    @Test
    fun editedCompletedWorkout_rewritesContentButKeepsCompletedAndUpdatedAt() = runBlocking<Unit> {
        val store = FakeImportStore(notes = listOf(NOTE))
        store.completeLikeRepository(NOTE, COMPLETED_AT)
        val edited = NOTE.copy(
            text = combineTextAndTimes(
                text = "Bench\n      8x 80kg\n      6x 85kg",
                times = listOf("0'00''", "0'30''", "2'30''"),
                flags = List(3) { ExerciseFlag.BILATERAL },
            ),
        )
        store.notes = listOf(edited)

        val first = CanonicalImportRunner(store, projector).run()
        assertEquals(1, first.migrated)
        val workout = store.workout(NOTE.timestamp)
        assertEquals(WorkoutStatus.COMPLETED.name, workout.status)
        assertEquals(COMPLETED_AT, workout.updatedAt)
        assertEquals(edited.text, workout.rawDraftText)
        assertEquals(2, store.setsFor(workout.id).size)

        store.writes = 0
        CanonicalImportRunner(store, projector).run()
        assertEquals(0, store.writes)
    }

    @Test
    fun legacyNoteWithoutCompletion_importsAsPartialOnceThenSkips() = runBlocking<Unit> {
        val store = FakeImportStore(notes = listOf(NOTE))

        val first = CanonicalImportRunner(store, projector).run()
        assertEquals(1, first.migrated)
        assertEquals(WorkoutStatus.PARTIAL.name, store.workout(NOTE.timestamp).status)

        store.writes = 0
        val second = CanonicalImportRunner(store, projector).run()
        assertEquals(0, store.writes)
        assertEquals(1, second.skipped)
        assertEquals(0, second.migrated)
    }

    @Test
    fun completionRacingTheImport_isNotOverwritten() = runBlocking<Unit> {
        val store = FakeImportStore(notes = listOf(NOTE))
        // The snapshot sees no row; the completion path writes before the import transaction runs.
        store.beforeTransaction = { store.completeLikeRepository(NOTE, COMPLETED_AT) }

        val report = CanonicalImportRunner(store, projector).run()

        assertEquals(0, report.migrated)
        assertEquals(1, report.skipped)
        val workout = store.workout(NOTE.timestamp)
        assertEquals(WorkoutStatus.COMPLETED.name, workout.status)
        assertEquals(COMPLETED_AT, workout.updatedAt)
    }

    @Test
    fun sourceHash_tracksEveryProjectedNoteField() {
        val base = CanonicalImportSource.hash(NOTE)
        assertEquals(base, CanonicalImportSource.hash(NOTE.copy()))
        listOf(
            NOTE.copy(timestamp = NOTE.timestamp + 1),
            NOTE.copy(title = "Pull"),
            NOTE.copy(text = NOTE.text + "\n"),
            NOTE.copy(rowMetadata = "x"),
            NOTE.copy(learnings = null),
            NOTE.copy(categoryName = "Legs"),
            NOTE.copy(categoryColor = 56L),
        ).forEach { changed -> assertNotEquals(changed.toString(), base, CanonicalImportSource.hash(changed)) }
        // Category identity is normalized, like CanonicalKeys.category.
        assertEquals(base, CanonicalImportSource.hash(NOTE.copy(categoryName = "  push ")))
    }

    @Test
    fun storedHash_readsStampWithAndWithoutReviewReasons() {
        val hash = CanonicalImportSource.hash(NOTE)
        val stamp = CanonicalImportSource.stamp(NOTE)
        assertEquals(hash, CanonicalImportSource.storedHash(stamp))
        assertEquals(hash, CanonicalImportSource.storedHash("$stamp: exercise 1 has no reliable name"))
        assertNull(CanonicalImportSource.storedHash(CANONICAL_IMPORT_VERSION))
        assertNull(CanonicalImportSource.storedHash("$CANONICAL_IMPORT_VERSION: projection failed (X)"))
        assertNull(CanonicalImportSource.storedHash("canonical-import-v0 src=$hash"))
        assertNull(CanonicalImportSource.storedHash(null))
    }

    @Test
    fun merge_neverLowersStatusOrMovesUpdatedAtBack() {
        val projected = projectedWorkout().copy(status = WorkoutStatus.PARTIAL.name)
        val completed = projected.copy(status = WorkoutStatus.COMPLETED.name, updatedAt = COMPLETED_AT)
        val mergedCompleted = CanonicalImportSource.merge(completed, projected)
        assertEquals(WorkoutStatus.COMPLETED.name, mergedCompleted.status)
        assertEquals(COMPLETED_AT, mergedCompleted.updatedAt)

        val draft = projected.copy(status = WorkoutStatus.DRAFT.name)
        assertEquals(WorkoutStatus.PARTIAL.name, CanonicalImportSource.merge(draft, projected).status)

        val unknown = projected.copy(status = "ARCHIVED")
        assertEquals("ARCHIVED", CanonicalImportSource.merge(unknown, projected).status)

        assertEquals(projected, CanonicalImportSource.merge(null, projected))
        assertTrue(CanonicalImportSource.merge(completed, projected).createdAt <= projected.createdAt)
    }

    private fun projectedWorkout(): CanonicalWorkoutEntity =
        projector.project(NOTE, CATEGORY, CanonicalExerciseCatalog(LEGACY_EXERCISES)).workout

    /** In-memory store that counts every canonical write the runner makes. */
    private inner class FakeImportStore(var notes: List<NoteEntity>) : CanonicalImportStore {
        private val workouts = linkedMapOf<String, CanonicalWorkoutEntity>()
        private val exercises = linkedMapOf<String, List<CanonicalWorkoutExerciseEntity>>()
        private val sets = linkedMapOf<String, List<CanonicalWorkoutSetEntity>>()
        var writes = 0
        var childReplaces = 0
        var beforeTransaction: (suspend () -> Unit)? = null

        /** Mirrors WorkoutRepository.saveCanonicalCompletionAndBuildDetails, then resets counters. */
        suspend fun completeLikeRepository(note: NoteEntity, completedAt: Long): CanonicalWorkoutEntity {
            val projection = projector.project(note, CATEGORY, CanonicalExerciseCatalog(LEGACY_EXERCISES))
            val workout = projection.workout.copy(status = WorkoutStatus.COMPLETED.name, updatedAt = completedAt)
            replaceWorkout(workout, projection.workoutExercises, projection.workoutSets)
            writes = 0
            childReplaces = 0
            return workout
        }

        fun seedWorkout(workout: CanonicalWorkoutEntity) {
            workouts[workout.id] = workout
        }

        fun workout(legacyTimestamp: Long): CanonicalWorkoutEntity =
            workouts.values.single { it.legacyTimestamp == legacyTimestamp }

        fun setsFor(workoutId: String): List<CanonicalWorkoutSetEntity> = sets[workoutId].orEmpty()

        override suspend fun notes(): List<NoteEntity> = notes
        override suspend fun legacyExercises(): List<ExerciseEntity> = LEGACY_EXERCISES
        override suspend fun legacyBackedWorkouts(): List<CanonicalWorkoutEntity> =
            workouts.values.filter { it.legacyTimestamp != null }

        override suspend fun workoutByLegacyTimestamp(legacyTimestamp: Long): CanonicalWorkoutEntity? =
            workouts.values.firstOrNull { it.legacyTimestamp == legacyTimestamp }

        override suspend fun workoutExercises(workoutId: String): List<CanonicalWorkoutExerciseEntity> =
            exercises[workoutId].orEmpty().sortedWith(compareBy({ it.position }, { it.id }))

        override suspend fun workoutSets(workoutExerciseIds: List<String>): List<CanonicalWorkoutSetEntity> =
            sets.values.flatten()
                .filter { it.workoutExerciseId in workoutExerciseIds }
                .sortedWith(compareBy({ it.workoutExerciseId }, { it.position }))

        override suspend fun <T> inTransaction(block: suspend () -> T): T {
            beforeTransaction?.invoke()
            beforeTransaction = null
            return block()
        }

        override suspend fun upsertCategory(category: CanonicalCategoryEntity) {
            writes++
        }

        override suspend fun upsertExercises(catalog: CanonicalExerciseCatalog) {
            writes++
        }

        override suspend fun updateWorkout(workout: CanonicalWorkoutEntity) {
            writes++
            workouts[workout.id] = workout
        }

        override suspend fun replaceWorkout(
            workout: CanonicalWorkoutEntity,
            workoutExercises: List<CanonicalWorkoutExerciseEntity>,
            workoutSets: List<CanonicalWorkoutSetEntity>,
        ) {
            writes++
            childReplaces++
            workouts[workout.id] = workout
            exercises[workout.id] = workoutExercises
            sets[workout.id] = workoutSets
        }
    }

    private companion object {
        const val COMPLETED_AT = 1_005_400_000L

        val NOTE = NoteEntity(
            timestamp = 1_000_000_000L,
            title = "Push",
            text = combineTextAndTimes(
                text = "Bench\n      8x 80kg",
                times = listOf("0'00''", "0'30''"),
                flags = listOf(ExerciseFlag.UNILATERAL, ExerciseFlag.UNILATERAL),
            ),
            categoryName = "Push",
            categoryColor = 55L,
            learnings = "Controlled reps",
        )

        val CATEGORY = CanonicalCategoryEntity(
            id = CanonicalKeys.category("Push", 55L),
            name = "Push",
            colorArgb = 55L,
            position = 0,
            isBuiltIn = false,
            isArchived = false,
        )

        val LEGACY_EXERCISES = listOf(
            ExerciseEntity(
                exerciseId = 1L,
                name = "Bench press",
                parentId = null,
                muscleGroup = "Chest",
                aliases = "Bench",
            ),
        )
    }
}
