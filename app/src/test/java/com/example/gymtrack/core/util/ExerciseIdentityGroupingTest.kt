package com.example.gymtrack.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** #318: aliases, plurals, joined words and typos of one exercise share one identity. */
class ExerciseIdentityGroupingTest {

    /** Confirmed splits from the long-horizon simulation, each with the spelling it must join. */
    private val confirmedSplits = listOf(
        "BP" to "Bench Press",
        "Curls" to "Biceps Curl",
        "Pushdown" to "Triceps Pushdown",
        "legpress" to "Leg Press",
        "hang" to "Dead Hang",
        "split squat" to "Bulgarian Split Squat",
        "romanian dl" to "Romanian Deadlift",
        "Sqaut" to "Squat",
        "Lateral Rasie" to "Lateral Raise",
        // Other spellings the simulation types for the same exercises.
        "RDL" to "Romanian Deadlift",
        "BSS" to "Bulgarian Split Squat",
        "Back Squat" to "Squat",
        "squats" to "Squat",
        "Leg Pres" to "Leg Press",
        "Bench Pres" to "Bench Press",
        "Bnech Press" to "Bench Press",
        "Romanain Deadlift" to "Romanian Deadlift",
        "Tricpes Pushdown" to "Triceps Pushdown",
        "Lat Pulldwon" to "Lat Pulldown",
        "latpull" to "Lat Pulldown",
        "Bicpes Curl" to "Biceps Curl",
        "face pulls" to "Face Pull",
        "Inclne DB Press" to "Incline DB Press",
        "Cabel Fly" to "Cable Fly",
        "OHP" to "Overhead Press",
    )

    @Test
    fun confirmedSplitNamesResolveToOneIdentity() {
        confirmedSplits.forEach { (typed, canonical) ->
            assertEquals(
                "'$typed' should group with '$canonical'",
                ExerciseIdentityResolver.resolve(canonical).baseComparisonKey,
                ExerciseIdentityResolver.resolve(typed).baseComparisonKey,
            )
        }
    }

    @Test
    fun confirmedSplitNamesGroupAsStoredByTheParser() {
        val parser = WorkoutParser()
        fun storedKey(name: String) = parser.parseWorkout("$name\n8x 50").single().exerciseIdentity.baseComparisonKey
        confirmedSplits.forEach { (typed, canonical) ->
            assertEquals("'$typed' should group with '$canonical'", storedKey(canonical), storedKey(typed))
        }
    }

    @Test
    fun displayNameIsCleanCanonicalName() {
        assertEquals("Bench Press", ExerciseIdentityResolver.resolve("BP").canonicalName)
        assertEquals("Leg Press", ExerciseIdentityResolver.resolve("legpress").canonicalName)
        assertEquals("Squat", ExerciseIdentityResolver.resolve("Sqaut").canonicalName)
        assertEquals("Lateral Raise", ExerciseIdentityResolver.resolve("Lateral Rasie").canonicalName)
    }

    @Test
    fun rawTextIsNeverRewritten() {
        val identity = ExerciseIdentityResolver.resolve("Sqaut")
        assertEquals("Sqaut", identity.rawName)
        assertEquals(setOf("Sqaut"), identity.aliases)
        assertEquals("Sqaut", WorkoutParser().parseWorkout("Sqaut\n5x 80").single().exerciseIdentity.rawName)
    }

    @Test
    fun distinctExercisesAreNotMerged() {
        val distinct = listOf(
            "hip thrust" to "dip",
            "row" to "rear delt row",
            "curl" to "leg curl",
            "curl" to "hammer curl",
            "curl" to "preacher curl",
            "bench press" to "leg press",
            "bench press" to "overhead press",
            "bench press" to "incline press",
            "leg press" to "chest press",
            "squat" to "front squat",
            "squat" to "hack squat",
            "squat" to "split squat",
            "deadlift" to "romanian deadlift",
            "pushdown" to "pulldown",
            "pullup" to "pushup",
            "adductor" to "abductor",
            "dead hang" to "hanging leg raise",
            "lat pulldown" to "lat raise",
            "lateral raise" to "rear delt fly",
            "incline press" to "decline press",
        )
        distinct.forEach { (a, b) ->
            assertNotEquals(
                "'$a' and '$b' are different exercises",
                ExerciseIdentityResolver.resolve(a).baseComparisonKey,
                ExerciseIdentityResolver.resolve(b).baseComparisonKey,
            )
        }
    }

    @Test
    fun typoToleranceIsConservative() {
        fun name(raw: String) = ExerciseIdentityResolver.resolve(raw).canonicalName
        // Under five letters: never corrected (except a missing doubled last letter).
        assertEquals("Hip", name("hip"))
        assertEquals("Rack Pull", name("rack pull"))
        // A different first letter is a different word, not a typo.
        assertEquals("Pench Press", name("pench press"))
        assertEquals("Zottman Curl", name("zottman curl"))
        // Real exercise words are kept even when one edit from another one.
        assertEquals("Hip Abduction", name("abductor"))
        assertEquals("Hip Adduction", name("adductor"))
    }

    @Test
    fun equipmentStaysOutOfTheBaseExercise() {
        val cable = ExerciseIdentityResolver.resolve("cable curl")
        val dumbbell = ExerciseIdentityResolver.resolve("curls db")
        assertEquals("Biceps Curl", cable.canonicalName)
        assertEquals(cable.baseComparisonKey, dumbbell.baseComparisonKey)
        assertNotEquals(cable.progressComparisonKey, dumbbell.progressComparisonKey)
    }
}
