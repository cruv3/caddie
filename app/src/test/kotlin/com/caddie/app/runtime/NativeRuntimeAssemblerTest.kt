package com.caddie.app.runtime

import com.caddie.agent.core.ActionAttemptJournal
import com.caddie.agent.core.ActionDispatchClaim
import com.caddie.agent.core.AgentMessage
import com.caddie.agent.core.ConversationRequestFactory
import com.caddie.agent.core.ModelClient
import com.caddie.agent.core.ModelDelta
import com.caddie.agent.core.ModelRequest
import com.caddie.agent.core.OversightDecision
import com.caddie.agent.core.OversightPolicy
import com.caddie.agent.core.RunId
import com.caddie.agent.core.RunRecord
import com.caddie.agent.core.RunSnapshot
import com.caddie.agent.core.RequestFactory
import com.caddie.agent.core.SessionStore
import com.caddie.agent.core.ToolDefinition
import com.caddie.agent.core.ToolCallId
import com.caddie.executor.accessibility.ActionOutcome
import com.caddie.executor.accessibility.ExecutionGateway
import com.caddie.executor.accessibility.RequestedAction
import com.caddie.executor.accessibility.SemanticTarget
import com.caddie.executor.accessibility.SnapshotCompleteness
import com.caddie.executor.accessibility.UiObservation
import com.caddie.tool.mcp.client.McpClientManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeRuntimeAssemblerTest {
    @Test
    fun `assembler runs model with local semantic and MCP registry`() = runTest {
        val store = InMemoryStore()
        val model = RecordingModel()
        var closeCalls = 0
        val assembler = NativeRuntimeAssembler(
            model = model,
            store = store,
            actionJournal = store,
            mcp = manager(),
            oversight = object : OversightPolicy {
                override suspend fun approve(call: ModelDelta.ToolCall) =
                    OversightDecision(approved = true)
            },
            requestFactory = ConversationRequestFactory("Use provided tools safely."),
            closeAction = { closeCalls += 1 },
        )
        val host = assembler.create(FakeGateway())

        val result = host.run("open settings")
        host.close()
        host.close()

        assertTrue(result is NativeTaskResult.Completed)
        assertEquals("done", (result as NativeTaskResult.Completed).answer)
        assertTrue(model.request.tools.any { it.name == "android.observe" })
        assertEquals(1, closeCalls)
    }

    @Test
    fun `study profile applies only to its own run`() = runTest {
        val store = InMemoryStore()
        val model = RecordingModel()
        val normalOversight = approvingOversight()
        val contextualizedTasks = mutableListOf<String>()
        val host = NativeRuntimeAssembler(
            model = model,
            store = store,
            actionJournal = store,
            mcp = manager(),
            oversight = normalOversight,
            requestFactory = ConversationRequestFactory("normal prompt"),
            normalRequestFactoryProvider = { task, delegate ->
                contextualizedTasks += task
                object : RequestFactory {
                    override fun create(
                        snapshot: RunSnapshot,
                        tools: List<ToolDefinition>,
                    ): ModelRequest {
                        val request = delegate.create(snapshot, tools)
                        return request.copy(
                            messages = request.messages + AgentMessage(
                                AgentMessage.Role.SYSTEM,
                                "retrieved context for $task",
                            ),
                        )
                    }
                }
            },
        ).create(FakeGateway())
        val studyProfile = NativeRunProfile(
            oversight = approvingOversight(),
            requestFactory = ConversationRequestFactory("study prompt"),
        )

        host.run("study task", studyProfile)
        host.run("normal task")
        host.run("normal follow-up")

        assertEquals("study prompt", model.requests[0].messages.first().content)
        assertTrue(model.requests[0].messages.none { "retrieved context" in it.content })
        assertFalse(model.requests[0].tools.any { it.name == "caddie.ask_user" })
        assertEquals("normal prompt", model.requests[1].messages.first().content)
        assertTrue(model.requests[1].tools.any { it.name == "caddie.ask_user" })
        assertTrue(model.requests[1].messages.any {
            it.content == "retrieved context for normal task"
        })
        assertTrue(model.requests[2].messages.first().content.contains("normal task"))
        assertTrue(model.requests[2].messages.first().content.contains("SHORT-TERM"))
        assertEquals(listOf("normal task", "normal follow-up"), contextualizedTasks)
    }

    @Test
    fun `normal short term history is never attached to a study profile`() = runTest {
        val store = InMemoryStore()
        val model = RecordingModel()
        val host = NativeRuntimeAssembler(
            model = model,
            store = store,
            actionJournal = store,
            mcp = manager(),
            oversight = approvingOversight(),
            requestFactory = ConversationRequestFactory("normal prompt"),
        ).create(FakeGateway())
        val studyProfile = NativeRunProfile(
            oversight = approvingOversight(),
            requestFactory = ConversationRequestFactory("study prompt"),
        )

        host.run("normal seed")
        host.run("study task", studyProfile)

        assertTrue(model.requests[0].messages.none { "SHORT-TERM" in it.content })
        assertTrue(model.requests[1].messages.none { "SHORT-TERM" in it.content })
        assertEquals("study prompt", model.requests[1].messages.first().content)
    }

    private class RecordingModel : ModelClient {
        lateinit var request: ModelRequest
        val requests = mutableListOf<ModelRequest>()

        override fun stream(request: ModelRequest) = (
            if (request.tools.any { it.name == "caddie.complete" }) {
                flowOf(
                    ModelDelta.ToolCall(
                        ToolCallId("complete-${requests.size}"),
                        "caddie.complete",
                        """{"message":"done"}""",
                    ),
                    ModelDelta.Completed,
                )
            } else {
                flowOf(ModelDelta.Text("done"), ModelDelta.Completed)
            }
        ).also {
            this.request = request
            requests += request
        }
    }

    private class InMemoryStore : SessionStore, ActionAttemptJournal {
        private val records = linkedMapOf<RunId, MutableList<RunRecord>>()

        override suspend fun snapshot(runId: RunId): RunSnapshot =
            com.caddie.agent.core.reduce(records[runId].orEmpty(), recoveredProcess = false)

        override suspend fun append(event: RunRecord) {
            records.getOrPut(event.runId) { mutableListOf() } += event
        }

        override suspend fun dispatched(record: RunRecord.ActionDispatched): ActionDispatchClaim {
            append(record)
            return ActionDispatchClaim.CLAIMED
        }

        override suspend fun executed(record: RunRecord.ActionExecuted) = append(record)
        override suspend fun terminal(record: RunRecord.ActionTerminal) = append(record)
    }

    private class FakeGateway : ExecutionGateway {
        override suspend fun observe() = UiObservation(
            id = "snapshot",
            capturedAtElapsedRealtimeMillis = 1,
            completeness = SnapshotCompleteness.ALL_INTERACTIVE_WINDOWS,
            inputWindows = emptyList(),
        )

        override suspend fun performSemantic(
            target: SemanticTarget,
            action: RequestedAction,
        ): ActionOutcome = ActionOutcome.Accepted
    }

    private fun manager() = McpClientManager(
        connectionFactory = { error("not used") },
        scope = CoroutineScope(Job()),
    )

    private fun approvingOversight() = object : OversightPolicy {
        override suspend fun approve(call: ModelDelta.ToolCall) =
            OversightDecision(approved = true)
    }
}
