package com.caddie.app.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PassiveTouchInterventionArbiterTest {
    @Test
    fun `tap classification is fast enough to feel immediate`() {
        assertEquals(120L, PassiveTouchInterventionArbiter.CLASSIFICATION_WINDOW_MS)
    }

    @Test
    fun `every touch candidate reports activity before classification`() {
        val activity = mutableListOf<String>()
        val arbiter = PassiveTouchInterventionArbiter(
            captureTarget = { "run-1" },
            observeTouch = { activity += it },
            confirmTouch = { _, _ -> true },
            schedule = { _, _ -> },
        )

        arbiter.onTouchCandidate("first")
        arbiter.onTouchCandidate("second")

        assertEquals(listOf("run-1", "run-1"), activity)
    }

    @Test
    fun `display touch becomes a tap after the classification window`() {
        val confirmed = mutableListOf<String>()
        val scheduled = mutableListOf<Pair<Long, () -> Unit>>()
        val arbiter = PassiveTouchInterventionArbiter(
            captureTarget = { "run-1" },
            confirmTouch = { _, source -> confirmed += source; true },
            schedule = { delay, action -> scheduled += delay to action },
        )

        arbiter.onTouchCandidate("display")

        assertEquals(
            PassiveTouchInterventionArbiter.CLASSIFICATION_WINDOW_MS,
            scheduled.single().first,
        )
        assertTrue(confirmed.isEmpty())
        scheduled.single().second()
        assertEquals(listOf("display"), confirmed)
    }

    @Test
    fun `scroll observed during classification suppresses the touch`() {
        var confirmations = 0
        val scheduled = mutableListOf<() -> Unit>()
        val arbiter = PassiveTouchInterventionArbiter(
            captureTarget = { "run-1" },
            confirmTouch = { _, _ -> confirmations += 1; true },
            schedule = { _, action -> scheduled += action },
        )

        arbiter.onTouchCandidate("display")
        assertTrue(arbiter.onScrollObserved())
        scheduled.single()()

        assertEquals(0, confirmations)
        assertFalse(arbiter.onScrollObserved())
    }

    @Test
    fun `semantic human click confirms immediately and invalidates delayed candidate`() {
        val confirmed = mutableListOf<String>()
        val scheduled = mutableListOf<() -> Unit>()
        val arbiter = PassiveTouchInterventionArbiter(
            captureTarget = { "run-1" },
            confirmTouch = { _, source -> confirmed += source; true },
            schedule = { _, action -> scheduled += action },
        )

        arbiter.onTouchCandidate("display")
        assertTrue(arbiter.onConfirmedTouch("accessibility:click"))
        scheduled.single()()

        assertEquals(listOf("accessibility:click"), confirmed)
    }

    @Test
    fun `new candidate replaces the previous delayed candidate`() {
        val confirmed = mutableListOf<String>()
        val scheduled = mutableListOf<() -> Unit>()
        val arbiter = PassiveTouchInterventionArbiter(
            captureTarget = { "run-1" },
            confirmTouch = { _, source -> confirmed += source; true },
            schedule = { _, action -> scheduled += action },
        )

        arbiter.onTouchCandidate("first")
        arbiter.onTouchCandidate("second")
        scheduled[0]()
        scheduled[1]()

        assertEquals(listOf("second"), confirmed)
    }

    @Test
    fun `close invalidates a delayed candidate`() {
        var confirmations = 0
        val scheduled = mutableListOf<() -> Unit>()
        val arbiter = PassiveTouchInterventionArbiter(
            captureTarget = { "run-1" },
            confirmTouch = { _, _ -> confirmations += 1; true },
            schedule = { _, action -> scheduled += action },
        )

        arbiter.onTouchCandidate("display")
        arbiter.close()
        scheduled.single()()

        assertEquals(0, confirmations)
    }

    @Test
    fun `touch without an active run never creates a delayed confirmation`() {
        var confirmations = 0
        val scheduled = mutableListOf<() -> Unit>()
        val arbiter = PassiveTouchInterventionArbiter(
            captureTarget = { null },
            confirmTouch = { _, _ -> confirmations += 1; true },
            schedule = { _, action -> scheduled += action },
        )

        assertFalse(arbiter.onTouchCandidate("display"))

        assertTrue(scheduled.isEmpty())
        assertEquals(0, confirmations)
    }

    @Test
    fun `delayed confirmation keeps the run captured at touch time`() {
        var activeRun: String? = "run-1"
        val confirmed = mutableListOf<Pair<String, String>>()
        val scheduled = mutableListOf<() -> Unit>()
        val arbiter = PassiveTouchInterventionArbiter(
            captureTarget = { activeRun },
            confirmTouch = { run, source -> confirmed += run to source; run == activeRun },
            schedule = { _, action -> scheduled += action },
        )

        assertTrue(arbiter.onTouchCandidate("display"))
        activeRun = "run-2"
        scheduled.single()()

        assertEquals(listOf("run-1" to "display"), confirmed)
    }

    @Test
    fun `immediate click keeps the run captured at touch time`() {
        var activeRun: String? = "run-1"
        val confirmed = mutableListOf<Pair<String, String>>()
        val arbiter = PassiveTouchInterventionArbiter(
            captureTarget = { activeRun },
            confirmTouch = { run, source -> confirmed += run to source; run == activeRun },
            schedule = { _, _ -> },
        )

        assertTrue(arbiter.onTouchCandidate("display"))
        activeRun = "run-2"
        assertFalse(arbiter.onConfirmedTouch("accessibility:click"))

        assertEquals(listOf("run-1" to "accessibility:click"), confirmed)
    }
}
