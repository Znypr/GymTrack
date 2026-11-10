package com.example.gymtrack.feature.about

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegalTextTest {
    private val moduleDir: File = listOf(File("."), File("app")).first { File(it, "build.gradle.kts").isFile && File(it, "src/main").isDirectory }
    private val docsLegal = File(moduleDir.canonicalFile.parentFile, "docs/legal")

    private fun asset(doc: LegalDoc) = File(moduleDir, "src/main/assets/${doc.assetPath}")

    @Test
    fun inAppCopiesMatchDocsLegal() {
        LegalDoc.values().forEach { doc ->
            val source = File(docsLegal, doc.assetPath.substringAfter('/'))
            assertEquals(
                "assets/${doc.assetPath} is out of date: copy docs/legal/${source.name} over it",
                source.readText().replace("\r\n", "\n"),
                asset(doc).readText().replace("\r\n", "\n"),
            )
        }
    }

    @Test
    fun internalNotesAndAppendixAreLeftOut() {
        val privacy = LegalText.stripInternal(asset(LegalDoc.PRIVACY_EN).readText())
        assertFalse(privacy.contains("[pro]"))
        assertFalse(privacy.contains("Appendix"))
        assertFalse(privacy.lines().any { it.trimStart().startsWith(">") })
        assertTrue(privacy.contains("no internet permission"))
    }

    @Test
    fun impressumPlaceholdersStayVisible() {
        val text = LegalText.blocks(asset(LegalDoc.IMPRESSUM).readText())
            .joinToString("\n") { block ->
                when (block) {
                    is LegalBlock.Heading -> block.text
                    is LegalBlock.Paragraph -> block.spans.joinToString("") { it.text }
                    is LegalBlock.Bullet -> block.spans.joinToString("") { it.text }
                }
            }
        assertTrue(text.contains("[NAME — to be filled by the owner]"))
        assertTrue(text.contains("[ADDRESS — to be filled by the owner]"))
        assertFalse(text.contains("[pro]"))
    }

    @Test
    fun inlineMarkdownBecomesSpans() {
        val spans = LegalText.spans("Email: [a@b.de](mailto:a@b.de), see **[Impressum](IMPRESSUM.md)** and [site](https://x.y)")
        assertEquals("Email: a@b.de, see Impressum and site (https://x.y)", spans.joinToString("") { it.text })
        assertTrue(spans.single { it.bold }.text == "Impressum")
    }

    @Test
    fun blocksHaveHeadingsBulletsAndParagraphs() {
        val blocks = LegalText.blocks("# Title\n\nLine one\nline two\n\n- **a** b\n- c\n")
        assertEquals(LegalBlock.Heading(1, "Title"), blocks[0])
        assertEquals(LegalBlock.Paragraph(listOf(Span("Line one line two"))), blocks[1])
        assertEquals(LegalBlock.Bullet(listOf(Span("a", bold = true), Span(" b"))), blocks[2])
        assertEquals(4, blocks.size)
    }
}
