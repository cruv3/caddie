package com.caddie.runtime.persistence.recovery

import com.caddie.agent.core.ActionResolution
import com.caddie.agent.core.AttemptId
import com.caddie.agent.core.RecoverySessionStore
import com.caddie.agent.core.RunRecord
import com.caddie.agent.core.StepId

/** Provides recoverable runs and persisted recovery decisions. */
internal interface RecoveryRepository {
    suspend fun incompleteAttempts(): List<RunRecord.ActionDispatched>

    suspend fun closeAndPause(
        attempt: RunRecord.ActionDispatched,
        result: ReconciliationResult,
        stepId: StepId,
    ): Boolean
}

/** Implements recovery queries using the durable session store. */
internal class StoreRecoveryRepository(
    private val store: RecoverySessionStore,
) : RecoveryRepository {
    override suspend fun incompleteAttempts(): List<RunRecord.ActionDispatched> =
        store.incompleteAttempts()

    override suspend fun closeAndPause(
        attempt: RunRecord.ActionDispatched,
        result: ReconciliationResult,
        stepId: StepId,
    ): Boolean =
        store.closeAttemptAndPause(
            dispatched = attempt,
            resolution = result.toActionResolution(),
            stepId = stepId,
        )
}

internal fun ReconciliationResult.toActionResolution(): ActionResolution =
    when (this) {
        ReconciliationResult.POSTCONDITION_SATISFIED ->
            ActionResolution.POSTCONDITION_SATISFIED
        ReconciliationResult.POSTCONDITION_NOT_SATISFIED ->
            ActionResolution.POSTCONDITION_NOT_SATISFIED
        ReconciliationResult.RECONCILIATION_INCONCLUSIVE ->
            ActionResolution.RECONCILIATION_INCONCLUSIVE
        ReconciliationResult.RECONCILIATION_UNAVAILABLE ->
            ActionResolution.RECONCILIATION_UNAVAILABLE
    }

internal fun requireDistinctRetryAttemptId(
    oldAttemptId: AttemptId,
    proposedAttemptId: AttemptId,
): AttemptId {
    require(proposedAttemptId != oldAttemptId) {
        "A retry requires a new attempt ID"
    }
    return proposedAttemptId
}
