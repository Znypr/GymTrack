package com.example.gymtrack.feature.editor.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ActiveSetScrollPolicyTest {

    @Test
    fun `early active rows anchor at top`() {
        assertEquals(0, activeSetAnchorIndex(0))
        assertEquals(0, activeSetAnchorIndex(1))
        assertEquals(0, activeSetAnchorIndex(2))
    }

    @Test
    fun `a row cut by the bar is pushed up just enough (#366)`() {
        // Viewport ends at the accessory bar (y 1825 px on the device that timed out).
        assertEquals(RevealMove.BY_DELTA, revealMove(itemOffset = 1780, itemSize = 120, viewportStart = 0, viewportEnd = 1825))
        assertEquals(RevealMove.NONE, revealMove(itemOffset = 1700, itemSize = 120, viewportStart = 0, viewportEnd = 1825))
    }

    @Test
    fun `an off-screen row jumps back and a row above the top comes to the top`() {
        assertEquals(RevealMove.TO_ANCHOR, revealMove(itemOffset = null, itemSize = 0, viewportStart = 0, viewportEnd = 1825))
        assertEquals(RevealMove.TO_TOP, revealMove(itemOffset = -40, itemSize = 120, viewportStart = 0, viewportEnd = 1825))
    }

    @Test
    fun `later active rows retain two rows of context`() {
        assertEquals(1, activeSetAnchorIndex(3))
        assertEquals(4, activeSetAnchorIndex(6))
        assertEquals(8, activeSetAnchorIndex(10))
    }
}
