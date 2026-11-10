package com.example.gymtrack.core.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.yield
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSessionTest {

    private val stored = Settings(
        is24Hour = false,
        roundingSeconds = 15,
        darkMode = false,
        categories = listOf(Category("Arms", 0xFF123456)) + DEFAULT_CATEGORIES,
        defaultWeightUnit = WeightUnit.LB,
    )

    @Test
    fun `no save happens before load returns`() = runBlocking {
        val loadGate = CompletableDeferred<Settings>()
        val saved = mutableListOf<Settings>()
        val session = SettingsSession(
            scope = this,
            load = { loadGate.await() },
            save = { saved += it },
        )

        val loadJob = session.start()
        repeat(5) { yield() }

        // Load is still suspended: nothing rendered, nothing saved, and early updates are dropped.
        assertNull(session.settings.value)
        assertFalse(session.update(Settings()))
        repeat(5) { yield() }
        assertTrue("save ran before load returned: $saved", saved.isEmpty())

        loadGate.complete(stored)
        loadJob.join()
        repeat(5) { yield() }

        assertEquals(stored, session.settings.value)
        assertTrue("load itself must never trigger a save", saved.isEmpty())
    }

    @Test
    fun `user change after load is saved`() = runBlocking {
        val saved = mutableListOf<Settings>()
        val session = SettingsSession(scope = this, load = { stored }, save = { saved += it })

        session.start().join()
        val changed = stored.copy(darkMode = true)
        assertTrue(session.update(changed))
        repeat(5) { yield() }

        assertEquals(changed, session.settings.value)
        assertEquals(listOf(changed), saved)
    }

    @Test
    fun `failed load shows defaults without writing them`() = runBlocking {
        val saved = mutableListOf<Settings>()
        var reported: Throwable? = null
        val session = SettingsSession(
            scope = this,
            load = { throw java.io.IOException("corrupt") },
            save = { saved += it },
            onLoadError = { reported = it },
        )

        session.start().join()
        repeat(5) { yield() }

        assertEquals(Settings(), session.settings.value)
        assertTrue(saved.isEmpty())
        assertTrue(reported is java.io.IOException)
    }

    @Test
    fun `darkMode has one default`() {
        assertEquals(Settings.DEFAULT_DARK_MODE, Settings().darkMode)
    }
}
