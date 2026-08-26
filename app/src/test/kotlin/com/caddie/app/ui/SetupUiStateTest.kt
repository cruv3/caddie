package com.caddie.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupUiStateTest {
    @Test
    fun `normal mode is ready without optional microphone permission`() {
        val state = SetupUiState(accessibility = true, overlay = true, mic = false)

        assertTrue(state.isReady(studyFeaturesEnabled = false))
    }

    @Test
    fun `study mode still requires microphone permission`() {
        val withoutMic = SetupUiState(accessibility = true, overlay = true, mic = false)
        val withMic = withoutMic.copy(mic = true)

        assertFalse(withoutMic.isReady(studyFeaturesEnabled = true))
        assertTrue(withMic.isReady(studyFeaturesEnabled = true))
    }

    @Test
    fun `accessibility and overlay remain required in every mode`() {
        val missingAccessibility = SetupUiState(accessibility = false, overlay = true, mic = true)
        val missingOverlay = SetupUiState(accessibility = true, overlay = false, mic = true)

        assertFalse(missingAccessibility.isReady(studyFeaturesEnabled = false))
        assertFalse(missingOverlay.isReady(studyFeaturesEnabled = false))
    }
}
