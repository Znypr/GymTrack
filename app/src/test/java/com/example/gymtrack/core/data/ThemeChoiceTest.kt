package com.example.gymtrack.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeChoiceTest {

    @Test
    fun `stored choice wins over the legacy dark-mode flag`() {
        ThemeChoice.entries.forEach { choice ->
            assertEquals(choice, ThemeChoice.fromStorage(choice.storageValue, legacyDarkMode = true))
            assertEquals(choice, ThemeChoice.fromStorage(choice.storageValue, legacyDarkMode = false))
        }
    }

    @Test
    fun `v1 dark-mode switch migrates to Normal dark or light`() {
        assertEquals(ThemeChoice.NORMAL_DARK, ThemeChoice.fromStorage(null, legacyDarkMode = true))
        assertEquals(ThemeChoice.NORMAL_LIGHT, ThemeChoice.fromStorage(null, legacyDarkMode = false))
    }

    @Test
    fun `fresh install uses the model default`() {
        assertEquals(Settings.DEFAULT_THEME, ThemeChoice.fromStorage(null, legacyDarkMode = null))
        assertEquals(ThemeChoice.NORMAL_DARK, Settings.DEFAULT_THEME)
    }

    @Test
    fun `unknown stored value falls back like a missing one`() {
        assertEquals(ThemeChoice.NORMAL_LIGHT, ThemeChoice.fromStorage("NEON", legacyDarkMode = false))
        assertEquals(Settings.DEFAULT_THEME, ThemeChoice.fromStorage("", legacyDarkMode = null))
    }

    @Test
    fun `stored values are stable strings`() {
        assertEquals(
            listOf("NORMAL_DARK", "NORMAL_LIGHT", "NOTEBOOK", "SYSTEM"),
            ThemeChoice.entries.map { it.storageValue },
        )
        assertEquals(ThemeChoice.NOTEBOOK, ThemeChoice.fromStorage(" notebook ", null))
    }

    @Test
    fun `dark resolution follows the system only for SYSTEM`() {
        assertTrue(ThemeChoice.NORMAL_DARK.isDark(systemDark = false))
        assertFalse(ThemeChoice.NORMAL_LIGHT.isDark(systemDark = true))
        assertFalse(ThemeChoice.NOTEBOOK.isDark(systemDark = true))
        assertTrue(ThemeChoice.SYSTEM.isDark(systemDark = true))
        assertFalse(ThemeChoice.SYSTEM.isDark(systemDark = false))
    }
}
