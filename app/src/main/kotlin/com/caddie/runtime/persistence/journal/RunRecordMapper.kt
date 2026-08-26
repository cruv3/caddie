package com.caddie.runtime.persistence.journal

import com.caddie.agent.core.ActionResolution
import com.caddie.agent.core.AttemptId
import com.caddie.agent.core.RunId
import com.caddie.agent.core.RunRecord
import com.caddie.agent.core.RunState
import com.caddie.agent.core.SessionId
import com.caddie.agent.core.StepId
import com.caddie.agent.core.ToolCallId

private const val SCHEMA_VERSION = 1
private const val REDACTED_TASK = "[not persisted]"
private const val REDACTED_RESULT =
    """{"recovered":true,"payload_persisted":false}"""
private const val REDACTED_ARGUMENTS = "{}"
private const val REDACTED_ASSISTANT_TEXT = "[not persisted]"

/** Converts between domain run records and persisted journal rows. */
internal class RunRecordMapper {
    fun toRow(
        record: RunRecord,
        sequence: Long,
        createdAtEpochMs: Long,
    ): JournalEntity {
        val base =
            JournalEntity(
                recordId = record.recordId,
                runId = record.runId.value,
                runSequence = sequence,
                createdAtEpochMs = createdAtEpochMs,
                recordType = record.recordType(),
            )

        return when (record) {
            is RunRecord.RunCreated ->
                base.copy(sessionId = record.sessionId.value)
            is RunRecord.RunStarted -> base
            is RunRecord.ToolDispatched ->
                base.copy(
                    toolCallId = record.callId.value,
                    toolName = record.toolName,
                )
            is RunRecord.ToolFinished ->
                base.copy(
                    toolCallId = record.callId.value,
                    isError = record.isError,
                )
            is RunRecord.AssistantCompleted -> base
            is RunRecord.RunPaused ->
                base.copy(
                    stepId = record.stepId.value,
                    runState = record.state.name,
                )
            is RunRecord.RunResumed ->
                base.copy(stepId = record.stepId.value)
            is RunRecord.RunCompleted -> base
            is RunRecord.RunAborted -> base
            is RunRecord.ActionDispatched ->
                base.copy(
                    attemptId = record.attemptId.value,
                    actionKind = record.actionKind,
                    selectorFingerprint = record.selectorFingerprint,
                    postconditionFingerprint = record.postconditionFingerprint,
                    recoverySpecId = record.recoverySpecId,
                )
            is RunRecord.ActionExecuted ->
                base.copy(
                    attemptId = record.attemptId.value,
                    outcome = record.dispatchOutcome,
                )
            is RunRecord.ActionTerminal ->
                base.copy(
                    attemptId = record.attemptId.value,
                    outcome = record.resolution.name,
                )
        }
    }

    fun toRecord(row: JournalEntity): RunRecord {
        if (row.schemaVersion != SCHEMA_VERSION) {
            throw JournalFormatException(
                "Unsupported journal schema version: ${row.schemaVersion}",
            )
        }

        val runId = RunId(row.runId.required("journal row", "run_id"))
        val record =
            when (row.recordType) {
                "RUN_CREATED" ->
                    RunRecord.RunCreated(
                        sessionId =
                            SessionId(
                                row.sessionId?.required("RUN_CREATED", "session_id")
                                    ?: "not-persisted:${runId.value}",
                            ),
                        runId = runId,
                        task = REDACTED_TASK,
                    )
                "RUN_STARTED" -> RunRecord.RunStarted(runId)
                "TOOL_DISPATCHED" ->
                    RunRecord.ToolDispatched(
                        runId = runId,
                        callId =
                            ToolCallId(
                                row.toolCallId.required(
                                    "TOOL_DISPATCHED",
                                    "tool_call_id",
                                ),
                            ),
                        toolName =
                            row.toolName.required(
                                "TOOL_DISPATCHED",
                                "tool_name",
                            ),
                        argumentsJson = REDACTED_ARGUMENTS,
                        assistantText = "",
                    )
                "TOOL_FINISHED" ->
                    RunRecord.ToolFinished(
                        runId = runId,
                        callId =
                            ToolCallId(
                                row.toolCallId.required(
                                    "TOOL_FINISHED",
                                    "tool_call_id",
                                ),
                            ),
                        contentJson = REDACTED_RESULT,
                        isError =
                            row.isError
                                ?: throw JournalFormatException(
                                    "TOOL_FINISHED requires is_error",
                                ),
                    )
                "ASSISTANT_COMPLETED" ->
                    RunRecord.AssistantCompleted(
                        runId = runId,
                        text = REDACTED_ASSISTANT_TEXT,
                    )
                "RUN_PAUSED" ->
                    RunRecord.RunPaused(
                        runId = runId,
                        stepId =
                            StepId(
                                row.stepId.required(
                                    "RUN_PAUSED",
                                    "step_id",
                                ),
                            ),
                        state = row.pausedState(),
                        reason = null,
                    )
                "RUN_RESUMED" ->
                    RunRecord.RunResumed(
                        runId = runId,
                        stepId =
                            StepId(
                                row.stepId.required(
                                    "RUN_RESUMED",
                                    "step_id",
                                ),
                            ),
                    )
                "RUN_COMPLETED" -> RunRecord.RunCompleted(runId)
                "RUN_ABORTED" -> RunRecord.RunAborted(runId)
                "ACTION_DISPATCHED" ->
                    RunRecord.ActionDispatched(
                        runId = runId,
                        attemptId =
                            AttemptId(
                                row.attemptId.required(
                                    "ACTION_DISPATCHED",
                                    "attempt_id",
                                ),
                            ),
                        actionKind =
                            row.actionKind.required(
                                "ACTION_DISPATCHED",
                                "action_kind",
                            ),
                        selectorFingerprint = row.selectorFingerprint,
                        postconditionFingerprint = row.postconditionFingerprint,
                        recoverySpecId = row.recoverySpecId,
                    )
                "ACTION_EXECUTED" ->
                    RunRecord.ActionExecuted(
                        runId = runId,
                        attemptId =
                            AttemptId(
                                row.attemptId.required(
                                    "ACTION_EXECUTED",
                                    "attempt_id",
                                ),
                            ),
                        dispatchOutcome =
                            row.outcome.required(
                                "ACTION_EXECUTED",
                                "outcome",
                            ),
                    )
                "ACTION_TERMINAL" ->
                    RunRecord.ActionTerminal(
                        runId = runId,
                        attemptId =
                            AttemptId(
                                row.attemptId.required(
                                    "ACTION_TERMINAL",
                                    "attempt_id",
                                ),
                            ),
                        resolution =
                            row.outcome.parseEnum(
                                recordType = "ACTION_TERMINAL",
                                fieldLabel = "resolution",
                            ),
                    )
                else ->
                    throw JournalFormatException(
                        "Unknown journal record type: ${row.recordType}",
                    )
            }

        if (record.recordId != row.recordId) {
            throw JournalFormatException(
                "Journal record_id does not match ${row.recordType} metadata",
            )
        }
        return record
    }

    private fun JournalEntity.pausedState(): RunState {
        val state =
            runState.parseEnum<RunState>(
                recordType = "RUN_PAUSED",
                fieldLabel = "run_state",
            )
        if (
            state != RunState.PAUSED_OVERSIGHT &&
            state != RunState.PAUSED_NETWORK &&
            state != RunState.PAUSED_RECOVERABLE
        ) {
            throw JournalFormatException(
                "RUN_PAUSED has non-paused run_state: $state",
            )
        }
        return state
    }

    private fun RunRecord.recordType(): String =
        when (this) {
            is RunRecord.RunCreated -> "RUN_CREATED"
            is RunRecord.RunStarted -> "RUN_STARTED"
            is RunRecord.ToolDispatched -> "TOOL_DISPATCHED"
            is RunRecord.ToolFinished -> "TOOL_FINISHED"
            is RunRecord.AssistantCompleted -> "ASSISTANT_COMPLETED"
            is RunRecord.RunPaused -> "RUN_PAUSED"
            is RunRecord.RunResumed -> "RUN_RESUMED"
            is RunRecord.RunCompleted -> "RUN_COMPLETED"
            is RunRecord.RunAborted -> "RUN_ABORTED"
            is RunRecord.ActionDispatched -> "ACTION_DISPATCHED"
            is RunRecord.ActionExecuted -> "ACTION_EXECUTED"
            is RunRecord.ActionTerminal -> "ACTION_TERMINAL"
        }
}

private fun String?.required(
    recordType: String,
    columnName: String,
): String =
    this?.takeIf(String::isNotBlank)
        ?: throw JournalFormatException("$recordType requires $columnName")

private inline fun <reified T : Enum<T>> String?.parseEnum(
    recordType: String,
    fieldLabel: String,
): T {
    val value = required(recordType, fieldLabel)
    return enumValues<T>().firstOrNull { it.name == value }
        ?: throw JournalFormatException(
            "$recordType has unknown $fieldLabel: $value",
        )
}

/** Signals invalid or unsupported data in a journal row. */
internal class JournalFormatException(message: String) :
    IllegalStateException(message)
