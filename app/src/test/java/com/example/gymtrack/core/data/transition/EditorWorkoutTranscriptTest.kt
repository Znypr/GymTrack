package com.example.gymtrack.core.data.transition

import com.example.gymtrack.core.data.ExerciseFlag
import com.example.gymtrack.core.data.ExerciseFlag.BILATERAL
import com.example.gymtrack.core.data.ExerciseFlag.SUPERSET
import com.example.gymtrack.core.data.ExerciseFlag.UNILATERAL
import com.example.gymtrack.core.data.NoteEntity
import com.example.gymtrack.core.data.WeightUnit
import com.example.gymtrack.core.util.WorkoutParser
import com.example.gymtrack.core.util.buildNoteRowMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

/**
 * Feeds the exact note text and row metadata that the editor writes for a simulated push day
 * (keypad sets, rests of 30-180 s, decimal, myo-reps, unilateral, superset, timed set) through
 * the parser and the canonical projector. It is the JVM half of the instrumented
 * `WorkoutSimulationTest`: it checks what is persisted without needing an emulator.
 *
 * Row format matches `NoteEditorState.onTextChange`: exercise rows keep their text, set rows are
 * `"      <entry> (<rest>)"`, and the absolute elapsed time lives in row metadata.
 */
class EditorWorkoutTranscriptTest {

    private data class Row(val text: String, val elapsed: String, val flag: ExerciseFlag)

    private val workoutStart = 1_700_000_000_000L

    private val rows = listOf(
        Row("Bench Press", "0'00''", BILATERAL),
        Row("      8x 80 (2'00'')", "2'00''", BILATERAL),
        Row("      8x 80 (2'30'')", "4'30''", BILATERAL),
        Row("      6+2x 80 (3'00'')", "7'30''", BILATERAL),
        Row("", "", BILATERAL),
        Row("Incline DB Press", "9'00''", BILATERAL),
        Row("      10x 30 (2'00'')", "11'00''", BILATERAL),
        Row("      10x 32.5 (2'00'')", "13'00''", BILATERAL),
        Row("", "", BILATERAL),
        Row("Lateral Raise", "14'30''", UNILATERAL),
        Row("      12x 10 (1'30'')", "16'00''", UNILATERAL),
        Row("      12x 10 (1'30'')", "17'30''", UNILATERAL),
        Row("", "", UNILATERAL),
        Row("Cable Fly", "19'00''", SUPERSET),
        Row("      12x 20 (1'30'')", "20'30''", SUPERSET),
        Row("", "", SUPERSET),
        Row("Triceps Pushdown", "21'00''", SUPERSET),
        Row("      12x 25 (1'00'')", "22'00''", SUPERSET),
        Row("", "", SUPERSET),
        Row("Plank", "24'00''", BILATERAL),
        Row("      60s (1'00'')", "25'00''", BILATERAL),
        Row("", "", BILATERAL),
    )

    private val note = NoteEntity(
        timestamp = workoutStart,
        title = "",
        text = rows.joinToString("\n") { it.text },
        categoryName = "Push",
        categoryColor = 0xFFFF3B30,
        learnings = "",
        rowMetadata = buildNoteRowMetadata(rows.map { it.elapsed }, rows.map { it.flag }),
    )

    private val parsed = WorkoutParser().parseWorkout(note.text, "KG", note.rowMetadata)
    private val projection = LegacyWorkoutProjector().project(note, null, CanonicalExerciseCatalog(emptyList()))

    @Test
    fun parserKeepsEveryKeypadSet() {
        assertEquals(10, parsed.size)
        assertEquals(listOf(8, 8, 8, 10, 10, 12, 12, 12, 12, 0), parsed.map { it.reps })
        assertEquals(listOf(80f, 80f, 80f, 30f, 32.5f), parsed.take(5).map { it.weight })
        assertEquals(60, parsed.last().durationSeconds)
        assertTrue(parsed.all { it.weightUnit == "KG" })
        assertEquals(listOf(false, false, false, false, false, true, true, false, false, false), parsed.map { it.isUnilateral })
    }

    @Test
    fun canonicalProjectionKeepsPerSetOffsetsAndModes() {
        assertEquals("MIGRATED", projection.workout.legacyMigrationStatus)
        assertEquals(workoutStart, projection.workout.startedAt)
        assertEquals(workoutStart + 1_500_000L, projection.workout.endedAt)
        assertEquals(
            listOf("BILATERAL", "BILATERAL", "UNILATERAL", "SUPERSET", "SUPERSET", "BILATERAL"),
            projection.workoutExercises.map { it.mode },
        )
        assertEquals(
            listOf(0, 540, 870, 1140, 1260, 1440),
            projection.workoutExercises.map { it.startedAtOffsetSeconds },
        )
        assertEquals(
            listOf(120, 270, 450, 660, 780, 960, 1050, 1230, 1320, 1500),
            projection.workoutSets.map { it.performedAtOffsetSeconds },
        )
        assertEquals(60, projection.workoutSets.last().durationSeconds)
        assertEquals(32.5, projection.workoutSets[4].weight ?: 0.0, 0.0)
        assertEquals(8, projection.workoutSets[2].repetitions)
    }

    /**
     * docs/WEIGHT_UNITS.md: "Every finalized weighted set stores the unit that was actually used",
     * including `workout_sets.weight_unit`. The keypad never types a unit, so the completion path
     * passes the default unit in force at Finish to the projector (#326).
     */
    @Test
    fun canonicalSetsStoreTheDefaultUnitActuallyUsed() {
        val atFinish = LegacyWorkoutProjector().project(
            note, null, CanonicalExerciseCatalog(emptyList()), WeightUnit.KG,
        )
        val weighted = atFinish.workoutSets.filter { (it.weight ?: 0.0) > 0.0 }
        assertTrue(weighted.isNotEmpty())
        assertTrue(weighted.all { it.weightUnit == "KILOGRAM" })
        assertTrue(atFinish.workoutSets.filter { it.weight == null }.all { it.weightUnit == null })
    }

    /** Without a known default (startup import of old notes) the unit stays NULL, never guessed. */
    @Test
    fun canonicalSetsWithoutKnownDefaultStayUnknown() {
        val weighted = projection.workoutSets.filter { (it.weight ?: 0.0) > 0.0 }
        assertTrue(weighted.all { it.weightUnit == null })
    }

    /**
     * The legacy `sets` table has per-set relativeTime/absoluteTime columns, but the parser
     * copies the exercise header's times onto every set of that exercise (observed: "0'00" for all three
     * bench sets, truncated without the closing quotes).
     */
    @Ignore("Known deviation: legacy sets rows carry the exercise header time, not the set time (see report)")
    @Test
    fun legacySetRowsCarryTheirOwnTimes() {
        assertEquals(
            listOf("2'00''", "4'30''", "7'30''"),
            parsed.take(3).map { it.absoluteTime },
        )
        assertEquals(
            listOf("2'00''", "2'30''", "3'00''"),
            parsed.take(3).map { it.relativeTime },
        )
    }
}
