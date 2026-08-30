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
    fun `later active rows retain two rows of context`() {
        assertEquals(1, activeSetAnchorIndex(3))
        assertEquals(4, activeSetAnchorIndex(6))
        assertEquals(8, activeSetAnchorIndex(10))
    }
}
