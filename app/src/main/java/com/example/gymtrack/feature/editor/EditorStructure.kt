package com.example.gymtrack.feature.editor

import com.example.gymtrack.core.data.ExerciseFlag

/** How a row is drawn. Derived from position, exactly like the storage rule (EDITOR_BEHAVIOUR §1.2). */
enum class EditorRowKind {
    /** First row, or any row after a blank row: the exercise name. */
    EXERCISE,

    /** A set row, or the open (still blank) set row at the end of the list. */
    SET,

    /** A blank row followed by another row: the gap between two exercises. */
    SEPARATOR,
}

/** Where a row sits on a superset bracket (EDITOR_V2 §3). */
enum class BracketPosition { NONE, TOP, MIDDLE, BOTTOM, SINGLE }

data class EditorRowInfo(
    val kind: EditorRowKind,
    /** Index of the exercise row that owns this row. */
    val exerciseIndex: Int,
    /** The exercise's flag (the single source; set rows follow it). */
    val exerciseFlag: ExerciseFlag,
    /** True on the row under which the exercise block's `+ set` belongs. */
    val isBlockEnd: Boolean,
    val bracket: BracketPosition,
)

/**
 * Builds the per-row layout from blank-ness and flags only (no text parsing), so it is cheap to
 * keep in a derivedStateOf: typing digits into the open set row never changes the result.
 */
fun buildEditorStructure(isBlank: List<Boolean>, flags: List<ExerciseFlag>): List<EditorRowInfo> {
    val size = isBlank.size
    if (size == 0) return emptyList()
    fun isMain(i: Int) = i == 0 || isBlank[i - 1]

    val kinds = List(size) { i ->
        when {
            isMain(i) -> EditorRowKind.EXERCISE
            isBlank[i] && i < size - 1 -> EditorRowKind.SEPARATOR
            else -> EditorRowKind.SET
        }
    }
    val exerciseOf = IntArray(size)
    var current = 0
    for (i in 0 until size) {
        if (kinds[i] == EditorRowKind.EXERCISE) current = i
        exerciseOf[i] = current
    }
    // Last row of each block that is not the separator.
    val blockEnds = mutableSetOf<Int>()
    for (i in 0 until size) {
        val nextIsNewBlock = i == size - 1 || kinds[i + 1] == EditorRowKind.EXERCISE
        if (nextIsNewBlock) {
            var end = i
            if (kinds[end] == EditorRowKind.SEPARATOR && end > exerciseOf[i]) end--
            blockEnds += end
        }
    }

    // Superset groups: consecutive exercise blocks whose exercise flag is SUPERSET.
    val bracket = Array(size) { BracketPosition.NONE }
    var i = 0
    while (i < size) {
        val ex = exerciseOf[i]
        val isSs = flags.getOrElse(ex) { ExerciseFlag.BILATERAL } == ExerciseFlag.SUPERSET
        if (!isSs || kinds[i] != EditorRowKind.EXERCISE || isBlank[i]) { i++; continue }
        // Extend over following blocks that are also supersets.
        var lastRow = i
        var j = i
        while (j < size) {
            val exJ = exerciseOf[j]
            val ssJ = flags.getOrElse(exJ) { ExerciseFlag.BILATERAL } == ExerciseFlag.SUPERSET
            if (!ssJ || (kinds[exJ] == EditorRowKind.EXERCISE && isBlank[exJ])) break
            if (kinds[j] != EditorRowKind.SEPARATOR) lastRow = j
            j++
        }
        for (r in i..lastRow) {
            bracket[r] = when {
                r == i && r == lastRow -> BracketPosition.SINGLE
                r == i -> BracketPosition.TOP
                r == lastRow -> BracketPosition.BOTTOM
                else -> BracketPosition.MIDDLE
            }
        }
        i = j
    }

    return List(size) { r ->
        EditorRowInfo(
            kind = kinds[r],
            exerciseIndex = exerciseOf[r],
            exerciseFlag = flags.getOrElse(exerciseOf[r]) { ExerciseFlag.BILATERAL },
            isBlockEnd = r in blockEnds,
            bracket = bracket[r],
        )
    }
}
