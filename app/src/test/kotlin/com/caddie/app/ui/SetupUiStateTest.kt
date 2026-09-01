package com.caddie.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupUiStateTest {
    @Test
    fun `normal mode requires microphone permission`() {
        val withoutMic = SetupUiState(accessibility = true, overlay = true, mic = false)
        val withMic = withoutMic.copy(mic = true)

        assertFalse(withoutMic.isReady())
        assertTrue(withMic.isReady())
    }

    @Test
    fun `study mode still requires microphone permission`() {
        val withoutMic = SetupUiState(accessibility = true, overlay = true, mic = false)
        val withMic = withoutMic.copy(mic = true)

        assertFalse(withoutMic.isReady())
        assertTrue(withMic.isReady())
    }

    @Test
    fun `accessibility and overlay remain required in every mode`() {
        val missingAccessibility = SetupUiState(accessibility = false, overlay = true, mic = true)
        val missingOverlay = SetupUiState(accessibility = true, overlay = false, mic = true)

        assertFalse(missingAccessibility.isReady())
        assertFalse(missingOverlay.isReady())
    }
}
