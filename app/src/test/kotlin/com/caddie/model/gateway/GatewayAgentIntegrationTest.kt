package com.caddie.model.gateway

import com.caddie.agent.core.AgentLoop
import com.caddie.agent.core.AgentMessage
import com.caddie.agent.core.ModelDelta
import com.caddie.agent.core.ModelRequest
import com.caddie.agent.core.OversightDecision
import com.caddie.agent.core.OversightPolicy
import com.caddie.agent.core.RequestFactory
import com.caddie.agent.core.RunId
import com.caddie.agent.core.RunRecord
import com.caddie.agent.core.RunSnapshot
import com.caddie.agent.core.RunState
import com.caddie.agent.core.SessionStore
import com.caddie.agent.core.StepId
import com.caddie.agent.core.StepOutcome
import com.caddie.agent.core.ToolDefinition
import com.caddie.agent.core.ToolRegistry
import com.caddie.agent.core.ToolResult
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GatewayAgentIntegrationTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `truncated stream pauses network and executes no tool`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setHeader(
                        "Content-Type",
                        "text/event-stream",
                    ).setBody(
                        """data: {"choices":[{"delta":{"content":"partial"},"finish_reason":null}]}""" +
                            "\n\n",
                    ),
            )
            val tools = RecordingTools()
            val store = RecordingStore()
            val loop = loop(tools, store)

            val outcome =
                loop.step(
                    RunId("run-1"),
                    StepId("step-1"),
                )

            assertEquals(
                StepOutcome.PAUSED_NETWORK,
                outcome,
            )
            assertEquals(0, tools.executionCount)
            assertEquals(
                RunState.PAUSED_NETWORK,
                (
                    store.records.single()
                        as RunRecord.RunPaused
                ).state,
            )
        }

    @Test
    fun `completed call crosses oversight before one tool dispatch`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setHeader(
                        "Content-Type",
                        "text/event-stream",
                    ).setBody(
                        """
                        data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call-1","function":{"name":"phone.open","arguments":"{}"}}]},"finish_reason":"tool_calls"}]}

                        data: [DONE]

                        """.trimIndent(),
                    ),
            )
            val order = mutableListOf<String>()
            val tools =
                RecordingTools {
                    order += "execute"
                }
            val store =
                RecordingStore { record ->
                    if (
                        record
                            is RunRecord.ToolDispatched
                    ) {
                        order += "dispatch"
                    }
                }
            val loop =
                AgentLoop(
                    modelClient(),
                    tools,
                    object : OversightPolicy {
                        override suspend fun approve(
                            call: ModelDelta.ToolCall,
                        ): OversightDecision {
                            order += "oversight"
                            return OversightDecision(true)
                        }
                    },
                    store,
                    store,
                )

            val outcome =
                loop.step(
                    RunId("run-1"),
                    StepId("step-1"),
                )

            assertEquals(
                StepOutcome.TOOL_FINISHED,
                outcome,
            )
            assertEquals(
                listOf(
                    "oversight",
                    "dispatch",
                    "execute",
                ),
                order,
            )
            assertEquals(1, tools.executionCount)
        }

    private fun loop(
        tools: RecordingTools,
        store: RecordingStore,
    ) = AgentLoop(
        modelClient(),
        tools,
        object : OversightPolicy {
            override suspend fun approve(
                call: ModelDelta.ToolCall,
            ) = OversightDecision(true)
        },
        store,
        store,
    )

    private fun modelClient() =
        GatewayModelClient(
            GatewayConfiguration(
                server.url("/").toString(),
                ModelProfile(
                    "test",
                    "synthetic-model",
                ),
                retryPolicy =
                    RetryPolicy(maxAttempts = 1),
            ),
        )

    private class RecordingTools(
        private val beforeResult: () -> Unit = {},
    ) : ToolRegistry {
        var executionCount = 0
            private set

        override fun definitions() =
            listOf(
                ToolDefinition(
                    "phone.open",
                    "Open",
                    """{"type":"object"}""",
                ),
            )

        override suspend fun execute(
            runId: RunId,
            call: ModelDelta.ToolCall,
        ): ToolResult {
            executionCount += 1
            beforeResult()
            return ToolResult(
                call.id,
                """{"ok":true}""",
            )
        }
    }

    private class RecordingStore(
        private val onAppend: (RunRecord) -> Unit = {},
    ) : SessionStore, RequestFactory {
        val records = mutableListOf<RunRecord>()

        override suspend fun snapshot(
            runId: RunId,
        ) = RunSnapshot(
            runId,
            RunState.RUNNING,
            listOf(
                AgentMessage(
                    AgentMessage.Role.USER,
                    "Synthetic request",
                ),
            ),
        )

        override suspend fun append(
            event: RunRecord,
        ) {
            records += event
            onAppend(event)
        }

        override fun create(
            snapshot: RunSnapshot,
            tools: List<ToolDefinition>,
        ) = ModelRequest(
            snapshot.runId,
            snapshot.messages,
            tools,
        )
    }
}
