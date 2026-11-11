package com.example.gymtrack.core.muscle

import com.example.gymtrack.core.muscle.MuscleMappingSeed as Seed
import com.example.gymtrack.core.util.ExerciseIdentityResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** #309: the seed matches `docs/design/ANATOMY_MAPPING.md` §2 and every row carries its source. */
class MuscleMappingSeedTest {
    @Test
    fun everyDocRowIsInTheSeedOnceWithASource() {
        assertEquals((1..48).toList(), Seed.ALL.map { it.row })
        Seed.ALL.forEach { entry ->
            assertTrue("Row ${entry.row} has no source", entry.source.isNotBlank())
            assertTrue("Row ${entry.row} has no primary", entry.primary.isNotEmpty())
            if (entry.status == CitationStatus.CONSENSUS) {
                assertTrue("Consensus row ${entry.row} must say so", entry.source.startsWith("No direct study found"))
            }
        }
        // The doc's tally: 8 consensus rows.
        assertEquals(8, Seed.ALL.count { it.status == CitationStatus.CONSENSUS })
    }

    @Test
    fun docTableSpotChecks() {
        assertEquals(setOf(Muscle.CHEST_LOWER, Muscle.CHEST_UPPER), Seed.BENCH_BARBELL.primary)
        assertEquals(setOf(Muscle.DELT_FRONT, Muscle.TRICEPS), Seed.BENCH_BARBELL.secondary)
        assertEquals(setOf(Muscle.QUADS, Muscle.GLUTE_MAX), Seed.SQUAT.primary)
        // Kubo 2019: no hamstring credit in squats.
        assertTrue(Muscle.HAMSTRINGS !in Seed.SQUAT.primary + Seed.SQUAT.secondary)
        // Kinoshita 2023: seated calf raise does not credit the gastrocnemius.
        assertEquals(setOf(Muscle.SOLEUS), Seed.CALF_SEATED.primary + Seed.CALF_SEATED.secondary)
        // Grouping fix: brachioradialis credit goes to forearm extensors.
        assertTrue(Muscle.FOREARM_EXTENSORS in Seed.HAMMER_CURL.secondary)
        // Isometric stabilisers are not counted.
        assertTrue(Muscle.LATS !in Seed.DEADLIFT.primary + Seed.DEADLIFT.secondary)
        assertTrue(Muscle.ERECTORS !in Seed.ROW_SUPPORTED.secondary)
    }

    @Test
    fun everyCanonicalNameInTheDocResolves() {
        val expected = mapOf(
            "Bench Press" to Seed.BENCH_BARBELL,
            "Lateral Raise" to Seed.LATERAL_RAISE,
            "Rear Delt Fly" to Seed.REAR_DELT_FLY,
            "Lat Pulldown" to Seed.LAT_PULLDOWN,
            "Pull-Up" to Seed.PULL_UP,
            "Assisted Pull-Up" to Seed.PULL_UP,
            "Row" to Seed.ROW_SUPPORTED,
            "T-Bar Row" to Seed.ROW_FREE,
            "Diagonal Row" to Seed.ROW_SUPPORTED,
            "Shrug" to Seed.SHRUG,
            "Leg Press" to Seed.LEG_PRESS,
            "Hack Squat" to Seed.HACK_SQUAT,
            "Reverse Hack Squat" to Seed.HACK_SQUAT,
            "Leg Extension" to Seed.LEG_EXTENSION,
            "Seated Leg Curl" to Seed.SEATED_LEG_CURL,
            "Hip Thrust" to Seed.HIP_THRUST,
            "Hip Abduction" to Seed.HIP_ABDUCTION,
            "Hip Adduction" to Seed.HIP_ADDUCTION,
            "Calf Raise" to Seed.CALF_STANDING,
            "Machine Dip" to Seed.DIP,
            "Biceps Curl" to Seed.BICEPS_CURL,
            "Hammer Curl" to Seed.HAMMER_CURL,
            "Brachialis Curl" to Seed.HAMMER_CURL,
            "Preacher Curl" to Seed.PREACHER_CURL,
            "Triceps Pushdown" to Seed.TRICEPS_PUSHDOWN,
            "Triceps Extension" to Seed.LYING_TRICEPS,
            "Overhead Triceps Extension" to Seed.OVERHEAD_TRICEPS,
            "Pec Deck" to Seed.FLY,
            "Chest Fly" to Seed.FLY,
            "Hanging Leg Raise" to Seed.LEG_RAISE,
            "Sit-Up" to Seed.CRUNCH,
            "Squat" to Seed.SQUAT,
            "Bulgarian Split Squat" to Seed.SPLIT_SQUAT,
        )
        expected.forEach { (name, entry) -> assertEquals(name, entry, Seed.lookup(name)) }
    }

    @Test
    fun parserKeywordsResolve() {
        assertEquals(Seed.DEADLIFT, Seed.lookup("Deadlift"))
        assertEquals(Seed.DEADLIFT, Seed.lookup("Sumo Deadlift"))
        assertEquals(Seed.ROMANIAN_DEADLIFT, Seed.lookup("Romanian Deadlift"))
        assertEquals(Seed.ROMANIAN_DEADLIFT, Seed.lookup("RDL"))
        assertEquals(Seed.LUNGE, Seed.lookup("Walking Lunges"))
        assertEquals(Seed.BACK_EXTENSION, Seed.lookup("Back Extension"))
        assertEquals(Seed.BACK_EXTENSION, Seed.lookup("Hyperextension"))
        assertEquals(Seed.CRUNCH, Seed.lookup("Cable Crunch"))
        assertEquals(Seed.OVERHEAD_PRESS, Seed.lookup("OHP"))
        assertEquals(Seed.CHIN_UP, Seed.lookup("Chin Up"))
        assertEquals(Seed.FRONT_SQUAT, Seed.lookup("Front Squat"))
        assertEquals(Seed.LYING_LEG_CURL, Seed.lookup("Lying Leg Curl"))
        assertEquals(Seed.FACE_PULL, Seed.lookup("Face Pull"))
    }

    @Test
    fun variantsFollowTheSetModifier() {
        assertEquals(Seed.BENCH_INCLINE, Seed.lookup("Bench Press", "incline"))
        assertEquals(Seed.BENCH_DECLINE, Seed.lookup("Bench Press", "Decline"))
        assertEquals(Seed.BENCH_DUMBBELL, Seed.lookup("Bench Press", "dumbbell"))
        assertEquals(Seed.BENCH_INCLINE, Seed.lookup("Incline Dumbbell Press"))
        assertEquals(Seed.CALF_SEATED, Seed.lookup("Calf Raise", "seated"))
        assertEquals(Seed.CALF_SEATED, Seed.lookup("Seated Calf Raise"))
        assertEquals(Seed.ROW_FREE, Seed.lookup("Row", "barbell"))
        assertEquals(Seed.ROW_SUPPORTED, Seed.lookup("T-Bar Row", "supported"))
    }

    @Test
    fun exercisesTheDocDoesNotCoverStayUnmapped() {
        listOf("Rear Delt Row", "Dead Hang", "Chest Press", "Pullover", "Treadmill", "Kettlebell Swing").forEach { name ->
            assertNull(name, Seed.lookup(name))
        }
    }

    @Test
    fun theSeedKeysAreTheIdentityBaseKeys() {
        // The seed is keyed by ExerciseIdentity.baseComparisonKey: typed variants land on the same row.
        val identity = ExerciseIdentityResolver.resolve(rawName = "lat pull prime")
        assertEquals("lat_pulldown", identity.baseComparisonKey)
        assertEquals(Seed.LAT_PULLDOWN, Seed.lookup(identity.canonicalName))
        assertEquals(Seed.LAT_PULLDOWN, Seed.lookup("lat pull prime"))
    }
}
