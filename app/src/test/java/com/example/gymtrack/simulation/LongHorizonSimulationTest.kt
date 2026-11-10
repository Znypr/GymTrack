package com.example.gymtrack.simulation

import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.data.WorkoutRepository
import com.example.gymtrack.core.data.repository.toDomainModel
import com.example.gymtrack.core.data.transition.CanonicalExerciseCatalog
import com.example.gymtrack.core.data.transition.LegacyWorkoutProjector
import com.example.gymtrack.core.timer.NoteTimerState
import com.example.gymtrack.core.util.WorkoutParser
import com.example.gymtrack.feature.home.WorkoutStartDecision
import com.example.gymtrack.feature.home.canMarkFinished
import com.example.gymtrack.feature.home.decideWorkoutStart
import com.example.gymtrack.feature.stats.buildTrainingInsights
import java.io.File
import java.util.TimeZone
import kotlin.system.measureTimeMillis
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Months of daily GymTrack use in seconds, on the JVM: a seeded generator produces 12 months of
 * training, each session becomes the exact note the editor writes, and the real parser,
 * canonical projector, exercise identity resolver, timer state and Stats functions process it.
 * Invariants run after every simulated workout and every simulated week.
 *
 * Findings do not fail the build (they are product gaps, listed in docs/simulation-findings.md);
 * the test fails only when the harness itself is broken. The report is written to
 * app/build/reports/simulation/jvm-long-horizon.md.
 *
 * Persistence-level behaviour (draft vs finalize, startup import, backup/restore, UI) is covered
 * on a device by androidTest `LongHorizonAppSimulationTest`.
 */
class LongHorizonSimulationTest {
    private lateinit var originalZone: TimeZone

    @Before
    fun pinPhoneZone() {
        originalZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
    }

    @After
    fun restoreZone() = TimeZone.setDefault(originalZone)

    @Test
    fun sameSeedProducesSameHistory() {
        val first = WorkoutHistoryGenerator(seed = 7L).generate()
        val second = WorkoutHistoryGenerator(seed = 7L).generate()
        assertEquals(first.fingerprint(), second.fingerprint())
        assertTrue(first.fingerprint() != WorkoutHistoryGenerator(seed = 8L).generate().fingerprint())
    }

    @Test
    fun twelveMonthsOfDailyUse() {
        val all = SimulationFindings()
        val summaries = mutableListOf<String>()
        SEEDS.forEach { seed ->
            val history = WorkoutHistoryGenerator(seed = seed, months = 12).generate()
            assertTrue("seed $seed: too few workouts (${history.workouts.size})", history.workouts.size >= 150)
            val findings = SimulationFindings()
            simulate(history, findings)
            summaries += "seed $seed: ${history.workouts.size} workouts, ${history.workouts.sumOf { it.intendedSetCount }} sets, " +
                "${history.events.size} events, ${findings.all.size} distinct findings"
            all.merge(findings)
        }

        val report = buildString {
            appendLine("# JVM long-horizon simulation")
            appendLine()
            summaries.forEach { appendLine("- $it") }
            appendLine()
            append(all.toMarkdown("Findings (all seeds)"))
        }
        val out = File("build/reports/simulation/jvm-long-horizon.md")
        out.parentFile?.mkdirs()
        out.writeText(report)
        println(report)
        assertTrue("simulation produced no output", report.isNotBlank())

        // Editor-state findings fixed in #316 #317 #319 #320 are hard invariants now: the
        // transcript plays the real NoteEditorState, so a regression fails the build.
        val regressed = all.all.filter { it.id in FIXED_EDITOR_FINDINGS }
        assertTrue("fixed editor findings came back: ${regressed.map { "${it.id}: ${it.example}" }}", regressed.isEmpty())
    }

    private fun simulate(history: SimHistory, findings: SimulationFindings) {
        val invariants = HistoryInvariants(history, findings)
        val parser = WorkoutParser()
        val projector = LegacyWorkoutProjector(parser)
        val sessions = mutableListOf<EditorSession>()
        val notes = mutableListOf<NoteLine>()
        var timer = NoteTimerState()
        val lastDay = history.workouts.maxOf { it.day }
        val workoutsByDay = history.workouts.groupBy { it.day }

        for (day in 0..lastDay) {
            workoutsByDay[day].orEmpty().forEach { workout ->
                // Every Start goes through the start sheet (#293). The simulated user answers
                // "Keep both"; the old workout must stay finishable (History "Mark finished").
                val previous = sessions.lastOrNull()
                val activeNote = previous?.takeIf { timer.activeNoteTimestamp == it.workout.startMillis }
                    ?.let { (it.draftAtBack ?: it.finalNote).toDomainModel() }
                val decision = decideWorkoutStart(
                    activeTimestamp = timer.activeNoteTimestamp,
                    activeNote = activeNote,
                    activeIsFinished = previous?.isFinished == true,
                )
                if (decision is WorkoutStartDecision.AskFirst) {
                    // Keep both: the new workout takes the timer, the old one stays unfinished.
                    val oldStillFinishable = canMarkFinished(decision.active, isFinished = false)
                    if (!oldStillFinishable) {
                        findings.report(
                            "UNFINISHED-WORKOUT-ORPHANED", Severity.HIGH,
                            "Starting a new workout makes the unfinished one un-finishable: no Finish button, its sets never reach the sets table / Stats charts",
                            history, day, workout.index,
                            "workout ${previous?.workout?.index} left with Back on ${previous?.workout?.date}; start sheet offered Keep both, but History cannot finish it",
                            issue = "#293",
                        )
                    }
                }
                timer = NoteTimerState.started(workout.startMillis, workout.startMillis)

                val session = EditorTranscript.build(workout)
                sessions += session

                if (workout.lateSet != null) {
                    val morning = session.truth.last().enteredAtMillis
                    val hours = timer.elapsedSeconds(morning) / 3600
                    findings.report(
                        "TIMER-RUNS-OVERNIGHT", Severity.LOW,
                        "Timer of a workout left with Back keeps running overnight with no prompt or cap",
                        history, day, workout.index, "timer shows ${hours} h when the user returns next morning", issue = "#293",
                    )
                }

                if (session.isFinished) {
                    timer = NoteTimerState()
                    val note = session.finalNote
                    val parsed = parser.parseWorkout(note.text, workout.unit.storageValue, note.rowMetadata)
                    // The completion path passes the default unit in force at Finish (#326).
                    val projection = projector.project(note, null, CanonicalExerciseCatalog(emptyList()), workout.unit)
                    invariants.checkFinishedWorkout(
                        session,
                        parsed,
                        CanonicalRows(
                            // What WorkoutRepository.saveCompletedWorkout writes at Finish.
                            projection.workout.copy(
                                status = "COMPLETED",
                                endedAt = WorkoutRepository.completedWorkoutEndedAt(note, session.finishedAtMillis),
                            ),
                            projection.workoutExercises,
                            projection.workoutSets,
                        ),
                    )
                    notes += note.toDomainModel()
                } else {
                    notes += (session.draftAtBack ?: session.finalNote).toDomainModel()
                }
            }

            if (day % 7 == 6 || day == lastDay) {
                invariants.checkStats(day, notes, sessions)
            }
            if (day in PERF_DAYS || day == lastDay) measure(history, findings, day, notes, parser, projector)
        }
    }

    private fun measure(
        history: SimHistory,
        findings: SimulationFindings,
        day: Int,
        notes: List<NoteLine>,
        parser: WorkoutParser,
        projector: LegacyWorkoutProjector,
    ) {
        val parseMs = measureTimeMillis { notes.forEach { parser.parseWorkout(it.text, rowMetadata = it.rowMetadata) } }
        val projectMs = measureTimeMillis {
            notes.forEach { note ->
                projector.project(
                    com.example.gymtrack.core.data.NoteEntity(note.timestamp, note.title, note.text, note.categoryName, note.categoryColor, note.learnings, note.rowMetadata),
                    null,
                    CanonicalExerciseCatalog(emptyList()),
                )
            }
        }
        val insightsMs = measureTimeMillis { buildTrainingInsights(notes, parser) }
        findings.metric(
            "seed ${history.seed} day $day (${history.dateOf(day)}), ${notes.size} workouts: parse-all ${parseMs} ms " +
                "(Stats build), project-all ${projectMs} ms (startup canonical import), insights ${insightsMs} ms — JVM desktop, phone is slower",
        )
    }

    private companion object {
        val SEEDS = listOf(11L, 23L, 47L)
        val PERF_DAYS = setOf(91, 182, 364) // months 3, 6, 12
        val FIXED_EDITOR_FINDINGS = setOf(
            "UNILATERAL-LOST", // #316
            "UNILATERAL-INHERITED", // #317
            "SET-TIME-SHIFTED", // #319
            "PHANTOM-SET", // #320
            "LOST-SET",
            "CANONICAL-NEEDS-REVIEW", // #320: nameless exercise from the half-typed set
            "UNFINISHED-WORKOUT-ORPHANED", // #293: start sheet + History "Mark finished"
        )
    }
}
