package com.caddie.executor.accessibility

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityWindowTransitionGateTest {
    @Test
    fun `await blocks until the next expected window event`() = runTest {
        val gate = AccessibilityWindowTransitionGate(timeoutMillis = 1_000)
        gate.expectNextWindow()

        val waiting = async { gate.awaitExpectedWindow() }
        yield()
        assertFalse(waiting.isCompleted)

        gate.notifyWindowChanged()

        waiting.await()
        assertTrue(waiting.isCompleted)
    }

    @Test
    fun `event arriving before await still satisfies expected transition`() = runTest {
        val gate = AccessibilityWindowTransitionGate(timeoutMillis = 1_000)
        gate.expectNextWindow()
        gate.notifyWindowChanged()

        gate.awaitExpectedWindow()
    }

    @Test
    fun `ordinary observation without expected transition never waits`() = runTest {
        val gate = AccessibilityWindowTransitionGate(timeoutMillis = 1_000)

        gate.awaitExpectedWindow()
    }
}
