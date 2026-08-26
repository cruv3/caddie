package com.caddie.app.runtime

import com.caddie.agent.core.AgentMessage
import com.caddie.agent.core.ModelRequest
import com.caddie.agent.core.RequestFactory
import com.caddie.agent.core.RunId
import com.caddie.agent.core.RunSnapshot
import com.caddie.agent.core.RunState
import com.caddie.agent.core.ToolDefinition
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeRunCorrectionsTest {
    @Test
    fun `correction is injected exactly once into its active run`() {
        val runId = RunId("run")
        val corrections = NativeRunCorrections()
        val factory = CorrectionAwareRequestFactory(BaseFactory, corrections)
        val snapshot = RunSnapshot(runId, RunState.RUNNING)

        corrections.submit(runId, "nicht Anna, sondern Lena")

        val corrected = factory.create(snapshot, emptyList())
        val next = factory.create(snapshot, emptyList())
        assertTrue(corrected.messages.last().content.contains("nicht Anna, sondern Lena"))
        assertFalse(next.messages.any { it.content.contains("nicht Anna, sondern Lena") })
    }

    @Test
    fun `study correction updates frozen progress before building the next request`() {
        val runId = RunId("run")
        val corrections = NativeRunCorrections()
        var frozenStep = "wrong"
        val delegate = object : RequestFactory {
            override fun create(snapshot: RunSnapshot, tools: List<ToolDefinition>) =
                ModelRequest(
                    snapshot.runId,
                    listOf(AgentMessage(AgentMessage.Role.SYSTEM, frozenStep)),
                    tools,
                )
        }
        val factory = CorrectionAwareRequestFactory(delegate, corrections) {
            frozenStep = "correct"
        }
        corrections.submit(runId, "nicht 800, sondern 80,40")

        val request = factory.create(RunSnapshot(runId, RunState.RUNNING), emptyList())

        assertEquals("correct", request.messages.first().content)
    }

    private object BaseFactory : RequestFactory {
        override fun create(snapshot: RunSnapshot, tools: List<ToolDefinition>) =
            ModelRequest(
                snapshot.runId,
                listOf(AgentMessage(AgentMessage.Role.USER, "original task")),
                tools,
            )
    }
}
