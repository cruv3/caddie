package com.caddie.app.composition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class NativeExecutionCompositionTest {
    @Test
    fun `shadow is production enabled and does not require shizuku`() {
        val composition = NativeExecutionComposition.shadow()

        assertEquals("android-native-execution-enabled", composition.variant)
        assertTrue(composition.productionEnabled)
        assertFalse(composition.shizukuRequired)
    }
}
