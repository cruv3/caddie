package com.caddie.runtime.persistence.journal

import androidx.room.withTransaction
import com.caddie.agent.core.ActionAttemptJournal
import com.caddie.agent.core.ActionDispatchClaim
import com.caddie.agent.core.ActionResolution
import com.caddie.agent.core.AttemptId
import com.caddie.agent.core.RecoveryResumeResult
import com.caddie.agent.core.RecoverySessionStore
import com.caddie.agent.core.RunId
import com.caddie.agent.core.RunRecord
import com.caddie.agent.core.RunSnapshot
import com.caddie.agent.core.RunState
import com.caddie.agent.core.StepId
import com.caddie.agent.core.reduce
import com.caddie.runtime.persistence.database.RuntimeDatabase

/** Persists run records and recovery state in the Room journal. */
internal class RoomRecoverySessionStore(
    private val database: RuntimeDatabase,
    private val clock: JournalClock = SystemJournalClock,
) : RecoverySessionStore,
    ActionAttemptJournal {
    private val dao = database.journalDao()
    private val mapper = RunRecordMapper()

    override suspend fun append(event: RunRecord) {
        database.withTransaction {
            appendInTransaction(event)
        }
    }

    override suspend fun snapshot(runId: RunId): RunSnapshot =
        reduce(
            records = dao.records(runId.value).map(mapper::toRecord),
            recoveredProcess = false,
        )

    override suspend fun incompleteAttempts(): List<RunRecord.ActionDispatched> {
        val records = dao.allRecords().map(mapper::toRecord)
        val terminalAttempts =
            records
                .filterIsInstance<RunRecord.ActionTerminal>()
                .mapTo(mutableSetOf()) { it.runId to it.attemptId }
        return records
            .filterIsInstance<RunRecord.ActionDispatched>()
            .filter { it.runId to it.attemptId !in terminalAttempts }
    }

    override suspend fun dispatched(
        record: RunRecord.ActionDispatched,
    ): ActionDispatchClaim =
        database.withTransaction {
            when (appendInTransaction(record)) {
                AppendOutcome.INSERTED -> ActionDispatchClaim.CLAIMED
                AppendOutcome.ALREADY_PRESENT ->
                    ActionDispatchClaim.ALREADY_CLAIMED
            }
        }

    override suspend fun executed(record: RunRecord.ActionExecuted) {
        append(record)
    }

    override suspend fun terminal(record: RunRecord.ActionTerminal) {
        append(record)
    }

    override suspend fun closeAttemptAndPause(
        dispatched: RunRecord.ActionDispatched,
        resolution: ActionResolution,
        stepId: StepId,
    ): Boolean =
        database.withTransaction {
            val terminal =
                RunRecord.ActionTerminal(
                    runId = dispatched.runId,
                    attemptId = dispatched.attemptId,
                    resolution = resolution,
                )
            if (appendInTransaction(terminal) == AppendOutcome.ALREADY_PRESENT) {
                return@withTransaction false
            }

            appendInTransaction(
                RunRecord.RunPaused(
                    runId = dispatched.runId,
                    stepId = stepId,
                    state = RunState.PAUSED_RECOVERABLE,
                    reason = null,
                ),
            )
            true
        }

    override suspend fun resumeAfterRecovery(
        runId: RunId,
        stepId: StepId,
        contextReady: Boolean,
    ): RecoveryResumeResult =
        database.withTransaction {
            val resume =
                RunRecord.RunResumed(
                    runId = runId,
                    stepId = stepId,
                )
            val records =
                dao.records(runId.value).map(mapper::toRecord)
            if (records.any { it == resume }) {
                return@withTransaction RecoveryResumeResult.ALREADY_RESUMED
            }
            val stateHistory = records
            val latestPauseIndex =
                stateHistory.indexOfLast {
                    it is RunRecord.RunPaused &&
                        it.state == RunState.PAUSED_RECOVERABLE
                }
            if (stateHistory.isEmpty()) {
                throw RecoveryNotResumableException()
            }
            val terminal =
                stateHistory.getOrNull(latestPauseIndex - 1)
                    as? RunRecord.ActionTerminal
            val hasMatchingDispatch =
                terminal != null &&
                    stateHistory
                        .take(latestPauseIndex - 1)
                        .filterIsInstance<RunRecord.ActionDispatched>()
                        .any {
                            it.runId == terminal.runId &&
                                it.attemptId == terminal.attemptId
                        }
            val snapshot =
                reduce(
                    records = stateHistory,
                    recoveredProcess = false,
                )
            if (
                latestPauseIndex <= 0 ||
                latestPauseIndex != stateHistory.lastIndex ||
                !hasMatchingDispatch ||
                snapshot.state != RunState.PAUSED_RECOVERABLE ||
                snapshot.unverifiedAttemptId != null
            ) {
                throw RecoveryNotResumableException()
            }
            if (!contextReady) {
                return@withTransaction RecoveryResumeResult.CONTEXT_REQUIRED
            }

            when (
                appendInTransaction(resume)
            ) {
                AppendOutcome.INSERTED -> RecoveryResumeResult.RESUMED
                AppendOutcome.ALREADY_PRESENT ->
                    RecoveryResumeResult.ALREADY_RESUMED
            }
        }

    private suspend fun appendInTransaction(
        record: RunRecord,
    ): AppendOutcome {
        val existing = dao.byId(record.recordId)
        if (existing != null) {
            val proposed =
                mapper.toRow(
                    record = record,
                    sequence = existing.runSequence,
                    createdAtEpochMs = existing.createdAtEpochMs,
                )
            if (existing.sameDurablePayloadAs(proposed)) {
                return AppendOutcome.ALREADY_PRESENT
            }
            throw JournalConflictException(record.recordId)
        }

        val row =
            mapper.toRow(
                record = record,
                sequence = dao.lastSequence(record.runId.value) + 1,
                createdAtEpochMs = clock.nowEpochMillis(),
            )
        return when (dao.append(row)) {
            InsertResult.INSERTED -> AppendOutcome.INSERTED
            InsertResult.ALREADY_PRESENT -> AppendOutcome.ALREADY_PRESENT
        }
    }

    /** Describes the internal result of appending a record. */
    private enum class AppendOutcome {
        INSERTED,
        ALREADY_PRESENT,
    }
}

/** Signals that a run no longer satisfies recovery requirements. */
internal class RecoveryNotResumableException :
    IllegalStateException("Recovery resume is not permitted")
