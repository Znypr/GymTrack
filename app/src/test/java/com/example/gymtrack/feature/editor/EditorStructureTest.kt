package com.example.gymtrack.feature.editor

import com.example.gymtrack.core.data.ExerciseFlag
import com.example.gymtrack.core.data.ExerciseFlag.BILATERAL
import com.example.gymtrack.core.data.ExerciseFlag.SUPERSET
import com.example.gymtrack.core.data.ExerciseFlag.UNILATERAL
import org.junit.Assert.assertEquals
import org.junit.Test

class EditorStructureTest {

    private fun structure(texts: List<String>, flags: List<ExerciseFlag>) =
        buildEditorStructure(texts.map(String::isBlank), flags)

    @Test
    fun `kinds follow the storage rule, the trailing blank row is the open set row`() {
        val s = structure(
            listOf("Bench", "      8x 80 (1'00'')", "", "Squat", "      5x 100 (2'00'')", ""),
            List(6) { BILATERAL },
        )
        assertEquals(
            listOf(
                EditorRowKind.EXERCISE, EditorRowKind.SET, EditorRowKind.SEPARATOR,
                EditorRowKind.EXERCISE, EditorRowKind.SET, EditorRowKind.SET,
            ),
            s.map { it.kind },
        )
        assertEquals(listOf(0, 0, 0, 3, 3, 3), s.map { it.exerciseIndex })
        // `+ set` belongs under the last set before the separator, and on the open row at the end.
        assertEquals(listOf(1, 5), s.indices.filter { s[it].isBlockEnd })
    }

    @Test
    fun `an exercise without sets is its own block end`() {
        val s = structure(listOf("Bench", "", "Squat", ""), List(4) { BILATERAL })
        assertEquals(listOf(true, false, false, true), s.map { it.isBlockEnd })
    }

    @Test
    fun `flag is read from the exercise row`() {
        val s = structure(listOf("Raise", "      12x 10 (1'00'')", ""), listOf(UNILATERAL, BILATERAL, BILATERAL))
        assertEquals(List(3) { UNILATERAL }, s.map { it.exerciseFlag })
    }

    @Test
    fun `consecutive superset exercises share one bracket`() {
        val texts = listOf(
            "Fly", "      12x 20 (1'00'')", "",
            "Raise", "      15x 12 (0'30'')", "",
            "Pushdown", "      12x 30 (1'00'')",
        )
        val flags = listOf(SUPERSET, SUPERSET, SUPERSET, SUPERSET, SUPERSET, SUPERSET, BILATERAL, BILATERAL)
        val s = structure(texts, flags)
        assertEquals(
            listOf(
                BracketPosition.TOP, BracketPosition.MIDDLE, BracketPosition.MIDDLE,
                BracketPosition.MIDDLE, BracketPosition.BOTTOM, BracketPosition.NONE,
                BracketPosition.NONE, BracketPosition.NONE,
            ),
            s.map { it.bracket },
        )
    }

    @Test
    fun `a lone superset exercise draws a single bracket, bilateral draws none`() {
        val s = structure(listOf("Fly"), listOf(SUPERSET))
        assertEquals(BracketPosition.SINGLE, s[0].bracket)
        assertEquals(BracketPosition.NONE, structure(listOf("Fly"), listOf(BILATERAL))[0].bracket)
    }
}
