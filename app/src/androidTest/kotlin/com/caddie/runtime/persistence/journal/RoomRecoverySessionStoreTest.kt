package com.caddie.runtime.persistence.journal

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caddie.agent.core.ActionDispatchClaim
import com.caddie.agent.core.ActionResolution
import com.caddie.agent.core.AttemptId
import com.caddie.agent.core.RecoveryResumeResult
import com.caddie.agent.core.RunId
import com.caddie.agent.core.RunRecord
import com.caddie.agent.core.RunState
import com.caddie.agent.core.SessionId
import com.caddie.agent.core.StepId
import com.caddie.agent.core.ToolCallId
import com.caddie.runtime.persistence.database.RuntimeDatabase
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomRecoverySessionStoreTest {
    private lateinit var context: Context
    private lateinit var database: RuntimeDatabase
    private lateinit var clock: FakeJournalClock
    private lateinit var store: RoomRecoverySessionStore

    @Before
    fun openDatabase() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DATABASE_NAME)
        database = openFileDatabase()
        clock = FakeJournalClock(1_700_000_000_000)
        store = RoomRecoverySessionStore(database, clock)
    }

    @After
    fun closeDatabase() {
        database.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun store_survives_database_reopen_and_exposes_incomplete_attempt() = runTest {
        val dispatched = dispatchedAction()
        store.append(
            RunRecord.RunCreated(
                SessionId("session-1"),
                RunId("run-1"),
                "private task",
            ),
        )
        store.append(RunRecord.RunStarted(RunId("run-1")))
        store.append(dispatched)
        database.close()

        database = openFileDatabase()
        val recoveredStore = RoomRecoverySessionStore(database, clock)

        assertEquals(
            AttemptId("attempt-1"),
            recoveredStore.incompleteAttempts().single().attemptId,
        )
        val snapshot = recoveredStore.snapshot(RunId("run-1"))
        assertEquals(RunState.RUNNING, snapshot.state)
        assertEquals(AttemptId("attempt-1"), snapshot.unverifiedAttemptId)
    }

    @Test
    fun identical_logical_append_after_clock_advance_is_idempotent() = runTest {
        val created =
            RunRecord.RunCreated(
                SessionId("session-1"),
                RunId("run-1"),
                "private task",
            )
        store.append(created)
        clock.now = 1_800_000_000_000

        store.append(created)

        val rows = database.journalDao().records("run-1")
        assertEquals(1, rows.size)
        assertEquals(1_700_000_000_000, rows.single().createdAtEpochMs)
        assertEquals(1L, rows.single().runSequence)
    }

    @Test
    fun same_record_id_with_conflicting_durable_payload_is_rejected() = runTest {
        store.append(
            RunRecord.ToolFinished(
                RunId("run-1"),
                ToolCallId("call-1"),
                """{"private":"first"}""",
                false,
            ),
        )

        try {
            store.append(
                RunRecord.ToolFinished(
                    RunId("run-1"),
                    ToolCallId("call-1"),
                    """{"private":"second"}""",
                    true,
                ),
            )
            fail("Expected JournalConflictException")
        } catch (conflict: JournalConflictException) {
            assertTrue(conflict.message.orEmpty().contains("tool-result:run-1:call-1"))
        }
    }

    @Test
    fun action_journal_methods_share_append_stream_and_terminal_closes_attempt() = runTest {
        val dispatched = dispatchedAction()
        val executed =
            RunRecord.ActionExecuted(
                RunId("run-1"),
                AttemptId("attempt-1"),
                "ACCEPTED",
            )
        val terminal =
            RunRecord.ActionTerminal(
                RunId("run-1"),
                AttemptId("attempt-1"),
                ActionResolution.VERIFIED,
            )

        assertEquals(ActionDispatchClaim.CLAIMED, store.dispatched(dispatched))
        store.executed(executed)
        assertEquals(listOf(dispatched), store.incompleteAttempts())
        store.terminal(terminal)

        assertEquals(
            listOf(
                "ACTION_DISPATCHED",
                "ACTION_EXECUTED",
                "ACTION_TERMINAL",
            ),
            database.journalDao().records("run-1").map { it.recordType },
        )
        assertTrue(store.incompleteAttempts().isEmpty())
    }

    @Test
    fun identical_action_dispatch_returns_one_claim_and_one_already_claimed() = runTest {
        val dispatched = dispatchedAction()

        assertEquals(ActionDispatchClaim.CLAIMED, store.dispatched(dispatched))
        assertEquals(
            ActionDispatchClaim.ALREADY_CLAIMED,
            store.dispatched(dispatched),
        )

        assertEquals(1, database.journalDao().records("run-1").size)
    }

    @Test
    fun conflicting_action_dispatch_with_same_record_id_is_rejected() = runTest {
        val dispatched = dispatchedAction()
        store.dispatched(dispatched)

        try {
            store.dispatched(dispatched.copy(actionKind = "LONG_CLICK"))
            fail("Expected JournalConflictException")
        } catch (conflict: JournalConflictException) {
            assertTrue(
                conflict.message.orEmpty()
                    .contains("action-dispatched:run-1:attempt-1"),
            )
        }

        assertEquals(1, database.journalDao().records("run-1").size)
    }

    @Test
    fun concurrent_identical_action_dispatch_yields_exactly_one_claim() = runTest {
        val dispatched = dispatchedAction()

        val claims =
            listOf(
                async { store.dispatched(dispatched) },
                async { store.dispatched(dispatched) },
            ).awaitAll()

        assertEquals(1, claims.count { it == ActionDispatchClaim.CLAIMED })
        assertEquals(
            1,
            claims.count { it == ActionDispatchClaim.ALREADY_CLAIMED },
        )
        assertEquals(1, database.journalDao().records("run-1").size)
    }

    @Test
    fun terminal_record_closes_matching_attempt_regardless_of_row_order() = runTest {
        val dispatched = dispatchedAction()
        store.terminal(
            RunRecord.ActionTerminal(
                RunId("run-1"),
                AttemptId("attempt-1"),
                ActionResolution.VERIFIED,
            ),
        )
        store.dispatched(dispatched)

        assertTrue(store.incompleteAttempts().isEmpty())
    }

    @Test
    fun close_attempt_and_pause_is_atomic_and_idempotent() = runTest {
        val dispatched = appendRunningAttempt()

        assertTrue(
            store.closeAttemptAndPause(
                dispatched,
                ActionResolution.OUTCOME_UNKNOWN,
                StepId("recovery-step-1"),
            ),
        )
        assertFalse(
            store.closeAttemptAndPause(
                dispatched,
                ActionResolution.OUTCOME_UNKNOWN,
                StepId("recovery-step-1"),
            ),
        )

        assertEquals(
            listOf(
                "RUN_CREATED",
                "RUN_STARTED",
                "ACTION_DISPATCHED",
                "ACTION_TERMINAL",
                "RUN_PAUSED",
            ),
            database.journalDao().records("run-1").map { it.recordType },
        )
        val snapshot = store.snapshot(RunId("run-1"))
        assertEquals(RunState.PAUSED_RECOVERABLE, snapshot.state)
        assertEquals(null, snapshot.unverifiedAttemptId)
    }

    @Test
    fun resume_without_context_does_not_append_or_change_state() = runTest {
        appendRecoverableRun(RunId("run-1"), StepId("pause-step-1"))

        val result =
            store.resumeAfterRecovery(
                RunId("run-1"),
                StepId("resume-step-1"),
                contextReady = false,
            )

        assertEquals(RecoveryResumeResult.CONTEXT_REQUIRED, result)
        assertEquals(5, database.journalDao().records("run-1").size)
        assertEquals(
            RunState.PAUSED_RECOVERABLE,
            store.snapshot(RunId("run-1")).state,
        )
    }

    @Test
    fun resume_without_context_still_requires_a_terminalized_recovery_attempt() = runTest {
        appendPausedRun(RunId("run-1"), StepId("pause-step-1"))

        assertRecoveryNotResumable {
            store.resumeAfterRecovery(
                RunId("run-1"),
                StepId("resume-step-1"),
                contextReady = false,
            )
        }

        assertEquals(
            RunState.PAUSED_RECOVERABLE,
            store.snapshot(RunId("run-1")).state,
        )
        assertEquals(3, database.journalDao().records("run-1").size)
    }

    @Test
    fun resume_rejects_nonexistent_run_without_writing() = runTest {
        assertRecoveryNotResumable {
            store.resumeAfterRecovery(
                RunId("missing-run"),
                StepId("resume-step-1"),
                contextReady = true,
            )
        }

        assertTrue(database.journalDao().allRecords().isEmpty())
    }

    @Test
    fun resume_rejects_running_run_and_preserves_state() = runTest {
        appendStartedRun(RunId("run-1"))
        appendActionTerminal(RunId("run-1"))

        assertRecoveryNotResumable {
            store.resumeAfterRecovery(
                RunId("run-1"),
                StepId("resume-step-1"),
                contextReady = true,
            )
        }

        assertEquals(RunState.RUNNING, store.snapshot(RunId("run-1")).state)
        assertEquals(3, database.journalDao().records("run-1").size)
    }

    @Test
    fun resume_rejects_completed_and_aborted_runs() = runTest {
        val terminalRecords =
            listOf(
                RunRecord.RunCompleted(RunId("completed-run")),
                RunRecord.RunAborted(RunId("aborted-run")),
            )
        terminalRecords.forEach { terminal ->
            appendStartedRun(terminal.runId)
            appendActionTerminal(terminal.runId)
            store.append(terminal)

            assertRecoveryNotResumable {
                store.resumeAfterRecovery(
                    terminal.runId,
                    StepId("resume-${terminal.runId.value}"),
                    contextReady = true,
                )
            }
        }

        assertEquals(
            RunState.COMPLETED,
            store.snapshot(RunId("completed-run")).state,
        )
        assertEquals(
            RunState.ABORTED,
            store.snapshot(RunId("aborted-run")).state,
        )
    }

    @Test
    fun resume_rejects_recoverable_pause_without_action_terminal() = runTest {
        appendPausedRun(RunId("run-1"), StepId("pause-step-1"))

        assertRecoveryNotResumable {
            store.resumeAfterRecovery(
                RunId("run-1"),
                StepId("resume-step-1"),
                contextReady = true,
            )
        }

        assertEquals(
            RunState.PAUSED_RECOVERABLE,
            store.snapshot(RunId("run-1")).state,
        )
        assertEquals(3, database.journalDao().records("run-1").size)
    }

    @Test
    fun resume_rejects_an_orphan_terminal_without_matching_dispatch() = runTest {
        appendStartedRun(RunId("run-1"))
        appendActionTerminal(RunId("run-1"))
        store.append(
            RunRecord.RunPaused(
                RunId("run-1"),
                StepId("pause-step-1"),
                RunState.PAUSED_RECOVERABLE,
                "private recovery reason",
            ),
        )

        assertRecoveryNotResumable {
            store.resumeAfterRecovery(
                RunId("run-1"),
                StepId("resume-step-1"),
                contextReady = true,
            )
        }

        assertEquals(4, database.journalDao().records("run-1").size)
    }

    @Test
    fun resume_rejects_a_recoverable_run_with_a_later_unclosed_attempt() = runTest {
        appendRecoverableRun(RunId("run-1"), StepId("pause-step-1"))
        store.dispatched(
            dispatchedAction().copy(attemptId = AttemptId("attempt-later")),
        )

        assertRecoveryNotResumable {
            store.resumeAfterRecovery(
                RunId("run-1"),
                StepId("resume-step-1"),
                contextReady = true,
            )
        }

        assertEquals(
            AttemptId("attempt-later"),
            store.snapshot(RunId("run-1")).unverifiedAttemptId,
        )
        assertEquals(
            6,
            database.journalDao().records("run-1").size,
        )
    }

    @Test
    fun resume_rejects_a_closed_attempt_appended_after_recovery_pause() = runTest {
        appendRecoverableRun(RunId("run-1"), StepId("pause-step-1"))
        val laterAttempt =
            dispatchedAction().copy(attemptId = AttemptId("attempt-later"))
        store.dispatched(laterAttempt)
        store.terminal(
            RunRecord.ActionTerminal(
                RunId("run-1"),
                laterAttempt.attemptId,
                ActionResolution.VERIFIED,
            ),
        )

        assertRecoveryNotResumable {
            store.resumeAfterRecovery(
                RunId("run-1"),
                StepId("resume-step-1"),
                contextReady = true,
            )
        }
        assertEquals(
            0,
            database.journalDao().records("run-1")
                .count { it.recordType == "RUN_RESUMED" },
        )
    }

    @Test
    fun resume_rejects_an_orphan_terminal_appended_after_recovery_pause() = runTest {
        appendRecoverableRun(RunId("run-1"), StepId("pause-step-1"))
        store.terminal(
            RunRecord.ActionTerminal(
                RunId("run-1"),
                AttemptId("attempt-orphan"),
                ActionResolution.OUTCOME_UNKNOWN,
            ),
        )

        assertRecoveryNotResumable {
            store.resumeAfterRecovery(
                RunId("run-1"),
                StepId("resume-step-1"),
                contextReady = true,
            )
        }
        assertEquals(
            0,
            database.journalDao().records("run-1")
                .count { it.recordType == "RUN_RESUMED" },
        )
    }

    @Test
    fun resume_with_context_appends_once_and_reports_duplicate() = runTest {
        appendRecoverableRun(RunId("run-1"), StepId("pause-step-1"))

        assertEquals(
            RecoveryResumeResult.RESUMED,
            store.resumeAfterRecovery(
                RunId("run-1"),
                StepId("resume-step-1"),
                contextReady = true,
            ),
        )
        clock.now += 1_000
        assertEquals(
            RecoveryResumeResult.ALREADY_RESUMED,
            store.resumeAfterRecovery(
                RunId("run-1"),
                StepId("resume-step-1"),
                contextReady = true,
            ),
        )

        assertEquals(
            listOf(
                "RUN_CREATED",
                "RUN_STARTED",
                "ACTION_DISPATCHED",
                "ACTION_TERMINAL",
                "RUN_PAUSED",
                "RUN_RESUMED",
            ),
            database.journalDao().records("run-1").map { it.recordType },
        )
        assertEquals(RunState.RUNNING, store.snapshot(RunId("run-1")).state)
    }

    @Test
    fun concurrent_resume_appends_once_and_reports_one_duplicate() = runTest {
        appendRecoverableRun(RunId("run-1"), StepId("pause-step-1"))

        val results =
            listOf(
                async {
                    store.resumeAfterRecovery(
                        RunId("run-1"),
                        StepId("resume-step-1"),
                        contextReady = true,
                    )
                },
                async {
                    store.resumeAfterRecovery(
                        RunId("run-1"),
                        StepId("resume-step-1"),
                        contextReady = true,
                    )
                },
            ).awaitAll()

        assertEquals(1, results.count { it == RecoveryResumeResult.RESUMED })
        assertEquals(
            1,
            results.count { it == RecoveryResumeResult.ALREADY_RESUMED },
        )
        assertEquals(
            1,
            database.journalDao().records("run-1")
                .count { it.recordType == "RUN_RESUMED" },
        )
    }

    @Test
    fun identical_delayed_resume_is_already_resumed_after_later_journal_entries() = runTest {
        appendRecoverableRun(RunId("run-1"), StepId("pause-step-1"))
        assertEquals(
            RecoveryResumeResult.RESUMED,
            store.resumeAfterRecovery(
                RunId("run-1"),
                StepId("resume-step-1"),
                contextReady = true,
            ),
        )
        store.dispatched(
            dispatchedAction().copy(attemptId = AttemptId("attempt-later")),
        )

        assertEquals(
            RecoveryResumeResult.ALREADY_RESUMED,
            store.resumeAfterRecovery(
                RunId("run-1"),
                StepId("resume-step-1"),
                contextReady = true,
            ),
        )
        assertEquals(
            1,
            database.journalDao().records("run-1")
                .count { it.recordType == "RUN_RESUMED" },
        )
    }

    @Test
    fun different_resume_step_is_rejected_after_run_already_resumed() = runTest {
        appendRecoverableRun(RunId("run-1"), StepId("pause-step-1"))
        assertEquals(
            RecoveryResumeResult.RESUMED,
            store.resumeAfterRecovery(
                RunId("run-1"),
                StepId("resume-step-1"),
                contextReady = true,
            ),
        )

        assertRecoveryNotResumable {
            store.resumeAfterRecovery(
                RunId("run-1"),
                StepId("resume-step-2"),
                contextReady = true,
            )
        }
        assertEquals(
            1,
            database.journalDao().records("run-1")
                .count { it.recordType == "RUN_RESUMED" },
        )
    }

    private suspend fun appendRunningAttempt(): RunRecord.ActionDispatched {
        val dispatched = dispatchedAction()
        store.append(
            RunRecord.RunCreated(
                SessionId("session-1"),
                RunId("run-1"),
                "private task",
            ),
        )
        store.append(RunRecord.RunStarted(RunId("run-1")))
        store.dispatched(dispatched)
        return dispatched
    }

    private suspend fun appendPausedRun(
        runId: RunId,
        pauseStepId: StepId,
    ) {
        appendStartedRun(runId)
        store.append(
            RunRecord.RunPaused(
                runId,
                pauseStepId,
                RunState.PAUSED_RECOVERABLE,
                "private recovery reason",
            ),
        )
    }

    private suspend fun appendRecoverableRun(
        runId: RunId,
        pauseStepId: StepId,
    ) {
        appendStartedRun(runId)
        store.dispatched(
            dispatchedAction().copy(
                runId = runId,
                attemptId = AttemptId("attempt-${runId.value}"),
            ),
        )
        appendActionTerminal(runId)
        store.append(
            RunRecord.RunPaused(
                runId,
                pauseStepId,
                RunState.PAUSED_RECOVERABLE,
                "private recovery reason",
            ),
        )
    }

    private suspend fun appendStartedRun(runId: RunId) {
        store.append(
            RunRecord.RunCreated(
                SessionId("session-${runId.value}"),
                runId,
                "private task",
            ),
        )
        store.append(RunRecord.RunStarted(runId))
    }

    private suspend fun appendActionTerminal(runId: RunId) {
        store.terminal(
            RunRecord.ActionTerminal(
                runId,
                AttemptId("attempt-${runId.value}"),
                ActionResolution.OUTCOME_UNKNOWN,
            ),
        )
    }

    private suspend fun assertRecoveryNotResumable(
        block: suspend () -> Unit,
    ) {
        try {
            block()
            fail("Expected recovery resume rejection")
        } catch (error: IllegalStateException) {
            assertEquals("Recovery resume is not permitted", error.message)
        }
    }

    private fun dispatchedAction() =
        RunRecord.ActionDispatched(
            RunId("run-1"),
            AttemptId("attempt-1"),
            "CLICK",
            "selector-fingerprint",
            "postcondition-fingerprint",
            "recovery-spec-1",
        )

    private fun openFileDatabase(): RuntimeDatabase =
        Room.databaseBuilder(
            context,
            RuntimeDatabase::class.java,
            DATABASE_NAME,
        ).build()

    private class FakeJournalClock(
        var now: Long,
    ) : JournalClock {
        override fun nowEpochMillis(): Long = now
    }

    private companion object {
        const val DATABASE_NAME = "room-recovery-session-store-test.db"
    }
}
