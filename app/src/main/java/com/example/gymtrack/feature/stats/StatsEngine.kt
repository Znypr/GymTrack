package com.example.gymtrack.feature.stats

import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.data.WeightUnit
import com.example.gymtrack.core.util.WorkoutParser
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** Where a finished [StatsState] is kept between app runs. The Android one is [StatsPersistentCacheStore]. */
interface StatsStateStore {
    fun read(storageKey: String): StatsState?

    /** Stores [state]; may drop other entries of the same [slotOf] slot, which are stale. */
    fun write(storageKey: String, state: StatsState)

    companion object {
        const val SLOT_SEPARATOR = '|'

        /** The part of a key that names what is shown (range + unit), before the content hash. */
        fun slotOf(storageKey: String): String = storageKey.substringBefore(SLOT_SEPARATOR)
    }
}

enum class StatsLoadSource { MEMORY, PERSISTED, BUILT }

data class StatsLoadResult(val state: StatsState, val source: StatsLoadSource)

/**
 * Loads Stats for a history (#303): memory cache, then the persisted cache, then a build. A
 * build parses each note at most once ([StatsDigestCache]) and spreads parsing over cores.
 * Platform-neutral: no Android types; the caller picks the dispatcher.
 *
 * One engine is shared per process ([shared]), so a new Stats ViewModel in the same process
 * still hits memory even before the persisted write has landed.
 */
class StatsEngine(
    private val store: StatsStateStore? = null,
    val digests: StatsDigestCache = StatsDigestCache(),
    private val maxMemoryEntries: Int = DEFAULT_MEMORY_ENTRIES,
    private val parallelism: Int = Runtime.getRuntime().availableProcessors().coerceIn(1, MAX_PARALLELISM),
) {
    private val memory = object : LinkedHashMap<String, StatsState>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, StatsState>?): Boolean =
            size > maxMemoryEntries
    }

    /** Builds done by this engine; tests use it to prove a cache hit. */
    @Volatile
    var buildCount: Int = 0
        private set

    /**
     * Cache key: every input that changes the result. All parts are stable across processes
     * (String hashes and numbers only; see [StatsWorkoutFacts.signature]).
     */
    fun cacheKey(
        notes: List<NoteLine>,
        facts: StatsWorkoutFacts,
        range: TimeRange,
        displayUnit: WeightUnit,
        nowMillis: Long,
    ): String = buildString {
        append(range.name)
        append('_')
        append(displayUnit.name)
        append(StatsStateStore.SLOT_SEPARATOR)
        append(notesSignature(notes))
        append('_')
        append(facts.signature())
        append('_')
        append(if (range == TimeRange.ALL_TIME) "all" else nowMillis / TimeUnit.DAYS.toMillis(1))
    }

    /**
     * Loads Stats. [onBuilding] runs before a build (to show a progress message). [publish]
     * receives the state as soon as it is known; a fresh build is then persisted even if the
     * caller is cancelled right after publishing, so the next load finds it.
     */
    suspend fun load(
        notes: List<NoteLine>,
        facts: StatsWorkoutFacts,
        range: TimeRange,
        displayUnit: WeightUnit,
        nowMillis: Long,
        onBuilding: () -> Unit = {},
        publish: (StatsState) -> Unit = {},
    ): StatsLoadResult {
        val key = cacheKey(notes, facts, range, displayUnit, nowMillis)
        synchronized(memory) { memory[key] }?.let { cached ->
            publish(cached)
            return StatsLoadResult(cached, StatsLoadSource.MEMORY)
        }

        store?.read(key)?.let { persisted ->
            // Notes are not persisted (they are the input); re-attach the counted ones.
            val state = persisted.copy(filteredNotes = countedNotes(notes, facts, range, nowMillis))
            remember(key, state)
            publish(state)
            return StatsLoadResult(state, StatsLoadSource.PERSISTED)
        }

        onBuilding()
        val state = build(notes, facts, range, displayUnit, nowMillis)
        remember(key, state)
        publish(state)
        if (store != null) {
            withContext(NonCancellable) { store.write(key, state) }
        }
        return StatsLoadResult(state, StatsLoadSource.BUILT)
    }

    /** A full build, parsing only notes not already digested, in parallel chunks. */
    suspend fun build(
        notes: List<NoteLine>,
        facts: StatsWorkoutFacts,
        range: TimeRange,
        displayUnit: WeightUnit,
        nowMillis: Long,
    ): StatsState {
        val counted = countedNotes(notes, facts, range, nowMillis)
        if (parallelism > 1 && counted.size >= MIN_NOTES_FOR_PARALLEL) {
            val chunkSize = (counted.size + parallelism - 1) / parallelism
            coroutineScope {
                counted.chunked(chunkSize)
                    .map { chunk -> async(Dispatchers.Default) { digests.digestAll(chunk, facts.storedSetUnits) } }
                    .awaitAll()
            }
        }
        buildCount++
        return buildStatsState(notes, range, facts, displayUnit, nowMillis, WorkoutParser(), digests)
    }

    private fun remember(key: String, state: StatsState) {
        synchronized(memory) { memory[key] = state }
    }

    companion object {
        const val DEFAULT_MEMORY_ENTRIES = 8
        private const val MAX_PARALLELISM = 4
        private const val MIN_NOTES_FOR_PARALLEL = 32

        @Volatile
        private var sharedEngine: StatsEngine? = null

        /** The process-wide engine; [store] is used when the engine is first made. */
        fun shared(store: StatsStateStore?): StatsEngine =
            sharedEngine ?: synchronized(this) {
                sharedEngine ?: StatsEngine(store).also { sharedEngine = it }
            }

        /** Drops the process-wide engine (memory cache and digests); for cold-build measurements. */
        fun resetShared() {
            synchronized(this) { sharedEngine = null }
        }

        /** The workouts Stats counts in [range] (finished only, #322). */
        fun countedNotes(
            notes: List<NoteLine>,
            facts: StatsWorkoutFacts,
            range: TimeRange,
            nowMillis: Long,
        ): List<NoteLine> {
            val cutoff = if (range.days > 0) nowMillis - TimeUnit.DAYS.toMillis(range.days.toLong()) else 0L
            return notes.filter { it.timestamp >= cutoff && facts.isFinished(it.timestamp) }
        }

        fun notesSignature(notes: List<NoteLine>): Long {
            var result = notes.size.toLong()
            for (note in notes) {
                result = 31 * result + note.timestamp
                result = 31 * result + note.text.hashCode()
                result = 31 * result + note.rowMetadata.hashCode()
                result = 31 * result + (note.categoryName?.hashCode() ?: 0)
                result = 31 * result + (note.categoryColor ?: 0L)
            }
            return result
        }
    }
}
