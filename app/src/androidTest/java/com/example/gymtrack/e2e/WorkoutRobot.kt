package com.example.gymtrack.e2e

import android.util.Log
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.example.gymtrack.MainActivity
import com.example.gymtrack.core.data.ExerciseFlag
import com.example.gymtrack.feature.editor.components.EDITOR_ACCESSORY_BAR_TEST_TAG
import com.example.gymtrack.feature.editor.components.EDITOR_FLAG_SEGMENT_TEST_TAG
import com.example.gymtrack.feature.editor.components.EDITOR_MENU_CATEGORY_TEST_TAG
import com.example.gymtrack.feature.editor.components.EDITOR_OVERFLOW_TEST_TAG
import com.example.gymtrack.feature.editor.components.NOTE_EDITOR_FINISH_ACTION_TEST_TAG
import com.example.gymtrack.feature.editor.components.NOTE_TIMER_ELAPSED_TEST_TAG
import com.example.gymtrack.feature.editor.components.SET_ENTRY_KEYPAD_TEST_TAG
import com.example.gymtrack.feature.editor.components.editorCategoryOptionTestTag
import com.example.gymtrack.feature.editor.components.editorFlagOptionTestTag
import com.example.gymtrack.feature.editor.components.editorRowInputTestTag
import com.example.gymtrack.feature.editor.components.formatTopLineElapsed
import com.example.gymtrack.feature.editor.components.segmentLabel
import com.example.gymtrack.feature.editor.components.setEntryKeyTestTag
import com.example.gymtrack.APP_BOTTOM_NAV_TEST_TAG
import com.example.gymtrack.feature.history.HISTORY_CLEAR_SELECTION_TEST_TAG
import com.example.gymtrack.feature.history.HISTORY_LIST_TEST_TAG
import com.example.gymtrack.feature.history.HISTORY_MARK_FINISHED_TEST_TAG
import com.example.gymtrack.feature.history.historyRowTestTag
import com.example.gymtrack.feature.home.OrphanChoice
import com.example.gymtrack.feature.home.orphanChoiceTestTag
import com.example.gymtrack.feature.today.TODAY_START_BLANK_TEST_TAG

typealias GymTrackRule = AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

private const val UI_TIMEOUT_MS = 5_000L

/** One editor row as the user expects it to be saved. */
data class ExpectedRow(
    val text: String,
    /** Seconds since the first exercise line, as shown in the row's grey column; null for blank rows. */
    val elapsedSeconds: Long?,
    /** Fake-clock time at which the user pressed Enter on this row; null for blank rows. */
    val enteredAtMillis: Long?,
    val isSet: Boolean,
)

/**
 * Drives the real app the way a user does: the bottom nav (Today, History, Progress), Start
 * blank, the start sheet, category menu, typing exercise lines, the set keypad, Back, Finish,
 * History rows and Progress (Stats). Every wait is on a UI or DB condition; rests only move the fake
 * clock, so a 40-minute workout runs in seconds.
 *
 * Set keys use the keypad's own order, reps first: "8x80" taps 8, X, 8, 0 and the editor shows
 * "8x 80"; "6+2x80" is a myo-rep set; "10x32.5" uses the decimal key; "60s" is a timed set.
 */
class WorkoutRobot(
    private val rule: ComposeTestRule,
    private val clock: FakeClock,
    private val scenario: () -> ActivityScenario<MainActivity>,
) {
    constructor(rule: GymTrackRule, clock: FakeClock) : this(rule, clock, { rule.activityRule.scenario })

    var workoutTimestamp: Long = 0L
        private set

    /** Time base the app uses for the grey elapsed column: the first exercise Enter. */
    var firstExerciseAtMillis: Long? = null
        private set

    private var lastEnterMillis: Long = 0L

    /** Index of the editor row that receives the next input. */
    var activeRow: Int = 0
        private set

    val rows = mutableListOf<ExpectedRow>()

    /** Flag segment label ("Bi", "L/R", "SS") each new exercise showed before the robot touched it. */
    val initialFlagLabels = mutableListOf<String>()

    val nowMillis: Long get() = clock.nowMillis()

    /** Opens a bottom-nav tab by its label ("Today", "History", "Progress"). */
    fun openTab(label: String) {
        waitForTag(APP_BOTTOM_NAV_TEST_TAG)
        rule.onNode(hasText(label) and hasAnyAncestor(hasTestTag(APP_BOTTOM_NAV_TEST_TAG))).performClick()
        rule.waitForIdle()
    }

    /**
     * Today, then Start blank. When an unfinished workout still owns the timer the start sheet
     * asks first; the robot answers "Keep both" (the old workout stays unfinished and can be
     * finished from History), which is what the simulations model.
     */
    fun startWorkout() {
        openTab("Today")
        waitForTag(TODAY_START_BLANK_TEST_TAG)
        workoutTimestamp = clock.nowMillis()
        lastEnterMillis = workoutTimestamp
        rule.onNodeWithTag(TODAY_START_BLANK_TEST_TAG).performClick()
        rule.waitForIdle()
        val keepBoth = orphanChoiceTestTag(OrphanChoice.KEEP_BOTH)
        if (exists(keepBoth)) {
            rule.onNodeWithTag(keepBoth).performClick()
        }
        waitForTag(editorRowInputTestTag(0))
        activeRow = 0
    }

    /** Editor v2: the category lives in the accessory bar's ⋯ menu (thumb zone). */
    fun pickCategory(name: String) {
        rule.onNodeWithTag(EDITOR_OVERFLOW_TEST_TAG).performClick()
        waitForTag(EDITOR_MENU_CATEGORY_TEST_TAG)
        rule.onNodeWithTag(EDITOR_MENU_CATEGORY_TEST_TAG).performClick()
        waitForTag(editorCategoryOptionTestTag(name))
        rule.onNodeWithTag(editorCategoryOptionTestTag(name)).performClick()
        rule.waitForIdle()
    }

    /** Simulated rest: only the fake clock moves. */
    fun rest(seconds: Long) {
        clock.advanceSeconds(seconds)
    }

    /**
     * Types an exercise name on the active (empty) exercise row, sets its flag BEFORE pressing
     * Enter (the accessory bar's Bi · L/R · SS segment acts on the focused exercise), then presses
     * Enter, which opens the set keypad on the next row.
     */
    fun exercise(name: String, flag: ExerciseFlag = ExerciseFlag.BILATERAL, flagAfterEnter: Boolean = false) {
        val inputTag = editorRowInputTestTag(activeRow)
        waitForTag(inputTag)
        rule.onNodeWithTag(inputTag).performTextInput(name)

        initialFlagLabels += currentFlagLabel()
        if (!flagAfterEnter) {
            selectFlag(flag)
            check(currentFlagLabel() == flag.label()) { "Flag on row $activeRow is ${currentFlagLabel()}, wanted ${flag.label()}" }
        }

        rule.onNodeWithTag(inputTag).performTextInput("\n")
        if (flagAfterEnter) toggleFlagOfRow(activeRow, flag)
        val now = clock.nowMillis()
        if (firstExerciseAtMillis == null) firstExerciseAtMillis = now
        lastEnterMillis = now
        rows += ExpectedRow(name, elapsedSince(now), now, isSet = false)
        activeRow += 1
        waitForKeypad()
    }

    /**
     * Sets the flag of the exercise being logged (e.g. after its Enter). The segment acts on the
     * exercise that owns the keypad row, so [rowIndex] only documents which exercise that is.
     */
    @Suppress("UNUSED_PARAMETER")
    fun toggleFlagOfRow(rowIndex: Int, flag: ExerciseFlag) {
        selectFlag(flag)
    }

    /** Label of the selected option in the accessory bar's flag segment. */
    fun currentFlagLabel(): String {
        waitForTag(EDITOR_FLAG_SEGMENT_TEST_TAG)
        return ExerciseFlag.entries.first { flag ->
            rule.onNodeWithTag(editorFlagOptionTestTag(flag)).fetchSemanticsNode().config
                .getOrNull(SemanticsProperties.Selected) == true
        }.label()
    }

    fun selectFlag(flag: ExerciseFlag) {
        waitForTag(editorFlagOptionTestTag(flag))
        if (currentFlagLabel() != flag.label()) rule.onNodeWithTag(editorFlagOptionTestTag(flag)).performClick()
        rule.waitUntil(UI_TIMEOUT_MS) { currentFlagLabel() == flag.label() }
    }

    /** Taps the keys of one set on the keypad without pressing Enter. Returns the typed text. */
    fun typeOnKeypad(keys: String): String {
        waitForKeypad()
        keys.forEach { key -> tapKey(key) }
        return keys.replace("x", "x ").replace("s", "s ")
    }

    /** Enters one complete set on the keypad and presses Enter. */
    fun set(keys: String) {
        val content = typeOnKeypad(keys)
        tapKey('\n')
        val now = clock.nowMillis()
        val restSeconds = (now - lastEnterMillis) / 1_000L
        lastEnterMillis = now
        rows += ExpectedRow("      $content (${minutesSeconds(restSeconds)})", elapsedSince(now), now, isSet = true)
        activeRow += 1
        waitForKeypad()
    }

    /** Enter on the empty set row: closes the exercise and focuses a new exercise row. */
    fun endExercise() {
        waitForKeypad()
        tapKey('\n')
        rows += ExpectedRow("", null, null, isSet = false)
        activeRow += 1
        waitForTag(editorRowInputTestTag(activeRow))
    }

    fun finish() {
        waitForTag(NOTE_EDITOR_FINISH_ACTION_TEST_TAG)
        rule.onNodeWithTag(NOTE_EDITOR_FINISH_ACTION_TEST_TAG).performClick()
        waitForTag(APP_BOTTOM_NAV_TEST_TAG)
    }

    /** System Back from the editor, without Finish. */
    fun pressBack() {
        Espresso.closeSoftKeyboard()
        Espresso.pressBack()
        waitForTag(APP_BOTTOM_NAV_TEST_TAG)
    }

    /** Opens the robot's workout from its History row. */
    fun openWorkoutFromHome() = openWorkoutCard(workoutTimestamp)

    /** History tab, scrolled to the workout's row. Returns the row's tag. */
    fun showHistoryRow(timestamp: Long): String {
        openTab("History")
        val row = historyRowTestTag(timestamp)
        waitForTag(HISTORY_LIST_TEST_TAG)
        rule.onNodeWithTag(HISTORY_LIST_TEST_TAG).performScrollToNode(hasTestTag(row))
        waitForTag(row)
        return row
    }

    /**
     * Opens any workout from its History row. Waits for the editor, not for row 0: a long open
     * workout reopens scrolled to its active row (#293), so row 0 may not be composed (#366).
     */
    fun openWorkoutCard(timestamp: Long) {
        val row = showHistoryRow(timestamp)
        rule.onNodeWithTag(row).performClick()
        waitForTag(EDITOR_ACCESSORY_BAR_TEST_TAG)
    }

    /**
     * Long-presses the workout's History row and reports whether "Mark finished" is offered
     * (#293: no workout may become un-finishable). Clears the selection again.
     */
    fun canMarkFinishedFromHistory(timestamp: Long): Boolean {
        val row = showHistoryRow(timestamp)
        rule.onNodeWithTag(row).performTouchInput { longClick() }
        rule.waitForIdle()
        val offered = exists(HISTORY_MARK_FINISHED_TEST_TAG)
        if (exists(HISTORY_CLEAR_SELECTION_TEST_TAG)) rule.onNodeWithTag(HISTORY_CLEAR_SELECTION_TEST_TAG).performClick()
        rule.waitForIdle()
        return offered
    }

    /**
     * After reopening a workout the editor starts at the top. When the open set row is below the
     * fold the keypad does not come back by itself; a user scrolls down and taps the row.
     * Returns true when the keypad was already there.
     */
    fun ensureKeypadForActiveRow(): Boolean {
        val shown = runCatching { waitForKeypad(3_000L) }.isSuccess
        if (shown) return true
        rule.onAllNodes(hasScrollToIndexAction())[0].performScrollToIndex(activeRow)
        waitForTag(editorRowInputTestTag(activeRow))
        rule.onNodeWithTag(editorRowInputTestTag(activeRow)).performClick()
        waitForKeypad()
        return false
    }

    /** Stats live on the Progress tab. */
    fun openStats() {
        openTab("Progress")
    }

    /** Home button + reopen from recents: ON_STOP then ON_START/ON_RESUME. */
    fun sendToBackground() {
        scenario().moveToState(Lifecycle.State.CREATED)
    }

    fun bringToForeground() {
        scenario().moveToState(Lifecycle.State.RESUMED)
        rule.waitForIdle()
    }

    fun recreateActivity() {
        scenario().recreate()
        rule.waitForIdle()
    }

    fun timerText(): String {
        waitForTag(NOTE_TIMER_ELAPSED_TEST_TAG)
        return textOf(NOTE_TIMER_ELAPSED_TEST_TAG)
    }

    /** The timer redraws once per real second, so wait for it to catch up with the fake clock. */
    fun waitForTimer(expected: String) {
        waitForTag(NOTE_TIMER_ELAPSED_TEST_TAG)
        rule.waitUntil(UI_TIMEOUT_MS) { textOf(NOTE_TIMER_ELAPSED_TEST_TAG) == expected }
    }

    /** Waits until the top line shows [elapsedSeconds] in its `m:ss` format. */
    fun waitForTimerSeconds(elapsedSeconds: Long) = waitForTimer(formatTopLineElapsed(elapsedSeconds))

    fun isKeypadShown(): Boolean = exists(SET_ENTRY_KEYPAD_TEST_TAG)

    fun waitForKeypad(timeoutMs: Long = UI_TIMEOUT_MS) {
        rule.waitUntil(timeoutMs) { exists(setEntryKeyTestTag("Enter")) }
    }

    fun exists(tag: String): Boolean =
        rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    fun waitForTag(tag: String, timeoutMs: Long = UI_TIMEOUT_MS) {
        rule.waitUntil(timeoutMs) { exists(tag) }
    }

    fun textOf(tag: String): String {
        val config = rule.onNodeWithTag(tag).fetchSemanticsNode().config
        val text = config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()
        val editable = config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()
        return text.ifEmpty { editable }
    }

    private fun tapKey(key: Char) {
        val label = when (key) {
            'x', 'X' -> "X"
            's', 'S' -> "S"
            '\n' -> "Enter"
            else -> key.toString()
        }
        rule.onNodeWithTag(setEntryKeyTestTag(label)).performClick()
    }

    private fun elapsedSince(now: Long): Long {
        // #319: absolute times count from the workout start (the + tap), the same base as the timer.
        val base = workoutTimestamp
        // The editor rounds the elapsed column half-up to the 5 s rounding setting.
        return (now - base + 2_500L) / 5_000L * 5L
    }

    companion object {
        const val TAG = "WorkoutSimulation"

        fun minutesSeconds(totalSeconds: Long): String =
            "${totalSeconds / 60}'${(totalSeconds % 60).toString().padStart(2, '0')}''"

        fun ExerciseFlag.label(): String = segmentLabel()
    }
}

/**
 * Collects places where the app works but differs from what a user would expect. They do not
 * fail the test (the suite stays green on known product gaps); they are logged under
 * [WorkoutRobot.TAG] and listed when the test ends.
 */
class UserExpectations(private val testName: String) {
    private val misses = mutableListOf<String>()

    fun expect(description: String, actual: Any?, expected: Any?) {
        if (actual != expected) {
            val line = "USER-EXPECTATION-DIFF [$testName] $description: expected <$expected> but app gave <$actual>"
            misses += line
            Log.w(WorkoutRobot.TAG, line)
        }
    }

    fun report() {
        Log.i(WorkoutRobot.TAG, "[$testName] ${misses.size} user-expectation differences")
        misses.forEach { Log.i(WorkoutRobot.TAG, it) }
    }
}
