package com.caddie.agent.core

fun reduce(
    records: List<RunRecord>,
    recoveredProcess: Boolean,
): RunSnapshot {
    require(records.isNotEmpty()) { "Cannot reduce an empty run journal" }

    val runId = records.first().runId
    require(records.all { it.runId == runId }) {
        "A run journal cannot mix run ids"
    }

    var state = RunState.CREATED
    val messages = mutableListOf<AgentMessage>()
    val dispatched = linkedMapOf<ToolCallId, RunRecord.ToolDispatched>()
    val finished = linkedMapOf<ToolCallId, RunRecord.ToolFinished>()
    val failedToolCalls = linkedSetOf<ToolCallId>()
    val actionDispatches =
        linkedMapOf<AttemptId, RunRecord.ActionDispatched>()
    val actionTerminals =
        linkedMapOf<AttemptId, RunRecord.ActionTerminal>()

    records.forEach { record ->
        when (record) {
            is RunRecord.RunCreated -> {
                state = RunState.CREATED
                messages += AgentMessage(AgentMessage.Role.USER, record.task)
            }
            is RunRecord.RunStarted ->
                state = transition(state, RunEvent.START)
            is RunRecord.ToolDispatched -> {
                dispatched[record.callId] = record
                messages += AgentMessage(
                    role = AgentMessage.Role.ASSISTANT,
                    content = record.assistantText,
                    toolCall = AgentToolCall(
                        id = record.callId,
                        name = record.toolName,
                        argumentsJson = record.argumentsJson,
                    ),
                )
            }
            is RunRecord.ToolFinished -> {
                finished[record.callId] = record
                if (record.isError) failedToolCalls += record.callId
                messages += AgentMessage(
                    role = AgentMessage.Role.TOOL,
                    content = record.contentJson,
                    toolCallId = record.callId,
                )
            }
            is RunRecord.AssistantCompleted ->
                messages += AgentMessage(
                    AgentMessage.Role.ASSISTANT,
                    record.text,
                )
            is RunRecord.ActionDispatched ->
                actionDispatches[record.attemptId] = record
            is RunRecord.ActionExecuted -> Unit
            is RunRecord.ActionTerminal ->
                actionTerminals[record.attemptId] = record
            is RunRecord.RunPaused -> state = record.state
            is RunRecord.RunResumed ->
                state = transition(state, RunEvent.RESUME)
            is RunRecord.RunCompleted ->
                state = transition(state, RunEvent.COMPLETE)
            is RunRecord.RunAborted ->
                state = transition(state, RunEvent.ABORT)
        }
    }

    val unmatchedToolCalls = dispatched.keys - finished.keys
    val unmatchedAttempts = actionDispatches.keys - actionTerminals.keys
    val mustRecover =
        recoveredProcess &&
            (unmatchedToolCalls.isNotEmpty() || unmatchedAttempts.isNotEmpty())
    val recoveredState =
        if (mustRecover) {
            RunState.PAUSED_RECOVERABLE
        } else {
            state
        }

    return RunSnapshot(
        runId = runId,
        state = recoveredState,
        messages = messages,
        unverifiedToolCallId = unmatchedToolCalls.lastOrNull(),
        unverifiedAttemptId = unmatchedAttempts.lastOrNull(),
        hasDispatchedAction = actionDispatches.isNotEmpty(),
        failedToolCallIds = failedToolCalls,
    )
}
