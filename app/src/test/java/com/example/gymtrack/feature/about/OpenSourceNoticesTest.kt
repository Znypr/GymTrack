package com.example.gymtrack.feature.about

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fails when a shipped dependency or bundled font has no notice in [OpenSourceNotices] (#338).
 * Fix: add (or extend) a notice in OpenSourceNotices.kt in the same change.
 */
class OpenSourceNoticesTest {
    private val moduleDir: File = listOf(File("."), File("app")).first { File(it, "build.gradle.kts").isFile && File(it, "src/main").isDirectory }
    private val rootDir: File = moduleDir.canonicalFile.parentFile

    private val shippedConfigurations = setOf("implementation", "api", "runtimeOnly", "kapt", "ksp", "annotationProcessor")
    private val dependencyLine = Regex("""^\s*(\w+)\((.+)\)\s*$""")

    @Test
    fun everyShippedDependencyHasANotice() {
        val coordinates = shippedDependencies()
        assertTrue("no dependencies found; did the build file move?", coordinates.size >= 5)
        val missing = coordinates.filterNot(OpenSourceNotices::covers)
        assertTrue("Add an OssNotice in OpenSourceNotices.kt for: $missing", missing.isEmpty())
    }

    @Test
    fun everyBundledFontHasANoticeAndItsLicenceText() {
        val fonts = File(moduleDir, "src/main/res/font").listFiles().orEmpty().filter { it.isFile }.map { it.name }
        assertTrue(fonts.isNotEmpty())
        val missing = fonts.filterNot { OpenSourceNotices.covers("font:$it") }
        assertTrue("Add an OssNotice for fonts: $missing", missing.isEmpty())
        OpenSourceNotices.bundledLicenseFiles.forEach { path ->
            assertTrue("missing licence text $path", File(moduleDir, "src/main/assets/$path").isFile)
        }
    }

    @Test
    fun aNewDependencyWithoutNoticeIsCaught() {
        // Guards the guard: an unknown library must not be covered by a broad prefix.
        assertTrue(!OpenSourceNotices.covers("com.squareup.okhttp3:okhttp"))
        assertTrue(!OpenSourceNotices.covers("font:new_font.ttf"))
        assertTrue(OpenSourceNotices.covers("androidx.room:room-runtime"))
    }

    /** `group:artifact` of every dependency in a shipped configuration (BOMs included). */
    private fun shippedDependencies(): List<String> {
        val catalog = catalogLibraries()
        val build = File(moduleDir, "build.gradle.kts").readText()
        val block = build.substringAfter("\ndependencies {").substringBefore("\n}")
        return block.lines().mapNotNull { line ->
            val m = dependencyLine.find(line) ?: return@mapNotNull null
            if (m.groupValues[1] !in shippedConfigurations) return@mapNotNull null
            val arg = m.groupValues[2].trim().removePrefix("platform(").removeSuffix(")")
            when {
                arg.startsWith("\"") -> arg.trim('"').split(":").take(2).joinToString(":")
                arg.startsWith("libs.") -> catalog[arg.removePrefix("libs.").replace('.', '-')]
                    ?: error("unknown catalog alias $arg")
                else -> error("unrecognised dependency notation: $line")
            }
        }.distinct()
    }

    /** Version catalog `[libraries]`: alias (dashes) → `group:name`. */
    private fun catalogLibraries(): Map<String, String> {
        val toml = File(rootDir, "gradle/libs.versions.toml").readText()
        val section = toml.substringAfter("[libraries]").substringBefore("\n[")
        val entry = Regex("""^([\w-]+)\s*=\s*\{(.*)}""")
        return section.lines().mapNotNull { line ->
            val m = entry.find(line.trim()) ?: return@mapNotNull null
            val body = m.groupValues[2]
            val module = Regex("""module\s*=\s*"([^"]+)"""").find(body)?.groupValues?.get(1)
            val group = Regex("""group\s*=\s*"([^"]+)"""").find(body)?.groupValues?.get(1)
            val name = Regex("""name\s*=\s*"([^"]+)"""").find(body)?.groupValues?.get(1)
            val coordinate = module ?: if (group != null && name != null) "$group:$name" else return@mapNotNull null
            m.groupValues[1] to coordinate
        }.toMap()
    }
}
