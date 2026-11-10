package com.example.gymtrack.simulation

import com.example.gymtrack.core.data.ExerciseFlag
import com.example.gymtrack.core.data.NoteEntity
import com.example.gymtrack.core.util.parseNoteText
import com.example.gymtrack.feature.editor.NoteEditorDriver
import com.example.gymtrack.feature.editor.SavedEditorNote

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
 * The note an editor session leaves behind. The session is played through the real
 * `NoteEditorState` (fake clock, saves captured in memory, see [NoteEditorDriver]) with the
 * keystrokes a user makes:
 * - IME name + Enter per exercise, the flag tapped before or after that Enter;
 * - keypad keys + Enter per set; an empty Enter before the next exercise;
 * - ON_STOP with a half-typed set, then a kill (a new editor from the draft) and Finish;
 * - Back, then a reopen (a new editor from the draft) next morning for a forgotten set.
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

    fun build(workout: SimWorkout): EditorSession {
        var editor = NoteEditorDriver(startMillis = workout.startMillis) // the + tap
        val truth = mutableListOf<TruthSet>()

        fun setFlag(row: Int, flag: ExerciseFlag) {
            repeat(ExerciseFlag.entries.size) {
                if (editor.state.lines[row].flag.value != flag) editor.state.toggleFlag(row)
            }
        }

        workout.exercises.forEachIndexed { index, exercise ->
            if (index > 0) editor.enter(editor.lastIndex) // empty Enter ends the previous exercise
            editor.advance(exercise.walkSeconds)
            val row = editor.lastIndex
            editor.type(row, exercise.typedName)
            if (!exercise.flagAfterEnter) setFlag(row, exercise.flag)
            editor.enter(row)
            if (exercise.flagAfterEnter) setFlag(row, exercise.flag)
            exercise.sets.forEach { set ->
                editor.advance(set.restBeforeSeconds)
                editor.typeAndEnter(editor.lastIndex, set.editorText)
                truth += set.truth(exercise, editor.now)
            }
        }

        var draftAtBack: NoteEntity? = null
        when {
            workout.partialKeys != null -> {
                // User taps "8x8" on the keypad, the phone locks (ON_STOP draft save), Android
                // kills the process, the user reopens and taps Finish.
                editor.advance(90)
                editor.type(editor.lastIndex, workout.partialKeys.replace("x", "x "))
                editor.stop()
                val draft = editor.lastSave
                draftAtBack = note(workout, draft)
                editor = NoteEditorDriver(initialNote = draft.toNoteLine(), startMillis = editor.now)
            }
            workout.lateSet != null -> {
                // Back after the last set; reopened next morning, one forgotten set added.
                editor.back()
                val draft = editor.lastSave
                draftAtBack = note(workout, draft)
                editor = NoteEditorDriver(initialNote = draft.toNoteLine(), startMillis = editor.now)
                editor.advance(workout.lateSet.restBeforeSeconds)
                editor.typeAndEnter(editor.lastIndex, workout.lateSet.editorText)
                truth += workout.lateSet.truth(workout.exercises.last(), editor.now)
            }
            workout.finishMode == FinishMode.NEVER_FINISHED -> {
                editor.back()
                draftAtBack = note(workout, editor.lastSave)
            }
        }

        val finishedAt = if (workout.finishMode == FinishMode.NEVER_FINISHED) {
            null
        } else {
            editor.advance(workout.finishDelaySeconds)
            editor.finish()
            editor.now
        }
        val final = editor.lastSave
        val parsed = parseNoteText(final.text, final.rowMetadata)
        val rows = parsed.first.mapIndexed { i, text ->
            EditorRow(text, parsed.second.getOrElse(i) { "" }, parsed.third.getOrElse(i) { ExerciseFlag.BILATERAL })
        }
        return EditorSession(workout, rows, truth, draftAtBack, note(workout, final), finishedAt)
    }

    private fun note(workout: SimWorkout, saved: SavedEditorNote): NoteEntity = NoteEntity(
        timestamp = saved.timestamp,
        title = "",
        text = saved.text,
        categoryName = workout.category.name,
        categoryColor = workout.category.color,
        learnings = workout.learnings,
        rowMetadata = saved.rowMetadata,
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
