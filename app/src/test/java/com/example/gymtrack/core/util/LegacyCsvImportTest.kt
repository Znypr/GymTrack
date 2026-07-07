package com.example.gymtrack.core.util

import com.example.gymtrack.core.data.ExerciseFlag
import com.example.gymtrack.core.data.Settings
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyCsvImportTest {
    @Test
    fun importsOldIndexedPhoneExportWithTimesAndFlags() {
        val csv = """
            Title,Category,Timestamp,Learnings
            ,Legs,30/06/2026 16:32,
            Main Index,Main Entry,Time,Flag
            0,seated hamstrings cy,0'00'',bi
            1,reverse hacksquat,16'40'',bi
            2,leg press,56'55'',bi
            3,leg extension RL,57'25'',uni
            Main Index,Sub Entry,Time,Flag
            0,10x 67kg (0'05''),0'05'',1x
            0,6x 77kg (2'12''),2'20'',1x
            1,6x 170kg (0'05''),16'45'',1x
            2,7x 75kg (0'00''),57'15'',1x
            3,6x 40kg (0'00''),60'00'',2x
        """.trimIndent()
        val file = File.createTempFile("legacy-gymtrack", ".csv").apply { writeText(csv) }

        try {
            val note = importNote(file, Settings())

            assertNotNull(note)
            requireNotNull(note)
            assertEquals("", note.title)
            assertEquals("Legs", note.categoryName)
            assertEquals(parseFullDateTime("30/06/2026 16:32"), note.timestamp)
            assertEquals("", note.learnings)
            assertTrue(note.text.contains("seated hamstrings cy"))
            assertTrue(note.text.contains("reverse hacksquat"))
            assertTrue(note.text.contains("    10x 67kg (0'05'')"))
            assertTrue(note.text.contains("    6x 40kg (0'00'')"))

            val parsed = parseNoteText(note.text, note.rowMetadata)
            assertEquals("0'00''", parsed.second[0])
            assertEquals("0'05''", parsed.second[1])
            assertEquals(ExerciseFlag.UNILATERAL, parsed.third[parsed.third.lastIndex])
        } finally {
            file.delete()
        }
    }
}
