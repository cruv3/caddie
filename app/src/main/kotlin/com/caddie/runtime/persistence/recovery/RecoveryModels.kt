package com.caddie.runtime.persistence.recovery

import com.caddie.agent.core.AttemptId
import com.caddie.agent.core.RunId

/** Describes the safe next step after inspecting an interrupted action. */
enum class ReconciliationResult {
    POSTCONDITION_SATISFIED,
    POSTCONDITION_NOT_SATISFIED,
    RECONCILIATION_INCONCLUSIVE,
    RECONCILIATION_UNAVAILABLE,
}

/** Inspects external state without performing another action. */
fun interface ReadOnlyRecoveryProbe {
    suspend fun evaluate(): ReconciliationResult
}

/** Resolves the read-only probe for a recoverable action. */
fun interface RecoveryProbeRegistry {
    fun resolve(allowlistedSpecId: String): ReadOnlyRecoveryProbe?
}

/** Bundles a paused run with the action attempt that requires reconciliation. */
data class RecoverableRun(
    val runId: RunId,
    val attemptId: AttemptId,
    val result: ReconciliationResult,
    val requiresExplicitResume: Boolean = true,
)
