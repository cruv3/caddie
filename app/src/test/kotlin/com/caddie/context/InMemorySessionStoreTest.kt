package com.caddie.context

import com.caddie.agent.core.RunId
import com.caddie.agent.core.RunRecord
import com.caddie.agent.core.RunState
import com.caddie.agent.core.SessionId
import com.caddie.agent.core.StepId
import com.caddie.agent.core.ToolCallId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class InMemorySessionStoreTest {
    @Test
    fun `duplicate tool result id is idempotent`() = runTest {
        val store = InMemorySessionStore()
        val result = RunRecord.ToolFinished(
            RunId("r1"),
            ToolCallId("c1"),
            "{}",
            false,
        )

        store.append(result)
        store.append(result)

        assertEquals(1, store.records(RunId("r1")).size)
    }

    @Test
    fun `separate pause and resume cycles keep distinct stable records`() =
        runTest {
            val store = InMemorySessionStore()
            val runId = RunId("r1")
            val records = listOf(
                RunRecord.RunCreated(SessionId("s1"), runId, "Task"),
                RunRecord.RunStarted(runId),
                RunRecord.RunPaused(
                    runId,
                    StepId("pause-1"),
                    RunState.PAUSED_NETWORK,
                    "offline",
                ),
                RunRecord.RunResumed(runId, StepId("resume-1")),
                RunRecord.RunPaused(
                    runId,
                    StepId("pause-2"),
                    RunState.PAUSED_NETWORK,
                    "offline",
                ),
                RunRecord.RunResumed(runId, StepId("resume-2")),
            )

            records.forEach { store.append(it) }

            assertEquals(records, store.records(runId))
        }
}
