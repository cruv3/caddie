package com.caddie.app.overlay

import com.caddie.agent.core.OversightDecision
import com.caddie.agent.core.ToolCallId
import com.caddie.app.runtime.NativeOverlayEventMapper
import com.caddie.app.runtime.NativeTaskResult
import com.caddie.app.overlay.event.ThoughtEvent
import com.caddie.study.PendingConfirmation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayServiceStateTest {
    @Test
    fun `confirmation presentation is rejected after destruction or replacement`() {
        assertTrue(shouldPresentConfirmation(serviceDestroyed = false, pendingStillCurrent = true))
        assertFalse(shouldPresentConfirmation(serviceDestroyed = true, pendingStillCurrent = true))
        assertFalse(shouldPresentConfirmation(serviceDestroyed = false, pendingStillCurrent = false))
    }

    @Test
    fun `confirmation arriving after destruction is declined instead of left pending`() = runTest {
        val pending = PendingConfirmation(
            callId = ToolCallId("late-confirmation"),
            toolName = "smartphone_tap_coordinates",
            description = "Confirm action",
            isBatch = false,
            decision = CompletableDeferred<OversightDecision>(),
        )

        rejectConfirmationAfterDestroy(pending)

        assertTrue(pending.decision.isCompleted)
        val decision = pending.decision.await()
        assertFalse(decision.approved)
        assertEquals("overlay service stopped", decision.reason)
    }

    @Test
    fun `native preflight failure uses existing overlay error state`() {
        val event = NativeOverlayEventMapper.map(
            NativeTaskResult.AccessibilityUnavailable,
        ) as ThoughtEvent.TaskFinished

        assertEquals(RunState.Error, OverlayUiState().applyTaskFinished(event).state)
    }

    @Test
    fun `native terminal results stay event driven without duplicate finish event`() {
        assertNull(
            NativeOverlayEventMapper.map(
                NativeTaskResult.Completed(com.caddie.agent.core.RunId("run"), "done"),
            ),
        )
    }

    @Test
    fun `study retry and expired capture never leave a permanent listening pill`() {
        val listening = OverlayUiState(state = RunState.Listening, currentStepLabel = "listening...")

        assertEquals(RunState.Error, listening.applyStudyRetry().state)
        assertEquals(RunState.Hidden, listening.expireListening().state)
        assertEquals(RunState.Acting, OverlayUiState(state = RunState.Acting).expireListening().state)
        assertEquals(RunState.Hidden, listening.applyStudyRetry().expireStudyRetry().state)
        assertEquals(RunState.Acting, OverlayUiState(state = RunState.Acting).expireStudyRetry().state)
    }

    @Test
    fun `text question fallback keeps the question id and clears it only when resolved`() {
        val waiting = OverlayUiState().showTextQuestion(
            question = "Which station should I use?",
            questionId = "run-12:1",
        )

        assertEquals("run-12:1", waiting.textQuestionId)
        assertEquals("Which station should I use?", waiting.topMessage)
        assertEquals(RunState.Listening, waiting.state)

        val resolved = waiting.resolveQuestion()

        assertNull(resolved.textQuestionId)
        assertNull(resolved.topMessage)
        assertEquals(RunState.Thinking, resolved.state)
    }

    @Test
    fun `text question fallback is limited to normal mode when voice capture cannot start`() {
        assertTrue(
            shouldOfferTextQuestionAnswer(
                studyFeaturesEnabled = false,
                voiceCaptureStarted = false,
            ),
        )
        assertFalse(
            shouldOfferTextQuestionAnswer(
                studyFeaturesEnabled = false,
                voiceCaptureStarted = true,
            ),
        )
        assertFalse(
            shouldOfferTextQuestionAnswer(
                studyFeaturesEnabled = true,
                voiceCaptureStarted = false,
            ),
        )
    }
}
