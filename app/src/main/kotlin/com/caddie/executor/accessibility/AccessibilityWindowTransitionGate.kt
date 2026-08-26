package com.caddie.executor.accessibility

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Waits for the first Accessibility window event caused by a launched activity. */
class AccessibilityWindowTransitionGate(
    private val timeoutMillis: Long = 3_000,
) {
    private val generation = AtomicLong()
    private val expectedGeneration = AtomicReference<Long?>()
    private val changes = MutableStateFlow(0L)

    init {
        require(timeoutMillis > 0) { "Window transition timeout must be positive" }
    }

    fun expectNextWindow() {
        expectedGeneration.set(generation.get() + 1)
    }

    fun clearExpectation() {
        expectedGeneration.set(null)
    }

    fun notifyWindowChanged() {
        changes.value = generation.incrementAndGet()
    }

    suspend fun awaitExpectedWindow() {
        val expected = expectedGeneration.getAndSet(null) ?: return
        if (generation.get() >= expected) return
        withTimeoutOrNull(timeoutMillis) {
            changes.first { it >= expected }
        }
    }
}
