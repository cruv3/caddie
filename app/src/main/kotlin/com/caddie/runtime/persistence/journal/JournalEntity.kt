package com.caddie.runtime.persistence.journal

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Stores one append-only run record in the Room journal. */
@Entity(
    tableName = "runtime_journal",
    indices = [
        Index(value = ["run_id", "run_sequence"], unique = true),
        Index(value = ["run_id", "attempt_id"]),
    ],
)
internal data class JournalEntity(
    @PrimaryKey
    @ColumnInfo(name = "record_id")
    val recordId: String,
    @ColumnInfo(name = "run_id")
    val runId: String,
    @ColumnInfo(name = "run_sequence")
    val runSequence: Long,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "record_type")
    val recordType: String,
    @ColumnInfo(name = "attempt_id")
    val attemptId: String? = null,
    @ColumnInfo(name = "session_id")
    val sessionId: String? = null,
    @ColumnInfo(name = "step_id")
    val stepId: String? = null,
    @ColumnInfo(name = "tool_call_id")
    val toolCallId: String? = null,
    @ColumnInfo(name = "tool_name")
    val toolName: String? = null,
    @ColumnInfo(name = "run_state")
    val runState: String? = null,
    @ColumnInfo(name = "action_kind")
    val actionKind: String? = null,
    @ColumnInfo(name = "selector_fingerprint")
    val selectorFingerprint: String? = null,
    @ColumnInfo(name = "postcondition_fingerprint")
    val postconditionFingerprint: String? = null,
    @ColumnInfo(name = "recovery_spec_id")
    val recoverySpecId: String? = null,
    @ColumnInfo(name = "outcome")
    val outcome: String? = null,
    @ColumnInfo(name = "failure_category")
    val failureCategory: String? = null,
    @ColumnInfo(name = "is_error")
    val isError: Boolean? = null,
    @ColumnInfo(name = "schema_version")
    val schemaVersion: Int = 1,
) {
    fun sameDurablePayloadAs(other: JournalEntity): Boolean =
        recordId == other.recordId &&
            runId == other.runId &&
            recordType == other.recordType &&
            attemptId == other.attemptId &&
            sessionId == other.sessionId &&
            stepId == other.stepId &&
            toolCallId == other.toolCallId &&
            toolName == other.toolName &&
            runState == other.runState &&
            actionKind == other.actionKind &&
            selectorFingerprint == other.selectorFingerprint &&
            postconditionFingerprint == other.postconditionFingerprint &&
            recoverySpecId == other.recoverySpecId &&
            outcome == other.outcome &&
            failureCategory == other.failureCategory &&
            isError == other.isError &&
            schemaVersion == other.schemaVersion
}
