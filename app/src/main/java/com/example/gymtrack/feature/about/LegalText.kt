package com.example.gymtrack.feature.about

/**
 * The legal documents shown in-app, offline (#338). The files in `assets/legal/` are copies of
 * the markdown files in `docs/legal` (a unit test fails when they drift). The public page is built from the same
 * markdown by `docs/legal/build_pages.py`; [stripInternal] mirrors what that script leaves out.
 */
enum class LegalDoc(val assetPath: String, val title: String, val languageTag: String) {
    PRIVACY_EN("legal/PRIVACY_POLICY.md", "Privacy policy", "en"),
    PRIVACY_DE("legal/DATENSCHUTZ.md", "Datenschutzerklärung", "de"),
    IMPRESSUM("legal/IMPRESSUM.md", "Impressum", "de"),
}

/** Public privacy policy URL (Play listing + in-app link). Placeholder until the site is live. */
const val PRIVACY_POLICY_URL = "https://znypr.github.io/GymTrack/privacy"

/** One rendered piece of a legal document. Inline `**bold**` is kept as [Span]s. */
sealed interface LegalBlock {
    data class Heading(val level: Int, val text: String) : LegalBlock
    data class Paragraph(val spans: List<Span>) : LegalBlock
    data class Bullet(val spans: List<Span>) : LegalBlock
}

data class Span(val text: String, val bold: Boolean = false)

object LegalText {
    private val proToLineEnd = Regex("""\s*\*\*\[pro]\*\*.*""")
    private val appendix = Regex("^## Appendix", RegexOption.MULTILINE)
    private val trailingRule = Regex("""\n---\s*$""")
    private val link = Regex("""\[([^\]]+)]\(([^)\s]+)\)""")
    private val code = Regex("`([^`]+)`")
    private val bold = Regex("""\*\*([^*]+)\*\*""")
    private val italic = Regex("""(?<![*\w])\*([^*]+)\*(?![*\w])""")

    /**
     * Same cut as build_pages.py: no internal blockquote notes, no "**[pro]**" remarks, nothing
     * from "## Appendix" on. `[...]` placeholders stay visible until the owner fills them in.
     */
    fun stripInternal(markdown: String): String {
        var md = markdown.replace("\r\n", "\n")
        md = appendix.split(md).first()
        md = md.lines().joinToString("\n") { proToLineEnd.replace(it, "") }
        md = md.lines().filterNot { it.trimStart().startsWith(">") }.joinToString("\n").trimEnd()
        md = trailingRule.replace(md, "")
        return md + "\n"
    }

    /** Headings, paragraphs and bullets of [markdown] after [stripInternal]. */
    fun blocks(markdown: String): List<LegalBlock> {
        val out = mutableListOf<LegalBlock>()
        val paragraph = StringBuilder()
        fun flush() {
            if (paragraph.isNotBlank()) out += LegalBlock.Paragraph(spans(paragraph.toString().trim()))
            paragraph.clear()
        }
        for (raw in stripInternal(markdown).lines()) {
            val line = raw.trimEnd()
            when {
                line.isBlank() || line.trim() == "---" -> flush()
                line.startsWith("#") -> {
                    flush()
                    val level = line.takeWhile { it == '#' }.length
                    out += LegalBlock.Heading(level, plain(line.drop(level).trim()))
                }
                line.trimStart().startsWith("- ") -> {
                    flush()
                    out += LegalBlock.Bullet(spans(line.trimStart().removePrefix("- ").trim()))
                }
                else -> {
                    if (paragraph.isNotEmpty()) paragraph.append(' ')
                    paragraph.append(line.trim())
                }
            }
        }
        flush()
        return out
    }

    /** Inline markdown to spans: links become their label (plus the URL for web links). */
    internal fun spans(text: String): List<Span> {
        val cleaned = link.replace(text) { m ->
            val label = m.groupValues[1]
            val href = m.groupValues[2]
            if (href.startsWith("http") && href != label) "$label ($href)" else label
        }.let { code.replace(it) { m -> m.groupValues[1] } }
            .let { italic.replace(it) { m -> m.groupValues[1] } }
        val result = mutableListOf<Span>()
        var last = 0
        for (m in bold.findAll(cleaned)) {
            if (m.range.first > last) result += Span(cleaned.substring(last, m.range.first))
            result += Span(m.groupValues[1], bold = true)
            last = m.range.last + 1
        }
        if (last < cleaned.length) result += Span(cleaned.substring(last))
        return result
    }

    private fun plain(text: String): String = spans(text).joinToString("") { it.text }
}
