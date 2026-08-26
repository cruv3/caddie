package com.caddie.app.composition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidRuntimeCompositionTest {
    @Test
    fun `phase one enables persistence factory`() {
        var factoryCalls = 0

        val composition = AndroidRuntimeComposition.phaseOne {
            factoryCalls += 1
            error("Persistence must remain disabled")
        }

        assertEquals(0, factoryCalls)
        assertEquals("android-native-phase-1", composition.variant)
        assertTrue(composition.productionEnabled)
        assertTrue(composition.persistenceEnabled)
    }
}
