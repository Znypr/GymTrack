package com.example.gymtrack.feature.home

import com.example.gymtrack.core.data.NoteLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingWorkoutDeletionTest {

    private val push = NoteLine(title = "Push", text = "Bench 80x5", timestamp = 1_000L)
    private val pull = NoteLine(title = "Pull", text = "Row 60x8", timestamp = 2_000L)
    private val legs = NoteLine(title = "Legs", text = "Squat 100x5", timestamp = 3_000L)

    private class FakeStore(scope: CoroutineScope, var failWith: Exception? = null) {
        val deleted = mutableListOf<Set<NoteLine>>()
        val errors = mutableListOf<Throwable>()
        val deletion = PendingWorkoutDeletion(
            scope = scope,
            delete = { notes ->
                failWith?.let { throw it }
                deleted += notes
            },
            onDeleteError = { errors += it },
        )
    }

    @Test
    fun `undo restores the workouts without deleting anything`() = runBlocking {
        val store = FakeStore(this)

        store.deletion.request(setOf(push, pull))
        assertEquals(setOf(1_000L, 2_000L), store.deletion.hiddenTimestamps.first())
        assertEquals(2, store.deletion.pending.value?.count)

        assertTrue(store.deletion.undo())
        repeat(5) { yield() }

        assertEquals(null, store.deletion.pending.value)
        assertEquals(emptySet<Long>(), store.deletion.hiddenTimestamps.first())
        assertTrue("undo must not touch the database", store.deleted.isEmpty())
    }

    @Test
    fun `nothing is deleted until the delete is committed`() = runBlocking {
        val store = FakeStore(this)

        store.deletion.request(setOf(push))
        repeat(5) { yield() }
        assertTrue(store.deleted.isEmpty())

        store.deletion.commit().join()

        assertEquals(listOf(setOf(push)), store.deleted)
        assertEquals(null, store.deletion.pending.value)
        assertEquals(emptySet<Long>(), store.deletion.hiddenTimestamps.first())
        assertFalse("nothing left to undo after commit", store.deletion.undo())
    }

    @Test
    fun `a second delete commits the first and keeps only the second undoable`() = runBlocking {
        val store = FakeStore(this)

        store.deletion.request(setOf(push))
        store.deletion.request(setOf(legs))
        // The second request committed push; this commits legs after it (deletes run in order).
        store.deletion.commit().join()
        assertEquals(listOf(setOf(push), setOf(legs)), store.deleted)
    }

    @Test
    fun `undo after a second delete restores only the second`() = runBlocking {
        val store = FakeStore(this)

        store.deletion.request(setOf(push))
        store.deletion.request(setOf(legs))
        val firstId = 1L
        assertTrue(store.deletion.pending.value!!.id > firstId)
        assertTrue(store.deletion.undo())
        store.deletion.commit().join()

        assertEquals(listOf(setOf(push)), store.deleted)
        assertEquals(emptySet<Long>(), store.deletion.hiddenTimestamps.first())
    }

    @Test
    fun `failed delete shows the workouts again and reports the error`() = runBlocking {
        val store = FakeStore(this, failWith = IllegalStateException("db locked"))

        store.deletion.request(setOf(pull))
        store.deletion.commit().join()

        assertEquals(emptySet<Long>(), store.deletion.hiddenTimestamps.first())
        assertEquals(1, store.errors.size)
    }

    @Test
    fun `empty request is ignored`() = runBlocking {
        val store = FakeStore(this)
        store.deletion.request(emptySet())
        assertEquals(null, store.deletion.pending.value)
        store.deletion.commit().join()
        assertTrue(store.deleted.isEmpty())
    }

    @Test
    fun `deleting every workout needs confirmation`() {
        val all = listOf(push, pull, legs)
        assertTrue(deleteNeedsConfirmation(all.toSet(), all))
        assertTrue(deleteNeedsConfirmation(setOf(push), listOf(push)))
        assertFalse(deleteNeedsConfirmation(setOf(push, pull), all))
        assertFalse(deleteNeedsConfirmation(emptySet(), all))
        assertFalse(deleteNeedsConfirmation(emptySet(), emptyList()))
    }

    @Test
    fun `snackbar message names the count`() {
        assertEquals("Workout deleted", deletedWorkoutsMessage(1))
        assertEquals("3 workouts deleted", deletedWorkoutsMessage(3))
    }
}
