package com.caddie.agent.core

import com.caddie.tool.mcp.client.McpCallResult
import com.caddie.tool.mcp.client.McpClientManager
import com.caddie.tool.mcp.client.McpConnection
import com.caddie.tool.mcp.client.McpConnectionFactory
import com.caddie.tool.mcp.client.McpRemoteTool
import com.caddie.tool.mcp.client.McpToolPage
import com.caddie.tool.mcp.config.McpServerConfiguration
import com.caddie.tool.registry.DynamicToolRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CancellationException

class AgentLoopTest {
    private val runId = RunId("r1")
    private val stepId = StepId("step-1")
    private val toolCall = ModelDelta.ToolCall(
        ToolCallId("c1"),
        "phone.open",
        "{}",
    )

    @Test
    fun `text only completion records visible answer and completes run`() = runTest {
        val store = RecordingStore()
        val tools = FakeTools()
        val model = SequenceModel(
            flowOf(ModelDelta.Text("Settings "), ModelDelta.Text("is open"), ModelDelta.Completed),
        )
        val loop = AgentLoop(
            model,
            tools,
            ApproveAll,
            store,
            store,
        )

        val outcome = loop.step(runId, stepId)

        assertEquals(StepOutcome.RUN_COMPLETED, outcome)
        assertEquals(1, model.requests.size)
        assertEquals(0, tools.executionCount)
        assertEquals(
            listOf(
                RunRecord.AssistantCompleted(runId, "Settings is open"),
                RunRecord.RunCompleted(runId),
            ),
            store.appended,
        )
    }

    @Test
    fun `normal runtime pauses after exactly one unsuccessful text repair`() = runTest {
        val store = RecordingStore()
        val model = SequenceModel(
            flowOf(ModelDelta.Text("I think it worked")),
            flowOf(ModelDelta.Text("It worked")),
        )
        val tools = FakeTools()
        val reports = mutableListOf<String>()
        val loop = AgentLoop(
            model = model,
            tools = tools,
            oversight = ApproveAll,
            store = store,
            requestFactory = store,
            requireTerminalCall = true,
            failureReporter = reports::add,
        )

        assertEquals(StepOutcome.PAUSED_RECOVERABLE, loop.step(runId, stepId))
        assertEquals(2, model.requests.size)
        assertEquals(0, tools.executionCount)
        assertEquals(2, reports.size)
        assertTrue(store.appended.single() is RunRecord.RunPaused)
        assertTrue((store.appended.single() as RunRecord.RunPaused).reason!!.contains("after one repair"))
        assertTrue(store.appended.none { it is RunRecord.RunCompleted })
    }

    @Test
    fun `text repair receives runtime feedback and completes only through a real terminal call`() = runTest {
        val observationId = ToolCallId("observed")
        val snapshot = RunSnapshot(
            runId, RunState.RUNNING,
            messages = listOf(
                AgentMessage(AgentMessage.Role.USER, "Open Chrome"),
                AgentMessage(AgentMessage.Role.ASSISTANT, "", toolCall = AgentToolCall(ToolCallId("opened"), "android.open_app", "{}")),
                AgentMessage(AgentMessage.Role.TOOL, "{}", toolCallId = ToolCallId("opened")),
                AgentMessage(AgentMessage.Role.ASSISTANT, "", toolCall = AgentToolCall(observationId, "android.observe", "{}")),
                AgentMessage(AgentMessage.Role.TOOL, "{}", toolCallId = observationId),
            ),
        )
        val store = RecordingStore(snapshot)
        val completion = toolCall.copy(name = "caddie.complete")
        val model = SequenceModel(
            flowOf(ModelDelta.Text("Chrome is open")),
            flowOf(completion),
        )
        val tools = FakeTools {
            assertEquals(completion, it)
            ToolResult(it.id, "{}", continuation = ToolContinuation.COMPLETE_RUN, terminalMessage = "Chrome is open.")
        }
        val loop = AgentLoop(model, tools, ApproveAll, store, store, requireTerminalCall = true)

        assertEquals(StepOutcome.RUN_COMPLETED, loop.step(runId, stepId))
        assertEquals(2, model.requests.size)
        val repaired = model.requests.last()
        assertEquals(model.requests.first().messages, repaired.messages.dropLast(2))
        assertEquals(model.requests.first().tools, repaired.tools)
        assertEquals(AgentMessage(AgentMessage.Role.ASSISTANT, "Chrome is open"), repaired.messages[repaired.messages.lastIndex - 1])
        assertEquals(AgentMessage.Role.SYSTEM, repaired.messages.last().role)
        assertTrue(repaired.messages.last().content.contains("Runtime protocol error"))
        assertTrue(repaired.messages.last().content.contains("No tool was dispatched"))
        assertTrue(repaired.messages.last().content.contains("caddie.complete"))
        assertEquals(1, tools.executionCount)
        assertEquals(listOf("caddie.complete"), store.appended.filterIsInstance<RunRecord.ToolDispatched>().map { it.toolName })
        assertEquals(listOf("Chrome is open."), store.appended.filterIsInstance<RunRecord.AssistantCompleted>().map { it.text })
        assertTrue(store.appended.last() is RunRecord.RunCompleted)
    }

    @Test
    fun `text repair dispatches only the supplied action exactly once`() = runTest {
        val store = RecordingStore()
        val action = toolCall.copy(name = "android.open_app")
        val model = SequenceModel(
            flowOf(ModelDelta.Text("I opened the app")),
            flowOf(ModelDelta.Text("Opening Chrome"), action),
        )
        val tools = FakeTools {
            assertEquals(action, it)
            ToolResult(it.id, "{}")
        }
        val approved = mutableListOf<ModelDelta.ToolCall>()
        val oversight = object : OversightPolicy {
            override suspend fun approve(call: ModelDelta.ToolCall): OversightDecision {
                approved += call
                return OversightDecision(true)
            }
        }
        val loop = AgentLoop(model, tools, oversight, store, store, requireTerminalCall = true)

        assertEquals(StepOutcome.TOOL_FINISHED, loop.step(runId, stepId))
        assertEquals(2, model.requests.size)
        assertEquals(listOf(action), approved)
        assertEquals(1, tools.executionCount)
        assertEquals("Opening Chrome", store.appended.filterIsInstance<RunRecord.ToolDispatched>().single().assistantText)
        assertTrue(store.appended.none { it is RunRecord.AssistantCompleted || it is RunRecord.RunCompleted })
    }

    @Test
    fun `repaired action still requires oversight approval`() = runTest {
        val store = RecordingStore()
        val tools = FakeTools()
        val model = SequenceModel(flowOf(ModelDelta.Text("Done")), flowOf(toolCall))
        val loop = AgentLoop(model, tools, DeclineAll, store, store, requireTerminalCall = true)

        assertEquals(StepOutcome.PAUSED_OVERSIGHT, loop.step(runId, stepId))
        assertEquals(2, model.requests.size)
        assertEquals(0, tools.executionCount)
        assertEquals(RunState.PAUSED_OVERSIGHT, (store.appended.single() as RunRecord.RunPaused).state)
    }

    @Test
    fun `network failures before or during text repair pause without another attempt`() = runTest {
        for (repair in listOf(false, true)) {
            val unavailable = flow<ModelDelta> { throw ModelUnavailableException("gateway_network: unavailable") }
            val model = if (repair) SequenceModel(flowOf(ModelDelta.Text("Done")), unavailable) else SequenceModel(unavailable)
            val store = RecordingStore()
            val tools = FakeTools()
            val loop = AgentLoop(model, tools, ApproveAll, store, store, requireTerminalCall = true)

            assertEquals(StepOutcome.PAUSED_NETWORK, loop.step(runId, stepId))
            assertEquals(if (repair) 2 else 1, model.requests.size)
            assertEquals(0, tools.executionCount)
            assertEquals(RunState.PAUSED_NETWORK, (store.appended.single() as RunRecord.RunPaused).state)
        }
    }

    @Test
    fun `participant correction during text repair prevents stale action dispatch`() = runTest {
        var current = true
        val store = RecordingStore()
        val tools = FakeTools()
        val model = SequenceModel(
            flowOf(ModelDelta.Text("Done")),
            flow {
                current = false
                emit(toolCall)
            },
        )
        val loop = AgentLoop(model, tools, ApproveAll, store, store,
            requireTerminalCall = true, isTurnCurrent = { _, _ -> current })

        assertEquals(StepOutcome.TOOL_SUPERSEDED, loop.step(runId, stepId))
        assertEquals(2, model.requests.size)
        assertEquals(0, tools.executionCount)
        assertTrue(store.appended.isEmpty())
    }

    @Test
    fun `explicit completion finishes with its human message`() = runTest {
        val completion = toolCall.copy(name = "caddie.complete")
        val store = RecordingStore()
        val model = SequenceModel(flowOf(completion))
        val loop = AgentLoop(
            model = model,
            tools = FakeTools { call ->
                ToolResult(
                    callId = call.id,
                    contentJson = """{"ok":true}""",
                    continuation = ToolContinuation.COMPLETE_RUN,
                    terminalMessage = "Die Nachricht wurde gesendet.",
                )
            },
            oversight = ApproveAll,
            store = store,
            requestFactory = store,
            requireTerminalCall = true,
        )

        assertEquals(StepOutcome.RUN_COMPLETED, loop.step(runId, stepId))
        assertEquals(1, model.requests.size)
        assertTrue(store.appended.any {
            it == RunRecord.AssistantCompleted(runId, "Die Nachricht wurde gesendet.")
        })
        assertTrue(store.appended.last() is RunRecord.RunCompleted)
    }

    @Test
    fun `repaired completion after android mutation is rejected until a fresh observation`() = runTest {
        val actionId = ToolCallId("action")
        val store = RecordingStore(
            currentSnapshot = RunSnapshot(
                runId,
                RunState.RUNNING,
                messages = listOf(
                    AgentMessage(
                        AgentMessage.Role.ASSISTANT,
                        "",
                        toolCall = AgentToolCall(actionId, "android.click", "{}"),
                    ),
                    AgentMessage(AgentMessage.Role.TOOL, "{}", toolCallId = actionId),
                ),
            ),
        )
        val completion = toolCall.copy(name = "caddie.complete")
        val model = SequenceModel(flowOf(ModelDelta.Text("Fertig.")), flowOf(completion))
        val loop = AgentLoop(
            model = model,
            tools = FakeTools { call ->
                ToolResult(
                    call.id,
                    """{"ok":true}""",
                    continuation = ToolContinuation.COMPLETE_RUN,
                    terminalMessage = "Fertig.",
                )
            },
            oversight = ApproveAll,
            store = store,
            requestFactory = store,
            requireTerminalCall = true,
        )

        assertEquals(StepOutcome.TOOL_FINISHED, loop.step(runId, stepId))
        assertEquals(2, model.requests.size)
        assertTrue((store.appended.last() as RunRecord.ToolFinished).isError)
        assertTrue(store.appended.none { it is RunRecord.RunCompleted })
    }

    @Test
    fun `explicit failure aborts without reporting success`() = runTest {
        for (repair in listOf(false, true)) {
            val failure = toolCall.copy(name = "caddie.fail")
            val store = RecordingStore()
            val failedTurn = flowOf(ModelDelta.Text("Cannot complete"), failure)
            val model = if (repair) SequenceModel(flowOf(ModelDelta.Text("Done")), failedTurn) else SequenceModel(failedTurn)
            val loop = AgentLoop(
                model = model,
                tools = FakeTools { call ->
                    ToolResult(
                        callId = call.id,
                        contentJson = """{"ok":false}""",
                        isError = true,
                        continuation = ToolContinuation.FAIL_RUN,
                        terminalMessage = "Die Ziel-App ist nicht installiert.",
                    )
                },
                oversight = ApproveAll,
                store = store,
                requestFactory = store,
                requireTerminalCall = true,
            )

            assertEquals(StepOutcome.RUN_ABORTED, loop.step(runId, stepId))
            assertEquals(if (repair) 2 else 1, model.requests.size)
            assertTrue(store.appended.none { it is RunRecord.RunCompleted })
            assertTrue(store.appended.last() is RunRecord.RunAborted)
            assertEquals("Die Ziel-App ist nicht installiert.", store.appended.filterIsInstance<RunRecord.AssistantCompleted>().single().text)
        }
    }

    @Test
    fun `correction arriving during final text supersedes completion`() = runTest {
        var turnCurrent = true
        val store = RecordingStore()
        val loop = AgentLoop(
            model = FakeModel(
                flow {
                    emit(ModelDelta.Text("Old answer"))
                    turnCurrent = false
                    emit(ModelDelta.Completed)
                },
            ),
            tools = FakeTools(),
            oversight = ApproveAll,
            store = store,
            requestFactory = store,
            isTurnCurrent = { _, _ -> turnCurrent },
        )

        assertEquals(StepOutcome.TOOL_SUPERSEDED, loop.step(runId, stepId))
        assertTrue(store.appended.isEmpty())
    }

    @Test
    fun `intervention during model stream discards the stale proposal`() = runTest {
        var revision = 4L
        val store = RecordingStore()
        val tools = FakeTools { error("stale tool must not execute") }
        val loop = AgentLoop(
            model = FakeModel(
                flow {
                    emit(ModelDelta.Text("I will tap it"))
                    revision += 1
                    emit(toolCall)
                },
            ),
            tools = tools,
            oversight = ApproveAll,
            store = store,
            requestFactory = store,
            beginTurn = { revision },
            isTurnCurrent = { _, startedAt -> revision == startedAt },
        )

        assertEquals(StepOutcome.TOOL_SUPERSEDED, loop.step(runId, stepId))
        assertEquals(0, tools.executionCount)
        assertTrue(store.appended.none { it is RunRecord.ToolDispatched })
    }

    @Test
    fun `completion invariant pauses before accepting a premature final answer`() = runTest {
        val store = RecordingStore()
        val tools = FakeTools()
        val transformer = object : ToolCallTransformer {
            override fun transform(call: ModelDelta.ToolCall) = call

            override fun validateCompletion() {
                error("required study action remains")
            }
        }
        val loop = AgentLoop(
            FakeModel(flowOf(ModelDelta.Text("Done"), ModelDelta.Completed)),
            tools,
            ApproveAll,
            store,
            store,
            transformer,
        )

        assertEquals(StepOutcome.PAUSED_PRE_DISPATCH, loop.step(runId, stepId))
        assertEquals(0, tools.executionCount)
        assertEquals(RunState.PAUSED_RECOVERABLE, (store.appended.single() as RunRecord.RunPaused).state)
    }

    @Test
    fun `empty model turn pauses without executing a tool`() = runTest {
        val store = RecordingStore()
        val tools = FakeTools()
        val loop = AgentLoop(
            FakeModel(flowOf(ModelDelta.Completed)),
            tools,
            ApproveAll,
            store,
            store,
        )

        val outcome = loop.step(runId, stepId)

        assertEquals(StepOutcome.PAUSED_RECOVERABLE, outcome)
        assertEquals(0, tools.executionCount)
        assertEquals(RunState.PAUSED_RECOVERABLE, (store.appended.single() as RunRecord.RunPaused).state)
    }

    @Test
    fun `multiple tool calls pause without executing either call`() = runTest {
        val store = RecordingStore()
        val tools = FakeTools()
        val secondCall = toolCall.copy(id = ToolCallId("c2"))
        val loop = AgentLoop(
            FakeModel(flowOf(toolCall, secondCall, ModelDelta.Completed)),
            tools,
            ApproveAll,
            store,
            store,
        )

        val outcome = loop.step(runId, stepId)

        assertEquals(StepOutcome.PAUSED_RECOVERABLE, outcome)
        assertEquals(0, tools.executionCount)
        assertEquals(RunState.PAUSED_RECOVERABLE, (store.appended.single() as RunRecord.RunPaused).state)
    }

    @Test
    fun `conversation request adds system prompt before reconstructed history`() {
        val factory = ConversationRequestFactory("You are Caddie")
        val snapshot = RunSnapshot(
            runId = runId,
            state = RunState.RUNNING,
            messages = listOf(AgentMessage(AgentMessage.Role.USER, "Open settings")),
        )

        val request = factory.create(snapshot, emptyList())

        assertEquals(
            listOf(
                AgentMessage(AgentMessage.Role.SYSTEM, "You are Caddie"),
                AgentMessage(AgentMessage.Role.USER, "Open settings"),
            ),
            request.messages,
        )
    }

    @Test
    fun `one step persists dispatch before execution and result afterward`() =
        runTest {
            val order = mutableListOf<String>()
            val tools = FakeTools { call ->
                order += "execute"
                ToolResult(call.id, """{"ok":true}""")
            }
            val store = RecordingStore { record ->
                order += when (record) {
                    is RunRecord.ToolDispatched -> "ToolDispatched"
                    is RunRecord.ToolFinished -> "ToolFinished"
                    else -> record::class.simpleName.orEmpty()
                }
            }
            val loop = AgentLoop(
                FakeModel(flowOf(toolCall)),
                tools,
                ApproveAll,
                store,
                store,
            )

            val outcome = loop.step(runId, stepId)

            assertEquals(StepOutcome.TOOL_FINISHED, outcome)
            assertEquals(
                listOf("ToolDispatched", "execute", "ToolFinished"),
                order,
            )
            assertEquals(1, tools.executionCount)
        }

    @Test
    fun `approved tool publishes its human action immediately before execution`() = runTest {
        val order = mutableListOf<String>()
        val tools = FakeTools { call ->
            order += "execute:${call.name}"
            ToolResult(call.id, """{"ok":true}""")
        }
        val loop = AgentLoop(
            model = FakeModel(flowOf(toolCall)),
            tools = tools,
            oversight = ApproveAll,
            store = RecordingStore(),
            requestFactory = RecordingStore(),
            onToolDispatch = { _, call -> order += "publish:${call.name}" },
        )

        assertEquals(StepOutcome.TOOL_FINISHED, loop.step(runId, stepId))
        assertEquals(
            listOf("publish:phone.open", "execute:phone.open"),
            order,
        )
    }

    @Test
    fun `dispatch waits for runtime intervention release after oversight`() = runTest {
        var released = false
        val store = RecordingStore()
        val tools = FakeTools { call ->
            assertTrue(released)
            ToolResult(call.id, """{"ok":true}""")
        }
        val loop = AgentLoop(
            model = FakeModel(flowOf(toolCall)),
            tools = tools,
            oversight = ApproveAll,
            store = store,
            requestFactory = store,
            beforeToolDispatch = { released = true; true },
        )

        assertEquals(StepOutcome.TOOL_FINISHED, loop.step(runId, stepId))
        assertEquals(1, tools.executionCount)
    }

    @Test
    fun `intervention while dispatch waits invalidates the proposed action`() = runTest {
        var revision = 1L
        val store = RecordingStore()
        val tools = FakeTools { error("stale tool must not execute") }
        val loop = AgentLoop(
            model = FakeModel(flowOf(toolCall)),
            tools = tools,
            oversight = ApproveAll,
            store = store,
            requestFactory = store,
            beginTurn = { revision },
            isTurnCurrent = { _, startedAt -> revision == startedAt },
            beforeToolDispatch = {
                revision += 1
                true
            },
        )

        assertEquals(StepOutcome.TOOL_SUPERSEDED, loop.step(runId, stepId))
        assertEquals(0, tools.executionCount)
        assertTrue(store.appended.none { it is RunRecord.ToolDispatched })
    }

    @Test
    fun `superseded proposal is never journaled or executed`() = runTest {
        val store = RecordingStore()
        val tools = FakeTools { error("tool must not execute") }
        val loop = AgentLoop(
            model = FakeModel(flowOf(toolCall)),
            tools = tools,
            oversight = ApproveAll,
            store = store,
            requestFactory = store,
            beforeToolDispatch = { false },
        )

        assertEquals(StepOutcome.TOOL_SUPERSEDED, loop.step(runId, stepId))
        assertEquals(0, tools.executionCount)
        assertTrue(store.appended.none { it is RunRecord.ToolDispatched })
    }

    @Test
    fun `correction arriving during oversight supersedes rather than pauses the run`() = runTest {
        var turnCurrent = true
        val store = RecordingStore()
        val tools = FakeTools { error("tool must not execute") }
        val oversight = object : OversightPolicy {
            override suspend fun approve(call: ModelDelta.ToolCall): OversightDecision {
                turnCurrent = false
                return OversightDecision(approved = false, reason = "old confirmation cancelled")
            }
        }
        val loop = AgentLoop(
            model = FakeModel(flowOf(toolCall)),
            tools = tools,
            oversight = oversight,
            store = store,
            requestFactory = store,
            isTurnCurrent = { _, _ -> turnCurrent },
        )

        assertEquals(StepOutcome.TOOL_SUPERSEDED, loop.step(runId, stepId))
        assertEquals(0, tools.executionCount)
        assertTrue(store.appended.none { it is RunRecord.RunPaused })
    }

    @Test
    fun `tool transformation happens once before oversight journal and execution`() = runTest {
        val effective = toolCall.copy(argumentsJson = """{"value":"80.00"}""")
        val observed = mutableListOf<ModelDelta.ToolCall>()
        val tools = FakeTools { call ->
            observed += call
            ToolResult(call.id, """{"ok":true}""")
        }
        val oversight = object : OversightPolicy {
            override suspend fun approve(call: ModelDelta.ToolCall): OversightDecision {
                observed += call
                return OversightDecision(true)
            }
        }
        val store = RecordingStore()
        val loop = AgentLoop(
            FakeModel(flowOf(toolCall)),
            tools,
            oversight,
            store,
            store,
            ToolCallTransformer { effective },
        )

        assertEquals(StepOutcome.TOOL_FINISHED, loop.step(runId, stepId))

        assertEquals(listOf(effective, effective), observed)
        assertEquals(
            effective.argumentsJson,
            (store.appended.first() as RunRecord.ToolDispatched).argumentsJson,
        )
    }

    @Test
    fun `transformed call is durably observed before oversight`() = runTest {
        val effective = toolCall.copy(argumentsJson = """{"value":"80.00"}""")
        val order = mutableListOf<String>()
        var recorded: ModelDelta.ToolCall? = null
        val transformer = object : ToolCallTransformer {
            override fun transform(call: ModelDelta.ToolCall) = effective

            override suspend fun beforeOversight(call: ModelDelta.ToolCall) {
                recorded = call
                order += "record"
            }
        }
        val oversight = object : OversightPolicy {
            override suspend fun approve(call: ModelDelta.ToolCall): OversightDecision {
                order += "oversight"
                return OversightDecision(true)
            }
        }
        val store = RecordingStore()
        val loop = AgentLoop(
            FakeModel(flowOf(toolCall)),
            FakeTools { call -> ToolResult(call.id, """{"ok":true}""") },
            oversight,
            store,
            store,
            transformer,
        )

        val outcome = loop.step(runId, stepId)
        assertEquals(effective, recorded)
        assertEquals(listOf("record", "oversight"), order)
        assertEquals(StepOutcome.TOOL_FINISHED, outcome)
    }

    @Test
    fun `failed transformed call persistence pauses before oversight and dispatch`() = runTest {
        var oversightCalls = 0
        val transformer = object : ToolCallTransformer {
            override fun transform(call: ModelDelta.ToolCall) = call
            override suspend fun beforeOversight(call: ModelDelta.ToolCall) {
                error("study database unavailable")
            }
        }
        val oversight = object : OversightPolicy {
            override suspend fun approve(call: ModelDelta.ToolCall): OversightDecision {
                oversightCalls += 1
                return OversightDecision(true)
            }
        }
        val tools = FakeTools()
        val store = RecordingStore()
        val loop = AgentLoop(
            FakeModel(flowOf(toolCall)), tools, oversight, store, store, transformer,
        )

        assertEquals(StepOutcome.PAUSED_PRE_DISPATCH, loop.step(runId, stepId))
        assertEquals(0, oversightCalls)
        assertEquals(0, tools.executionCount)
    }

    @Test
    fun `failed tool transformation pauses before real world dispatch`() = runTest {
        val tools = FakeTools()
        val store = RecordingStore()
        val loop = AgentLoop(
            FakeModel(flowOf(toolCall)),
            tools,
            ApproveAll,
            store,
            store,
            ToolCallTransformer { error("unsafe controlled error rewrite") },
        )

        val outcome = loop.step(runId, stepId)

        assertEquals(StepOutcome.PAUSED_PRE_DISPATCH, outcome)
        assertEquals(0, tools.executionCount)
        assertEquals(
            RunState.PAUSED_RECOVERABLE,
            (store.appended.single() as RunRecord.RunPaused).state,
        )
    }

    @Test
    fun `model correctable tool rejection is returned without dispatching or pausing`() = runTest {
        var oversightCalls = 0
        val tools = FakeTools()
        val store = RecordingStore()
        val loop = AgentLoop(
            FakeModel(flowOf(toolCall)),
            tools,
            object : OversightPolicy {
                override suspend fun approve(call: ModelDelta.ToolCall): OversightDecision {
                    oversightCalls += 1
                    return OversightDecision(true)
                }
            },
            store,
            store,
            callPreprocessor = {
                throw ToolCallValidationException("target is ambiguous; observe and retry")
            },
        )

        val outcome = loop.step(runId, stepId)

        assertEquals(StepOutcome.TOOL_FINISHED, outcome)
        assertEquals(0, oversightCalls)
        assertEquals(0, tools.executionCount)
        assertTrue(store.appended[0] is RunRecord.ToolDispatched)
        val result = store.appended[1] as RunRecord.ToolFinished
        assertTrue(result.isError)
        assertTrue(result.contentJson.contains("target is ambiguous"))
        assertTrue(store.appended.none { it is RunRecord.RunPaused })
    }

    @Test
    fun `declined tool call is never executed`() = runTest {
        val tools = FakeTools()
        val store = RecordingStore()
        val loop = AgentLoop(
            FakeModel(flowOf(toolCall)),
            tools,
            DeclineAll,
            store,
            store,
        )

        val outcome = loop.step(runId, stepId)

        assertEquals(StepOutcome.PAUSED_OVERSIGHT, outcome)
        assertEquals(0, tools.executionCount)
        assertEquals(
            RunState.PAUSED_OVERSIGHT,
            (store.appended.single() as RunRecord.RunPaused).state,
        )
    }

    @Test
    fun `model network failure pauses without executing a tool`() = runTest {
        val tools = FakeTools()
        val store = RecordingStore()
        val failures = mutableListOf<String>()
        val modelFlow = flow<ModelDelta> {
            throw ModelUnavailableException("gateway offline")
        }
        val loop = AgentLoop(
            FakeModel(modelFlow),
            tools,
            ApproveAll,
            store,
            store,
            failureReporter = failures::add,
        )

        val outcome = loop.step(runId, stepId)

        assertEquals(StepOutcome.PAUSED_NETWORK, outcome)
        assertEquals(0, tools.executionCount)
        assertEquals(
            RunState.PAUSED_NETWORK,
            (store.appended.single() as RunRecord.RunPaused).state,
        )
        assertEquals(listOf("model unavailable: gateway offline"), failures)
    }

    @Test
    fun `tool failure after dispatch pauses recoverable without a result`() =
        runTest {
            val tools = FakeTools {
                throw IllegalStateException("outcome unknown")
            }
            val store = RecordingStore()
            val loop = AgentLoop(
                FakeModel(flowOf(toolCall)),
                tools,
                ApproveAll,
                store,
                store,
            )

            val outcome = loop.step(runId, stepId)

            assertEquals(StepOutcome.PAUSED_RECOVERABLE, outcome)
            assertEquals(1, tools.executionCount)
            assertEquals(2, store.appended.size)
            assertEquals(
                toolCall.id,
                (store.appended[0] as RunRecord.ToolDispatched).callId,
            )
            assertEquals(
                RunState.PAUSED_RECOVERABLE,
                (store.appended[1] as RunRecord.RunPaused).state,
            )
        }

    @Test
    fun `uncertain returned tool result pauses before another model turn`() =
        runTest {
            val tools = FakeTools { call ->
                ToolResult(
                    callId = call.id,
                    contentJson = """{"status":"OUTCOME_UNKNOWN"}""",
                    isError = true,
                    continuation = ToolContinuation.PAUSE_FOR_VERIFICATION,
                )
            }
            val store = RecordingStore()
            val loop = AgentLoop(
                FakeModel(flowOf(toolCall)),
                tools,
                ApproveAll,
                store,
                store,
            )

            val outcome = loop.step(runId, stepId)

            assertEquals(StepOutcome.PAUSED_RECOVERABLE, outcome)
            assertEquals(1, tools.executionCount)
            assertTrue(store.appended[0] is RunRecord.ToolDispatched)
            assertTrue(store.appended[1] is RunRecord.ToolFinished)
            assertTrue(store.appended[2] is RunRecord.RunPaused)
        }

    @Test
    fun `participant intervention after dispatch replans instead of ending the run`() =
        runTest {
            var turnCurrent = true
            val tools = FakeTools { call ->
                turnCurrent = false
                ToolResult(
                    callId = call.id,
                    contentJson = """{"status":"OUTCOME_UNKNOWN"}""",
                    isError = true,
                    continuation = ToolContinuation.PAUSE_FOR_VERIFICATION,
                )
            }
            val store = RecordingStore()
            val loop = AgentLoop(
                FakeModel(flowOf(toolCall)),
                tools,
                ApproveAll,
                store,
                store,
                isTurnCurrent = { _, _ -> turnCurrent },
            )

            val outcome = loop.step(runId, stepId)

            assertEquals(StepOutcome.TOOL_SUPERSEDED, outcome)
            assertEquals(1, tools.executionCount)
            assertTrue(store.appended[0] is RunRecord.ToolDispatched)
            assertTrue(store.appended[1] is RunRecord.ToolFinished)
            assertFalse(store.appended.any { it is RunRecord.RunPaused })
        }

    @Test
    fun `remote MCP failure after dispatch pauses without retrying`() =
        runTest {
            val remote = FailingMcpConnection()
            val manager =
                McpClientManager(
                    McpConnectionFactory { remote },
                    backgroundScope,
                )
            manager.connect(
                McpServerConfiguration(
                    serverId = "server",
                    endpoint = "https://example.test/mcp",
                ),
            )
            val tools = DynamicToolRegistry(FakeTools(), manager)
            val remoteCall =
                ModelDelta.ToolCall(
                    ToolCallId("mcp-call"),
                    "mcp__server__change_state",
                    """{"enabled":true}""",
                )
            val store = RecordingStore()
            val loop =
                AgentLoop(
                    FakeModel(flowOf(remoteCall)),
                    tools,
                    ApproveAll,
                    store,
                    store,
                )

            val outcome = loop.step(runId, stepId)

            assertEquals(StepOutcome.PAUSED_RECOVERABLE, outcome)
            assertEquals(1, remote.callCount)
            assertTrue(store.appended[0] is RunRecord.ToolDispatched)
            assertTrue(store.appended[1] is RunRecord.RunPaused)
        }

    @Test
    fun `cancellation after dispatch records recovery pause then propagates`() =
        runTest {
            val tools = FakeTools {
                throw CancellationException("cancelled")
            }
            val store = RecordingStore()
            val loop = AgentLoop(
                FakeModel(flowOf(toolCall)),
                tools,
                ApproveAll,
                store,
                store,
            )
            var cancellationPropagated = false

            try {
                loop.step(runId, stepId)
            } catch (_: CancellationException) {
                cancellationPropagated = true
            }

            assertTrue(cancellationPropagated)
            assertEquals(
                RunState.PAUSED_RECOVERABLE,
                (store.appended.last() as RunRecord.RunPaused).state,
            )
        }

    private class SequenceModel(vararg responses: Flow<ModelDelta>) : ModelClient {
        private val responses = responses.toList()
        val requests = mutableListOf<ModelRequest>()

        override fun stream(request: ModelRequest): Flow<ModelDelta> {
            requests += request
            return responses[requests.lastIndex]
        }
    }

    private class FakeModel(
        private val deltas: Flow<ModelDelta>,
    ) : ModelClient {
        override fun stream(request: ModelRequest): Flow<ModelDelta> = deltas
    }

    private class FakeTools(
        private val onExecute: suspend (ModelDelta.ToolCall) -> ToolResult = {
            error("Tool must not execute")
        },
    ) : ToolRegistry {
        var executionCount = 0
            private set

        override fun definitions(): List<ToolDefinition> = emptyList()

        override suspend fun execute(
            runId: RunId,
            call: ModelDelta.ToolCall,
        ): ToolResult {
            executionCount += 1
            return onExecute(call)
        }
    }

    private class FailingMcpConnection : McpConnection {
        var callCount = 0

        override suspend fun listTools(cursor: String?) =
            McpToolPage(
                tools =
                    listOf(
                        McpRemoteTool(
                            name = "change_state",
                            description = "Changes remote state",
                            inputSchemaJson = """{"type":"object"}""",
                        ),
                    ),
                nextCursor = null,
            )

        override suspend fun callTool(
            name: String,
            argumentsJson: String,
        ): McpCallResult {
            callCount += 1
            error("response lost")
        }

        override suspend fun close() = Unit
    }

    private class RecordingStore(
        private val currentSnapshot: RunSnapshot? = null,
        private val onAppend: (RunRecord) -> Unit = {},
    ) : SessionStore, RequestFactory {
        val appended = mutableListOf<RunRecord>()

        override suspend fun snapshot(runId: RunId) =
            currentSnapshot ?: RunSnapshot(runId, RunState.RUNNING)

        override suspend fun append(event: RunRecord) {
            appended += event
            onAppend(event)
        }

        override fun create(
            snapshot: RunSnapshot,
            tools: List<ToolDefinition>,
        ) = ModelRequest(snapshot.runId, snapshot.messages, tools)
    }

    private object ApproveAll : OversightPolicy {
        override suspend fun approve(call: ModelDelta.ToolCall) =
            OversightDecision(approved = true)
    }

    private object DeclineAll : OversightPolicy {
        override suspend fun approve(call: ModelDelta.ToolCall) =
            OversightDecision(approved = false, reason = "declined")
    }
}
