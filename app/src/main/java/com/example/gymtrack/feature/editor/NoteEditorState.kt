package com.example.gymtrack.feature.editor

import com.example.gymtrack.core.time.AppClock
import android.content.Context
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.example.gymtrack.core.data.ExerciseFlag
import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.data.Settings
import com.example.gymtrack.core.timer.NoteTimerStore
import com.example.gymtrack.core.util.buildNoteRowMetadata
import com.example.gymtrack.core.util.formatElapsedMinutesSeconds
import com.example.gymtrack.core.util.formatSecondsToMinutesSeconds
import com.example.gymtrack.core.util.parseDurationSeconds
import com.example.gymtrack.core.util.parseNoteText
import com.example.gymtrack.feature.editor.guardrail.GuardrailBlock
import com.example.gymtrack.feature.editor.guardrail.GuardrailFix
import com.example.gymtrack.feature.editor.guardrail.GuardrailHint
import com.example.gymtrack.feature.editor.guardrail.SetGuardrails
import com.example.gymtrack.feature.editor.guardrail.SetHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class NoteRow(
    val id: Long,
    val text: MutableState<TextFieldValue>,
    val flag: MutableState<ExerciseFlag> = mutableStateOf(ExerciseFlag.BILATERAL),
    val focusRequester: FocusRequester = FocusRequester(),
)

internal fun shouldStopTimerOnExit(isLastNote: Boolean, finishWorkout: Boolean): Boolean =
    isLastNote && finishWorkout

/** What the set keypad is typing into (EDITOR_V2 §6). */
enum class KeypadMode {
    /** The open set row (the last row, or a half-typed draft): keys edit the row itself. */
    LIVE,

    /** A committed set: keys edit a copy of its entry; Next writes it back, times untouched. */
    EDIT,

    /** `+ set` on an earlier exercise: keys fill a new set that Next inserts after the anchor row. */
    INSERT,
}

/** A deleted set row, kept so the Undo snackbar can put it back where it was. */
class DeletedEditorRow internal constructor(
    internal val index: Int,
    internal val row: NoteRow,
    internal val timestamp: String,
    internal val flag: ExerciseFlag,
    internal val wasCommitted: Boolean,
)

/** Where [NoteEditorState] sends its saves. Production wraps [EditorViewModel]; tests use a fake. */
internal interface NoteEditorPersistence {
    val currentId: Long
    val currentTitle: String
    val currentLearnings: String
    fun saveDraft(text: String, rowMetadata: String, noteTimestamp: Long, onPersisted: () -> Unit, onFailure: (String) -> Unit)
    fun finalizeWorkout(text: String, rowMetadata: String, noteTimestamp: Long, onPersisted: () -> Unit, onFailure: (String) -> Unit)
    suspend fun stopTimer(noteTimestamp: Long)
}

private class EditorViewModelPersistence(
    private val viewModel: EditorViewModel,
    private val context: Context,
) : NoteEditorPersistence {
    override val currentId: Long get() = viewModel.currentId
    override val currentTitle: String get() = viewModel.currentTitle
    override val currentLearnings: String get() = viewModel.currentLearnings

    override fun saveDraft(text: String, rowMetadata: String, noteTimestamp: Long, onPersisted: () -> Unit, onFailure: (String) -> Unit) =
        viewModel.saveDraft(text, rowMetadata, noteTimestamp, onPersisted, onFailure)

    override fun finalizeWorkout(text: String, rowMetadata: String, noteTimestamp: Long, onPersisted: () -> Unit, onFailure: (String) -> Unit) =
        viewModel.finalizeWorkout(text, rowMetadata, noteTimestamp, onPersisted, onFailure)

    override suspend fun stopTimer(noteTimestamp: Long) = NoteTimerStore.stop(context, noteTimestamp)
}

/** A set just committed by Enter / Next: what the rest countdown (#349) starts from. */
data class SetCommit(val sequence: Int, val exerciseName: String, val flag: ExerciseFlag)

private val RELATIVE_TIME_SUFFIX = Regex(""".*\(\d+'\d{2}''\)$""")

class NoteEditorState internal constructor(
    private val persistence: NoteEditorPersistence,
    private val settings: Settings,
    private val scope: CoroutineScope,
    val listState: LazyListState,
    initialNote: NoteLine?,
    private val onSaveSuccess: () -> Unit,
    private val clock: () -> Long = AppClock::nowMillis,
) {
    constructor(
        viewModel: EditorViewModel,
        settings: Settings,
        context: Context,
        scope: CoroutineScope,
        listState: LazyListState,
        initialNote: NoteLine?,
        onSaveSuccess: () -> Unit,
    ) : this(EditorViewModelPersistence(viewModel, context), settings, scope, listState, initialNote, onSaveSuccess)

    var nextId by mutableStateOf(0L)
    val lines = mutableStateListOf<NoteRow>()
    val timestamps = mutableStateListOf<String>()
    val flags = mutableStateListOf<ExerciseFlag>()

    /**
     * Workout start: the note id, which is also when the timer started (the + tap). Every
     * absolute time is measured from it, in a new session and after a reopen alike (#319).
     */
    val noteTimestamp: Long = initialNote?.timestamp ?: clock()
    private var lastEnter: Long = noteTimestamp
    private var editRevision = 0L
    private var finishing = false

    /**
     * Rows whose times are history: committed by Enter in this session, or loaded with a time.
     * A time stamped by a save on a row nobody pressed Enter on is provisional; that row's Enter
     * still stamps it and starts the next rest.
     */
    private val committedRowIds = mutableSetOf<Long>()

    var pendingExerciseFocusRowId by mutableStateOf<Long?>(null)
        private set

    /**
     * The row the set keypad works on, or null when the keypad is closed. Owned by the state, not
     * the UI, so opening a past workout can keep it closed (#328) and every path is unit-tested.
     * In [KeypadMode.INSERT] this is the row the new set goes after.
     */
    var keypadRowId by mutableStateOf<Long?>(null)
        private set

    var keypadMode by mutableStateOf(KeypadMode.LIVE)
        private set

    /** The entry typed in [KeypadMode.EDIT] / [KeypadMode.INSERT]; the stored row is untouched until Next. */
    var keypadBuffer by mutableStateOf(TextFieldValue(""))
        private set

    /** The exercise row that has IME focus, so the flag segment knows its exercise. */
    var focusedRowId by mutableStateOf<Long?>(null)
        private set

    /** Bumped when the UI should drop IME focus (Enter on an exercise that already has sets). */
    var releaseFocusTick by mutableStateOf(0)
        private set

    var saved by mutableStateOf(false)
        private set

    /**
     * Input guardrails (#304). Starts with no history (only the history-free rules: unparsed line,
     * double log); [useHistory] swaps in the user's history once it is loaded off the main thread.
     */
    private var guardrails = SetGuardrails(SetHistory.EMPTY, settings.defaultWeightUnit.storageValue)

    /** Open mistake hints by row id. Nothing is changed until Fix; Keep dismisses for good. */
    val guardHints = mutableStateMapOf<Long, GuardrailHint>()

    /** The row of the newest hint: shown under the keypad row, the others under their own row. */
    var latestHintRowId by mutableStateOf<Long?>(null)
        private set

    /** Rows whose hint the user answered with Keep: never flagged again in this session. */
    private val keptRowIds = mutableSetOf<Long>()

    /** The last set committed by Enter, for the rest countdown. */
    var lastSetCommit by mutableStateOf<SetCommit?>(null)
        private set

    fun useHistory(history: SetHistory) {
        guardrails = SetGuardrails(history, settings.defaultWeightUnit.storageValue)
    }

    /** The current exercise's usual rest from history (#349), or null. */
    fun usualRestSeconds(exerciseName: String, flag: ExerciseFlag): Int? = guardrails.usualRestSeconds(exerciseName, flag)

    init {
        val parsed = parseNoteText(initialNote?.text ?: "", initialNote?.rowMetadata)
        if (parsed.first.isEmpty()) {
            addLine(NoteRow(nextId++, mutableStateOf(TextFieldValue(""))))
            timestamps.add("")
            flags.add(ExerciseFlag.BILATERAL)
        } else {
            parsed.first.forEachIndexed { index, text ->
                val cleanText = if (text.isNotBlank() && !text.startsWith(" ")) {
                    text.replace(Regex("""\s*\(\d+'\d{2}''\)$"""), "")
                } else text
                addLine(
                    NoteRow(
                        id = nextId++,
                        text = mutableStateOf(TextFieldValue(cleanText)),
                        flag = mutableStateOf(parsed.third.getOrNull(index) ?: ExerciseFlag.BILATERAL),
                    ),
                )
            }
            timestamps.addAll(parsed.second)
            flags.addAll(parsed.third)
        }
        cleanLists()
        lines.forEachIndexed { index, row -> if (timestamps[index].isNotBlank()) committedRowIds += row.id }
        if (initialNote != null) {
            val lastIndex = parsed.second.indexOfLast(String::isNotBlank)
            val lastSeconds = if (lastIndex >= 0) parseDurationSeconds(parsed.second[lastIndex]).toLong() else 0L
            lastEnter = noteTimestamp + lastSeconds * 1_000L
        }
    }

    fun markDirty() {
        if (finishing) return
        editRevision += 1L
        saved = false
    }

    fun indexOfRow(rowId: Long?): Int =
        if (rowId == null) -1 else lines.indexOfFirst { it.id == rowId }

    /** The value the keypad shows and edits. */
    val keypadValue: TextFieldValue
        get() = if (keypadMode == KeypadMode.LIVE) {
            lines.getOrNull(indexOfRow(keypadRowId))?.text?.value ?: TextFieldValue("")
        } else {
            keypadBuffer
        }

    /**
     * Exercise the flag segment acts on: the one owning the keypad row, else the focused exercise
     * row. Null when nothing is being edited (the segment is then hidden).
     */
    val currentExerciseIndex: Int?
        get() {
            val index = indexOfRow(keypadRowId).takeIf { it >= 0 } ?: indexOfRow(focusedRowId).takeIf { it >= 0 }
            return index?.let(::exerciseIndexOf)
        }

    /** True once at least one set has been committed: Finish is enabled from then on. */
    val hasLoggedSet: Boolean
        get() = lines.indices.any { i ->
            !isMainRow(i) && lines[i].text.value.text.isNotBlank() && timestamps.getOrElse(i) { "" }.isNotBlank()
        }

    /**
     * Opens the keypad on a set row. A committed set is edited through a copy of its entry, so its
     * indent and times can never be touched (#330 / B14). Exercise and separator rows are ignored.
     */
    fun openKeypad(rowId: Long) {
        if (finishing) return
        val index = indexOfRow(rowId)
        if (index < 0 || isMainRow(index)) return
        val row = lines[index]
        val text = row.text.value.text
        if (text.isBlank() && index < lines.lastIndex) return // a separator, not a set
        focusedRowId = null
        if (text.isNotBlank() && row.id in committedRowIds) {
            val entry = splitSetRow(text).entry
            keypadBuffer = TextFieldValue(entry, TextRange(entry.length))
            keypadMode = KeypadMode.EDIT
        } else {
            row.text.value = row.text.value.copy(selection = TextRange(text.length))
            keypadBuffer = TextFieldValue("")
            keypadMode = KeypadMode.LIVE
        }
        keypadRowId = rowId
    }

    fun closeKeypad() {
        keypadRowId = null
        keypadMode = KeypadMode.LIVE
        keypadBuffer = TextFieldValue("")
    }

    /**
     * Opening a workout: an active workout comes back with the keypad on its open set row; a past
     * workout opens at the top with the keypad closed until a row is tapped (#328).
     */
    fun restoreKeypadOnOpen(isActiveWorkout: Boolean) {
        if (!isActiveWorkout || keypadRowId != null) return
        val last = lines.lastIndex
        if (last <= 0 || isMainRow(last) || timestamps.getOrElse(last) { "" }.isNotBlank()) return
        openKeypad(lines[last].id)
    }

    /**
     * One keypad key, applied to [keypadValue] as it is right now (#362). The row the keypad owns
     * is looked up through [keypadRowId] at tap time, so a tap that lands before the next frame
     * (a second quick `1`, or a digit right after Next moved the keypad) never edits a stale copy.
     */
    internal fun onKeypadKey(key: com.example.gymtrack.feature.editor.components.KeypadKey) {
        if (finishing || keypadRowId == null) return
        val updated = com.example.gymtrack.feature.editor.components.applyKeypadKey(keypadValue, key) ?: return
        onKeypadInput(updated)
    }

    /** One keypad key (digit, X, S, +, ., ⌫) already applied to [keypadValue]. */
    fun onKeypadInput(newValue: TextFieldValue) {
        if (finishing) return
        when (keypadMode) {
            KeypadMode.LIVE -> {
                val index = indexOfRow(keypadRowId)
                if (index >= 0) onTextChange(index, newValue)
            }
            KeypadMode.EDIT, KeypadMode.INSERT -> keypadBuffer = newValue
        }
    }

    /** The keypad's Next key. LIVE keeps today's flow: commit + new set row, or next exercise. */
    fun onKeypadNext() {
        if (finishing) return
        val index = indexOfRow(keypadRowId)
        if (index < 0) {
            closeKeypad()
            return
        }
        when (keypadMode) {
            KeypadMode.LIVE -> {
                val current = lines[index].text.value.text
                onTextChange(index, TextFieldValue(current + "\n", TextRange(current.length + 1)))
            }
            KeypadMode.EDIT -> {
                val entry = keypadBuffer.text.trim()
                val row = lines[index]
                if (entry.isNotEmpty()) {
                    val updated = splitSetRow(row.text.value.text).withEntry(entry)
                    if (updated != row.text.value.text) {
                        markDirty()
                        row.text.value = TextFieldValue(updated, TextRange(updated.length))
                        keptRowIds -= row.id
                        saveNote(isLastNote = false, exit = false)
                    }
                    checkSet(index, restSeconds = null)
                }
                closeKeypad()
            }
            KeypadMode.INSERT -> {
                val entry = keypadBuffer.text.trim()
                if (entry.isEmpty()) {
                    closeKeypad()
                    return
                }
                insertCommittedSet(afterIndex = index, entry = entry)
            }
        }
    }

    /** `+ set` at the end of an exercise block (#330 / B9). */
    fun addSetToExercise(rowIndex: Int) {
        if (finishing || rowIndex !in lines.indices) return
        val ex = exerciseIndexOf(rowIndex)
        if (lines[ex].text.value.text.isBlank()) return
        var last = ex
        while (last + 1 < lines.size && !isMainRow(last + 1)) last++
        val lastRow = lines[last]
        val lastBlank = lastRow.text.value.text.isBlank()
        val lastIsListEnd = last == lines.lastIndex
        focusedRowId = null
        when {
            // The open set row of the current exercise: bring the keypad back to it.
            last != ex && lastIsListEnd && (lastBlank || lastRow.id !in committedRowIds) -> openKeypad(lastRow.id)
            // The last exercise ends on a committed row: open a fresh set row at the end, as Enter would.
            lastIsListEnd && !lastBlank -> {
                val newRow = NoteRow(nextId++, mutableStateOf(TextFieldValue("")), mutableStateOf(lines[ex].flag.value))
                lines.add(newRow)
                ensureListSize(lines.lastIndex)
                flags[lines.lastIndex] = newRow.flag.value
                openKeypad(newRow.id)
            }
            // An earlier exercise: type the set first, insert it on Next (no blank row in between).
            else -> {
                val anchor = if (lastBlank) last - 1 else last
                keypadBuffer = TextFieldValue("")
                keypadMode = KeypadMode.INSERT
                keypadRowId = lines[anchor].id
            }
        }
    }

    private fun insertCommittedSet(afterIndex: Int, entry: String) {
        markDirty()
        val now = clock()
        val restSeconds = ((now - lastEnter) / 1_000L).toInt()
        val text = "      $entry (${formatSecondsToMinutesSeconds((now - lastEnter) / 1_000L)})"
        lastEnter = now
        val flag = lines[exerciseIndexOf(afterIndex)].flag.value
        val newRow = NoteRow(nextId++, mutableStateOf(TextFieldValue(text, TextRange(text.length))), mutableStateOf(flag))
        val at = afterIndex + 1
        ensureListSize(afterIndex)
        lines.add(at, newRow)
        flags.add(at, flag)
        timestamps.add(at, formatElapsedMinutesSeconds(noteTimestamp, now, settings))
        committedRowIds += newRow.id
        checkSet(at, restSeconds)
        noteSetCommit(at)
        // Next = commit and a new set: stay in INSERT, now after the set just added.
        keypadBuffer = TextFieldValue("")
        keypadRowId = newRow.id
        saveNote(isLastNote = false, exit = false)
    }

    /** Swipe-left delete of a set row (#330 / B8). Returns what Undo needs, or null if not a set. */
    fun deleteSetRow(index: Int): DeletedEditorRow? {
        if (finishing || index !in lines.indices || isMainRow(index)) return null
        val row = lines[index]
        if (row.text.value.text.isBlank()) return null
        markDirty()
        if (keypadRowId == row.id) closeKeypad()
        guardHints.remove(row.id)
        ensureListSize(index)
        val deleted = DeletedEditorRow(index, row, timestamps[index], flags[index], row.id in committedRowIds)
        lines.removeAt(index)
        timestamps.removeAt(index)
        flags.removeAt(index)
        saveNote(isLastNote = false, exit = false)
        return deleted
    }

    fun undoDelete(deleted: DeletedEditorRow) {
        if (finishing || lines.any { it.id == deleted.row.id }) return
        markDirty()
        val at = deleted.index.coerceIn(0, lines.size)
        lines.add(at, deleted.row)
        timestamps.add(at, deleted.timestamp)
        flags.add(at, deleted.flag)
        if (deleted.wasCommitted) committedRowIds += deleted.row.id
        saveNote(isLastNote = false, exit = false)
    }

    /** "Edit as text" from a set row's long-press: replaces only the entry, keeping indent and times. */
    fun replaceSetEntry(index: Int, entry: String) {
        if (finishing || index !in lines.indices || isMainRow(index)) return
        val trimmed = entry.replace("\n", " ").replace("\r", " ").trim()
        if (trimmed.isEmpty()) return
        val row = lines[index]
        val updated = splitSetRow(row.text.value.text).withEntry(trimmed)
        if (updated == row.text.value.text) return
        markDirty()
        row.text.value = TextFieldValue(updated, TextRange(updated.length))
        keptRowIds -= row.id
        checkSet(index, restSeconds = null)
        saveNote(isLastNote = false, exit = false)
    }

    /**
     * The hint's first button (#304). Replaces the entry, opens the set on the keypad, or removes
     * it; a removed set is returned so the UI can offer Undo.
     */
    fun applyGuardHintFix(rowId: Long): DeletedEditorRow? {
        if (finishing) return null
        val hint = guardHints[rowId] ?: return null
        val index = indexOfRow(rowId)
        if (index < 0) {
            guardHints.remove(rowId)
            return null
        }
        return when (hint.fix) {
            GuardrailFix.REPLACE_ENTRY -> {
                guardHints.remove(rowId)
                hint.fixEntry?.let { replaceSetEntry(index, it) }
                null
            }
            GuardrailFix.EDIT -> {
                guardHints.remove(rowId)
                openKeypad(rowId)
                null
            }
            GuardrailFix.REMOVE -> deleteSetRow(index)
        }
    }

    /** Keep: the set is right as typed. Remembered for this row until its entry changes. */
    fun keepGuardHint(rowId: Long) {
        guardHints.remove(rowId)
        keptRowIds += rowId
    }

    /**
     * Runs the guardrails on the set at [index] (#304). Synchronous and cheap: one parse of this
     * exercise block plus a lookup in the history loaded at open. Never edits the row.
     */
    private fun checkSet(index: Int, restSeconds: Int?) {
        val row = lines.getOrNull(index) ?: return
        guardHints.remove(row.id)
        if (row.id in keptRowIds || isMainRow(index)) return
        val entry = splitSetRow(row.text.value.text).entry
        if (entry.isBlank()) return
        val ex = exerciseIndexOf(index)
        val earlierRows = (ex + 1 until index).filter { lines[it].text.value.text.isNotBlank() }
        val block = GuardrailBlock(
            exerciseName = lines[ex].text.value.text,
            flag = lines[ex].flag.value,
            earlierEntries = earlierRows.map { splitSetRow(lines[it].text.value.text).entry },
            entry = entry,
            restSeconds = restSeconds,
            ignoredEarlier = earlierRows.withIndex().filter { lines[it.value].id in guardHints }.map { it.index }.toSet(),
        )
        val hint = runCatching { guardrails.check(block) }.getOrNull() ?: return
        guardHints[row.id] = hint
        latestHintRowId = row.id
    }

    private fun noteSetCommit(index: Int) {
        val ex = exerciseIndexOf(index)
        lastSetCommit = SetCommit((lastSetCommit?.sequence ?: 0) + 1, lines[ex].text.value.text, lines[ex].flag.value)
    }

    /** An exercise row gained IME focus: the keypad closes (one input owner at a time). */
    fun onExerciseFocused(rowId: Long) {
        focusedRowId = rowId
        if (keypadRowId != null) closeKeypad()
        consumePendingExerciseFocus(rowId)
    }

    fun onExerciseFocusLost(rowId: Long) {
        if (focusedRowId == rowId) focusedRowId = null
    }

    fun consumePendingExerciseFocus(rowId: Long) {
        if (pendingExerciseFocusRowId == rowId) {
            pendingExerciseFocusRowId = null
        }
    }

    fun onTextChange(index: Int, newValue: TextFieldValue) {
        if (finishing) return
        val row = lines.getOrNull(index) ?: return
        markDirty()
        val raw = newValue.text
        if ('\n' !in raw && '\r' !in raw) {
            row.text.value = newValue
            return
        }
        // Enter. A row never keeps a line break: an IME Enter with the cursor mid-text (or a pasted
        // line break) commits the row as a whole, like Enter at its end, so rows and rowMetadata
        // stay one-to-one (#329).
        commitRow(index, raw.replace("\r", "").replace("\n", ""))
    }

    private fun commitRow(index: Int, content: String) {
        ensureListSize(index)
        val row = lines[index]
        val isMain = isMainRow(index)
        // A committed row's times are history: re-Enter never restamps them or adds another
        // relative time (#327).
        val committed = row.id in committedRowIds
        val now = clock()
        val formatted = when {
            content.isBlank() -> ""
            committed || isMain || content.trim().matches(RELATIVE_TIME_SUFFIX) -> content
            else -> "      $content (${formatSecondsToMinutesSeconds((now - lastEnter) / 1_000L)})"
        }
        val newSet = content.isNotBlank() && !committed && !isMain
        val restSeconds = ((now - lastEnter) / 1_000L).toInt()
        if (content.isNotBlank() && !committed) {
            lastEnter = now
            timestamps[index] = formatElapsedMinutesSeconds(noteTimestamp, now, settings)
            committedRowIds += row.id
        }
        row.text.value = TextFieldValue(formatted, TextRange(formatted.length))
        if (newSet) {
            checkSet(index, restSeconds)
            noteSetCommit(index)
        }

        val nextIndex = index + 1
        val next = lines.getOrNull(nextIndex)
        val startsNextExercise = formatted.isBlank()
        if (!startsNextExercise && next != null && next.text.value.text.isBlank()) {
            // The next row already waits for input: move on to it, do not add another (#327).
            if (nextIndex == lines.lastIndex) openKeypad(next.id) else closeKeypad()
            saveNote(isLastNote = false, exit = false)
            return
        }
        if (isMain && committed && next != null && next.text.value.text.isNotBlank()) {
            // Enter after renaming an exercise that already has sets: nothing to open, and no stray
            // blank row that would turn its first set into an "exercise".
            releaseFocusTick += 1
            saveNote(isLastNote = false, exit = false)
            return
        }
        // A new exercise starts bilateral; a new set row takes its exercise's flag (#317).
        val newFlag = if (startsNextExercise) ExerciseFlag.BILATERAL else lines[exerciseIndexOf(index)].flag.value
        val newRow = NoteRow(
            id = nextId++,
            text = mutableStateOf(TextFieldValue("")),
            flag = mutableStateOf(newFlag),
        )
        lines.add(nextIndex, newRow)
        if (flags.size <= nextIndex) flags.add(newFlag) else flags.add(nextIndex, newFlag)
        if (timestamps.size <= nextIndex) timestamps.add("") else timestamps.add(nextIndex, "")

        closeKeypad()
        if (!startsNextExercise) {
            // One tap per set: the keypad follows to the new set row (§8.1 #1). A row opened
            // mid-list (IME re-Enter) is not a keypad target and stays closed.
            openKeypad(newRow.id)
        }

        if (startsNextExercise) {
            // A second Enter on an empty set row explicitly hands ownership to the newly
            // inserted exercise row. The UI consumes this stable row ID after focus succeeds.
            pendingExerciseFocusRowId = newRow.id
            scope.launch {
                delay(50L)
                if (lines.any { it.id == newRow.id }) {
                    // Make sure the row is composed. EditorListSection performs the actual
                    // focus request from the row's attached FocusRequester.
                    listState.scrollToItem(nextIndex)
                }
            }
        }
        saveNote(isLastNote = false, exit = false)
    }

    /**
     * Cycles the flag of the exercise that owns [index]. The exercise row is the single source:
     * the new flag goes to the exercise and every one of its set rows, so what the tags show and
     * what the parser counts (it reads the set rows' flags) always agree (#316).
     */
    fun toggleFlag(index: Int) {
        if (finishing || index !in lines.indices) return
        setFlag(index, lines[exerciseIndexOf(index)].flag.value.next())
    }

    /** Sets the flag of the exercise that owns [index] and all its sets (the `Bi · L/R · SS` segment). */
    fun setFlag(index: Int, newFlag: ExerciseFlag) {
        if (finishing || index !in lines.indices) return
        val exerciseIndex = exerciseIndexOf(index)
        if (lines[exerciseIndex].flag.value == newFlag) return
        markDirty()
        var i = exerciseIndex
        while (i < lines.size) {
            lines[i].flag.value = newFlag
            ensureListSize(i)
            flags[i] = newFlag
            if (i + 1 < lines.size && isMainRow(i + 1)) break
            i++
        }
        saveNote(isLastNote = false, exit = false)
    }

    private fun isMainRow(index: Int): Boolean =
        index == 0 || lines.getOrNull(index - 1)?.text?.value?.text?.isBlank() != false

    internal fun exerciseIndexOf(index: Int): Int {
        var i = index
        while (i > 0 && !isMainRow(i)) i--
        return i
    }

    /**
     * The set being typed: the last row, a set row, with text but no Enter yet. It stays a draft
     * until Enter: saves never stamp it (so after a kill it reopens with the keypad), and Finish
     * leaves it out of the finished workout instead of counting it as a set (#320).
     */
    private fun isActiveSetDraft(index: Int): Boolean =
        index == lines.lastIndex &&
            !isMainRow(index) &&
            lines[index].text.value.text.isNotBlank() &&
            timestamps.getOrElse(index) { "" }.isBlank()

    fun saveNote(
        isLastNote: Boolean,
        exit: Boolean,
        finishWorkout: Boolean = false,
    ) {
        if (finishing || (saved && !exit)) return
        if (exit) finishing = true
        val range = lines.indices
        range.forEach { index ->
            val row = lines[index]
            if (row.text.value.text.isNotBlank() && timestamps.getOrElse(index) { "" }.isBlank() && !isActiveSetDraft(index)) {
                ensureListSize(index)
                timestamps[index] = formatElapsedMinutesSeconds(noteTimestamp, clock(), settings)
            }
            ensureListSize(index)
            flags[index] = row.flag.value
        }
        val plainContent = range.joinToString("\n") { index ->
            if (finishWorkout && isActiveSetDraft(index)) "" else lines[index].text.value.text
        }
        val validTimestamps = range.map { timestamps.getOrElse(it) { "" } }
        val validFlags = range.map { flags.getOrElse(it) { ExerciseFlag.BILATERAL } }
        val rowMetadata = buildNoteRowMetadata(validTimestamps, validFlags)
        val isNew = persistence.currentId == -1L
        val isEmpty = plainContent.isBlank() && persistence.currentTitle.isBlank() && persistence.currentLearnings.isBlank()
        if (isNew && isEmpty) {
            if (exit) finishExit(isLastNote, finishWorkout = true)
            return
        }

        val requestedRevision = editRevision
        val onPersisted = {
            if (requestedRevision == editRevision) saved = true
            if (exit) finishExit(isLastNote, finishWorkout)
        }
        val onFailure: (String) -> Unit = {
            saved = false
            if (exit) finishing = false
        }
        if (finishWorkout) {
            persistence.finalizeWorkout(plainContent, rowMetadata, noteTimestamp, onPersisted, onFailure)
        } else {
            persistence.saveDraft(plainContent, rowMetadata, noteTimestamp, onPersisted, onFailure)
        }
    }

    /** Called with the workout's timestamp right before leaving after an explicit Finish. */
    var onWorkoutFinished: ((Long) -> Unit)? = null

    private fun finishExit(isLastNote: Boolean, finishWorkout: Boolean) {
        if (!shouldStopTimerOnExit(isLastNote, finishWorkout)) {
            onSaveSuccess()
            return
        }
        scope.launch {
            persistence.stopTimer(noteTimestamp)
            // Navigation must run on the main thread. The composition scope already is main in
            // the app; under the Compose test scheduler it can resume elsewhere after DataStore.
            withContext(Dispatchers.Main) {
                onWorkoutFinished?.invoke(noteTimestamp)
                onSaveSuccess()
            }
        }
    }

    private fun addLine(row: NoteRow, index: Int = lines.size) = lines.add(index, row)

    private fun ensureListSize(index: Int) {
        while (timestamps.size <= index) timestamps.add("")
        while (flags.size <= index) flags.add(ExerciseFlag.BILATERAL)
    }

    private fun cleanLists() {
        while (timestamps.size < lines.size) timestamps.add("")
        while (timestamps.size > lines.size) timestamps.removeAt(timestamps.lastIndex)
        while (flags.size < lines.size) flags.add(ExerciseFlag.BILATERAL)
        while (flags.size > lines.size) flags.removeAt(flags.lastIndex)
    }
}

@Composable
fun rememberNoteEditorState(
    viewModel: EditorViewModel,
    settings: Settings,
    note: NoteLine?,
    onSaveSuccess: () -> Unit,
): NoteEditorState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    return remember(viewModel) {
        NoteEditorState(viewModel, settings, context, scope, listState, note, onSaveSuccess)
    }
}
