package com.caddie.agent.core

import kotlinx.coroutines.flow.Flow

/** Streams model output for an agent request. */
interface ModelClient {
    fun stream(request: ModelRequest): Flow<ModelDelta>
}

/** Marks a safe pre-dispatch rejection that the model can correct on its next turn. */
class ToolCallValidationException(message: String) : IllegalArgumentException(message)

/** Lists available tools and executes requested tool calls. */
interface ToolRegistry {
    fun definitions(): List<ToolDefinition>

    suspend fun execute(
        runId: RunId,
        call: ModelDelta.ToolCall,
    ): ToolResult
}

/** Applies an optional run-scoped argument rewrite before oversight and dispatch. */
fun interface ToolCallTransformer {
    fun transform(call: ModelDelta.ToolCall): ModelDelta.ToolCall

    /** Persists transformation-specific metadata before oversight can expose or dispatch the call. */
    suspend fun beforeOversight(call: ModelDelta.ToolCall) = Unit

    /** Observes the verified tool result so run-scoped plans only advance on success. */
    fun afterExecution(call: ModelDelta.ToolCall, result: ToolResult) = Unit

    /** Updates run-scoped deterministic state before a corrected request is built. */
    fun onParticipantCorrection(text: String) = Unit

    /** Rejects a model's final answer when run-scoped invariants are not complete yet. */
    fun validateCompletion() = Unit

    companion object {
        val Identity = ToolCallTransformer { it }
    }
}

/** Decides whether a proposed tool call may proceed. */
interface OversightPolicy {
    suspend fun approve(call: ModelDelta.ToolCall): OversightDecision
}

/** Stores and reconstructs the durable state of agent runs. */
interface SessionStore {
    suspend fun snapshot(runId: RunId): RunSnapshot

    suspend fun append(event: RunRecord)
}

/** Extends session storage with explicit recovery and resume operations. */
interface RecoverySessionStore : SessionStore {
    suspend fun incompleteAttempts(): List<RunRecord.ActionDispatched>

    suspend fun closeAttemptAndPause(
        dispatched: RunRecord.ActionDispatched,
        resolution: ActionResolution,
        stepId: StepId,
    ): Boolean

    suspend fun resumeAfterRecovery(
        runId: RunId,
        stepId: StepId,
        contextReady: Boolean,
    ): RecoveryResumeResult
}

/** Describes the result of trying to resume a recoverable run. */
enum class RecoveryResumeResult {
    RESUMED,
    CONTEXT_REQUIRED,
    ALREADY_RESUMED,
}

/** Indicates whether this caller owns permission to dispatch an action. */
enum class ActionDispatchClaim {
    /** This caller owns the durable claim and may perform the physical action. */
    CLAIMED,

    /** A durable claim already exists; physical execution is not authorized. */
    ALREADY_CLAIMED,
}

/** Durably claims real-world action attempts before execution. */
interface ActionAttemptJournal {
    suspend fun dispatched(
        record: RunRecord.ActionDispatched,
    ): ActionDispatchClaim

    suspend fun executed(record: RunRecord.ActionExecuted)

    suspend fun terminal(record: RunRecord.ActionTerminal)
}

/** Accepts run records produced by the agent runtime. */
interface EventSink {
    suspend fun emit(record: RunRecord)
}

/** Builds model requests from the current run snapshot and available tools. */
interface RequestFactory {
    fun create(
        snapshot: RunSnapshot,
        tools: List<ToolDefinition>,
    ): ModelRequest
}
