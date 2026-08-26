package com.caddie.runtime.persistence

import com.caddie.agent.core.AgentMessage
import com.caddie.agent.core.RunId
import com.caddie.agent.core.RunRecord
import com.caddie.agent.core.RunSnapshot
import com.caddie.agent.core.RunState
import com.caddie.agent.core.SessionId
import com.caddie.agent.core.SessionStore
import com.caddie.agent.core.ToolCallId
import com.caddie.agent.core.reduce
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessSessionStoreTest {
    @Test
    fun `active process keeps full messages while durable store stays redacted`() = runTest {
        val durable = RedactingStore()
        val store = ProcessSessionStore(durable)
        val runId = RunId("r1")
        val callId = ToolCallId("c1")

        store.append(RunRecord.RunCreated(SessionId("s1"), runId, "Open settings"))
        store.append(RunRecord.RunStarted(runId))
        store.append(
            RunRecord.ToolDispatched(
                runId,
                callId,
                "android.click",
                """{"text":"Settings"}""",
            ),
        )
        store.append(RunRecord.ToolFinished(runId, callId, """{"ok":true}""", false))

        val active = store.snapshot(runId)
        val persisted = durable.snapshot(runId)

        assertEquals("Open settings", active.messages.first().content)
        assertEquals("""{"text":"Settings"}""", active.messages[1].toolCall?.argumentsJson)
        assertEquals("""{"ok":true}""", active.messages[2].content)
        assertTrue(persisted.messages.all { it.content.contains("not persisted") || it.content.isBlank() })
    }

    @Test
    fun `new process sees a running durable run only as recoverable pause`() = runTest {
        val durable = RedactingStore()
        val runId = RunId("r1")
        ProcessSessionStore(durable).apply {
            append(RunRecord.RunCreated(SessionId("s1"), runId, "Private task"))
            append(RunRecord.RunStarted(runId))
        }

        val recovered = ProcessSessionStore(durable).snapshot(runId)

        assertEquals(RunState.PAUSED_RECOVERABLE, recovered.state)
        assertEquals(emptyList<AgentMessage>(), recovered.messages)
    }

    private class RedactingStore : SessionStore {
        private val records = linkedMapOf<RunId, MutableList<RunRecord>>()

        override suspend fun append(event: RunRecord) {
            val redacted = when (event) {
                is RunRecord.RunCreated -> event.copy(task = "[not persisted]")
                is RunRecord.ToolDispatched ->
                    event.copy(argumentsJson = "{}", assistantText = "")
                is RunRecord.ToolFinished ->
                    event.copy(contentJson = """{"payload":"not persisted"}""")
                is RunRecord.AssistantCompleted -> event.copy(text = "[not persisted]")
                else -> event
            }
            records.getOrPut(event.runId) { mutableListOf() }.add(redacted)
        }

        override suspend fun snapshot(runId: RunId): RunSnapshot =
            reduce(records.getValue(runId), recoveredProcess = false)
    }
}
