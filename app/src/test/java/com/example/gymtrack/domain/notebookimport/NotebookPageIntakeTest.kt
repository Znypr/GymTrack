package com.example.gymtrack.domain.notebookimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NotebookPageIntakeTest {

    @Test
    fun sha256FingerprintIsDeterministicLowercaseHex() {
        val fingerprint = NotebookPageIntake.sha256Hex("abc".encodeToByteArray())

        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            fingerprint,
        )
    }

    @Test
    fun differentPageBytesProduceDifferentFingerprints() {
        val first = NotebookPageIntake.sha256Hex("page one".encodeToByteArray())
        val second = NotebookPageIntake.sha256Hex("page two".encodeToByteArray())

        assertNotEquals(first, second)
    }

    @Test
    fun createPageDraftsPreservesUploadOrderAndMetadata() {
        val pages = NotebookPageIntake.createPageDrafts(
            listOf(
                NotebookPageSource(
                    id = "page-a",
                    sourceBytes = "first page".encodeToByteArray(),
                    sourceUri = "content://gymtrack/notebook/page-a",
                    capturedAtEpochMillis = 1_000L,
                ),
                NotebookPageSource(
                    id = "page-b",
                    sourceBytes = "second page".encodeToByteArray(),
                    sourceUri = "content://gymtrack/notebook/page-b",
                    capturedAtEpochMillis = 2_000L,
                ),
            )
        )

        assertEquals(listOf("page-a", "page-b"), pages.map { it.id })
        assertEquals(listOf(0, 1), pages.map { it.position })
        assertEquals("content://gymtrack/notebook/page-a", pages.first().sourceUri)
        assertEquals(1_000L, pages.first().capturedAtEpochMillis)
    }

    @Test
    fun duplicateExactPageBytesAreRejectedBeforeBatchCreation() {
        assertThrows(IllegalArgumentException::class.java) {
            NotebookPageIntake.createPageDrafts(
                listOf(
                    NotebookPageSource(
                        id = "page-a",
                        sourceBytes = "same page".encodeToByteArray(),
                    ),
                    NotebookPageSource(
                        id = "page-b",
                        sourceBytes = "same page".encodeToByteArray(),
                    ),
                )
            )
        }
    }

    @Test
    fun sourceIdsMustBeUnique() {
        assertThrows(IllegalArgumentException::class.java) {
            NotebookPageIntake.createPageDrafts(
                listOf(
                    NotebookPageSource(
                        id = "page-a",
                        sourceBytes = "first page".encodeToByteArray(),
                    ),
                    NotebookPageSource(
                        id = "page-a",
                        sourceBytes = "second page".encodeToByteArray(),
                    ),
                )
            )
        }
    }

    @Test
    fun emptySourceBytesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            NotebookPageSource(
                id = "page-a",
                sourceBytes = ByteArray(0),
            )
        }
    }

    @Test
    fun sourceBytesAreCopiedBeforeFingerprinting() {
        val bytes = "original page".encodeToByteArray()
        val source = NotebookPageSource(
            id = "page-a",
            sourceBytes = bytes,
        )
        bytes[0] = 'X'.code.toByte()

        val pages = NotebookPageIntake.createPageDrafts(listOf(source))

        assertEquals(
            NotebookPageIntake.sha256Hex("original page".encodeToByteArray()),
            pages.single().sourceFingerprintSha256,
        )
    }
}
