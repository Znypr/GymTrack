package com.example.gymtrack.simulation

import com.example.gymtrack.core.data.ExerciseFlag
import com.example.gymtrack.core.data.NoteEntity
import com.example.gymtrack.core.data.Settings
import com.example.gymtrack.core.util.buildNoteRowMetadata
import com.example.gymtrack.core.util.formatElapsedMinutesSeconds
import com.example.gymtrack.core.util.formatSecondsToMinutesSeconds

/** A set the user really did, in generator truth (independent of how the app stores it). */
data class TruthSet(
    val exerciseKey: String,
    val reps: Int,
    val weight: Double,
    val durationSeconds: Int?,
    val unilateral: Boolean,
    val enteredAtMillis: Long,
)

data class EditorRow(val text: String, val elapsed: String, val flag: ExerciseFlag)

/**
 * The note an editor session leaves behind, built with the same rules as
 * `NoteEditorState.onTextChange` / `saveNote` (verified against the UI by `WorkoutSimulationTest`):
 * - the grey elapsed column counts from the first exercise Enter, rounded to 5 s;
 * - after a reopen (Back, then open from Home, or process death) it counts from the + tap;
 * - set rows read `"      <keys> (<rest>)"`, rest = time since the previous non-blank Enter;
 * - a new row inherits the flag of the row Enter was pressed on;
 * - ON_STOP stamps a half-typed row with the stop time and leaves its text as typed.
 */
data class EditorSession(
    val workout: SimWorkout,
    val rows: List<EditorRow>,
    val truth: List<TruthSet>,
    /** Editor text right before the first Back (for workouts left without Finish), else null. */
    val draftAtBack: NoteEntity?,
    val finalNote: NoteEntity,
    /** Epoch millis of the Finish tap; null when the workout is never finished. */
    val finishedAtMillis: Long?,
) {
    val isFinished: Boolean get() = finishedAtMillis != null
}

object EditorTranscript {
    private val settings = Settings()

    fun build(workout: SimWorkout): EditorSession {
        val t0 = workout.startMillis
        var now = t0
        var lastEnter = t0
        var base: Long? = null
        var rowFlag = ExerciseFlag.BILATERAL
        val rows = mutableListOf<EditorRow>()
        val truth = mutableListOf<TruthSet>()

        fun elapsed(at: Long) = formatElapsedMinutesSeconds(base ?: at, at, settings)

        workout.exercises.forEach { exercise ->
            now += exercise.walkSeconds * 1_000L
            val headerInitialFlag = rowFlag
            if (base == null) base = now
            lastEnter = now
            rows += EditorRow(exercise.typedName, elapsed(now), exercise.flag)
            val setFlag = if (exercise.flagAfterEnter) headerInitialFlag else exercise.flag
            exercise.sets.forEach { set ->
                now += set.restBeforeSeconds * 1_000L
                val rest = (now - lastEnter) / 1_000L
                lastEnter = now
                rows += EditorRow("      ${set.editorText} (${formatSecondsToMinutesSeconds(rest)})", elapsed(now), setFlag)
                truth += set.truth(exercise, now)
            }
            rows += EditorRow("", "", setFlag)
            rowFlag = setFlag
        }

        var draftAtBack: NoteEntity? = null
        when {
            workout.partialKeys != null -> {
                // User taps "8x8" on the keypad, the phone locks (ON_STOP draft save stamps the
                // row), Android kills the process, the user reopens and taps Finish.
                now += 90_000L
                rows[rows.lastIndex] = EditorRow(workout.partialKeys.replace("x", "x "), elapsed(now), rowFlag)
                draftAtBack = note(workout, rows)
            }
            workout.lateSet != null -> {
                // Back after the last set; reopened next morning, one forgotten set added.
                draftAtBack = note(workout, rows)
                val lastAbsSeconds = rows.last { it.elapsed.isNotBlank() }.elapsed.toSeconds()
                base = t0
                lastEnter = t0 + lastAbsSeconds * 1_000L
                now += workout.lateSet.restBeforeSeconds * 1_000L
                val rest = (now - lastEnter) / 1_000L
                lastEnter = now
                rows[rows.lastIndex] = EditorRow(
                    "      ${workout.lateSet.editorText} (${formatSecondsToMinutesSeconds(rest)})",
                    elapsed(now),
                    rowFlag,
                )
                rows += EditorRow("", "", rowFlag)
                truth += workout.lateSet.truth(workout.exercises.last(), now)
            }
            workout.finishMode == FinishMode.NEVER_FINISHED -> draftAtBack = note(workout, rows)
        }

        val finishedAt = if (workout.finishMode == FinishMode.NEVER_FINISHED) null else now + workout.finishDelaySeconds * 1_000L
        return EditorSession(workout, rows.toList(), truth, draftAtBack, note(workout, rows), finishedAt)
    }

    fun note(workout: SimWorkout, rows: List<EditorRow>): NoteEntity = NoteEntity(
        timestamp = workout.startMillis,
        title = "",
        text = rows.joinToString("\n") { it.text },
        categoryName = workout.category.name,
        categoryColor = workout.category.color,
        learnings = workout.learnings,
        rowMetadata = buildNoteRowMetadata(rows.map { it.elapsed }, rows.map { it.flag }),
    )

    /** "12'05''" → 725. */
    fun String.toSeconds(): Long {
        val match = Regex("""(\d+)'(\d{2})''""").matchEntire(this) ?: return 0L
        return match.groupValues[1].toLong() * 60L + match.groupValues[2].toLong()
    }

    private fun SimSet.truth(exercise: SimExercise, at: Long) = TruthSet(
        exerciseKey = exercise.key,
        reps = reps,
        weight = weight,
        durationSeconds = durationSeconds,
        unilateral = exercise.flag == ExerciseFlag.UNILATERAL,
        enteredAtMillis = at,
    )
}
