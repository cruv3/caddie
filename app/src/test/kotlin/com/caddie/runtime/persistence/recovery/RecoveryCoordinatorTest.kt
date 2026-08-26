package com.caddie.runtime.persistence.recovery

import com.caddie.agent.core.ActionResolution
import com.caddie.agent.core.AttemptId
import com.caddie.agent.core.RecoveryResumeResult
import com.caddie.agent.core.RecoverySessionStore
import com.caddie.agent.core.RunId
import com.caddie.agent.core.RunRecord
import com.caddie.agent.core.RunSnapshot
import com.caddie.agent.core.RunState
import com.caddie.agent.core.StepId
import java.util.concurrent.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RecoveryCoordinatorTest {
    @Test
    fun `authorized retry requires a distinct new attempt id`() {
        val oldAttempt = attempt(attemptId = "attempt-old")

        val newAttemptId =
            requireDistinctRetryAttemptId(
                oldAttemptId = oldAttempt.attemptId,
                proposedAttemptId = AttemptId("attempt-new"),
            )

        assertNotEquals(oldAttempt.attemptId, newAttemptId)
    }

    @Test
    fun `authorized retry rejects reuse of recovered attempt id`() {
        val oldAttempt = attempt(attemptId = "attempt-old")

        try {
            requireDistinctRetryAttemptId(
                oldAttemptId = oldAttempt.attemptId,
                proposedAttemptId = oldAttempt.attemptId,
            )
            fail("Expected reused recovery attempt ID to be rejected")
        } catch (error: IllegalArgumentException) {
            assertEquals(
                "A retry requires a new attempt ID",
                error.message,
            )
        }
    }

    @Test
    fun `satisfied probe closes old attempt and leaves run paused for explicit resume`() = runTest {
        val repository = FakeRecoveryRepository(listOf(attempt()))
        val registry =
            RecordingRegistry(
                mapOf(
                    "known-spec" to
                        ReadOnlyRecoveryProbe {
                            ReconciliationResult.POSTCONDITION_SATISFIED
                        },
                ),
            )
        val coordinator = coordinator(repository, registry)

        val recovered = coordinator.recover()

        assertEquals(
            listOf(
                RecoverableRun(
                    runId = RunId("run-1"),
                    attemptId = AttemptId("attempt-1"),
                    result = ReconciliationResult.POSTCONDITION_SATISFIED,
                    requiresExplicitResume = true,
                ),
            ),
            recovered,
        )
        assertEquals(
            ActionResolution.POSTCONDITION_SATISFIED,
            repository.closed.single().resolution,
        )
        assertEquals(RunState.PAUSED_RECOVERABLE, repository.pausedState)
        assertTrue(recovered.single().requiresExplicitResume)
    }

    @Test
    fun `missing recovery spec is unavailable without consulting registry`() = runTest {
        val repository = FakeRecoveryRepository(listOf(attempt(recoverySpecId = null)))
        val registry = RecordingRegistry(emptyMap())

        val recovered = coordinator(repository, registry).recover()

        assertEquals(
            ReconciliationResult.RECONCILIATION_UNAVAILABLE,
            recovered.single().result,
        )
        assertEquals(emptyList<String>(), registry.resolvedSpecIds)
        assertEquals(
            ActionResolution.RECONCILIATION_UNAVAILABLE,
            repository.closed.single().resolution,
        )
    }

    @Test
    fun `nonallowlisted recovery spec is unavailable without evaluating a probe`() = runTest {
        val repository = FakeRecoveryRepository(listOf(attempt(recoverySpecId = "sensitive-spec")))
        val registry = RecordingRegistry(emptyMap())

        val recovered = coordinator(repository, registry).recover()

        assertEquals(
            ReconciliationResult.RECONCILIATION_UNAVAILABLE,
            recovered.single().result,
        )
        assertEquals(listOf("sensitive-spec"), registry.resolvedSpecIds)
        assertEquals(0, registry.evaluationCount)
    }

    @Test
    fun `recovery contracts expose only read operations`() {
        assertEquals(
            setOf("evaluate"),
            ReadOnlyRecoveryProbe::class.java.declaredMethods.mapTo(mutableSetOf()) { it.name },
        )
        assertEquals(
            setOf("resolve"),
            RecoveryProbeRegistry::class.java.declaredMethods.mapTo(mutableSetOf()) { it.name },
        )
    }

    @Test
    fun `not satisfied probe closes attempt and still requires explicit resume`() = runTest {
        val repository = FakeRecoveryRepository(listOf(attempt()))
        val registry =
            RecordingRegistry(
                mapOf(
                    "known-spec" to
                        ReadOnlyRecoveryProbe {
                            ReconciliationResult.POSTCONDITION_NOT_SATISFIED
                        },
                ),
            )

        val recovered = coordinator(repository, registry).recover()

        assertEquals(
            ReconciliationResult.POSTCONDITION_NOT_SATISFIED,
            recovered.single().result,
        )
        assertEquals(
            ActionResolution.POSTCONDITION_NOT_SATISFIED,
            repository.closed.single().resolution,
        )
        assertTrue(recovered.single().requiresExplicitResume)
        assertEquals(RunState.PAUSED_RECOVERABLE, repository.pausedState)
    }

    @Test
    fun `inconclusive probe closes attempt with matching resolution`() = runTest {
        val repository = FakeRecoveryRepository(listOf(attempt()))
        val registry =
            RecordingRegistry(
                mapOf(
                    "known-spec" to
                        ReadOnlyRecoveryProbe {
                            ReconciliationResult.RECONCILIATION_INCONCLUSIVE
                        },
                ),
            )

        val recovered = coordinator(repository, registry).recover()

        assertEquals(
            ReconciliationResult.RECONCILIATION_INCONCLUSIVE,
            recovered.single().result,
        )
        assertEquals(
            ActionResolution.RECONCILIATION_INCONCLUSIVE,
            repository.closed.single().resolution,
        )
        assertTrue(recovered.single().requiresExplicitResume)
    }

    @Test
    fun `ordinary probe exception becomes unavailable`() = runTest {
        val repository = FakeRecoveryRepository(listOf(attempt()))
        val registry =
            RecordingRegistry(
                mapOf(
                    "known-spec" to
                        ReadOnlyRecoveryProbe {
                            throw IllegalStateException("probe failed")
                        },
                ),
            )

        val recovered = coordinator(repository, registry).recover()

        assertEquals(
            ReconciliationResult.RECONCILIATION_UNAVAILABLE,
            recovered.single().result,
        )
        assertEquals(
            ActionResolution.RECONCILIATION_UNAVAILABLE,
            repository.closed.single().resolution,
        )
        assertEquals(1, registry.evaluationCount)
    }

    @Test
    fun `ordinary registry exception becomes unavailable and pauses for explicit resume`() = runTest {
        val repository = FakeRecoveryRepository(listOf(attempt()))
        val registry =
            RecoveryProbeRegistry {
                throw IllegalStateException("registry failed")
            }

        val recovered = coordinator(repository, registry).recover()

        assertEquals(
            ReconciliationResult.RECONCILIATION_UNAVAILABLE,
            recovered.single().result,
        )
        assertEquals(
            ActionResolution.RECONCILIATION_UNAVAILABLE,
            repository.closed.single().resolution,
        )
        assertEquals(1, repository.pauseCount)
        assertEquals(RunState.PAUSED_RECOVERABLE, repository.pausedState)
        assertTrue(recovered.single().requiresExplicitResume)
    }

    @Test
    fun `probe cancellation propagates without closing repository`() = runTest {
        val repository = FakeRecoveryRepository(listOf(attempt()))
        val cancellation = CancellationException("cancel recovery")
        val registry =
            RecordingRegistry(
                mapOf(
                    "known-spec" to
                        ReadOnlyRecoveryProbe {
                            throw cancellation
                        },
                ),
            )

        try {
            coordinator(repository, registry).recover()
            fail("Expected recovery cancellation")
        } catch (actual: CancellationException) {
            assertTrue(actual === cancellation)
        }

        assertEquals(emptyList<ClosedAttempt>(), repository.closed)
        assertEquals(null, repository.pausedState)
    }

    @Test
    fun `registry cancellation propagates without closing repository`() = runTest {
        val repository = FakeRecoveryRepository(listOf(attempt()))
        val cancellation = CancellationException("cancel registry resolution")
        val registry =
            RecoveryProbeRegistry {
                throw cancellation
            }

        try {
            coordinator(repository, registry).recover()
            fail("Expected recovery cancellation")
        } catch (actual: CancellationException) {
            assertTrue(actual === cancellation)
        }

        assertEquals(emptyList<ClosedAttempt>(), repository.closed)
        assertEquals(0, repository.pauseCount)
        assertEquals(null, repository.pausedState)
    }

    @Test
    fun `repeated startup emits one terminal pause and recovery summary`() = runTest {
        val repository = FakeRecoveryRepository(listOf(attempt()))
        val registry =
            RecordingRegistry(
                mapOf(
                    "known-spec" to
                        ReadOnlyRecoveryProbe {
                            ReconciliationResult.POSTCONDITION_SATISFIED
                        },
                ),
            )
        val coordinator = coordinator(repository, registry)

        val first = coordinator.recover()
        val second = coordinator.recover()

        assertEquals(1, first.size)
        assertEquals(emptyList<RecoverableRun>(), second)
        assertEquals(1, repository.closed.size)
        assertEquals(1, repository.pauseCount)
        assertEquals(1, registry.evaluationCount)
    }

    @Test
    fun `multiple attempts preserve repository order and derive step ids from attempts`() = runTest {
        val attempts =
            listOf(
                attempt(
                    runId = "run-2",
                    attemptId = "attempt-b",
                    recoverySpecId = null,
                ),
                attempt(
                    runId = "run-1",
                    attemptId = "attempt-a",
                    recoverySpecId = null,
                ),
            )
        val repository = FakeRecoveryRepository(attempts)

        val recovered = coordinator(repository, RecordingRegistry(emptyMap())).recover()

        assertEquals(attempts.map { it.attemptId }, recovered.map { it.attemptId })
        assertEquals(
            listOf(StepId("recovery-attempt-b"), StepId("recovery-attempt-a")),
            repository.closed.map { it.stepId },
        )
    }

    @Test
    fun `store adapter maps every reconciliation result exactly`() = runTest {
        val store = RecordingRecoverySessionStore()
        val repository = StoreRecoveryRepository(store)
        val expected =
            mapOf(
                ReconciliationResult.POSTCONDITION_SATISFIED to
                    ActionResolution.POSTCONDITION_SATISFIED,
                ReconciliationResult.POSTCONDITION_NOT_SATISFIED to
                    ActionResolution.POSTCONDITION_NOT_SATISFIED,
                ReconciliationResult.RECONCILIATION_INCONCLUSIVE to
                    ActionResolution.RECONCILIATION_INCONCLUSIVE,
                ReconciliationResult.RECONCILIATION_UNAVAILABLE to
                    ActionResolution.RECONCILIATION_UNAVAILABLE,
            )

        expected.forEach { (result, resolution) ->
            repository.closeAndPause(
                attempt(attemptId = "attempt-${result.name}"),
                result,
                StepId("step-${result.name}"),
            )
            assertEquals(resolution, store.closed.last().resolution)
        }
    }

    private fun coordinator(
        repository: RecoveryRepository,
        registry: RecoveryProbeRegistry,
    ): RecoveryCoordinator =
        RecoveryCoordinator(
            repository = repository,
            probeRegistry = registry,
            stepIds = { attemptId -> StepId("recovery-${attemptId.value}") },
        )

    private fun attempt(
        runId: String = "run-1",
        attemptId: String = "attempt-1",
        recoverySpecId: String? = "known-spec",
    ) = RunRecord.ActionDispatched(
        runId = RunId(runId),
        attemptId = AttemptId(attemptId),
        actionKind = "CLICK",
        selectorFingerprint = "selector-fingerprint",
        postconditionFingerprint = "postcondition-fingerprint",
        recoverySpecId = recoverySpecId,
    )
}

private data class ClosedAttempt(
    val attempt: RunRecord.ActionDispatched,
    val resolution: ActionResolution,
    val stepId: StepId,
)

private class FakeRecoveryRepository(
    incomplete: List<RunRecord.ActionDispatched>,
) : RecoveryRepository {
    private val incomplete = incomplete.toMutableList()
    val closed = mutableListOf<ClosedAttempt>()
    var pausedState: RunState? = null
        private set
    var pauseCount = 0
        private set

    override suspend fun incompleteAttempts(): List<RunRecord.ActionDispatched> =
        incomplete.toList()

    override suspend fun closeAndPause(
        attempt: RunRecord.ActionDispatched,
        result: ReconciliationResult,
        stepId: StepId,
    ): Boolean {
        if (!incomplete.remove(attempt)) {
            return false
        }
        closed += ClosedAttempt(attempt, result.toActionResolution(), stepId)
        pausedState = RunState.PAUSED_RECOVERABLE
        pauseCount += 1
        return true
    }
}

private class RecordingRegistry(
    private val probes: Map<String, ReadOnlyRecoveryProbe>,
) : RecoveryProbeRegistry {
    val resolvedSpecIds = mutableListOf<String>()
    var evaluationCount = 0
        private set

    override fun resolve(allowlistedSpecId: String): ReadOnlyRecoveryProbe? {
        resolvedSpecIds += allowlistedSpecId
        val probe = probes[allowlistedSpecId] ?: return null
        return ReadOnlyRecoveryProbe {
            evaluationCount += 1
            probe.evaluate()
        }
    }
}

private class RecordingRecoverySessionStore : RecoverySessionStore {
    val closed = mutableListOf<ClosedAttempt>()

    override suspend fun incompleteAttempts(): List<RunRecord.ActionDispatched> = emptyList()

    override suspend fun closeAttemptAndPause(
        dispatched: RunRecord.ActionDispatched,
        resolution: ActionResolution,
        stepId: StepId,
    ): Boolean {
        closed += ClosedAttempt(dispatched, resolution, stepId)
        return true
    }

    override suspend fun resumeAfterRecovery(
        runId: RunId,
        stepId: StepId,
        contextReady: Boolean,
    ): RecoveryResumeResult = error("Recovery coordinator must never resume a run")

    override suspend fun snapshot(runId: RunId): RunSnapshot =
        RunSnapshot(runId, RunState.PAUSED_RECOVERABLE)

    override suspend fun append(event: RunRecord) {
        error("Recovery adapter must close and pause atomically through the store")
    }
}
