package com.example.gymtrack.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression tests for #300 (autocorrect renames) and #301 (silently dropped sets). */
class WorkoutParserSilentChangeTest {

    private val parser = WorkoutParser()

    private fun nameOf(header: String): String = parser.parseWorkout("$header\n    10x 50").single().exerciseName

    // --- #300 autocorrect ---

    @Test
    fun hipThrustIsNotRenamedToDipThrust() {
        assertEquals("Hip thrust", nameOf("Hip thrust"))
        assertEquals("Hip thrust machine", nameOf("hip thrust machine"))
    }

    @Test
    fun rackPullIsNotRenamedToBackPull() {
        assertEquals("Rack pull", nameOf("Rack pull"))
        assertEquals("Rack pull", nameOf("rack pulls"))
    }

    @Test
    fun dipAndBackExtensionStayThemselves() {
        assertEquals("Dip", nameOf("dip"))
        assertEquals("Dip", nameOf("dips"))
        assertEquals("Back extension", nameOf("back extension"))
    }

    @Test
    fun shortRealWordsAreNotCorrected() {
        assertEquals("Chin up", nameOf("chin up"))
        assertEquals("Side raise", nameOf("side raise"))
        assertEquals("Pec deck", nameOf("pec deck"))
        assertEquals("Cheat curl", nameOf("cheat curl"))
        assertEquals("Pulley row", nameOf("pulley row"))
    }

    @Test
    fun genuineTyposAreStillCorrectedAndReported() {
        val result = parser.parse("squatt\n    5x 100")
        assertEquals("Squat", result.sets.single().exerciseName)
        val diagnostic = result.diagnostics.single()
        assertEquals(ParseDiagnosticReason.NAME_AUTOCORRECTED, diagnostic.reason)
        assertEquals(0, diagnostic.lineIndex)
        assertEquals("squatt", diagnostic.original)
        assertEquals("squat", diagnostic.replacement)
        assertEquals("Triceps pushdown", nameOf("tricpe pushdown"))
        assertEquals("Deadlift", nameOf("deadlfit"))
    }

    @Test
    fun realExerciseNamesProduceZeroAutocorrections() {
        val renamed = REAL_EXERCISE_NAMES.mapNotNull { name ->
            parser.parse("$name\n    10x 50").diagnostics
                .filter { it.reason == ParseDiagnosticReason.NAME_AUTOCORRECTED }
                .takeIf { it.isNotEmpty() }
                ?.let { list -> "$name: " + list.joinToString { "${it.original}->${it.replacement}" } }
        }
        assertEquals(emptyList<String>(), renamed)
    }

    @Test
    fun autocorrectIsMemoized() {
        val first = parser.autocorrect("deadlfit")
        assertSame(first, parser.autocorrect("deadlfit"))
        assertEquals("deadlift", first)
    }

    // --- #301 set formats ---

    private fun singleSet(header: String, set: String, unit: String = "KG") =
        parser.parse("$header\n    $set", defaultWeightUnit = unit)

    @Test
    fun xFirstFormatParses() {
        val set = singleSet("Bench press", "x10 50").sets.single()
        assertEquals(10, set.reps)
        assertEquals(50f, set.weight)
        val spaced = singleSet("Bench press", "x 10 50").sets.single()
        assertEquals(10, spaced.reps)
        assertEquals(50f, spaced.weight)
    }

    @Test
    fun repsWordFormatParses() {
        val set = singleSet("Bench press", "10 reps 50").sets.single()
        assertEquals(10, set.reps)
        assertEquals(50f, set.weight)
        assertEquals(8, singleSet("Bench press", "8 rep 40").sets.single().reps)
    }

    @Test
    fun weightFirstWithUnitParses() {
        val set = singleSet("Bench press", "100kg x5", unit = "LB").sets.single()
        assertEquals(5, set.reps)
        assertEquals(100f, set.weight)
        assertEquals("KG", set.weightUnit)
    }

    @Test
    fun decimalCommaIsSupported() {
        assertEquals(62.5f, singleSet("Bench press", "10x 62,5").sets.single().weight)
        assertEquals(12.5f, singleSet("Plank", "30s 12,5").sets.single().weight)
        assertEquals(30, singleSet("Plank", "30s 12,5").sets.single().durationSeconds)
    }

    @Test
    fun firstSetWithoutWeightIsReportedNotSilentlyDropped() {
        val result = parser.parse("Bench press\n      8x\n      10x 60\n      8x")
        assertEquals(listOf(10, 8), result.sets.map { it.reps })
        assertEquals(listOf(60f, 60f), result.sets.map { it.weight })
        val diagnostic = result.diagnostics.single()
        assertEquals(1, diagnostic.lineIndex)
        assertEquals("      8x", diagnostic.line)
        assertEquals(ParseDiagnosticReason.MISSING_WEIGHT, diagnostic.reason)
        assertTrue(diagnostic.reason.dropsSet)
        assertEquals(result.diagnostics, result.droppedLines)
    }

    @Test
    fun loneNumberCountsAsRepsForCardioOnly() {
        val cardio = singleSet("Treadmill", "30")
        assertEquals(30, cardio.sets.single().reps)
        assertTrue(cardio.diagnostics.isEmpty())

        val bench = singleSet("Bench press", "30")
        assertTrue(bench.sets.isEmpty())
        assertEquals(ParseDiagnosticReason.AMBIGUOUS_NUMBER, bench.diagnostics.single().reason)
        assertEquals(ParseDiagnosticReason.AMBIGUOUS_NUMBER, singleSet("Bench press", "62,5").diagnostics.single().reason)
    }

    @Test
    fun indentedNoteLineIsReported() {
        val result = singleSet("Bench press", "felt heavy today")
        assertTrue(result.sets.isEmpty())
        assertEquals(ParseDiagnosticReason.UNRECOGNISED_SET, result.diagnostics.single().reason)
    }

    @Test
    fun zeroSecondTimedSetIsReported() {
        assertEquals(ParseDiagnosticReason.ZERO_DURATION, singleSet("Plank", "0s").diagnostics.single().reason)
    }

    /**
     * Every set line from docs/EDITOR_BEHAVIOUR.md (sections 2.3, 4, 7) and docs/WEIGHT_UNITS.md
     * either becomes a set or a diagnostic: the number of set lines always equals sets + dropped.
     */
    @Test
    fun documentedFormatsNeverVanish() {
        for ((header, sets) in DOCUMENTED_FORMATS) {
            for (set in sets) {
                for (unit in listOf("KG", "LB")) {
                    val result = parser.parse("$header\n      $set", defaultWeightUnit = unit)
                    assertEquals("$header / $set", 1, result.sets.size + result.droppedLines.size)
                }
            }
        }
    }

    @Test
    fun documentedFormatsParseToExpectedValues() {
        fun check(header: String, set: String, reps: Int, weight: Float, unit: String, duration: Int? = null, default: String = "KG") {
            val parsed = parser.parse("$header\n      $set", defaultWeightUnit = default)
            assertTrue("$set: ${parsed.diagnostics}", parsed.diagnostics.none { it.reason.dropsSet })
            val dto = parsed.sets.single()
            assertEquals(set, reps, dto.reps)
            assertEquals(set, weight, dto.weight)
            assertEquals(set, unit, dto.weightUnit)
            assertEquals(set, duration, dto.durationSeconds)
        }
        check("Bench press", "10x 60", 10, 60f, "KG")
        check("Bench press", "10x 60 (1'38'')", 10, 60f, "KG")
        check("Bench press", "8+4+4x 50", 16, 50f, "KG")
        check("Lateral raise", "15x 12.5 (0'50'')", 15, 12.5f, "KG")
        check("Plank", "60s (1'30'')", 0, 0f, "KG", duration = 60)
        check("Plank", "45s 10 (2'10'')", 0, 10f, "KG", duration = 45)
        check("Cable hold", "30s 20", 0, 20f, "KG", duration = 30)
        check("Bench Press", "5x 100", 5, 100f, "LB", default = "LB")
        check("Bench Press", "5x 100 kg", 5, 100f, "KG", default = "LB")
        check("Bench Press", "8x 220 lb", 8, 220f, "LB")
        check("Pullups", "8x", 8, 0f, "KG")
        check("Treadmill", "20", 20, 0f, "KG")
    }

    @Test
    fun weightCarryStillWorksAfterADroppedLine() {
        val result = parser.parse("Bench press\n      10x 60\n      felt heavy\n      8x")
        assertEquals(listOf(60f, 60f), result.sets.map { it.weight })
        assertEquals(2, result.diagnostics.single().lineIndex)
        assertNull(result.diagnostics.single().original)
    }

    companion object {
        val DOCUMENTED_FORMATS = mapOf(
            "Bench press" to listOf(
                "10x 60", "8x 70 (2'51'')", "8x (2'44'')", "8x", "8+4+4x 50", "10+3+2x", "5x 100", "5x 100 kg",
                "8x 220 lb", "10x 62,5", "x10 50", "10 reps 50", "100kg x5", "62,5", "10", "60s", "30s 20",
                "felt good", "0s",
            ),
            "Pullups" to listOf("8x", "10x 20", "10", "x8"),
            "Treadmill" to listOf("20", "10x 60", "62,5"),
            "Plank" to listOf("60s", "45s 10", "30 S 20kg", "30s 12,5", "8x"),
        )

        /** Real exercise names (docs, intake examples, common gym vocabulary): none may be autocorrected. */
        val REAL_EXERCISE_NAMES = listOf(
            "Bench press", "Incline db press", "latpulldown rl", "lat pull", "leg extension rl", "plank",
            "lateral raise db", "rear delt prime", "tricep pushdown bar", "Hip thrust", "bb hip thrust",
            "Rack pull", "rack pulls", "dip", "dips", "dips prime", "back extension", "hyperextension", "chin up",
            "chin ups", "pull up", "pullups", "push ups", "row", "seated row", "cable row", "upright row",
            "bent over row", "pendlay row", "t bar row", "tbar row", "diag rowing", "fly", "cable fly", "pec fly",
            "pec deck", "rear delt fly", "reverse fly", "butterfly", "side raise", "lat raise", "front raise",
            "face pull", "shrugs", "good morning", "sumo deadlift", "romanian deadlift", "stiff leg deadlift",
            "deficit deadlift", "hack squat", "front squat", "goblet squat", "sissy squat", "bulgarian split squat",
            "pendulum", "leg press", "leg curl", "seated hamstring curl", "nordic curl", "calf raise",
            "donkey calf raise", "toe raise", "hip abduction", "hip adduction", "adductor", "abductor",
            "glute bridge", "glute kickback", "cable kickback", "lunges", "step up", "box jump", "hammer curl",
            "preacher curl", "concentration curl", "spider curl", "drag curl", "zottman curl", "cheat curl",
            "wrist curl", "bicep curl", "biceps curl", "skull crusher", "jm press", "overhead extension",
            "tricep extension", "rope pushdown", "close grip bench", "floor press", "pin press", "arnold press",
            "military press", "overhead press", "shoulder press", "landmine press", "chest press", "hs chest press",
            "pullover", "cable crossover", "farmer walk", "sled push", "ab wheel", "russian twist", "crunches",
            "hanging leg raise", "leg raise", "muscle up", "treadmill", "bike", "stairmaster", "rowing machine",
            "elliptical", "pulley row", "trap bar deadlift", "hex bar deadlift", "inverted row", "pallof press",
            "seal row", "meadows row", "zercher squat", "cossack squat", "curtsy lunge", "belt squat",
        )
    }
}
