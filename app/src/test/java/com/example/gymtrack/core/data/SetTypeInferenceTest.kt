package com.example.gymtrack.core.data

import com.example.gymtrack.core.data.SetType.WARMUP
import com.example.gymtrack.core.data.SetType.WORKING
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** #306: warm-up vs working inference and rest, the pure rule in [SetTypeInference]. */
class SetTypeInferenceTest {

    private fun types(vararg sets: Pair<Double, Int>, key: Any = "bench"): List<SetType> =
        SetTypeInference.classify(sets.map { (load, reps) -> SetTypeInput(key, load, reps) }).map { it.type }

    @Test
    fun classicRampBeforeWorkSetsIsWarmUp() {
        // Bar × 10, 60 × 5, 80 × 3, then 100 × 5 × 3.
        assertEquals(
            listOf(WARMUP, WARMUP, WARMUP, WORKING, WORKING, WORKING),
            types(20.0 to 10, 60.0 to 5, 80.0 to 3, 100.0 to 5, 100.0 to 5, 100.0 to 5),
        )
    }

    @Test
    fun heavyPrimerSingleBeforeTheTopSetIsWarmUp() {
        assertEquals(listOf(WARMUP, WARMUP, WORKING, WORKING), types(50.0 to 5, 90.0 to 1, 100.0 to 5, 100.0 to 5))
    }

    @Test
    fun lightBackOffSetAfterWorkSetsIsNotWarmUp() {
        assertEquals(listOf(WORKING, WORKING, WORKING), types(100.0 to 5, 100.0 to 5, 60.0 to 12))
    }

    @Test
    fun warmUpsBeforeAndBackOffAfterAreToldApart() {
        assertEquals(
            listOf(WARMUP, WORKING, WORKING, WORKING),
            types(40.0 to 8, 100.0 to 5, 100.0 to 5, 40.0 to 15),
        )
    }

    @Test
    fun ascendingPyramidWithMoreRepsOnLighterSetsStaysWorking() {
        // 75 % and 87.5 % of the top, each with more reps than the top set: real work.
        assertEquals(listOf(WORKING, WORKING, WORKING), types(60.0 to 10, 70.0 to 8, 80.0 to 6))
    }

    @Test
    fun straightSetsAndSmallJumpsStayWorking() {
        assertEquals(listOf(WORKING, WORKING, WORKING), types(80.0 to 8, 80.0 to 8, 80.0 to 7))
        assertEquals(listOf(WORKING, WORKING, WORKING), types(80.0 to 8, 85.0 to 6, 90.0 to 5))
    }

    @Test
    fun singleSetAndBodyweightBlocksStayWorking() {
        assertEquals(listOf(WORKING), types(100.0 to 5))
        assertEquals(listOf(WORKING, WORKING), types(0.0 to 12, 0.0 to 10))
        assertEquals(
            listOf(WORKING, WORKING),
            SetTypeInference.classify(
                listOf(SetTypeInput("plank", null, 0, isTimed = true), SetTypeInput("plank", 20.0, 0, isTimed = true)),
            ).map { it.type },
        )
    }

    @Test
    fun aRampThatGoesDownStopsBeingWarmUp() {
        // 60 then 40: not a ramp, so the 40 and everything after counts.
        assertEquals(listOf(WARMUP, WORKING, WORKING), types(60.0 to 5, 40.0 to 5, 100.0 to 5))
    }

    @Test
    fun eachExerciseBlockIsClassifiedOnItsOwn() {
        val result = SetTypeInference.classify(
            listOf(
                SetTypeInput("squat", 60.0, 5),
                SetTypeInput("squat", 140.0, 5),
                SetTypeInput("curl", 12.0, 12),
                SetTypeInput("curl", 12.0, 10),
            ),
        ).map { it.type }
        assertEquals(listOf(WARMUP, WORKING, WORKING, WORKING), result)
    }

    @Test
    fun userOverrideWinsAndIsMarkedUser() {
        val result = SetTypeInference.classify(
            listOf(
                SetTypeInput("bench", 60.0, 5, override = WORKING),
                SetTypeInput("bench", 100.0, 5, override = WARMUP),
                SetTypeInput("bench", 100.0, 5),
            ),
        )
        assertEquals(listOf(WORKING, WARMUP, WORKING), result.map { it.type })
        assertEquals(
            listOf(SetTypeSource.USER, SetTypeSource.USER, SetTypeSource.INFERRED),
            result.map { it.source },
        )
    }

    @Test
    fun restIsTheGapWithinABlockOnly() {
        val result = SetTypeInference.classify(
            listOf(
                SetTypeInput("bench", 100.0, 5, offsetSeconds = 300),
                SetTypeInput("bench", 100.0, 5, offsetSeconds = 420),
                SetTypeInput("bench", 100.0, 5, offsetSeconds = null),
                SetTypeInput("row", 60.0, 8, offsetSeconds = 900),
                SetTypeInput("row", 60.0, 8, offsetSeconds = 990),
                SetTypeInput("row", 60.0, 8, offsetSeconds = 990 + SetTypeInference.MAX_REST_SECONDS + 1),
            ),
        )
        assertNull("first set of a block has no rest", result[0].restSeconds)
        assertEquals(120, result[1].restSeconds)
        assertNull("unknown time", result[2].restSeconds)
        assertNull("exercise change is not rest", result[3].restSeconds)
        assertEquals(90, result[4].restSeconds)
        assertNull("a long break is not rest", result[5].restSeconds)
    }

    @Test
    fun storageValuesRoundTripAndUnknownIsWorkingInferred() {
        SetType.entries.forEach { assertEquals(it, SetType.fromStorage(it.storageValue)) }
        assertEquals(WORKING, SetType.fromStorage(null))
        assertEquals(WORKING, SetType.fromStorage("bogus"))
        assertEquals(SetTypeSource.INFERRED, SetTypeSource.fromStorage(null))
        assertEquals(SetTypeSource.USER, SetTypeSource.fromStorage("user"))
    }
}
