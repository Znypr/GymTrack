package com.example.gymtrack.e2e

import android.util.Log
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.gymtrack.MainActivity
import com.example.gymtrack.core.data.Category
import com.example.gymtrack.core.data.NoteDatabase
import com.example.gymtrack.core.data.SettingsStore
import com.example.gymtrack.core.data.VERSION_8_SCHEMA
import com.example.gymtrack.core.data.WeightUnit
import com.example.gymtrack.core.data.createLegacyDatabase
import com.example.gymtrack.core.data.deleteMigrationTestDatabase
import com.example.gymtrack.core.data.openMigratedDatabase
import com.example.gymtrack.core.timer.NoteTimerStore
import com.example.gymtrack.feature.editor.components.NOTE_EDITOR_FINISH_ACTION_TEST_TAG
import com.example.gymtrack.feature.home.HOME_OPEN_STATS_TEST_TAG
import com.example.gymtrack.feature.stats.statsBadgeValueTestTag
import com.example.gymtrack.simulation.EdgeTag
import com.example.gymtrack.simulation.EditorSession
import com.example.gymtrack.simulation.FinishMode
import com.example.gymtrack.simulation.Severity
import com.example.gymtrack.simulation.SimEvent
import com.example.gymtrack.simulation.SimEventType
import com.example.gymtrack.simulation.SimWorkout
import com.example.gymtrack.simulation.WorkoutHistoryGenerator
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Months of daily use on a real device in minutes. A seeded history (see
 * `WorkoutHistoryGenerator`) runs through the app's own save paths with a fake clock; a sample
 * of days is typed through the real editor UI. Findings go to logcat (tag `WorkoutSimulation`)
 * and to `/sdcard/Android/data/app.znypr.gymtrack.debug/files/simulation/<test>.md`.
 *
 * Instrumentation arguments: `simSeed` (default 11), `simMonths` (default 12), `simUiEvery`
 * (default 25: every Nth workout through the UI, plus edge days).
 *
 * Emulator only: [SimulatedAppStateRule] wipes the debug app's data and skips on phones.
 * Findings never fail a test; broken harness steps do.
 */
@RunWith(AndroidJUnit4::class)
class LongHorizonAppSimulationTest {
    private val appState = SimulatedAppStateRule()
    private val composeRule = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(appState).around(composeRule)

    private val args = InstrumentationRegistry.getArguments()
    private val seed = args.getString("simSeed")?.toLongOrNull() ?: 11L
    private val months = args.getString("simMonths")?.toIntOrNull() ?: 12
    private val uiEvery = args.getString("simUiEvery")?.toIntOrNull() ?: 25

    @Test
    fun repositoryHistory_monthsOfUseWithRelaunchBackupAndEdits() {
        val history = WorkoutHistoryGenerator(seed, months).generate()
        val driver = DeviceHistoryDriver(appState.context, NoteDatabase.getDatabase(appState.context), appState.clock, history)
        driver.run()
        val report = driver.writeReport("repository-history-seed$seed")
        assertEquals(history.workouts.size, driver.sessions.size)
        assertTrue(report.exists())
    }

    @Test
    fun migratedDatabase_oldSchemaAtMonthZeroThenThreeMonths() {
        val context = appState.context
        context.createLegacyDatabase(
            version = 8,
            schema = VERSION_8_SCHEMA,
            inserts = listOf(
                "INSERT INTO notes(timestamp, title, text, categoryName, categoryColor, learnings) VALUES " +
                    "(1756713600000, '', 'Bench Press\n    8x 70\n    8x 70', 'Push', ${0xFFFF3B30}, 'old app')",
                "INSERT INTO exercises(exerciseId, name, parentId, muscleGroup, aliases) VALUES (1, 'Bench Press', NULL, 'Chest', 'bench')",
                "INSERT INTO sets(setId, workoutId, exerciseId, weight, reps, isUnilateral, modifier, brand, relativeTime, absoluteTime) VALUES " +
                    "(1, 1756713600000, 1, 70, 8, 0, NULL, NULL, '02:00', '18:02'), (2, 1756713600000, 1, 70, 8, 0, NULL, NULL, '02:30', '18:05')",
            ),
        )
        val database = context.openMigratedDatabase()
        try {
            val history = WorkoutHistoryGenerator(seed, months = 3).generate()
            val driver = DeviceHistoryDriver(context, database, appState.clock, history, preexistingWorkouts = 1)
            driver.run(perfDays = setOf(91))
            driver.writeReport("migrated-history-seed$seed")
            runBlocking {
                val oldNote = database.noteDao().getById(1756713600000L)
                assertTrue("pre-migration workout lost", oldNote != null)
                val oldSets = database.setDao().getAllForBackup().filter { it.workoutId == 1756713600000L }
                assertEquals("pre-migration sets", 2, oldSets.size)
                assertTrue(oldSets.all { it.weightUnit == WeightUnit.KG.storageValue })
            }
        } finally {
            database.close()
            context.deleteMigrationTestDatabase()
        }
    }

    @Test
    fun uiSampleDays_realEditorOnEveryNthWorkoutAndEdgeDays() {
        val context = appState.context
        val history = WorkoutHistoryGenerator(seed, months).generate()
        val driver = DeviceHistoryDriver(context, NoteDatabase.getDatabase(context), appState.clock, history)
        val uiTags = setOf(
            EdgeTag.DST_BACK, EdgeTag.DST_FORWARD, EdgeTag.YEAR_BOUNDARY, EdgeTag.KILLED_MID_SET,
            EdgeTag.BACK_THEN_FINISH_NEXT_MORNING, EdgeTag.BACK_NEVER_FINISHED, EdgeTag.NEXT_DAY_WHILE_OLD_ACTIVE,
            EdgeTag.SAME_DAY_SECOND, EdgeTag.LATE_FIRST_EXERCISE,
        )
        var relaunches = 0
        driver.run(
            onEvent = { event ->
                if (event.type == SimEventType.RELAUNCH && relaunches++ % 4 == 0) settingsSurviveRelaunch(driver, event)
                if (event.type == SimEventType.EDIT_OLD_WORKOUT) Log.i(WorkoutRobot.TAG, "edit-old handled by driver on day ${event.day}")
            },
            uiDriver = { workout, session ->
                val sampled = workout.index % uiEvery == 0 || workout.tags.any { it in uiTags }
                sampled && driveInUi(driver, workout, session)
            },
        )
        statsScreenShowsWorkoutCount(driver)
        driver.writeReport("ui-sample-seed$seed")
    }

    /** Types one generated workout through the real editor, keypad and Home. */
    /** Returns false when the UI could not finish the day; the driver then saves it the normal way. */
    private fun driveInUi(driver: DeviceHistoryDriver, workout: SimWorkout, session: EditorSession): Boolean {
        val clock = appState.clock
        clock.setTo(workout.startMillis)
        var scenario = ActivityScenario.launch(MainActivity::class.java)
        val robot = WorkoutRobot(composeRule, clock) { scenario }
        try {
            robot.startWorkout()
            robot.pickCategory(workout.category.name)
            workout.exercises.forEachIndexed { position, exercise ->
                if (position > 0) robot.endExercise()
                robot.rest(exercise.walkSeconds)
                robot.exercise(exercise.typedName, exercise.flag, exercise.flagAfterEnter)
                exercise.sets.forEach { set ->
                    robot.rest(set.restBeforeSeconds)
                    robot.set(set.keys)
                }
            }
            when {
                workout.partialKeys != null -> {
                    robot.rest(90)
                    robot.typeOnKeypad(workout.partialKeys)
                    robot.sendToBackground()
                    // Process death stand-in: the activity is destroyed while stopped; the user
                    // reopens the app (Home) and the workout from its card.
                    scenario.close()
                    scenario = ActivityScenario.launch(MainActivity::class.java)
                    robot.openWorkoutFromHome()
                    robot.rest(workout.finishDelaySeconds)
                    robot.finish()
                }
                workout.finishMode == FinishMode.BACK_THEN_FINISH_NEXT_MORNING -> {
                    robot.pressBack()
                    scenario.close()
                    robot.rest(workout.lateSet!!.restBeforeSeconds)
                    scenario = ActivityScenario.launch(MainActivity::class.java)
                    robot.openWorkoutFromHome()
                    if (!robot.ensureKeypadForActiveRow()) {
                        driver.findings.report(
                            "KEYPAD-NOT-RESTORED-ON-REOPEN", Severity.MEDIUM,
                            "Reopening a long active workout jumps to the top; the open set row is off screen and the keypad does not come back until the user scrolls down and taps it",
                            driver.history, workout.day, workout.index,
                            "workout ${workout.index} ${workout.date}: open set row ${robot.activeRow} below the fold after reopen",
                            issue = "#293",
                        )
                    }
                    robot.set(workout.lateSet.keys)
                    robot.rest(workout.finishDelaySeconds)
                    robot.finish()
                }
                workout.finishMode == FinishMode.NEVER_FINISHED -> robot.pressBack()
                else -> {
                    robot.rest(workout.finishDelaySeconds)
                    robot.finish()
                }
            }
            if (session.isFinished) driver.markFinishedByUi(workout.startMillis)
            if (EdgeTag.NEXT_DAY_WHILE_OLD_ACTIVE in workout.tags) checkOldWorkoutStillFinishable(driver, workout, robot)
            return true
        } catch (error: Throwable) {
            driver.findings.report(
                "UI-STEP-FAILED-${workout.tags.sorted().joinToString("+").ifEmpty { "PLAIN" }}", Severity.HIGH,
                "The scripted user could not complete a workout in the real UI",
                driverHistory(driver), workout.day, workout.index, "${error.javaClass.simpleName}: ${error.message?.take(300)}",
            )
            Log.w(WorkoutRobot.TAG, "UI step failed for workout ${workout.index} at row ${robot.activeRow}: ${error.message}")
            runCatching {
                composeRule.onAllNodes(isRoot()).fetchSemanticsNodes().forEachIndexed { i, _ ->
                    composeRule.onAllNodes(isRoot())[i].printToString(maxDepth = 40)
                        .lines().filter { it.contains("Tag") || it.contains("Text") || it.contains("Focused") }
                        .forEach { Log.w(WorkoutRobot.TAG, "TREE $i $it") }
                }
            }
            return false
        } finally {
            scenario.close()
        }
    }

    /**
     * After the next day's workout: the workout left yesterday with Back is still unfinished.
     * Can the user still finish it from Home?
     */
    private fun checkOldWorkoutStillFinishable(driver: DeviceHistoryDriver, workout: SimWorkout, robot: WorkoutRobot) {
        val old = driver.sessions.dropLast(1).lastOrNull { !it.isFinished } ?: return
        val timer = runBlocking { NoteTimerStore.observe(appState.context).first() }
        robot.openWorkoutCard(old.workout.startMillis)
        composeRule.waitForIdle()
        val finishShown = composeRule.onAllNodesWithTag(NOTE_EDITOR_FINISH_ACTION_TEST_TAG).fetchSemanticsNodes().isNotEmpty()
        if (!finishShown) {
            driver.findings.report(
                "UNFINISHED-WORKOUT-ORPHANED", Severity.HIGH,
                "Starting a new workout makes the unfinished one un-finishable: no Finish button, its sets never reach the sets table / Stats charts",
                driver.history, workout.day, old.workout.index,
                "confirmed in the real UI: opened workout ${old.workout.index} from Home after workout ${workout.index}; active timer=${timer.activeNoteTimestamp}; no Finish",
                issue = "#293",
            )
        }
        robot.pressBack()
    }

    /** Issue #291: settings written before a launch must survive the launch. */
    private fun settingsSurviveRelaunch(driver: DeviceHistoryDriver, event: SimEvent) {
        val context = appState.context
        val custom = runBlocking {
            val current = SettingsStore.load(context)
            current.copy(
                is24Hour = !current.is24Hour,
                categories = current.categories + Category("Probe${event.day}", 0xFF123456),
            ).also { SettingsStore.save(context, it) }
        }
        val launchMs = measureTimeMillis {
            ActivityScenario.launch(MainActivity::class.java).use { composeRule.waitForIdle() }
        }
        val reloaded = runBlocking { SettingsStore.load(context) }
        if (reloaded.is24Hour != custom.is24Hour || reloaded.categories.none { it.name == "Probe${event.day}" }) {
            driver.findings.report(
                "SETTINGS-RESET-ON-LAUNCH", Severity.HIGH, "Settings saved before a launch were overwritten by defaults",
                driverHistory(driver), event.day, null, "saved is24Hour=${custom.is24Hour}, after launch ${reloaded.is24Hour}", issue = "#291",
            )
        }
        runBlocking {
            SettingsStore.save(context, reloaded.copy(categories = reloaded.categories.filterNot { it.name.startsWith("Probe") }, is24Hour = true))
        }
        driver.findings.metric("day ${event.day}: cold-ish launch to idle ${launchMs} ms")
    }

    private fun statsScreenShowsWorkoutCount(driver: DeviceHistoryDriver) {
        // #322: the Workouts badge counts finished workouts only; unfinished ones are shown separately.
        val expected = driver.sessions.count { it.isFinished }
        ActivityScenario.launch(MainActivity::class.java).use {
            val robot = WorkoutRobot(composeRule, appState.clock) { it }
            robot.waitForTag(HOME_OPEN_STATS_TEST_TAG)
            val ms = measureTimeMillis {
                robot.openStats()
                composeRule.waitUntil(60_000L) {
                    robot.exists(statsBadgeValueTestTag("Workouts")) &&
                        robot.textOf(statsBadgeValueTestTag("Workouts")) == expected.toString()
                }
            }
            driver.findings.metric("Stats screen showed $expected workouts after ${ms} ms")
        }
    }

    private fun driverHistory(driver: DeviceHistoryDriver) = driver.history
}
