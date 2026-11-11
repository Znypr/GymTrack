package com.example.gymtrack.feature.suggest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.floor
import kotlin.random.Random

class SuggestionEngineTest {

    private fun kg(vararg sets: Pair<Int, Double>) = sets.map { (reps, load) -> PastSet(reps, load, LoadUnit.KG) }
    private fun session(id: Long, vararg sets: Pair<Int, Double>) = PastSession(id, kg(*sets))
    private fun today(vararg sets: Pair<Int, Double>) = sets.map { (reps, load) -> TodaySet(reps, load, LoadUnit.KG) }

    // --- matching set and double progression -------------------------------------------------

    @Test
    fun `no history gives no suggestion`() {
        assertNull(SuggestionEngine.nextSet(emptyList(), emptyList()))
    }

    @Test
    fun `repeats last session's matching set when not every set reached the top`() {
        val history = listOf(session(2, 8 to 80.0, 8 to 80.0, 6 to 80.0))
        val first = SuggestionEngine.nextSet(history, emptyList())!!
        assertEquals(SetSuggestionKind.REPEAT, first.kind)
        assertEquals(8, first.reps)
        assertEquals(80.0, first.load, 0.0)

        val third = SuggestionEngine.nextSet(history, today(8 to 80.0, 8 to 80.0))!!
        assertEquals(6, third.reps)
        assertEquals(3, third.setNumber)
    }

    @Test
    fun `adds one smallest step when every set reached the top of the range`() {
        val history = listOf(
            session(3, 8 to 80.0, 8 to 80.0, 8 to 80.0),
            session(2, 8 to 80.0, 7 to 80.0, 6 to 80.0),
            session(1, 8 to 77.5, 8 to 77.5, 8 to 77.5),
        )
        val s = SuggestionEngine.nextSet(history, emptyList())!!
        assertEquals(SetSuggestionKind.PROGRESS, s.kind)
        assertEquals(82.5, s.load, 1e-9)
        assertEquals(2.5, s.step, 1e-9)
        assertEquals(6, s.reps) // bottom of the user's own recent range
        assertEquals("6x 82.5", s.entry(LoadUnit.KG))
        assertEquals("6 × 82.5", s.label(LoadUnit.KG))
    }

    @Test
    fun `pyramid with different loads only repeats`() {
        val history = listOf(session(1, 10 to 60.0, 10 to 70.0, 10 to 80.0))
        val s = SuggestionEngine.nextSet(history, emptyList())!!
        assertEquals(SetSuggestionKind.REPEAT, s.kind)
        assertEquals(60.0, s.load, 0.0)
    }

    @Test
    fun `today's warm-ups are skipped when matching sets`() {
        val history = listOf(session(1, 8 to 100.0, 8 to 100.0, 7 to 100.0))
        val s = SuggestionEngine.nextSet(history, today(10 to 40.0, 5 to 60.0, 3 to 80.0, 8 to 100.0))!!
        assertEquals(2, s.setNumber)
        assertEquals(100.0, s.load, 0.0)
    }

    @Test
    fun `no suggestion past last session's set count`() {
        val history = listOf(session(1, 8 to 80.0, 8 to 80.0))
        assertNull(SuggestionEngine.nextSet(history, today(8 to 80.0, 8 to 80.0)))
    }

    // --- low confidence --------------------------------------------------------------------------

    @Test
    fun `no suggestion for bodyweight, mixed units, unparsed today set or kept typo`() {
        assertNull(SuggestionEngine.nextSet(listOf(session(1, 10 to 0.0)), emptyList()))
        assertNull(
            SuggestionEngine.nextSet(
                listOf(PastSession(1, listOf(PastSet(8, 80.0, LoadUnit.KG), PastSet(8, 175.0, LoadUnit.LB)))),
                emptyList(),
            ),
        )
        assertNull(SuggestionEngine.nextSet(listOf(session(1, 8 to 80.0)), listOf(TodaySet(null, null, null))))
        val typo = listOf(
            session(4, 8 to 825.0, 8 to 82.5),
            session(3, 8 to 80.0),
            session(2, 8 to 80.0),
            session(1, 8 to 77.5),
        )
        assertNull(SuggestionEngine.nextSet(typo, emptyList()))
        // The next set is fine again.
        assertEquals(82.5, SuggestionEngine.nextSet(typo, today(8 to 82.5))!!.load, 0.0)
    }

    // --- load step -------------------------------------------------------------------------------

    @Test
    fun `load step is the smallest gap used, snapped down and capped`() {
        assertEquals(2.5, SuggestionEngine.loadStep(listOf(session(1, 8 to 80.0)), LoadUnit.KG), 0.0)
        assertEquals(2.0, SuggestionEngine.loadStep(listOf(session(2, 10 to 12.0), session(1, 10 to 10.0)), LoadUnit.KG), 0.0)
        assertEquals(1.25, SuggestionEngine.loadStep(listOf(session(2, 5 to 101.25), session(1, 5 to 100.0)), LoadUnit.KG), 0.0)
        // A gap of 3 kg snaps down to 2.5; a gap of 20 kg is capped at 5.
        assertEquals(2.5, SuggestionEngine.loadStep(listOf(session(2, 5 to 63.0), session(1, 5 to 60.0)), LoadUnit.KG), 0.0)
        assertEquals(5.0, SuggestionEngine.loadStep(listOf(session(2, 5 to 120.0), session(1, 5 to 100.0)), LoadUnit.KG), 0.0)
        val lb = listOf(PastSession(1, listOf(PastSet(8, 135.0, LoadUnit.LB), PastSet(8, 140.0, LoadUnit.LB))))
        assertEquals(5.0, SuggestionEngine.loadStep(lb, LoadUnit.LB), 0.0)
        assertEquals(5.0, SuggestionEngine.loadStep(emptyList(), LoadUnit.LB), 0.0)
    }

    @Test
    fun `lb history fills in lb with the unit spelled out under a kg default`() {
        val history = listOf(PastSession(1, List(3) { PastSet(10, 135.0, LoadUnit.LB) }))
        val s = SuggestionEngine.nextSet(history, emptyList())!!
        assertEquals(140.0, s.load, 0.0)
        assertEquals("10x 140 lb", s.entry(LoadUnit.KG))
        assertEquals("10x 140", s.entry(LoadUnit.LB))
    }

    // --- policy, warm-up, sources ----------------------------------------------------------------

    @Test
    fun `no goal means history lines only and no disclaimer`() {
        val history = listOf(session(1, 8 to 80.0, 8 to 80.0, 6 to 80.0))
        val out = SuggestionEngine.suggest(history, emptyList(), TrainingGoal.NONE, ExerciseKind.COMPOUND, usualRestSeconds = 150)
        assertNull(out.policy)
        assertTrue(out.warmUp.isEmpty())
        val lines = out.whyLines(LoadUnit.KG)
        assertTrue(lines.all { it.source == SuggestionSource.HISTORY })
        assertTrue(lines.none { COACHING_DISCLAIMER in it.text })
        assertTrue(lines.any { "2'30" in it.text })
    }

    @Test
    fun `a goal adds labelled policy lines, a warm-up ramp and the disclaimer`() {
        val history = listOf(session(1, 8 to 100.0, 8 to 100.0, 6 to 100.0))
        val out = SuggestionEngine.suggest(history, emptyList(), TrainingGoal.BUILD_MUSCLE, ExerciseKind.COMPOUND)
        assertEquals(SuggestionSource.HISTORY, out.next!!.source)
        assertEquals(6..15, out.policyReps)
        assertEquals(120..180, out.policyRestSeconds)
        assertEquals(listOf(40.0, 60.0, 80.0), out.warmUp.map { it.load })
        val lines = out.whyLines(LoadUnit.KG)
        val policy = lines.filter { it.source == SuggestionSource.POLICY }
        assertTrue(policy.any { "Build muscle" in it.text && "2–3 min" in it.text })
        assertTrue(policy.any { "expert practice" in it.text })
        assertEquals(COACHING_DISCLAIMER, lines.last().text)
    }

    @Test
    fun `lose fat keeps build-muscle rest, never shorter`() {
        val muscle = GoalPolicy.BUILD_MUSCLE
        val fat = GoalPolicy.LOSE_FAT
        assertEquals(muscle.compoundRestSeconds, fat.compoundRestSeconds)
        assertEquals(muscle.isolationRestSeconds, fat.isolationRestSeconds)
        assertTrue(fat.isolationRestSeconds.first >= 90)
        assertTrue(fat.weeklySetsPerMuscle.first >= 8)
        assertEquals(1..6, GoalPolicy.GET_STRONGER.compoundReps)
        assertEquals(180..300, GoalPolicy.GET_STRONGER.compoundRestSeconds)
        assertNull(GoalPolicy.of(TrainingGoal.NONE))
    }

    @Test
    fun `warm-up ramp rounds down to the step and drops empty sets`() {
        assertEquals(listOf(30.0, 47.5, 62.5), SuggestionEngine.warmUpRamp(80.0, 2.5, LoadUnit.KG).map { it.load })
        assertEquals(listOf(2.5, 5.0), SuggestionEngine.warmUpRamp(7.5, 2.5, LoadUnit.KG).map { it.load })
        assertTrue(SuggestionEngine.warmUpRamp(0.0, 2.5, LoadUnit.KG).isEmpty())
    }

    @Test
    fun `exercise kind guess and goal storage`() {
        assertEquals(ExerciseKind.COMPOUND, ExerciseKind.guess("Bench press"))
        assertEquals(ExerciseKind.COMPOUND, ExerciseKind.guess("Leg press"))
        assertEquals(ExerciseKind.ISOLATION, ExerciseKind.guess("Leg extension"))
        assertEquals(ExerciseKind.ISOLATION, ExerciseKind.guess("Lateral raise"))
        assertEquals(ExerciseKind.UNKNOWN, ExerciseKind.guess("Farmer walk"))
        assertEquals(6..20, GoalPolicy.BUILD_MUSCLE.reps(ExerciseKind.UNKNOWN))
        assertEquals(TrainingGoal.NONE, TrainingGoal.fromStorage(null))
        assertEquals(TrainingGoal.NONE, TrainingGoal.fromStorage("SOMETHING_NEW"))
        assertEquals(TrainingGoal.GET_STRONGER, TrainingGoal.fromStorage("get_stronger"))
    }

    // --- summary parsing and lookup --------------------------------------------------------------

    @Test
    fun `sets summary parses kg, lb and bodyweight, anything odd empties the session`() {
        assertEquals(kg(8 to 80.0, 6 to 82.5), parseSetsSummary("8x 80 · 6x 82.5", LoadUnit.KG))
        assertEquals(listOf(PastSet(10, 135.0, LoadUnit.LB)), parseSetsSummary("10x 135 lb", LoadUnit.LB))
        assertEquals(listOf(PastSet(12, 0.0, LoadUnit.KG)), parseSetsSummary("12x", LoadUnit.KG))
        assertTrue(parseSetsSummary("8x 80 · banana", LoadUnit.KG).isEmpty())
        assertTrue(parseSetsSummary("", LoadUnit.KG).isEmpty())
    }

    @Test
    fun `history lookup tries candidate names and ignores case`() {
        val sessions = listOf(session(1, 8 to 80.0))
        val history = SuggestionHistory(mapOf("Bench Press" to sessions)) { listOf("Bench Press", it) }
        assertEquals(sessions, history.forExercise("bp"))
        assertEquals(sessions, SuggestionHistory(mapOf("Bench Press" to sessions)).forExercise("bench press "))
        assertTrue(SuggestionHistory.EMPTY.forExercise("Bench Press").isEmpty())
    }

    // --- months of simulated data: no load jump over one step ------------------------------------

    /**
     * A lifter who always takes the pill, over [sessions] workouts. Strength grows slowly with
     * noise and bad days; reps are what they can do at the load (Epley), at most [maxReps].
     * Returns every suggestion together with the previous session's matched set.
     */
    private fun simulate(
        seed: Int,
        startLoad: Double,
        startE1rm: Double,
        sets: Int,
        maxReps: Int,
        sessions: Int,
        typoEvery: Int = 0,
    ): List<Pair<SetSuggestion, PastSet>> {
        val random = Random(seed)
        var e1rm = startE1rm
        val history = ArrayDeque<PastSession>()
        val out = mutableListOf<Pair<SetSuggestion, PastSet>>()
        var nextLoad = startLoad
        for (s in 0 until sessions) {
            e1rm *= 1.0 + random.nextDouble(-0.004, 0.008) // ~+2 % a month on average, with bad days
            val todaySets = mutableListOf<TodaySet>()
            val logged = mutableListOf<PastSet>()
            for (i in 0 until sets) {
                val suggestion = SuggestionEngine.nextSet(history.toList(), todaySets)
                if (suggestion != null) out += suggestion to history.first().sets[suggestion.setNumber - 1]
                val load = suggestion?.load ?: nextLoad
                val capable = floor(30.0 * (e1rm / load - 1.0)).toInt() - i // Epley, fatigue per set
                val reps = capable.coerceIn(1, maxReps)
                todaySets += TodaySet(reps, load, LoadUnit.KG)
                logged += PastSet(reps, load, LoadUnit.KG)
            }
            nextLoad = logged.first().load
            val stored = if (typoEvery > 0 && s > 5 && s % typoEvery == 0) {
                // A kept typo: one set logged ten times too heavy (`825` for `82.5`).
                logged.mapIndexed { i, set -> if (i == 0) set.copy(load = set.load * 10) else set }
            } else {
                logged
            }
            history.addFirst(PastSession(s.toLong(), stored))
            if (history.size > 5) history.removeLast()
        }
        return out
    }

    @Test
    fun `six months of simulated training never jumps more than one step`() {
        for (seed in 1..20) {
            val pairs = simulate(seed, startLoad = 60.0, startE1rm = 80.0, sets = 3, maxReps = 10, sessions = 78)
            assertTrue("seed $seed made no suggestions", pairs.size > 100)
            pairs.forEach { (s, basis) ->
                assertTrue("seed $seed: ${basis.load} → ${s.load}", s.load - basis.load <= s.step + 1e-9)
                assertTrue("seed $seed: step ${s.step}", s.step <= SuggestionEngine.maxStep(LoadUnit.KG))
                assertTrue("seed $seed: load went down ${basis.load} → ${s.load}", s.load >= basis.load)
            }
            // Progress happens, but stays realistic: ~6 months of 3×/week from 60 kg.
            val finalLoad = pairs.last().first.load
            assertTrue("seed $seed final $finalLoad", finalLoad in 60.0..100.0)
        }
    }

    @Test
    fun `kept typos in history never produce a suggestion above the real loads plus one step`() {
        for (seed in 1..20) {
            val pairs = simulate(seed, startLoad = 60.0, startE1rm = 80.0, sets = 3, maxReps = 10, sessions = 120, typoEvery = 9)
            assertTrue(pairs.isNotEmpty())
            pairs.forEach { (s, _) -> assertTrue("seed $seed suggested ${s.load}", s.load < 200.0) }
        }
    }

    @Test
    fun `progression pace matches the lifter, about one step per few sessions`() {
        val pairs = simulate(seed = 7, startLoad = 60.0, startE1rm = 80.0, sets = 3, maxReps = 10, sessions = 78)
        val firstSets = pairs.filter { it.first.setNumber == 1 }
        val progressions = firstSets.count { it.first.kind == SetSuggestionKind.PROGRESS }
        assertNotNull(firstSets.firstOrNull())
        assertTrue("progressions $progressions of ${firstSets.size}", progressions in 2 until firstSets.size / 2)
    }
}
