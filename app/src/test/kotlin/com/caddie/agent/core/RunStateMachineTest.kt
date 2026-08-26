package com.caddie.agent.core

import org.junit.Assert.assertEquals
import org.junit.Test

class RunStateMachineTest {
    @Test
    fun `runtime ids preserve non-blank values`() {
        assertEquals("session-1", SessionId("session-1").value)
        assertEquals("run-1", RunId("run-1").value)
        assertEquals("step-1", StepId("step-1").value)
        assertEquals("call-1", ToolCallId("call-1").value)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `runtime ids reject blank values`() {
        RunId(" ")
    }

    @Test
    fun `contract transitions reach expected state`() {
        assertEquals(
            RunState.RUNNING,
            transition(RunState.CREATED, RunEvent.START),
        )
        assertEquals(
            RunState.PAUSED_RECOVERABLE,
            transition(RunState.RUNNING, RunEvent.PROCESS_RECOVERED),
        )
        assertEquals(
            RunState.RUNNING,
            transition(RunState.PAUSED_RECOVERABLE, RunEvent.RESUME),
        )
    }

    @Test(expected = IllegalStateException::class)
    fun `terminal runs cannot restart`() {
        transition(RunState.COMPLETED, RunEvent.START)
    }
}
