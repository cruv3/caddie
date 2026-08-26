package com.caddie.context.replay

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Reports the terminal state recorded by the controlled durable dispatcher. */
enum class ReplayDispatchOutcome {
    SUCCEEDED,
    REJECTED,
    FAILED,
    UNKNOWN,
}

/**
 * Owns durable action dispatch so a stable execution ID can never repeat a
 * physical action after process loss or an unknown result.
 */
fun interface ControlledReplayDispatcher {
    suspend fun dispatch(
        executionId: String,
        step: ReplayStep,
    ): ReplayDispatchOutcome
}

/** Explains why conservative replay execution stopped. */
enum class ReplayStopReason {
    NOT_ELIGIBLE,
    STALE_OBSERVATION,
    ENVIRONMENT_MISMATCH,
    AMBIGUOUS_PREDICATE,
    PREDICATE_NOT_MATCHED,
    DISPATCH_REJECTED,
    DISPATCH_FAILED,
    DISPATCH_UNKNOWN,
}

/** Retains safe progress and the exact check that stopped a replay. */
data class ReplayBreadcrumb(
    val trajectoryId: String,
    val revisionId: String,
    val completedStepIds: List<String>,
    val currentExecutionId: String?,
    val failedPredicate: String?,
    val outcome: ReplayStopReason,
)

/** Returns either verified completion or a durable diagnostic breadcrumb. */
sealed interface ReplayRunResult {
    data class Completed(
        val executionIds: List<String>,
    ) : ReplayRunResult

    data class Stopped(
        val breadcrumb: ReplayBreadcrumb,
    ) : ReplayRunResult
}

/**
 * Executes one fixed replay candidate with fresh verification around every
 * action; it never chooses another candidate or retries an unknown action.
 */
class VerifiedReplayEngine(
    private val observations: ReplayObservationSource,
    private val verifier: ReplayVerifier,
    private val dispatcher: ControlledReplayDispatcher,
) {
    suspend fun run(replay: ReplayTrajectory): ReplayRunResult {
        val completed = mutableListOf<String>()
        var lastSequence: Long? = null

        fun stopped(
            reason: ReplayStopReason,
            currentExecutionId: String? = null,
            failedCheck: String? = null,
        ) = ReplayRunResult.Stopped(
            ReplayBreadcrumb(
                trajectoryId = replay.trajectoryId,
                revisionId = replay.revisionId,
                completedStepIds = completed.toList(),
                currentExecutionId = currentExecutionId,
                failedPredicate = failedCheck,
                outcome = reason,
            ),
        )

        if (ReplayClassifier.classify(replay) != ReplayClassification.ELIGIBLE) {
            return stopped(ReplayStopReason.NOT_ELIGIBLE)
        }

        suspend fun freshObservation(
            check: String,
            executionId: String?,
        ): Pair<ReplayObservation?, ReplayRunResult.Stopped?> {
            val observation = observations.observe()
            if (lastSequence != null && observation.sequence <= lastSequence!!) {
                return null to stopped(ReplayStopReason.STALE_OBSERVATION, executionId, check)
            }
            lastSequence = observation.sequence
            if (
                verifier.verifyEnvironment(replay.environment, observation) !=
                ReplayVerification.MATCHED
            ) {
                return null to stopped(
                    ReplayStopReason.ENVIRONMENT_MISMATCH,
                    executionId,
                    "$check:environment",
                )
            }
            return observation to null
        }

        suspend fun verify(
            predicate: ReplayPredicate,
            check: String,
            executionId: String? = null,
        ): ReplayRunResult.Stopped? {
            val (observation, observationFailure) = freshObservation(check, executionId)
            if (observationFailure != null) return observationFailure
            return when (verifier.verifyPredicate(predicate, requireNotNull(observation))) {
                ReplayVerification.MATCHED -> null
                ReplayVerification.NOT_MATCHED ->
                    stopped(ReplayStopReason.PREDICATE_NOT_MATCHED, executionId, check)
                ReplayVerification.AMBIGUOUS ->
                    stopped(ReplayStopReason.AMBIGUOUS_PREDICATE, executionId, check)
            }
        }

        verify(requireNotNull(replay.startPredicate), "start")?.let { return it }

        replay.steps.forEachIndexed { index, step ->
            val executionId = "${replay.trajectoryId}:${replay.revisionId}:$index"
            verify(requireNotNull(step.precondition), "step:$index:pre", executionId)?.let {
                return it
            }

            currentCoroutineContext().ensureActive()
            val dispatchOutcome = dispatcher.dispatch(executionId, step)
            currentCoroutineContext().ensureActive()
            when (dispatchOutcome) {
                ReplayDispatchOutcome.SUCCEEDED -> Unit
                ReplayDispatchOutcome.REJECTED ->
                    return stopped(ReplayStopReason.DISPATCH_REJECTED, executionId, "dispatch")
                ReplayDispatchOutcome.FAILED ->
                    return stopped(ReplayStopReason.DISPATCH_FAILED, executionId, "dispatch")
                ReplayDispatchOutcome.UNKNOWN ->
                    return stopped(ReplayStopReason.DISPATCH_UNKNOWN, executionId, "dispatch")
            }

            verify(requireNotNull(step.postcondition), "step:$index:post", executionId)?.let {
                return it
            }
            completed += executionId
        }

        verify(requireNotNull(replay.terminalPredicate), "terminal")?.let { return it }
        return ReplayRunResult.Completed(completed.toList())
    }
}
