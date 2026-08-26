package com.caddie.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityConnectionStateTest {
    @Test
    fun `connection updates are visible to setup observers`() {
        val state = AccessibilityConnectionState()

        assertFalse(state.connected.value)
        state.update(true)
        assertTrue(state.connected.value)
        state.update(false)
        assertFalse(state.connected.value)
    }
}
