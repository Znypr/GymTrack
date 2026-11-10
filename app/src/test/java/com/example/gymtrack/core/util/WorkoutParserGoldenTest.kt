package com.example.gymtrack.core.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Golden snapshot of parser output for a corpus of exercise names and set formats.
 *
 * The expected file was captured from the parser BEFORE the #300/#301 fixes, so any diff in
 * `src/test/resources/parser/golden.txt` is a reviewed, intentional behaviour change.
 * On mismatch the actual output is written to `build/parser-golden-actual.txt` for diffing.
 */
class WorkoutParserGoldenTest {

    private val parser = WorkoutParser()

    @Test
    fun parserOutputMatchesGolden() {
        val actual = render()
        val expected = javaClass.getResourceAsStream("/parser/golden.txt")
            ?.bufferedReader()?.use { it.readText() }
        if (expected == null || expected.normalize() != actual.normalize()) {
            File("build/parser-golden-actual.txt").apply { parentFile?.mkdirs() }.writeText(actual)
        }
        assertEquals(expected?.normalize(), actual.normalize())
    }

    private fun String.normalize() = replace("\r\n", "\n").trimEnd()

    private fun render(): String = buildString {
        appendLine("# names (each parsed with one set `10x 50`)")
        for (name in NAME_CORPUS) {
            val result = parser.parse("$name\n    10x 50")
            appendLine("$name => " + result.sets.joinToString(" ; ") { it.nameLine() } + result.diagText())
        }
        appendLine()
        appendLine("# set formats")
        for (header in SET_HEADERS) {
            for (set in SET_CORPUS) {
                val result = parser.parse("$header\n    $set")
                appendLine("[$header] `$set` => " + result.sets.joinToString(" ; ") { it.setLine() } + result.diagText())
            }
        }
        appendLine()
        appendLine("# whole workouts")
        for ((label, text) in WORKOUT_CORPUS) {
            appendLine("## $label")
            for (unit in listOf("KG", "LB")) {
                val result = parser.parse(text, defaultWeightUnit = unit)
                result.sets.forEach {
                    appendLine("$unit ${it.nameLine()} | ${it.setLine()} | rel=${it.relativeTime} abs=${it.absoluteTime}")
                }
                result.diagnostics.forEach { appendLine("$unit !! ${it.render()}") }
            }
        }
    }

    private fun WorkoutParseResult.diagText(): String =
        if (diagnostics.isEmpty()) "" else "  !! " + diagnostics.joinToString(" ; ") { it.render() }

    private fun ParseDiagnostic.render(): String =
        "L$lineIndex ${reason.name}" + (original?.let { " $it->$replacement" } ?: "")

    private fun ParsedSetDTO.nameLine() =
        "name=$exerciseName mod=$modifier brand=$brand canonical=${exerciseIdentity.canonicalName}"

    private fun ParsedSetDTO.setLine() =
        "reps=$reps w=$weight $weightUnit dur=$durationSeconds uni=$isUnilateral"

    companion object {
        val NAME_CORPUS = listOf(
            "Bench press", "bench", "Incline db press", "incline bench", "decline bench", "close grip bench",
            "latpulldown rl", "lat pull", "latpulldown narrow", "wide grip pulldown", "ug pulldown",
            "leg extension rl", "plank", "lateral raise db", "lat raise", "side raise", "rear delt prime",
            "rear delt fly", "reverse fly", "tricep pushdown bar", "rope pushdown", "tricep extension",
            "tricep extension bar", "overhead extension", "skull crusher",
            "Squat", "front squat", "smith squat", "hack squat", "hacksquat", "reverse hacksquat", "pendulum",
            "sissy squat", "bulgarian split squat", "leg press", "cy leg press", "leg curl",
            "seated hamstring curl", "calve machine", "calf raise", "donkey calf raise", "toe raise",
            "Deadlift", "Romanian deadlift", "sumo deadlift", "deficit deadlift", "stiff leg deadlift",
            "good morning", "hip thrust", "bb hip thrust", "Hip thrust machine", "glute bridge", "glute kickback",
            "kickback", "rack pull", "rack pulls", "hip abduction", "hip adduction", "adductor", "abductor",
            "ad abductor", "lunges", "step up", "nordic curl",
            "pullups", "pull up", "chin up", "dips", "dip", "dips prime", "push ups", "crunches",
            "hanging leg raise", "leg raise", "legraise", "back extension", "hyperextension", "ab wheel",
            "russian twist", "muscle up",
            "cable fly", "chest fly", "pec fly", "machine fly", "fly", "butterfly", "buttferfli", "pec deck",
            "cable crossover", "pullover", "hs chest press", "chest press",
            "shoulder press", "military press", "overhead press", "arnold press", "at shoulder press",
            "face pull", "upright row", "shrugs", "shrug",
            "bicep curl", "biceps curl", "hammer curl", "preacher curl", "concentration curl", "dumbell curl",
            "db curl", "wrist curl", "ezbar curl",
            "t bar row", "tbar row", "diag rowing", "diagonal rowing", "seated row", "cable row",
            "barbell row", "bent over row", "bar rowing", "g80 row", "row", "wide row", "rowing",
            "treadmill", "bike", "cycle", "stairmaster", "stair master", "rowing machine", "elliptical",
            "farmer walk", "sled push", "benchpress", "squatt", "deadlfit", "latpulldwn", "tricpe pushdown",
            "incline dumbell press", "lat pulldwon", "curls",
        )

        val SET_HEADERS = listOf("Bench press", "Pullups", "Treadmill", "Plank")

        val SET_CORPUS = listOf(
            "10x 60", "10x60", "10 x 60", "10X 60", "10x 60kg", "10x 60 kg", "10x 60kgs", "8x 220 lb",
            "8x 220lbs", "15x 12.5", "8+4+4x 50", "10+3+2x", "8x", "12x 30 (1'00'')",
            "60s", "60 s", "45s 10", "30s 20kg", "30 S 20kg", "30s 12.5 lb", "10", "30",
            "x10 50", "x 10 50", "10 reps 50", "10 rep 50", "10reps", "100kg x5", "100 x5", "62,5",
            "10x 62,5", "10x 62.5", "5x 100 uni", "10x 60 (1'38'') 1'40''",
        )

        val WORKOUT_CORPUS = listOf(
            "push day" to """
                Bench press
                      10x 60 (1'38'')
                      8x 70 (2'51'')
                      8x (2'44'')

                incline db press
                      12x 26 (1'52'')
                      10x (2'31'')
            """.trimIndent(),
            "leg day" to """
                leg extension rl
                      12x 45 (1'05'')
                      10+3+2x (2'40'')

                plank
                      60s (1'30'')
                      45s 10 (2'10'')
            """.trimIndent(),
            "superset" to """
                lateral raise db
                      15x 12.5 (0'50'')
                      12x (2'05'')

                rear delt prime
                      15x 40 (0'40'')
                      15x (2'10'')

                tricep pushdown bar
                      12x 30 (1'00'')
            """.trimIndent(),
            "first set without weight" to """
                Bench press
                      8x
                      10x 60
                      8x
            """.trimIndent(),
            "units" to """
                Bench Press
                      5x 100 kg
                      8x 220 lb
                      8x
                Squat 0'00
                      5x 100
            """.trimIndent(),
        )
    }
}
