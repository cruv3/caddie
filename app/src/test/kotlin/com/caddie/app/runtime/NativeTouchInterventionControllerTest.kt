package com.caddie.app.runtime

import com.caddie.agent.core.RunId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeTouchInterventionControllerTest {
    @Test
    fun `touch is ignored when no native run is active`() {
        var pauses = 0
        val scheduled = mutableListOf<() -> Unit>()
        val controller = NativeTouchInterventionController(
            pauseNativeRun = { pauses += 1; false },
            resumeNativeRun = { _ -> true },
            schedule = { _, action -> scheduled += action },
        )

        assertFalse(controller.onHumanTouch(RunId("run-1")))
        assertEquals(1, pauses)
        assertTrue(scheduled.isEmpty())
    }

    @Test
    fun `latest touch owns quiet-time resume`() {
        var pauses = 0
        var resumes = 0
        val delays = mutableListOf<Long>()
        val scheduled = mutableListOf<() -> Unit>()
        val controller = NativeTouchInterventionController(
            pauseNativeRun = { pauses += 1; true },
            resumeNativeRun = { _ -> resumes += 1; true },
            schedule = { delay, action -> delays += delay; scheduled += action },
        )

        assertTrue(controller.onHumanTouch(RunId("run-1")))
        assertTrue(controller.onHumanTouch(RunId("run-1")))
        scheduled[0]()
        assertEquals(0, resumes)
        scheduled[1]()

        assertEquals(1, pauses)
        assertEquals(1, resumes)
        assertEquals(
            listOf(
                NativeTouchInterventionController.RESUME_QUIET_MS,
                NativeTouchInterventionController.RESUME_QUIET_MS,
            ),
            delays,
        )
    }

    @Test
    fun `continued activity refreshes quiet time without pausing the run again`() {
        var pauses = 0
        var resumes = 0
        val scheduled = mutableListOf<() -> Unit>()
        val controller = NativeTouchInterventionController(
            pauseNativeRun = { pauses += 1; true },
            resumeNativeRun = { _ -> resumes += 1; true },
            schedule = { _, action -> scheduled += action },
        )

        assertTrue(controller.onHumanTouch(RunId("run-1")))
        assertTrue(controller.onHumanActivity(RunId("run-1")))
        scheduled[0]()
        assertEquals(0, resumes)
        scheduled[1]()

        assertEquals(1, pauses)
        assertEquals(1, resumes)
    }

    @Test
    fun `provisional contact is silent and can be cancelled as a swipe`() {
        var holds = 0
        var confirmations = 0
        var resumes = 0
        val scheduled = mutableListOf<() -> Unit>()
        val controller = NativeTouchInterventionController(
            pauseNativeRun = { true },
            resumeNativeRun = { _ -> resumes += 1; true },
            schedule = { _, action -> scheduled += action },
            holdNativeRun = { holds += 1; true },
            confirmHeldPause = { confirmations += 1; true },
        )

        assertTrue(controller.beginPotentialTouch(RunId("run-1")))
        assertTrue(scheduled.isEmpty())
        assertTrue(controller.cancelHumanTouch(RunId("run-1")))
        assertEquals(1, holds)
        assertEquals(0, confirmations)
        assertEquals(1, resumes)
    }

    @Test
    fun `confirmed tap starts quiet-time resume only after finger lifts`() {
        var confirmations = 0
        var resumes = 0
        val scheduled = mutableListOf<() -> Unit>()
        val controller = NativeTouchInterventionController(
            pauseNativeRun = { true },
            resumeNativeRun = { _ -> resumes += 1; true },
            schedule = { _, action -> scheduled += action },
            holdNativeRun = { true },
            confirmHeldPause = { confirmations += 1; true },
        )

        assertTrue(controller.beginPotentialTouch(RunId("run-1")))
        assertTrue(scheduled.isEmpty())
        assertTrue(controller.confirmPotentialTouch(RunId("run-1")))
        assertEquals(1, confirmations)
        scheduled.single().invoke()
        assertEquals(1, resumes)
    }

    @Test
    fun `activity does not create a pause before a tap is confirmed`() {
        val scheduled = mutableListOf<() -> Unit>()
        val controller = NativeTouchInterventionController(
            pauseNativeRun = { true },
            resumeNativeRun = { _ -> true },
            schedule = { _, action -> scheduled += action },
        )

        assertFalse(controller.onHumanActivity(RunId("run-1")))
        assertTrue(scheduled.isEmpty())
    }

    @Test
    fun `closing controller releases a paused native run`() {
        var resumes = 0
        val controller = NativeTouchInterventionController(
            pauseNativeRun = { true },
            resumeNativeRun = { _ -> resumes += 1; true },
            schedule = { _, _ -> },
        )

        controller.onHumanTouch(RunId("run-1"))
        controller.close()

        assertEquals(1, resumes)
    }
}
