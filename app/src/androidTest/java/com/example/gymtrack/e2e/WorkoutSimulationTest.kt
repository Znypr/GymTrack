package com.example.gymtrack.e2e

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.gymtrack.MainActivity
import com.example.gymtrack.core.data.ExerciseFlag
import com.example.gymtrack.e2e.WorkoutRobot.Companion.minutesSeconds
import com.example.gymtrack.ACTIVE_WORKOUT_BAR_TEST_TAG
import com.example.gymtrack.feature.editor.components.NOTE_EDITOR_FINISH_ACTION_TEST_TAG
import com.example.gymtrack.feature.editor.components.editorRowInputTestTag
import com.example.gymtrack.feature.stats.statsBadgeValueTestTag
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestName
import org.junit.runner.RunWith

/**
 * Drives the real GymTrack UI through a push day in compressed time. Rests move a fake clock,
 * so a 25-minute session runs in seconds.
 *
 * Hard assertions cover documented behaviour (docs/TIMER.md, docs/EDITOR_SAVE_PIPELINE.md,
 * canonical schema). Behaviour that works but differs from what a user would expect is recorded
 * with [UserExpectations] and logged under the `WorkoutSimulation` logcat tag; `probe_*` tests
 * exist only to record those differences.
 *
 * Emulator only: the rule wipes the debug app's data and skips itself on a physical device.
 */
@RunWith(AndroidJUnit4::class)
class WorkoutSimulationTest {
    private val appState = SimulatedAppStateRule()
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(appState).around(composeRule)

    @get:Rule
    val testName = TestName()

    private val robot by lazy { WorkoutRobot(composeRule, appState.clock) }
    private val expectations by lazy { UserExpectations(testName.methodName) }

    @After
    fun reportUserExpectations() = expectations.report()

    @Test
    fun happyPath_pushDayIsSavedWithSimulatedTimes() {
        robot.startWorkout()
        robot.pickCategory("Push")

        robot.exercise("Bench Press")
        robot.rest(120); robot.set("8x80")
        robot.rest(150); robot.set("8x80")
        robot.rest(180); robot.set("6+2x80") // myo-reps: 8 reps
        robot.endExercise()

        robot.rest(90); robot.exercise("Incline DB Press")
        robot.rest(30); robot.set("10x30")
        robot.rest(120); robot.set("10x32.5") // decimal key
        robot.endExercise()

        robot.rest(90); robot.exercise("Lateral Raise", ExerciseFlag.UNILATERAL)
        robot.rest(30); robot.set("12x10")
        robot.rest(90); robot.set("12x10")
        robot.endExercise()

        // Superset: both exercises flagged SS, no rest between them.
        robot.rest(90); robot.exercise("Cable Fly", ExerciseFlag.SUPERSET)
        robot.rest(30); robot.set("12x20")
        robot.endExercise()
        robot.exercise("Triceps Pushdown", ExerciseFlag.SUPERSET)
        robot.rest(30); robot.set("12x25")
        robot.endExercise()

        robot.rest(120); robot.exercise("Plank")
        robot.rest(30); robot.set("60s") // timed set

        robot.waitForTimerSeconds((robot.nowMillis - robot.workoutTimestamp) / 1_000L)

        robot.rest(120) // stretch, then Finish
        val timerAtFinishSeconds = (robot.nowMillis - robot.workoutTimestamp) / 1_000L
        robot.finish()

        val saved = SavedWorkout.awaitSaved(appState.context, robot.workoutTimestamp) {
            it.workout?.status == "COMPLETED"
        }

        // Editor note: visible rows and the grey elapsed column.
        assertRowsMatch(saved)
        assertEquals("Push", saved.note?.categoryName)

        // Canonical tables.
        val workout = assertNotNull(saved.workout)
        assertEquals("COMPLETED", workout.status)
        assertEquals(robot.workoutTimestamp, workout.startedAt)
        // #323: the workout ends at Finish, not at the last set.
        assertEquals(robot.workoutTimestamp + timerAtFinishSeconds * 1_000L, workout.endedAt)
        assertEquals(
            listOf("BILATERAL", "BILATERAL", "UNILATERAL", "SUPERSET", "SUPERSET", "BILATERAL"),
            saved.exercises.map { it.mode },
        )
        assertEquals(
            robot.rows.filter { !it.isSet && it.elapsedSeconds != null }.map { it.elapsedSeconds!!.toInt() },
            saved.exercises.map { it.startedAtOffsetSeconds },
        )
        assertEquals(
            robot.rows.filter { it.isSet }.map { it.elapsedSeconds!!.toInt() },
            saved.exercises.flatMap { saved.setsOf(it) }.map { it.performedAtOffsetSeconds },
        )
        val perExercise = saved.exercises.map { exercise ->
            saved.setsOf(exercise).map { Triple(it.repetitions, it.weight, it.durationSeconds) }
        }
        assertEquals(
            listOf(
                listOf(Triple(8, 80.0, null), Triple(8, 80.0, null), Triple(8, 80.0, null)),
                listOf(Triple(10, 30.0, null), Triple(10, 32.5, null)),
                listOf(Triple(12, 10.0, null), Triple(12, 10.0, null)),
                listOf(Triple(12, 20.0, null)),
                listOf(Triple(12, 25.0, null)),
                listOf(Triple(null, null, 60)),
            ),
            perExercise,
        )

        // Legacy compatibility rows used by Stats charts.
        assertEquals(10, saved.legacySets.size)
        assertTrue(saved.legacySets.all { it.weightUnit == "KG" })
        assertEquals(2, saved.legacySets.count { it.isUnilateral })

        // Timer stopped by Finish.
        assertNull(saved.timer.activeNoteTimestamp)

        // #323: History row duration = the timer at Finish, in minutes.
        val card = robot.showHistoryRow(robot.workoutTimestamp)
        val cardText = robot.textOf(card)
        assertTrue(cardText, cardText.contains("${timerAtFinishSeconds / 60} min"))

        // User expectations that the app does not meet (recorded, not failed).
        expectations.expect(
            "every new exercise row starts as Bi (bilateral)",
            robot.initialFlagLabels,
            List(robot.initialFlagLabels.size) { "Bi" },
        )
        expectations.expect(
            "canonical workout_sets.weight_unit stores the unit used (docs/WEIGHT_UNITS.md)",
            saved.sets.filter { (it.weight ?: 0.0) > 0.0 }.map { it.weightUnit }.distinct(),
            listOf("KILOGRAM"),
        )
        expectations.expect(
            "legacy sets rows carry each set's own elapsed time",
            saved.legacySets.map { it.absoluteTime },
            robot.rows.filter { it.isSet }.map { minutesSeconds(it.elapsedSeconds!!) },
        )
        val names = saved.exercises.map { saved.exerciseName(it).lowercase() }
        listOf("bench", "incline", "lateral", "fly", "pushdown", "plank").forEachIndexed { index, word ->
            expectations.expect("exercise ${index + 1} keeps the typed name ($word)", names.getOrNull(index)?.contains(word), true)
        }

        // Stats counts the finished workout.
        robot.openStats()
        robot.waitForTag(statsBadgeValueTestTag("Workouts"))
        composeRule.waitUntil(10_000L) { robot.textOf(statsBadgeValueTestTag("Workouts")) == "1" }
        expectations.expect("Stats favourite category", robot.textOf(statsBadgeValueTestTag("Favorite")), "Push")
    }

    @Test
    fun interruption_backgroundThenBackWithoutFinishThenResumeFromHome() {
        robot.startWorkout()
        robot.exercise("Bench Press")
        robot.rest(120); robot.set("8x80")

        // A category edit is only persisted by the next draft save; ON_STOP must be that save.
        robot.pickCategory("Legs")
        robot.sendToBackground()
        val afterStop = SavedWorkout.awaitSaved(appState.context, robot.workoutTimestamp) {
            it.note?.categoryName == "Legs"
        }
        assertEquals("Legs", afterStop.note?.categoryName)
        robot.rest(60) // phone locked during the rest
        robot.bringToForeground()

        robot.waitForTimerSeconds((robot.nowMillis - robot.workoutTimestamp) / 1_000L)
        robot.waitForKeypad()
        robot.rest(90); robot.set("8x80")

        // Back without Finish: workout stays active and the timer keeps running (docs/TIMER.md).
        robot.pressBack()
        val afterBack = SavedWorkout.awaitSaved(appState.context, robot.workoutTimestamp) {
            it.noteLines.any { line -> line.contains("8x 80 (2'30'')") }
        }
        assertEquals(robot.workoutTimestamp, afterBack.timer.activeNoteTimestamp)
        assertTrue(afterBack.timer.isRunning)
        // #293: every tab shows the running workout in the active-workout bar.
        expectations.expect(
            "the active-workout bar shows the running workout (#293)",
            runCatching { robot.waitForTag(ACTIVE_WORKOUT_BAR_TEST_TAG) }.isSuccess,
            true,
        )
        expectations.expect(
            "Back finalizes derived set rows (docs/EDITOR_SAVE_PIPELINE.md save-trigger table)",
            afterBack.legacySets.size,
            2,
        )

        robot.rest(120) // checking last week's numbers on Home
        robot.openWorkoutFromHome()
        robot.waitForTag(NOTE_EDITOR_FINISH_ACTION_TEST_TAG)
        robot.waitForTimerSeconds((robot.nowMillis - robot.workoutTimestamp) / 1_000L)
        robot.waitForKeypad()
        robot.set("8x80")
        robot.finish()

        val saved = SavedWorkout.awaitSaved(appState.context, robot.workoutTimestamp) {
            it.workout?.status == "COMPLETED"
        }
        assertRowsMatch(saved)
        assertEquals("Legs", saved.note?.categoryName)
        assertNull(saved.timer.activeNoteTimestamp)
        assertEquals(listOf(120, 270, 390), saved.sets.map { it.performedAtOffsetSeconds })
        assertEquals(3, saved.legacySets.size)
        assertEquals(1, SavedWorkout.noteCount(appState.context))
    }

    @Test
    fun recreation_activityRecreatedMidWorkoutKeepsDraftTimerAndKeypad() {
        robot.startWorkout()
        robot.exercise("Bench Press")
        robot.rest(120); robot.set("8x80")
        robot.rest(60)

        robot.recreateActivity()

        robot.waitForTag(editorRowInputTestTag(0))
        robot.waitForTimerSeconds((robot.nowMillis - robot.workoutTimestamp) / 1_000L)
        robot.waitForKeypad()

        robot.rest(90); robot.set("8x80") // rest measured across the recreation: 2'30''
        robot.rest(150); robot.set("6+2x80")
        robot.finish()

        val saved = SavedWorkout.awaitSaved(appState.context, robot.workoutTimestamp) {
            it.workout?.status == "COMPLETED"
        }
        assertRowsMatch(saved)
        assertNull(saved.timer.activeNoteTimestamp)
        assertEquals(listOf(120, 270, 420), saved.sets.map { it.performedAtOffsetSeconds })
    }

    /** Records what happens to a half-typed set when the phone is locked mid-entry. */
    @Test
    fun probe_partialSetAcrossBackground() {
        robot.startWorkout()
        robot.exercise("Bench Press")
        robot.rest(120); robot.set("8x80")
        robot.rest(150)
        robot.typeOnKeypad("8x8") // weight half typed

        robot.sendToBackground()
        val afterStop = SavedWorkout.awaitSaved(appState.context, robot.workoutTimestamp) {
            it.noteLines.any { line -> line.trim() == "8x 8" }
        }
        robot.bringToForeground()

        expectations.expect("half-typed set is kept in the draft", afterStop.noteLines.getOrNull(2)?.trim(), "8x 8")
        expectations.expect(
            "half-typed set gets no elapsed time until Enter",
            afterStop.elapsedColumn.getOrNull(2),
            "",
        )
        expectations.expect("keypad is still open for the half-typed set after returning", robot.isKeypadShown(), true)
    }

    /** Records what the flag toggle does when the user sets it after the exercise's Enter. */
    @Test
    fun probe_flagToggledAfterExerciseEnter() {
        robot.startWorkout()
        robot.exercise("Lateral Raise")
        robot.toggleFlagOfRow(0, ExerciseFlag.UNILATERAL)
        robot.rest(60); robot.set("12x10")
        robot.rest(90); robot.set("12x10")
        robot.finish()

        val saved = SavedWorkout.awaitSaved(appState.context, robot.workoutTimestamp) {
            it.workout?.status == "COMPLETED"
        }
        assertEquals(listOf("UNILATERAL"), saved.exercises.map { it.mode })
        expectations.expect(
            "sets of an exercise toggled to Uni are saved as unilateral",
            saved.legacySets.map { it.isUnilateral },
            listOf(true, true),
        )
    }

    /** Records how times line up when the first exercise is typed a while after tapping +. */
    @Test
    fun probe_firstExerciseTypedLate() {
        robot.startWorkout()
        robot.pickCategory("Push")
        robot.rest(90) // walk to the bench
        robot.exercise("Bench Press")
        robot.rest(120); robot.set("8x80")
        robot.finish()

        val saved = SavedWorkout.awaitSaved(appState.context, robot.workoutTimestamp) {
            it.workout?.status == "COMPLETED"
        }
        assertRowsMatch(saved)
        val set = robot.rows.first { it.isSet }
        val workout = assertNotNull(saved.workout)
        expectations.expect(
            "canonical set time (started_at + offset) equals the moment the set was entered",
            workout.startedAt + (saved.sets.single().performedAtOffsetSeconds ?: 0) * 1_000L,
            set.enteredAtMillis,
        )
        expectations.expect(
            "grey elapsed column uses the same start as the workout timer",
            saved.elapsedColumn.getOrNull(1),
            minutesSeconds((set.enteredAtMillis!! - robot.workoutTimestamp) / 1_000L),
        )
    }

    /** #328 + #330: a finished workout opens at its top, keypad closed until a set is tapped. */
    @Test
    fun history_finishedWorkoutOpensAtTopWithKeypadClosed() {
        robot.startWorkout()
        robot.exercise("Bench Press")
        robot.rest(120); robot.set("8x80")
        robot.rest(120); robot.set("8x80")
        robot.finish()
        SavedWorkout.awaitSaved(appState.context, robot.workoutTimestamp) { it.workout?.status == "COMPLETED" }

        robot.openWorkoutFromHome()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(editorRowInputTestTag(0)).assertIsDisplayed()
        assertTrue("keypad stays closed on a past workout", !robot.isKeypadShown())

        // Tapping a committed set edits it on the keypad, never the IME.
        composeRule.onNodeWithTag(editorRowInputTestTag(1)).performClick()
        robot.waitForKeypad()
    }

    private fun assertRowsMatch(saved: SavedWorkout) {
        val expected = robot.rows
        val lines = saved.noteLines
        assertEquals(expected.map { it.text }, lines.take(expected.size))
        assertTrue("Only blank rows may follow the last entry: $lines", lines.drop(expected.size).all(String::isBlank))
        assertEquals(
            expected.map { row -> row.elapsedSeconds?.let(::minutesSeconds).orEmpty() },
            saved.elapsedColumn.take(expected.size),
        )
    }

    private fun <T> assertNotNull(value: T?): T {
        org.junit.Assert.assertNotNull(value)
        return value!!
    }
}
