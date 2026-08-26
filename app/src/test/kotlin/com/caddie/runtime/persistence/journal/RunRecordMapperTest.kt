package com.caddie.runtime.persistence.journal

import com.caddie.agent.core.ActionResolution
import com.caddie.agent.core.AttemptId
import com.caddie.agent.core.RunId
import com.caddie.agent.core.RunRecord
import com.caddie.agent.core.RunState
import com.caddie.agent.core.SessionId
import com.caddie.agent.core.StepId
import com.caddie.agent.core.ToolCallId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class RunRecordMapperTest {
    private val mapper = RunRecordMapper()

    @Test
    fun `run created projection omits raw task`() {
        val row =
            mapper.toRow(
                record =
                    RunRecord.RunCreated(
                        SessionId("session-1"),
                        RunId("run-1"),
                        "private task",
                    ),
                sequence = 1,
                createdAtEpochMs = 10,
            )

        assertFalse(row.toString().contains("private task"))
        assertEquals("session-1", row.sessionId)
        assertNull(row.toolName)
    }

    @Test
    fun `tool result projection omits content json`() {
        val row =
            mapper.toRow(
                RunRecord.ToolFinished(
                    RunId("run-1"),
                    ToolCallId("call-1"),
                    """{"private":"payload"}""",
                    false,
                ),
                sequence = 2,
                createdAtEpochMs = 20,
            )

        assertFalse(row.toString().contains("private"))
        assertFalse(row.toString().contains("payload"))
        assertEquals("TOOL_FINISHED", row.recordType)
        assertEquals("call-1", row.toolCallId)
        assertEquals(false, row.isError)
    }

    @Test
    fun `tool arguments and assistant text are omitted from recovery rows`() {
        val runId = RunId("run-1")
        val dispatched = mapper.toRow(
            RunRecord.ToolDispatched(
                runId,
                ToolCallId("call-1"),
                "android.click",
                """{"private":"argument"}""",
                "private narration",
            ),
            sequence = 1,
            createdAtEpochMs = 10,
        )
        val completed = mapper.toRow(
            RunRecord.AssistantCompleted(runId, "private answer"),
            sequence = 2,
            createdAtEpochMs = 20,
        )

        assertFalse(dispatched.toString().contains("argument"))
        assertFalse(dispatched.toString().contains("private narration"))
        assertFalse(completed.toString().contains("private answer"))
        assertEquals("{}", (mapper.toRecord(dispatched) as RunRecord.ToolDispatched).argumentsJson)
        assertEquals(
            "[not persisted]",
            (mapper.toRecord(completed) as RunRecord.AssistantCompleted).text,
        )
    }

    @Test
    fun `projection exposes only intentional journal columns`() {
        val allowedColumns =
            setOf(
                "recordId",
                "runId",
                "runSequence",
                "createdAtEpochMs",
                "recordType",
                "attemptId",
                "sessionId",
                "stepId",
                "toolCallId",
                "toolName",
                "runState",
                "actionKind",
                "selectorFingerprint",
                "postconditionFingerprint",
                "recoverySpecId",
                "outcome",
                "failureCategory",
                "isError",
                "schemaVersion",
            )

        assertEquals(
            allowedColumns,
            JournalEntity::class.java.declaredFields
                .filterNot { it.isSynthetic || it.name == "\$stable" }
                .mapTo(mutableSetOf()) { it.name },
        )
        assertEquals(
            JournalEntity(
                recordId = "run-created:run-1",
                runId = "run-1",
                runSequence = 1,
                createdAtEpochMs = 10,
                recordType = "RUN_CREATED",
                sessionId = "session-1",
            ),
            mapper.toRow(
                RunRecord.RunCreated(
                    SessionId("session-1"),
                    RunId("run-1"),
                    "private task",
                ),
                sequence = 1,
                createdAtEpochMs = 10,
            ),
        )
    }

    @Test
    fun `every run record round trips with minimized recovery sentinels`() {
        val runId = RunId("run-1")
        val records =
            listOf(
                RunRecord.RunCreated(SessionId("session-1"), runId, "private task"),
                RunRecord.RunStarted(runId),
                RunRecord.ToolDispatched(runId, ToolCallId("call-1"), "click"),
                RunRecord.ToolFinished(
                    runId,
                    ToolCallId("call-1"),
                    """{"private":"result"}""",
                    true,
                ),
                RunRecord.AssistantCompleted(runId, "private assistant answer"),
                RunRecord.RunPaused(
                    runId,
                    StepId("step-1"),
                    RunState.PAUSED_RECOVERABLE,
                    "private pause reason",
                ),
                RunRecord.RunResumed(runId, StepId("step-1")),
                RunRecord.RunCompleted(runId),
                RunRecord.RunAborted(runId),
                RunRecord.ActionDispatched(
                    runId,
                    AttemptId("attempt-1"),
                    "CLICK",
                    "selector-fingerprint",
                    "postcondition-fingerprint",
                    "recovery-spec-1",
                ),
                RunRecord.ActionExecuted(runId, AttemptId("attempt-1"), "ACCEPTED"),
                RunRecord.ActionTerminal(
                    runId,
                    AttemptId("attempt-1"),
                    ActionResolution.VERIFIED,
                ),
            )
        val expected =
            records.map { record ->
                when (record) {
                    is RunRecord.RunCreated -> record.copy(task = "[not persisted]")
                    is RunRecord.ToolFinished ->
                        record.copy(
                            contentJson =
                                """{"recovered":true,"payload_persisted":false}""",
                        )
                    is RunRecord.AssistantCompleted ->
                        record.copy(text = "[not persisted]")
                    is RunRecord.RunPaused -> record.copy(reason = null)
                    else -> record
                }
            }

        val reconstructed =
            records.mapIndexed { index, record ->
                mapper.toRecord(
                    mapper.toRow(
                        record = record,
                        sequence = index.toLong() + 1,
                        createdAtEpochMs = 100L + index,
                    ),
                )
            }

        assertEquals(expected, reconstructed)
    }

    @Test
    fun `historical run created without session uses deterministic sentinel`() {
        val record =
            mapper.toRecord(
                JournalEntity(
                    recordId = "run-created:run-1",
                    runId = "run-1",
                    runSequence = 1,
                    createdAtEpochMs = 10,
                    recordType = "RUN_CREATED",
                ),
            )

        assertEquals(
            RunRecord.RunCreated(
                SessionId("not-persisted:run-1"),
                RunId("run-1"),
                "[not persisted]",
            ),
            record,
        )
    }

    @Test
    fun `unknown record type throws format exception`() {
        val error =
            assertThrows(JournalFormatException::class.java) {
                mapper.toRecord(baseRow(recordType = "FUTURE_RECORD"))
            }

        assertEquals("Unknown journal record type: FUTURE_RECORD", error.message)
    }

    @Test
    fun `missing required metadata throws format exception`() {
        val error =
            assertThrows(JournalFormatException::class.java) {
                mapper.toRecord(baseRow(recordType = "TOOL_FINISHED"))
            }

        assertEquals("TOOL_FINISHED requires tool_call_id", error.message)
    }

    @Test
    fun `unknown enum throws format exception`() {
        val error =
            assertThrows(JournalFormatException::class.java) {
                mapper.toRecord(
                    baseRow(
                        recordId = "action-terminal:run-1:attempt-1",
                        recordType = "ACTION_TERMINAL",
                        attemptId = "attempt-1",
                        outcome = "FUTURE_RESOLUTION",
                    ),
                )
            }

        assertEquals(
            "ACTION_TERMINAL has unknown resolution: FUTURE_RESOLUTION",
            error.message,
        )
    }

    @Test
    fun `unsupported schema version throws format exception`() {
        val error =
            assertThrows(JournalFormatException::class.java) {
                mapper.toRecord(baseRow(schemaVersion = 2))
            }

        assertEquals("Unsupported journal schema version: 2", error.message)
    }

    @Test
    fun `computed record id mismatch throws format exception`() {
        val error =
            assertThrows(JournalFormatException::class.java) {
                mapper.toRecord(baseRow(recordId = "different-record"))
            }

        assertEquals("Journal record_id does not match RUN_STARTED metadata", error.message)
    }

    private fun baseRow(
        recordId: String = "run-started:run-1",
        recordType: String = "RUN_STARTED",
        attemptId: String? = null,
        outcome: String? = null,
        schemaVersion: Int = 1,
    ) = JournalEntity(
        recordId = recordId,
        runId = "run-1",
        runSequence = 1,
        createdAtEpochMs = 10,
        recordType = recordType,
        attemptId = attemptId,
        outcome = outcome,
        schemaVersion = schemaVersion,
    )
}
