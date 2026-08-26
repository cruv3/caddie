package com.caddie.runtime.persistence.recovery

import com.caddie.agent.core.AttemptId
import com.caddie.agent.core.StepId
import kotlin.coroutines.cancellation.CancellationException

/** Inspects interrupted runs and returns safe recovery decisions. */
class RecoveryCoordinator internal constructor(
    private val repository: RecoveryRepository,
    private val probeRegistry: RecoveryProbeRegistry,
    private val stepIds: (AttemptId) -> StepId,
) {
    suspend fun recover(): List<RecoverableRun> {
        val recovered = mutableListOf<RecoverableRun>()
        repository.incompleteAttempts().forEach { attempt ->
            val result =
                try {
                    attempt.recoverySpecId
                        ?.let(probeRegistry::resolve)
                        ?.evaluate()
                        ?: ReconciliationResult.RECONCILIATION_UNAVAILABLE
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    ReconciliationResult.RECONCILIATION_UNAVAILABLE
                }
            val closed =
                repository.closeAndPause(
                    attempt = attempt,
                    result = result,
                    stepId = stepIds(attempt.attemptId),
                )
            if (closed) {
                recovered +=
                    RecoverableRun(
                        runId = attempt.runId,
                        attemptId = attempt.attemptId,
                        result = result,
                    )
            }
        }
        return recovered
    }
}
