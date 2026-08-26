package com.caddie.app.runtime

import com.caddie.agent.core.RunId
import com.caddie.agent.core.RunState
import com.caddie.agent.core.StepOutcome
import com.caddie.app.overlay.event.ThoughtEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeOverlayEventMapperTest {
    @Test
    fun `maps deterministic study narration without exposing tool syntax`() {
        val event = NativeOverlayEventMapper.map(
            NativeAgentEvent.CurrentAction(RunId("run-1"), "Route nach Deutz öffnen"),
        )

        assertEquals(ThoughtEvent.CurrentAction("Route nach Deutz öffnen"), event)
    }

    @Test
    fun `native lifecycle maps to overlay events`() {
        val runId = RunId("run")

        assertTrue(NativeOverlayEventMapper.map(NativeAgentEvent.Started(runId)) is ThoughtEvent.TaskStarted)
        assertTrue(
            NativeOverlayEventMapper.map(
                NativeAgentEvent.StepFinished(runId, StepOutcome.TOOL_FINISHED),
            ) is ThoughtEvent.ToolCallFinished,
        )
        val terminalPause = NativeOverlayEventMapper.map(
            NativeAgentEvent.Paused(runId, RunState.PAUSED_NETWORK),
        )
        assertTrue(terminalPause is ThoughtEvent.TaskFinished)
        assertEquals(false, (terminalPause as ThoughtEvent.TaskFinished).ok)
        assertEquals("Agent angehalten", terminalPause.payload?.optString("message"))
        assertEquals(
            ThoughtEvent.TaskPaused,
            NativeOverlayEventMapper.map(NativeAgentEvent.InterventionPaused(runId)),
        )
        assertEquals(
            ThoughtEvent.TaskResumed,
            NativeOverlayEventMapper.map(NativeAgentEvent.InterventionResumed(runId)),
        )
        assertEquals(
            ThoughtEvent.QuestionAsked("Welcher Bahnhof?", "run:1"),
            NativeOverlayEventMapper.map(
                NativeAgentEvent.QuestionAsked(runId, "run:1", "Welcher Bahnhof?"),
            ),
        )
        assertEquals(
            ThoughtEvent.QuestionResolved,
            NativeOverlayEventMapper.map(NativeAgentEvent.QuestionResolved(runId)),
        )
        val completed = NativeOverlayEventMapper.map(NativeAgentEvent.Completed(runId, "Done"))
        assertTrue(completed is ThoughtEvent.TaskFinished)
        assertTrue((completed as ThoughtEvent.TaskFinished).ok)
        assertEquals("Done", completed.payload?.optString("message"))
        val aborted = NativeOverlayEventMapper.map(NativeAgentEvent.Aborted(runId))
        assertTrue(aborted is ThoughtEvent.TaskFinished)
        assertEquals("stopped", (aborted as ThoughtEvent.TaskFinished).payload?.optString("outcome"))
    }
}
