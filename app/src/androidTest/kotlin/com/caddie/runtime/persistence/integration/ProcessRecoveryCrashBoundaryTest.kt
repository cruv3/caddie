package com.caddie.runtime.persistence.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caddie.agent.core.ActionAttemptJournal
import com.caddie.agent.core.ActionDispatchClaim
import com.caddie.agent.core.ActionResolution
import com.caddie.agent.core.AttemptId
import com.caddie.agent.core.RunId
import com.caddie.agent.core.RunRecord
import com.caddie.agent.core.SessionId
import com.caddie.agent.core.StepId
import com.caddie.executor.accessibility.ActionAlreadyDispatchedException
import com.caddie.executor.accessibility.ActionOutcome
import com.caddie.executor.accessibility.ActionPerformer
import com.caddie.executor.accessibility.ActionRecoveryMetadata
import com.caddie.executor.accessibility.ActionRequest
import com.caddie.executor.accessibility.RequestedAction
import com.caddie.executor.accessibility.SemanticTarget
import com.caddie.executor.accessibility.VerificationDecision
import com.caddie.executor.accessibility.VerifiedActionExecutor
import com.caddie.runtime.persistence.database.RuntimeDatabase
import com.caddie.runtime.persistence.journal.RoomRecoverySessionStore
import com.caddie.runtime.persistence.journal.RunRecordMapper
import com.caddie.runtime.persistence.recovery.ReadOnlyRecoveryProbe
import com.caddie.runtime.persistence.recovery.RecoverableRun
import com.caddie.runtime.persistence.recovery.ReconciliationResult
import com.caddie.runtime.persistence.recovery.RecoveryCoordinator
import com.caddie.runtime.persistence.recovery.RecoveryProbeRegistry
import com.caddie.runtime.persistence.recovery.StoreRecoveryRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProcessRecoveryCrashBoundaryTest {
    private lateinit var context: Context
    private lateinit var database: RuntimeDatabase
    private lateinit var store: RoomRecoverySessionStore

    @Before
    fun openDatabase() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DATABASE_NAME)
        database = openFileDatabase()
        store = RoomRecoverySessionStore(database)
    }

    @After
    fun closeDatabase() {
        database.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun crash_before_dispatched_commit_performs_zero_actions_and_persists_no_attempt() =
        runTest {
            val result =
                runCrashScenario(
                    crashStage = CrashStage.BEFORE_DISPATCHED_COMMIT,
                )

            assertEquals(0, result.physicalActionCount)
            assertAttemptRows(
                result.records,
                dispatched = 0,
                executed = 0,
                terminalResolution = null,
                paused = 0,
            )
            assertTrue(result.recovered.isEmpty())
            assertEquals(0, result.readOnlyObservationCount)
        }

    @Test
    fun crash_after_dispatched_commit_recovers_read_only_without_physical_action() =
        runTest {
            val result =
                runCrashScenario(
                    crashStage = CrashStage.AFTER_DISPATCHED_COMMIT,
                )

            assertEquals(0, result.physicalActionCount)
            assertAttemptRows(
                result.records,
                dispatched = 1,
                executed = 0,
                terminalResolution = ActionResolution.RECONCILIATION_INCONCLUSIVE,
                paused = 1,
            )
            assertRecoveredInconclusive(result)
        }

    @Test
    fun crash_after_physical_dispatch_recovers_without_second_action() =
        runTest {
            val result =
                runCrashScenario(
                    performerCrashesAfterAction = true,
                )

            assertEquals(1, result.physicalActionCount)
            assertAttemptRows(
                result.records,
                dispatched = 1,
                executed = 0,
                terminalResolution = ActionResolution.RECONCILIATION_INCONCLUSIVE,
                paused = 1,
            )
            assertRecoveredInconclusive(result)
        }

    @Test
    fun crash_after_executed_commit_reconciles_without_second_action() =
        runTest {
            val result =
                runCrashScenario(
                    crashStage = CrashStage.AFTER_EXECUTED_COMMIT,
                )

            assertEquals(1, result.physicalActionCount)
            assertAttemptRows(
                result.records,
                dispatched = 1,
                executed = 1,
                terminalResolution = ActionResolution.RECONCILIATION_INCONCLUSIVE,
                paused = 1,
            )
            assertRecoveredInconclusive(result)
        }

    @Test
    fun crash_before_terminal_commit_terminalizes_old_attempt_once() =
        runTest {
            val result =
                runCrashScenario(
                    crashStage = CrashStage.BEFORE_TERMINAL_COMMIT,
                    recoverTwice = true,
                )

            assertEquals(1, result.physicalActionCount)
            assertAttemptRows(
                result.records,
                dispatched = 1,
                executed = 1,
                terminalResolution = ActionResolution.RECONCILIATION_INCONCLUSIVE,
                paused = 1,
            )
            assertRecoveredInconclusive(result)
        }

    @Test
    fun crash_after_terminal_commit_has_no_incomplete_attempt() =
        runTest {
            val result = runCrashScenario(crashStage = null)

            assertEquals(1, result.physicalActionCount)
            assertAttemptRows(
                result.records,
                dispatched = 1,
                executed = 1,
                terminalResolution = ActionResolution.VERIFIED,
                paused = 0,
            )
            assertTrue(result.recovered.isEmpty())
            assertEquals(0, result.readOnlyObservationCount)
        }

    private suspend fun runCrashScenario(
        crashStage: CrashStage? = null,
        performerCrashesAfterAction: Boolean = false,
        recoverTwice: Boolean = false,
    ): ScenarioResult {
        store.append(
            RunRecord.RunCreated(
                sessionId = SessionId("session-1"),
                runId = RUN_ID,
                task = "private task",
            ),
        )
        store.append(RunRecord.RunStarted(RUN_ID))
        val performer = CountingActionPerformer(performerCrashesAfterAction)
        val journal = CrashBoundaryJournal(store, crashStage)

        try {
            VerifiedActionExecutor(journal, performer).execute(REQUEST) {
                VerificationDecision.Satisfied
            }
            if (crashStage != null || performerCrashesAfterAction) {
                fail("Expected simulated process death")
            }
        } catch (_: SimulatedProcessDeath) {
            // The database is deliberately reopened below without cleanup.
        }

        reopenDatabase()
        var readOnlyObservationCount = 0
        val coordinator =
            recoveryCoordinator {
                readOnlyObservationCount++
            }
        val recovered = coordinator.recover()
        if (recoverTwice) {
            assertTrue(coordinator.recover().isEmpty())
        }

        if (hasDurableDispatch()) {
            assertOldAttemptCannotRedispatch(performer)
        }

        return ScenarioResult(
            physicalActionCount = performer.executionCount,
            readOnlyObservationCount = readOnlyObservationCount,
            records =
                database.journalDao().records(RUN_ID.value)
                    .map(RunRecordMapper()::toRecord),
            recovered = recovered,
        )
    }

    private fun reopenDatabase() {
        database.close()
        database = openFileDatabase()
        store = RoomRecoverySessionStore(database)
    }

    private fun recoveryCoordinator(
        onReadOnlyObservation: () -> Unit,
    ): RecoveryCoordinator =
        RecoveryCoordinator(
            repository = StoreRecoveryRepository(store),
            probeRegistry =
                RecoveryProbeRegistry {
                    ReadOnlyRecoveryProbe {
                        onReadOnlyObservation()
                        ReconciliationResult.RECONCILIATION_INCONCLUSIVE
                    }
                },
            stepIds = { StepId("recovery-${it.value}") },
        )

    private suspend fun hasDurableDispatch(): Boolean =
        database.journalDao().records(RUN_ID.value)
            .count { it.recordType == "ACTION_DISPATCHED" } == 1

    private suspend fun assertOldAttemptCannotRedispatch(
        performer: CountingActionPerformer,
    ) {
        try {
            VerifiedActionExecutor(
                CrashBoundaryJournal(store, stage = null),
                performer,
            ).execute(REQUEST) {
                fail("An old attempt must stop before verification")
                VerificationDecision.Satisfied
            }
            fail("An old attempt must not be physically dispatched again")
        } catch (_: ActionAlreadyDispatchedException) {
            // Durable dispatch claim correctly blocks the old attempt.
        }
        assertTrue(performer.executionCount <= 1)
    }

    private fun assertAttemptRows(
        records: List<RunRecord>,
        dispatched: Int,
        executed: Int,
        terminalResolution: ActionResolution?,
        paused: Int,
    ) {
        assertEquals(
            dispatched,
            records.filterIsInstance<RunRecord.ActionDispatched>()
                .count { it.attemptId == ATTEMPT_ID },
        )
        assertEquals(
            executed,
            records.filterIsInstance<RunRecord.ActionExecuted>()
                .count { it.attemptId == ATTEMPT_ID },
        )
        assertEquals(
            listOfNotNull(terminalResolution),
            records.filterIsInstance<RunRecord.ActionTerminal>()
                .filter { it.attemptId == ATTEMPT_ID }
                .map { it.resolution },
        )
        assertEquals(
            paused,
            records.filterIsInstance<RunRecord.RunPaused>().size,
        )
    }

    private fun assertRecoveredInconclusive(result: ScenarioResult) {
        assertEquals(ATTEMPT_ID, result.recovered.single().attemptId)
        assertEquals(
            ReconciliationResult.RECONCILIATION_INCONCLUSIVE,
            result.recovered.single().result,
        )
        assertEquals(1, result.readOnlyObservationCount)
    }

    private fun openFileDatabase(): RuntimeDatabase =
        Room.databaseBuilder(
            context,
            RuntimeDatabase::class.java,
            DATABASE_NAME,
        ).build()

    private enum class CrashStage {
        BEFORE_DISPATCHED_COMMIT,
        AFTER_DISPATCHED_COMMIT,
        AFTER_EXECUTED_COMMIT,
        BEFORE_TERMINAL_COMMIT,
    }

    private class CrashBoundaryJournal(
        private val delegate: ActionAttemptJournal,
        private val stage: CrashStage?,
    ) : ActionAttemptJournal {
        override suspend fun dispatched(
            record: RunRecord.ActionDispatched,
        ): ActionDispatchClaim {
            if (stage == CrashStage.BEFORE_DISPATCHED_COMMIT) {
                throw SimulatedProcessDeath()
            }
            val claim =
                delegate.dispatched(
                    record.copy(recoverySpecId = RECOVERY_SPEC_ID),
                )
            if (stage == CrashStage.AFTER_DISPATCHED_COMMIT) {
                throw SimulatedProcessDeath()
            }
            return claim
        }

        override suspend fun executed(record: RunRecord.ActionExecuted) {
            delegate.executed(record)
            if (stage == CrashStage.AFTER_EXECUTED_COMMIT) {
                throw SimulatedProcessDeath()
            }
        }

        override suspend fun terminal(record: RunRecord.ActionTerminal) {
            if (stage == CrashStage.BEFORE_TERMINAL_COMMIT) {
                throw SimulatedProcessDeath()
            }
            delegate.terminal(record)
        }
    }

    private class CountingActionPerformer(
        private val crashAfterAction: Boolean,
    ) : ActionPerformer {
        var executionCount = 0
            private set

        override suspend fun execute(
            target: SemanticTarget,
            action: RequestedAction,
        ): ActionOutcome {
            executionCount++
            if (crashAfterAction) {
                throw SimulatedProcessDeath()
            }
            return ActionOutcome.Accepted
        }
    }

    private class SimulatedProcessDeath : RuntimeException()

    private data class ScenarioResult(
        val physicalActionCount: Int,
        val readOnlyObservationCount: Int,
        val records: List<RunRecord>,
        val recovered: List<RecoverableRun>,
    )

    private companion object {
        const val DATABASE_NAME = "process-recovery-crash-boundary-test.db"
        const val RECOVERY_SPEC_ID = "allowlisted-test-postcondition"
        val RUN_ID = RunId("run-1")
        val ATTEMPT_ID = AttemptId("attempt-1")
        val REQUEST =
            ActionRequest(
                recovery = ActionRecoveryMetadata(RUN_ID, ATTEMPT_ID),
                target = SemanticTarget(text = "Send"),
                action = RequestedAction.Click,
            )
    }
}
