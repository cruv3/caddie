package com.caddie.agent.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Describes how one agent step ended. */
enum class StepOutcome {
    TOOL_FINISHED,
    TOOL_SUPERSEDED,
    RUN_COMPLETED,
    RUN_ABORTED,
    PAUSED_OVERSIGHT,
    PAUSED_NETWORK,
    PAUSED_PRE_DISPATCH,
    PAUSED_RECOVERABLE,
}

/** Signals that the configured model cannot currently serve a request. */
class ModelUnavailableException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** Runs one observe-decide-execute step and records every state transition. */
class AgentLoop(
    private val model: ModelClient,
    private val tools: ToolRegistry,
    private val oversight: OversightPolicy,
    private val store: SessionStore,
    private val requestFactory: RequestFactory,
    private val transformer: ToolCallTransformer = ToolCallTransformer.Identity,
    private val failureReporter: (String) -> Unit = {},
    private val beginTurn: suspend (RunId) -> Long = { 0L },
    private val isTurnCurrent: (RunId, Long) -> Boolean = { _, _ -> true },
    private val beforeToolDispatch: suspend (RunId) -> Boolean = { true },
    private val onToolDispatch: (RunId, ModelDelta.ToolCall) -> Unit = { _, _ -> },
    private val callPreprocessor: suspend (ModelDelta.ToolCall) -> ModelDelta.ToolCall = { it },
    private val requireTerminalCall: Boolean = false,
) {
    suspend fun step(
        runId: RunId,
        stepId: StepId,
    ): StepOutcome {
        val turnRevision = beginTurn(runId)
        val snapshot = store.snapshot(runId)
        check(snapshot.state == RunState.RUNNING) {
            "Cannot step run ${runId.value} in state ${snapshot.state}"
        }

        val request = requestFactory.create(snapshot, tools.definitions())
        val text = StringBuilder()
        val calls = mutableListOf<ModelDelta.ToolCall>()
        try {
            model.stream(request).collect { delta ->
                if (!isTurnCurrent(runId, turnRevision)) throw TurnSupersededException()
                when (delta) {
                    is ModelDelta.Text -> text.append(delta.value)
                    is ModelDelta.ToolCall -> calls += delta
                    ModelDelta.Completed -> Unit
                }
            }
        } catch (_: TurnSupersededException) {
            return StepOutcome.TOOL_SUPERSEDED
        } catch (error: ModelUnavailableException) {
            failureReporter("model unavailable: ${error.message}")
            store.append(
                RunRecord.RunPaused(
                    runId,
                    stepId,
                    RunState.PAUSED_NETWORK,
                    error.message,
                ),
            )
            return StepOutcome.PAUSED_NETWORK
        }

        if (calls.isEmpty() && text.isNotBlank()) {
            if (!isTurnCurrent(runId, turnRevision)) return StepOutcome.TOOL_SUPERSEDED
            if (requireTerminalCall) {
                store.append(
                    RunRecord.RunPaused(
                        runId,
                        stepId,
                        RunState.PAUSED_RECOVERABLE,
                        "normal run requires caddie.complete or caddie.fail",
                    ),
                )
                return StepOutcome.PAUSED_RECOVERABLE
            }
            try {
                transformer.validateCompletion()
            } catch (error: Exception) {
                failureReporter("completion rejected: ${error.message}")
                store.append(
                    RunRecord.RunPaused(
                        runId,
                        stepId,
                        RunState.PAUSED_RECOVERABLE,
                        "run completion rejected before all required actions finished",
                    ),
                )
                return StepOutcome.PAUSED_PRE_DISPATCH
            }
            store.append(RunRecord.AssistantCompleted(runId, text.toString()))
            store.append(RunRecord.RunCompleted(runId))
            return StepOutcome.RUN_COMPLETED
        }
        if (calls.size != 1) {
            failureReporter("model turn returned ${calls.size} tool calls")
            store.append(
                RunRecord.RunPaused(
                    runId,
                    stepId,
                    RunState.PAUSED_RECOVERABLE,
                    "model turn must contain final text or exactly one tool call",
                ),
            )
            return StepOutcome.PAUSED_RECOVERABLE
        }
        val proposedCall = calls.single()
        val call = try {
            val preprocessed = callPreprocessor(proposedCall)
            require(preprocessed.id == proposedCall.id && preprocessed.name == proposedCall.name) {
                "Tool preprocessing may change arguments only"
            }
            val transformed = transformer.transform(preprocessed)
            require(transformed.id == proposedCall.id && transformed.name == proposedCall.name) {
                "Tool transformation may change arguments only"
            }
            transformer.beforeOversight(transformed)
            transformed
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: ToolCallValidationException) {
            if (!isTurnCurrent(runId, turnRevision)) return StepOutcome.TOOL_SUPERSEDED
            val message = error.message ?: "tool call validation failed"
            failureReporter("tool call rejected for ${proposedCall.name}: $message")
            store.append(
                RunRecord.ToolDispatched(
                    runId,
                    proposedCall.id,
                    proposedCall.name,
                    proposedCall.argumentsJson,
                    text.toString(),
                ),
            )
            store.append(
                RunRecord.ToolFinished(
                    runId,
                    proposedCall.id,
                    buildJsonObject {
                        put("ok", false)
                        put("error", message)
                        put("retryable", true)
                    }.toString(),
                    isError = true,
                ),
            )
            return StepOutcome.TOOL_FINISHED
        } catch (error: Exception) {
            failureReporter(
                "tool transformation failed for ${proposedCall.name}: ${error.message}",
            )
            store.append(
                RunRecord.RunPaused(
                    runId,
                    stepId,
                    RunState.PAUSED_RECOVERABLE,
                    "tool transformation failed before dispatch",
                ),
            )
            return StepOutcome.PAUSED_PRE_DISPATCH
        }

        val decision = oversight.approve(call)
        if (!isTurnCurrent(runId, turnRevision)) return StepOutcome.TOOL_SUPERSEDED
        if (!decision.approved) {
            store.append(
                RunRecord.RunPaused(
                    runId,
                    stepId,
                    RunState.PAUSED_OVERSIGHT,
                    decision.reason,
                ),
            )
            return StepOutcome.PAUSED_OVERSIGHT
        }

        if (!beforeToolDispatch(runId) || !isTurnCurrent(runId, turnRevision)) {
            return StepOutcome.TOOL_SUPERSEDED
        }

        store.append(
            RunRecord.ToolDispatched(
                runId,
                call.id,
                call.name,
                call.argumentsJson,
                text.toString(),
            ),
        )
        onToolDispatch(runId, call)
        val rawResult =
            try {
                tools.execute(runId, call)
            } catch (cancellation: CancellationException) {
                withContext(NonCancellable) {
                    pauseForUnknownToolOutcome(runId, stepId)
                }
                throw cancellation
            } catch (error: Exception) {
                failureReporter("tool execution failed for ${call.name}: ${error::class.simpleName}")
                pauseForUnknownToolOutcome(runId, stepId)
                return StepOutcome.PAUSED_RECOVERABLE
            }
        val result = if (
            rawResult.continuation == ToolContinuation.COMPLETE_RUN &&
            !NormalCompletionEvidence.isReady(snapshot)
        ) {
            rawResult.copy(
                contentJson = """{"ok":false,"error":"Observe the current Android UI after the last action before completing."}""",
                isError = true,
                continuation = ToolContinuation.CONTINUE,
                terminalMessage = null,
            )
        } else {
            rawResult
        }
        store.append(
            RunRecord.ToolFinished(
                runId,
                call.id,
                result.contentJson,
                result.isError,
            ),
        )
        transformer.afterExecution(call, result)
        // A participant may change the UI while an accepted action is being verified.
        // Keep the durable result, then observe and plan again instead of ending the run.
        if (!isTurnCurrent(runId, turnRevision)) return StepOutcome.TOOL_SUPERSEDED
        when (result.continuation) {
            ToolContinuation.CONTINUE -> Unit
            ToolContinuation.PAUSE_FOR_VERIFICATION -> {
                pauseForUnknownToolOutcome(runId, stepId)
                return StepOutcome.PAUSED_RECOVERABLE
            }
            ToolContinuation.COMPLETE_RUN -> {
                val message = requireNotNull(result.terminalMessage).trim()
                require(message.isNotEmpty()) { "completion message must not be blank" }
                store.append(RunRecord.AssistantCompleted(runId, message))
                store.append(RunRecord.RunCompleted(runId))
                return StepOutcome.RUN_COMPLETED
            }
            ToolContinuation.FAIL_RUN -> {
                val message = requireNotNull(result.terminalMessage).trim()
                require(message.isNotEmpty()) { "failure message must not be blank" }
                store.append(RunRecord.AssistantCompleted(runId, message))
                store.append(RunRecord.RunAborted(runId))
                return StepOutcome.RUN_ABORTED
            }
        }
        return StepOutcome.TOOL_FINISHED
    }

    private suspend fun pauseForUnknownToolOutcome(
        runId: RunId,
        stepId: StepId,
    ) {
        store.append(
            RunRecord.RunPaused(
                runId,
                stepId,
                RunState.PAUSED_RECOVERABLE,
                "tool outcome unknown; observe and verify before continuing",
            ),
        )
    }

    private class TurnSupersededException : RuntimeException()
}
