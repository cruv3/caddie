package com.llm_smartphone_v2.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObserverConnectionGateTest {
    @Test
    fun ignoresCallbacksFromReplacedConnection() {
        val gate = ObserverConnectionGate()

        val first = gate.beginNewConnection()
        val second = gate.beginNewConnection()

        assertFalse(gate.isCurrent(first))
        assertTrue(gate.isCurrent(second))
        assertNull(gate.scheduleRetry(first))
        assertEquals(0, gate.retryCount)
    }

    @Test
    fun coalescesRetryForCurrentConnection() {
        val gate = ObserverConnectionGate()
        val generation = gate.beginNewConnection()

        assertEquals(2000L, gate.scheduleRetry(generation))
        assertNull(gate.scheduleRetry(generation))
        assertEquals(1, gate.retryCount)
    }
}
