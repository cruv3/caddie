package com.caddie.agent.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RunReducerTest {
    @Test
    fun `records reconstruct one complete model conversation in order`() {
        val runId = RunId("r1")
        val callId = ToolCallId("c1")
        val records = listOf(
            RunRecord.RunCreated(SessionId("s1"), runId, "Open settings"),
            RunRecord.RunStarted(runId),
            RunRecord.ToolDispatched(
                runId,
                callId,
                "android.click",
                """{"text":"Settings"}""",
            ),
            RunRecord.ToolFinished(runId, callId, """{"ok":true}""", false),
            RunRecord.AssistantCompleted(runId, "Settings is open"),
            RunRecord.RunCompleted(runId),
        )

        val snapshot = reduce(records, recoveredProcess = false)

        assertEquals(RunState.COMPLETED, snapshot.state)
        assertEquals(false, snapshot.hasDispatchedAction)
        assertEquals(
            listOf(
                AgentMessage(AgentMessage.Role.USER, "Open settings"),
                AgentMessage(
                    role = AgentMessage.Role.ASSISTANT,
                    content = "",
                    toolCall = AgentToolCall(
                        callId,
                        "android.click",
                        """{"text":"Settings"}""",
                    ),
                ),
                AgentMessage(
                    role = AgentMessage.Role.TOOL,
                    content = """{"ok":true}""",
                    toolCallId = callId,
                ),
                AgentMessage(AgentMessage.Role.ASSISTANT, "Settings is open"),
            ),
            snapshot.messages,
        )
    }

    @Test
    fun `snapshot remembers that a physical action was claimed`() {
        val runId = RunId("r-action")
        val snapshot = reduce(
            listOf(
                RunRecord.RunCreated(SessionId("s-action"), runId, "Task"),
                RunRecord.RunStarted(runId),
                RunRecord.ActionDispatched(
                    runId = runId,
                    attemptId = AttemptId("attempt-1"),
                    actionKind = "CLICK",
                    selectorFingerprint = null,
                    postconditionFingerprint = null,
                    recoverySpecId = null,
                ),
            ),
            recoveredProcess = false,
        )

        assertEquals(true, snapshot.hasDispatchedAction)
    }

    @Test
    fun `unmatched dispatched tool call recovers paused without redispatch`() {
        val runId = RunId("r1")
        val records = listOf(
            RunRecord.RunCreated(SessionId("s1"), runId, "Task"),
            RunRecord.RunStarted(runId),
            RunRecord.ToolDispatched(
                runId,
                ToolCallId("c1"),
                "smartphone_open_app",
            ),
        )

        val snapshot = reduce(records, recoveredProcess = true)

        assertEquals(RunState.PAUSED_RECOVERABLE, snapshot.state)
        assertEquals(ToolCallId("c1"), snapshot.unverifiedToolCallId)
    }

    @Test
    fun `finished tool call keeps running state after recovery`() {
        val runId = RunId("r1")
        val callId = ToolCallId("c1")
        val records = listOf(
            RunRecord.RunCreated(SessionId("s1"), runId, "Task"),
            RunRecord.RunStarted(runId),
            RunRecord.ToolDispatched(runId, callId, "smartphone_open_app"),
            RunRecord.ToolFinished(runId, callId, """{"ok":true}""", false),
        )

        val snapshot = reduce(records, recoveredProcess = true)

        assertEquals(RunState.RUNNING, snapshot.state)
        assertNull(snapshot.unverifiedToolCallId)
    }

    @Test
    fun `recovered dispatched action stays paused until terminalized`() {
        val runId = RunId("r1")
        val attemptId = AttemptId("attempt-1")
        val records = listOf(
            RunRecord.RunCreated(SessionId("s1"), runId, "Task"),
            RunRecord.RunStarted(runId),
            RunRecord.ActionDispatched(
                runId,
                attemptId,
                "click",
                "selector-v1:abc",
                "post-v1:def",
                "study.send-button-visible",
            ),
        )

        val snapshot = reduce(records, recoveredProcess = true)

        assertEquals(RunState.PAUSED_RECOVERABLE, snapshot.state)
        assertEquals(attemptId, snapshot.unverifiedAttemptId)
    }

    @Test
    fun `terminal recovery record prevents old attempt from becoming dispatchable`() {
        val runId = RunId("r1")
        val attemptId = AttemptId("attempt-1")
        val records = listOf(
            RunRecord.RunCreated(SessionId("s1"), runId, "Task"),
            RunRecord.RunStarted(runId),
            RunRecord.ActionDispatched(
                runId,
                attemptId,
                "click",
                "selector-v1:abc",
                "post-v1:def",
                "study.send-button-visible",
            ),
            RunRecord.ActionTerminal(
                runId,
                attemptId,
                ActionResolution.RECONCILIATION_INCONCLUSIVE,
            ),
        )

        val snapshot = reduce(records, recoveredProcess = true)

        assertNull(snapshot.unverifiedAttemptId)
    }
}
