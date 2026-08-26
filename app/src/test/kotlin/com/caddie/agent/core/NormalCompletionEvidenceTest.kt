package com.caddie.agent.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalCompletionEvidenceTest {
    private val runId = RunId("run")

    @Test
    fun `android mutation requires a later successful observation`() {
        val action = ToolCallId("action")
        val observe = ToolCallId("observe")
        val mutationOnly = snapshot(
            call(action, "android.click"),
            result(action),
        )
        val observed = snapshot(
            call(action, "android.click"),
            result(action),
            call(observe, "android.observe"),
            result(observe),
        )

        assertFalse(NormalCompletionEvidence.isReady(mutationOnly))
        assertTrue(NormalCompletionEvidence.isReady(observed))
        assertFalse(
            NormalCompletionEvidence.isReady(
                observed.copy(failedToolCallIds = setOf(observe)),
            ),
        )
    }

    @Test
    fun `read only or remote work does not require android observation`() {
        assertTrue(NormalCompletionEvidence.isReady(snapshot()))
        assertTrue(
            NormalCompletionEvidence.isReady(
                snapshot(
                    call(ToolCallId("remote"), "mcp__calendar__list_events"),
                    result(ToolCallId("remote")),
                ),
            ),
        )
        val remote = ToolCallId("failed-remote")
        assertFalse(
            NormalCompletionEvidence.isReady(
                snapshot(call(remote, "mcp__calendar__create_event"), result(remote))
                    .copy(failedToolCallIds = setOf(remote)),
            ),
        )
    }

    private fun snapshot(vararg messages: AgentMessage) =
        RunSnapshot(runId, RunState.RUNNING, messages.toList())

    private fun call(id: ToolCallId, name: String) = AgentMessage(
        role = AgentMessage.Role.ASSISTANT,
        content = "",
        toolCall = AgentToolCall(id, name, "{}"),
    )

    private fun result(id: ToolCallId) = AgentMessage(
        role = AgentMessage.Role.TOOL,
        content = "{}",
        toolCallId = id,
    )
}
