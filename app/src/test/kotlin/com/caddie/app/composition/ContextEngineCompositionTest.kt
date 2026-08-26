package com.caddie.app.composition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ContextEngineCompositionTest {
    @Test
    fun `disabled composition creates no runtime object`() {
        var creations = 0

        val composition = ContextEngineComposition.disabled(
            config = ContextEngineConfig(),
            providerFactory = { creations++; error("disabled") },
        )

        assertFalse(composition.productionEnabled)
        assertEquals(0, creations)
        assertFalse(composition.canStart)
        assertEquals(null, composition.providerOrNull())
    }
}
