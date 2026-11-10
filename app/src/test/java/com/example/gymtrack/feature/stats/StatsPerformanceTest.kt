package com.example.gymtrack.feature.stats

import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.data.WeightUnit
import com.example.gymtrack.simulation.EditorTranscript
import com.example.gymtrack.simulation.HistoryInvariants
import com.example.gymtrack.simulation.WorkoutHistoryGenerator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * #303: Stats on a seeded 1,000-workout history from the simulation generator. Budgets are
 * generous (JVM on a shared dev machine); the device budgets are 2 s first / 300 ms cached.
 */
class StatsPerformanceTest {

    private class MapStore : StatsStateStore {
        val entries = HashMap<String, StatsState>()
        override fun read(storageKey: String): StatsState? = entries[storageKey]
        override fun write(storageKey: String, state: StatsState) {
            entries.keys.removeAll { StatsStateStore.slotOf(it) == StatsStateStore.slotOf(storageKey) }
            entries[storageKey] = state.copy(filteredNotes = emptyList())
        }
    }

    @Test
    fun thousandWorkoutsBuildWithinBudgetAndUnchangedHistoryHitsTheCache() = runBlocking {
        val store = MapStore()
        val engine = StatsEngine(store)

        val first = timed { engine.load(notes, facts, TimeRange.ALL_TIME, WeightUnit.KG, now) }
        assertEquals(StatsLoadSource.BUILT, first.value.source)
        assertTrue("first build ${first.ms} ms", first.ms < FIRST_BUILD_BUDGET_MS)
        assertEquals(counted, first.value.state.totalNotes)
        // Each counted note parsed exactly once in the whole build.
        assertEquals(counted, engine.digests.parseCount)

        val second = timed { engine.load(notes, facts, TimeRange.ALL_TIME, WeightUnit.KG, now) }
        assertEquals(StatsLoadSource.MEMORY, second.value.source)
        assertEquals(1, engine.buildCount)
        assertTrue("cached load ${second.ms} ms", second.ms < CACHED_BUDGET_MS)

        // A new engine (a new ViewModel / process) with the same history reads the persisted entry.
        val fresh = StatsEngine(store)
        val persisted = timed {
            fresh.load(notes.map { it.copy() }, facts.copy(), TimeRange.ALL_TIME, WeightUnit.KG, now)
        }
        assertEquals(StatsLoadSource.PERSISTED, persisted.value.source)
        assertEquals(0, fresh.buildCount)
        assertEquals(first.value.state.filteredNotes, persisted.value.state.filteredNotes)
        assertTrue("persisted load ${persisted.ms} ms", persisted.ms < CACHED_BUDGET_MS)

        // Another range re-uses the digests: no note is parsed again.
        val month = timed { engine.load(notes, facts, TimeRange.LAST_YEAR, WeightUnit.KG, now) }
        assertEquals(StatsLoadSource.BUILT, month.value.source)
        assertEquals(counted, engine.digests.parseCount)

        // A new workout changes the key: no stale hit.
        val added = notes + notes.last().copy(timestamp = now - 1, text = notes.last().text + "\n")
        val addedFacts = facts.copy(completedTimestamps = facts.completedTimestamps + (now - 1))
        assertEquals(StatsLoadSource.BUILT, engine.load(added, addedFacts, TimeRange.ALL_TIME, WeightUnit.KG, now).source)
        assertEquals(1, store.entries.keys.count { StatsStateStore.slotOf(it) == "ALL_TIME_KG" })

        println(
            "STATS-BENCH ${notes.size} workouts ($counted counted): first build ${first.ms} ms, " +
                "memory hit ${second.ms} ms, persisted hit ${persisted.ms} ms, other range (digests reused) ${month.ms} ms",
        )
    }

    @Test
    fun factsSignatureDoesNotDependOnEnumIdentityHashes() {
        val facts = StatsWorkoutFacts(
            completedTimestamps = setOf(2L, 1L),
            storedSetUnits = mapOf(5L to listOf(WeightUnit.LB, WeightUnit.KG)),
        )
        // The value is fixed by timestamps and unit ordinals only, so it is the same in every process.
        var expected = 2L
        expected = 31 * expected + 1L
        expected = 31 * expected + 2L
        expected = 31 * expected + 5L
        expected = 31 * expected + 2
        expected = 31 * expected + (WeightUnit.LB.ordinal + 1)
        expected = 31 * expected + (WeightUnit.KG.ordinal + 1)
        assertEquals(expected, facts.signature())
    }

    @Test
    fun aLoadParsesEachNoteAtMostOnce() {
        var parses = 0
        val counting = StatsNoteDigester { note, units, parser ->
            parses++
            DefaultStatsNoteDigester.digest(note, units, parser)
        }
        val digests = StatsDigestCache(digester = counting)
        val sample = notes.take(200)
        buildStatsState(sample, TimeRange.ALL_TIME, facts, WeightUnit.KG, now, digests = digests)
        val countedInSample = sample.count { facts.isFinished(it.timestamp) }
        assertEquals(countedInSample, parses)
        buildStatsState(sample, TimeRange.LAST_3_MONTHS, facts, WeightUnit.LB, now, digests = digests)
        assertEquals(countedInSample, parses)
    }

    private class Timed<T>(val value: T, val ms: Long)

    private inline fun <T> timed(block: () -> T): Timed<T> {
        val start = System.nanoTime()
        val value = block()
        return Timed(value, (System.nanoTime() - start) / 1_000_000)
    }

    companion object {
        private const val FIRST_BUILD_BUDGET_MS = 10_000L
        private const val CACHED_BUDGET_MS = 300L

        private lateinit var notes: List<NoteLine>
        private lateinit var facts: StatsWorkoutFacts
        private var now = 0L
        private var counted = 0

        @BeforeClass
        @JvmStatic
        fun generateHistory() {
            val history = WorkoutHistoryGenerator(seed = 11L, months = 72).generate()
            val sessions = history.workouts.take(1_000).map { EditorTranscript.build(it) }
            check(sessions.size == 1_000) { "generator made only ${sessions.size} workouts" }
            notes = sessions.map { s ->
                val n = s.finalNote
                NoteLine(n.title, n.text, n.timestamp, n.categoryName, n.categoryColor, n.learnings.orEmpty(), n.rowMetadata.orEmpty())
            }
            facts = HistoryInvariants.factsFromSessions(sessions)
            now = notes.maxOf { it.timestamp } + 1
            counted = notes.count { facts.isFinished(it.timestamp) }
        }
    }
}
