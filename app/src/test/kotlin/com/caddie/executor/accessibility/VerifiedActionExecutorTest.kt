package com.caddie.executor.accessibility

import com.caddie.agent.core.ActionAttemptJournal
import com.caddie.agent.core.ActionDispatchClaim
import com.caddie.agent.core.ActionResolution
import com.caddie.agent.core.AttemptId
import com.caddie.agent.core.RunId
import com.caddie.agent.core.RunRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class VerifiedActionExecutorTest {
    private val recovery =
        ActionRecoveryMetadata(
            runId = RunId("run-1"),
            attemptId = AttemptId("attempt-1"),
        )
    private val request =
        ActionRequest(
            recovery = recovery,
            target = SemanticTarget(text = "Send"),
            action = RequestedAction.Click,
        )

    @Test
    fun `durable dispatched completes before physical action then executed and terminal`() =
        runTest {
            val order = mutableListOf<String>()
            val journal = RecordingActionJournal(order = order)
            val performer =
                CountingActionPerformer(ActionOutcome.Accepted) {
                    order += "PERFORMER"
                }

            val result =
                executor(journal, performer).execute(request) {
                    order += "VERIFIER"
                    VerificationDecision.Satisfied
                }

            assertEquals(
                listOf("DISPATCHED", "PERFORMER", "EXECUTED", "VERIFIER", "TERMINAL"),
                order,
            )
            assertEquals(
                listOf(
                    RunRecord.ActionDispatched(
                        runId = RunId("run-1"),
                        attemptId = AttemptId("attempt-1"),
                        actionKind = "CLICK",
                        selectorFingerprint = null,
                        postconditionFingerprint = null,
                        recoverySpecId = null,
                    ),
                    RunRecord.ActionExecuted(
                        runId = RunId("run-1"),
                        attemptId = AttemptId("attempt-1"),
                        dispatchOutcome = "Accepted",
                    ),
                    RunRecord.ActionTerminal(
                        runId = RunId("run-1"),
                        attemptId = AttemptId("attempt-1"),
                        resolution = ActionResolution.VERIFIED,
                    ),
                ),
                journal.records,
            )
            assertEquals("action-dispatched:run-1:attempt-1", journal.records[0].recordId)
            assertEquals("action-executed:run-1:attempt-1", journal.records[1].recordId)
            assertEquals("action-terminal:run-1:attempt-1", journal.records[2].recordId)
            assertEquals("attempt-1", result.attemptId)
            assertEquals(1, performer.executionCount)
        }

    @Test
    fun `dispatched action kind is derived exhaustively without persisting set text`() =
        runTest {
            val sensitiveText = "private password 123"
            val cases =
                listOf(
                    RequestedAction.Click to "CLICK",
                    RequestedAction.LongClick to "LONG_CLICK",
                    RequestedAction.SetText(sensitiveText, submit = true) to "SET_TEXT",
                    RequestedAction.SetChecked(checked = true) to "SET_CHECKED",
                    RequestedAction.Scroll(forward = true) to "SCROLL",
                )

            cases.forEach { (action, expectedKind) ->
                val journal = RecordingActionJournal()
                val performer = CountingActionPerformer(ActionOutcome.AlreadySatisfied)

                executor(journal, performer).execute(request.copy(action = action)) {
                    error("already-satisfied action must skip verification")
                }

                val dispatched =
                    journal.records.filterIsInstance<RunRecord.ActionDispatched>().single()
                assertEquals(expectedKind, dispatched.actionKind)
                assertEquals(null, dispatched.selectorFingerprint)
                assertEquals(null, dispatched.postconditionFingerprint)
                assertEquals(null, dispatched.recoverySpecId)
                assertFalse(dispatched.toString().contains(sensitiveText))
            }
        }

    @Test
    fun `crash before dispatched commit leaves zero durable and physical actions`() =
        runTest {
            val journal = RecordingActionJournal(failAt = JournalStage.DISPATCHED)
            val performer = CountingActionPerformer(ActionOutcome.Accepted)
            var verificationCalls = 0

            assertJournalFailure {
                executor(journal, performer).execute(request) {
                    verificationCalls++
                    VerificationDecision.Satisfied
                }
            }

            assertEquals(0, performer.executionCount)
            assertEquals(0, verificationCalls)
            assertEquals(
                0,
                journal.records.filterIsInstance<RunRecord.ActionDispatched>()
                    .count { it.attemptId == recovery.attemptId },
            )
            assertEquals(emptyList<RunRecord>(), journal.records)
        }

    @Test
    fun `executed journal failure after accepted action skips verification without retry`() =
        runTest {
            val journal = RecordingActionJournal(failAt = JournalStage.EXECUTED)
            val performer = CountingActionPerformer(ActionOutcome.Accepted)
            var verificationCalls = 0

            assertJournalFailure {
                executor(journal, performer).execute(request) {
                    verificationCalls++
                    VerificationDecision.Satisfied
                }
            }

            assertEquals(1, performer.executionCount)
            assertEquals(0, verificationCalls)
            assertEquals(listOf(RunRecord.ActionDispatched::class), journal.records.map { it::class })
        }

    @Test
    fun `terminal journal failure propagates without retry`() =
        runTest {
            val journal = RecordingActionJournal(failAt = JournalStage.TERMINAL)
            val performer = CountingActionPerformer(ActionOutcome.Accepted)
            var verificationCalls = 0

            assertJournalFailure {
                executor(journal, performer).execute(request) {
                    verificationCalls++
                    VerificationDecision.Satisfied
                }
            }

            assertEquals(1, performer.executionCount)
            assertEquals(1, verificationCalls)
            assertEquals(
                listOf(RunRecord.ActionDispatched::class, RunRecord.ActionExecuted::class),
                journal.records.map { it::class },
            )
        }

    @Test
    fun `retry after performer exception cannot physically dispatch same attempt twice`() =
        runTest {
            val journal = RecordingActionJournal()
            val performer =
                object : ActionPerformer {
                    var executionCount = 0

                    override suspend fun execute(
                        target: SemanticTarget,
                        action: RequestedAction,
                    ): ActionOutcome {
                        executionCount++
                        error("physical dispatch failed")
                    }
                }
            var retryVerificationCalls = 0

            try {
                executor(journal, performer).execute(request) {
                    error("verification should not run")
                }
                fail("performer failure should propagate")
            } catch (failure: IllegalStateException) {
                assertEquals("physical dispatch failed", failure.message)
            }
            assertAlreadyDispatched {
                executor(journal, performer).execute(request) {
                    retryVerificationCalls++
                    VerificationDecision.Satisfied
                }
            }

            assertEquals(1, performer.executionCount)
            assertEquals(0, retryVerificationCalls)
            assertEquals(
                1,
                journal.records.filterIsInstance<RunRecord.ActionDispatched>()
                    .count { it.attemptId == recovery.attemptId },
            )
            assertEquals(listOf(RunRecord.ActionDispatched::class), journal.records.map { it::class })
        }

    @Test
    fun `retry after executed journal failure cannot physically dispatch same attempt twice`() =
        runTest {
            val journal = RecordingActionJournal(failAt = JournalStage.EXECUTED)
            val performer = CountingActionPerformer(ActionOutcome.Accepted)
            var verificationCalls = 0

            assertJournalFailure {
                executor(journal, performer).execute(request) {
                    verificationCalls++
                    VerificationDecision.Satisfied
                }
            }
            assertAlreadyDispatched {
                executor(journal, performer).execute(request) {
                    verificationCalls++
                    VerificationDecision.Satisfied
                }
            }

            assertEquals(1, performer.executionCount)
            assertEquals(0, verificationCalls)
            assertEquals(listOf(RunRecord.ActionDispatched::class), journal.records.map { it::class })
        }

    @Test
    fun `retry after verifier cancellation cannot physically dispatch same attempt twice`() =
        runTest {
            val journal = RecordingActionJournal()
            val performer = CountingActionPerformer(ActionOutcome.Accepted)
            var retryVerificationCalls = 0
            val first =
                launch {
                    executor(journal, performer).execute(request) {
                        awaitCancellation()
                    }
                }
            yield()
            first.cancel()
            first.join()

            assertAlreadyDispatched {
                executor(journal, performer).execute(request) {
                    retryVerificationCalls++
                    VerificationDecision.Satisfied
                }
            }

            assertEquals(1, performer.executionCount)
            assertEquals(0, retryVerificationCalls)
            assertEquals(
                listOf(RunRecord.ActionDispatched::class, RunRecord.ActionExecuted::class),
                journal.records.map { it::class },
            )
        }

    @Test
    fun `retry after terminal journal failure cannot physically dispatch same attempt twice`() =
        runTest {
            val journal = RecordingActionJournal(failAt = JournalStage.TERMINAL)
            val performer = CountingActionPerformer(ActionOutcome.Accepted)
            var verificationCalls = 0

            assertJournalFailure {
                executor(journal, performer).execute(request) {
                    verificationCalls++
                    VerificationDecision.Satisfied
                }
            }
            assertAlreadyDispatched {
                executor(journal, performer).execute(request) {
                    verificationCalls++
                    VerificationDecision.Satisfied
                }
            }

            assertEquals(1, performer.executionCount)
            assertEquals(1, verificationCalls)
            assertEquals(
                listOf(RunRecord.ActionDispatched::class, RunRecord.ActionExecuted::class),
                journal.records.map { it::class },
            )
        }

    @Test
    fun `cancellation during physical performer propagates with only dispatched record`() =
        runTest {
            val journal = RecordingActionJournal()
            val performer =
                object : ActionPerformer {
                    var executionCount = 0

                    override suspend fun execute(
                        target: SemanticTarget,
                        action: RequestedAction,
                    ): ActionOutcome {
                        executionCount++
                        awaitCancellation()
                    }
                }
            var cancellationObserved = false

            val job =
                launch {
                    try {
                        executor(journal, performer).execute(request) {
                            fail("verification should not run")
                            VerificationDecision.Satisfied
                        }
                    } catch (_: CancellationException) {
                        cancellationObserved = true
                    }
                }
            yield()
            job.cancel()
            job.join()

            assertTrue(cancellationObserved)
            assertEquals(1, performer.executionCount)
            assertEquals(listOf(RunRecord.ActionDispatched::class), journal.records.map { it::class })
        }

    @Test
    fun `cancellation during verifier propagates with dispatched and executed but no terminal`() =
        runTest {
            val journal = RecordingActionJournal()
            val performer = CountingActionPerformer(ActionOutcome.Accepted)
            var cancellationObserved = false

            val job =
                launch {
                    try {
                        executor(journal, performer).execute(request) {
                            awaitCancellation()
                        }
                    } catch (_: CancellationException) {
                        cancellationObserved = true
                    }
                }
            yield()
            job.cancel()
            job.join()

            assertTrue(cancellationObserved)
            assertEquals(1, performer.executionCount)
            assertEquals(
                listOf(RunRecord.ActionDispatched::class, RunRecord.ActionExecuted::class),
                journal.records.map { it::class },
            )
        }

    @Test
    fun `result statuses map to durable terminal resolutions`() =
        runTest {
            val cases =
                listOf(
                    TerminalCase(
                        outcome = ActionOutcome.Accepted,
                        verification = { VerificationDecision.Satisfied },
                        expectedStatus = ExecutionStatus.VERIFIED,
                        expectedResolution = ActionResolution.VERIFIED,
                    ),
                    TerminalCase(
                        outcome = ActionOutcome.AlreadySatisfied,
                        expectedStatus = ExecutionStatus.ALREADY_SATISFIED,
                        expectedResolution = ActionResolution.VERIFIED,
                    ),
                    TerminalCase(
                        outcome = ActionOutcome.Accepted,
                        verification = { VerificationDecision.Contradicted },
                        expectedStatus = ExecutionStatus.POSTCONDITION_UNMET,
                        expectedResolution = ActionResolution.OUTCOME_UNKNOWN,
                    ),
                    TerminalCase(
                        outcome = ActionOutcome.Accepted,
                        verification = { error("observation failed") },
                        expectedStatus = ExecutionStatus.OUTCOME_UNKNOWN,
                        expectedResolution = ActionResolution.OUTCOME_UNKNOWN,
                    ),
                )

            cases.forEach { case ->
                val journal = RecordingActionJournal()
                val performer = CountingActionPerformer(case.outcome)

                val result =
                    executor(journal, performer).execute(request) {
                        case.verification?.invoke()
                            ?: error("typed pre-acceptance outcome must skip verification")
                    }

                assertEquals(case.expectedStatus, result.status)
                assertEquals(case.expectedResolution, journal.terminalRecord().resolution)
                assertEquals(1, performer.executionCount)
            }
        }

    @Test
    fun `every direct outcome has a literal wire name and skips verification`() =
        runTest {
            val expectedOutcomes =
                mapOf(
                    ActionOutcome.TargetMissing to
                        DirectOutcomeCase(ExecutionStatus.TARGET_MISSING, "TargetMissing"),
                    ActionOutcome.TargetAmbiguous to
                        DirectOutcomeCase(ExecutionStatus.TARGET_AMBIGUOUS, "TargetAmbiguous"),
                    ActionOutcome.TargetNotVisible to
                        DirectOutcomeCase(ExecutionStatus.TARGET_NOT_VISIBLE, "TargetNotVisible"),
                    ActionOutcome.TargetDisabled to
                        DirectOutcomeCase(ExecutionStatus.TARGET_DISABLED, "TargetDisabled"),
                    ActionOutcome.BlockedByWindow to
                        DirectOutcomeCase(ExecutionStatus.BLOCKED_BY_WINDOW, "BlockedByWindow"),
                    ActionOutcome.ActionUnavailable to
                        DirectOutcomeCase(ExecutionStatus.ACTION_UNAVAILABLE, "ActionUnavailable"),
                    ActionOutcome.ActionRejected to
                        DirectOutcomeCase(ExecutionStatus.ACTION_REJECTED, "ActionRejected"),
                    ActionOutcome.StaleObservation to
                        DirectOutcomeCase(ExecutionStatus.STALE_OBSERVATION, "StaleObservation"),
                    ActionOutcome.AccessibilityUnavailable to
                        DirectOutcomeCase(
                            ExecutionStatus.ACCESSIBILITY_UNAVAILABLE,
                            "AccessibilityUnavailable",
                        ),
                    ActionOutcome.InvalidTarget to
                        DirectOutcomeCase(ExecutionStatus.INVALID_TARGET, "InvalidTarget"),
                    ActionOutcome.AlreadySatisfied to
                        DirectOutcomeCase(
                            status = ExecutionStatus.ALREADY_SATISFIED,
                            wireName = "AlreadySatisfied",
                            resolution = ActionResolution.VERIFIED,
                        ),
                )
            var verificationCalls = 0

            expectedOutcomes.forEach { (outcome, expected) ->
                val journal = RecordingActionJournal()
                val performer = CountingActionPerformer(outcome)
                val result =
                    executor(journal, performer).execute(request) {
                        verificationCalls++
                        VerificationDecision.Satisfied
                    }

                assertEquals(expected.status, result.status)
                assertEquals(outcome, result.dispatchOutcome)
                assertEquals(expected.resolution, journal.terminalRecord().resolution)
                assertEquals(1, performer.executionCount)
                assertEquals(expected.wireName, journal.executedRecord().dispatchOutcome)
            }

            assertEquals(0, verificationCalls)
        }

    @Test
    fun `accepted verification timeout is unknown and terminal is durable`() =
        runTest {
            val journal = RecordingActionJournal()
            val performer = CountingActionPerformer(ActionOutcome.Accepted)
            val timeoutRequest =
                request.copy(
                    recovery =
                        recovery.copy(
                            attemptId = AttemptId("attempt-7"),
                        ),
                )

            val result =
                VerifiedActionExecutor(
                    actionAttemptJournal = journal,
                    actionPerformer = performer,
                    verificationTimeoutMillis = 100,
                ).execute(timeoutRequest) {
                    delay(101)
                    VerificationDecision.Satisfied
                }

            assertEquals("attempt-7", result.attemptId)
            assertEquals(ExecutionStatus.OUTCOME_UNKNOWN, result.status)
            assertEquals(ActionResolution.OUTCOME_UNKNOWN, journal.terminalRecord().resolution)
            assertEquals(1, performer.executionCount)
        }

    @Test
    fun `accepted action polls transient observations until postcondition is satisfied`() =
        runTest {
            val journal = RecordingActionJournal()
            val performer = CountingActionPerformer(ActionOutcome.Accepted)
            var verificationCalls = 0

            val result =
                VerifiedActionExecutor(
                    actionAttemptJournal = journal,
                    actionPerformer = performer,
                    verificationTimeoutMillis = 100,
                    verificationPollIntervalMillis = 10,
                ).execute(request) {
                    verificationCalls += 1
                    when (verificationCalls) {
                        1 -> error("accessibility root is changing")
                        2 -> VerificationDecision.Contradicted
                        else -> VerificationDecision.Satisfied
                    }
                }

            assertEquals(ExecutionStatus.VERIFIED, result.status)
            assertEquals(3, verificationCalls)
            assertEquals(1, performer.executionCount)
            assertEquals(ActionResolution.VERIFIED, journal.terminalRecord().resolution)
        }

    @Test
    fun `default verification window tolerates a slow Android activity handoff`() =
        runTest {
            val journal = RecordingActionJournal()
            val performer = CountingActionPerformer(ActionOutcome.Accepted)
            var verificationCalls = 0

            val result = executor(journal, performer).execute(request) {
                verificationCalls += 1
                if (verificationCalls < 56) {
                    error("new activity has no accessibility root yet")
                }
                VerificationDecision.Satisfied
            }

            assertEquals(ExecutionStatus.VERIFIED, result.status)
            assertEquals(1, performer.executionCount)
        }

    @Test
    fun `persistent contradiction is unmet without redispatching physical action`() =
        runTest {
            val journal = RecordingActionJournal()
            val performer = CountingActionPerformer(ActionOutcome.Accepted)
            var verificationCalls = 0

            val result =
                VerifiedActionExecutor(
                    actionAttemptJournal = journal,
                    actionPerformer = performer,
                    verificationTimeoutMillis = 100,
                    verificationPollIntervalMillis = 10,
                ).execute(request) {
                    verificationCalls += 1
                    VerificationDecision.Contradicted
                }

            assertEquals(ExecutionStatus.POSTCONDITION_UNMET, result.status)
            assertTrue(verificationCalls > 1)
            assertEquals(1, performer.executionCount)
            assertEquals(ActionResolution.OUTCOME_UNKNOWN, journal.terminalRecord().resolution)
        }

    @Test
    fun `ordinary verifier exception is unknown without retry`() =
        runTest {
            val journal = RecordingActionJournal()
            val performer = CountingActionPerformer(ActionOutcome.Accepted)

            val result =
                executor(journal, performer).execute(request) {
                    error("observation failed")
                }

            assertEquals(ExecutionStatus.OUTCOME_UNKNOWN, result.status)
            assertEquals(ActionResolution.OUTCOME_UNKNOWN, journal.terminalRecord().resolution)
            assertEquals(1, performer.executionCount)
        }

    @Test
    fun `performer exception propagates after dispatched without retry or terminal`() =
        runTest {
            val journal = RecordingActionJournal()
            val performer =
                object : ActionPerformer {
                    var executionCount = 0

                    override suspend fun execute(
                        target: SemanticTarget,
                        action: RequestedAction,
                    ): ActionOutcome {
                        executionCount++
                        error("physical dispatch failed")
                    }
                }

            try {
                executor(journal, performer).execute(request) {
                    fail("verification should not run")
                    VerificationDecision.Satisfied
                }
                fail("performer failure should propagate")
            } catch (failure: IllegalStateException) {
                assertEquals("physical dispatch failed", failure.message)
            }

            assertEquals(1, performer.executionCount)
            assertEquals(listOf(RunRecord.ActionDispatched::class), journal.records.map { it::class })
        }

    @Test
    fun `recovery metadata contains only durable run and attempt ids`() {
        assertThrows(IllegalArgumentException::class.java) {
            AttemptId("  ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            RunId("  ")
        }
        assertEquals(
            setOf(
                "runId",
                "attemptId",
            ),
            ActionRecoveryMetadata::class.java.declaredFields
                .filter { it.name != "\$stable" }
                .map { it.name }
                .toSet(),
        )
    }

    private fun executor(
        journal: ActionAttemptJournal,
        performer: ActionPerformer,
    ) = VerifiedActionExecutor(
        actionAttemptJournal = journal,
        actionPerformer = performer,
    )

    private data class TerminalCase(
        val outcome: ActionOutcome,
        val verification: (suspend () -> VerificationDecision)? = null,
        val expectedStatus: ExecutionStatus,
        val expectedResolution: ActionResolution,
    )

    private data class DirectOutcomeCase(
        val status: ExecutionStatus,
        val wireName: String,
        val resolution: ActionResolution = ActionResolution.FAILED,
    )

    private enum class JournalStage {
        DISPATCHED,
        EXECUTED,
        TERMINAL,
    }

    private class JournalFailure(stage: JournalStage) :
        RuntimeException("journal failed at $stage")

    private class RecordingActionJournal(
        private val order: MutableList<String>? = null,
        private val failAt: JournalStage? = null,
    ) : ActionAttemptJournal {
        val records = mutableListOf<RunRecord>()
        private val dispatchClaims = mutableMapOf<String, RunRecord.ActionDispatched>()

        override suspend fun dispatched(
            record: RunRecord.ActionDispatched,
        ): ActionDispatchClaim {
            order?.add(JournalStage.DISPATCHED.name)
            if (failAt == JournalStage.DISPATCHED) {
                throw JournalFailure(JournalStage.DISPATCHED)
            }
            val existing = dispatchClaims[record.recordId]
            if (existing != null) {
                check(existing == record)
                return ActionDispatchClaim.ALREADY_CLAIMED
            }
            dispatchClaims[record.recordId] = record
            records += record
            return ActionDispatchClaim.CLAIMED
        }

        override suspend fun executed(record: RunRecord.ActionExecuted) {
            record(JournalStage.EXECUTED, record)
        }

        override suspend fun terminal(record: RunRecord.ActionTerminal) {
            record(JournalStage.TERMINAL, record)
        }

        private fun record(
            stage: JournalStage,
            record: RunRecord,
        ) {
            order?.add(stage.name)
            if (failAt == stage) {
                throw JournalFailure(stage)
            }
            records += record
        }

        fun executedRecord(): RunRecord.ActionExecuted =
            records.filterIsInstance<RunRecord.ActionExecuted>().single()

        fun terminalRecord(): RunRecord.ActionTerminal =
            records.filterIsInstance<RunRecord.ActionTerminal>().single()
    }

    private class CountingActionPerformer(
        private val outcome: ActionOutcome,
        private val onExecute: () -> Unit = {},
    ) : ActionPerformer {
        var executionCount = 0

        override suspend fun execute(
            target: SemanticTarget,
            action: RequestedAction,
        ): ActionOutcome {
            executionCount++
            onExecute()
            return outcome
        }
    }

    private suspend fun assertJournalFailure(block: suspend () -> Unit) {
        try {
            block()
            fail("journal failure should propagate")
        } catch (_: JournalFailure) {
            // Expected.
        }
    }

    private suspend fun assertAlreadyDispatched(block: suspend () -> Unit) {
        try {
            block()
            fail("duplicate dispatch claim should stop execution")
        } catch (failure: ActionAlreadyDispatchedException) {
            assertEquals("Action attempt was already dispatched", failure.message)
        }
    }
}
