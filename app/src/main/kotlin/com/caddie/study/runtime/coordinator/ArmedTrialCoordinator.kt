package com.caddie.study.runtime.coordinator

import com.caddie.study.runtime.model.RuntimeStudyCondition
import com.caddie.study.runtime.model.TrialSpec
import com.caddie.study.runtime.routing.StudyRouting
import com.caddie.study.runtime.routing.StudyTaskRouter
import java.time.Instant

/**
 * Thread-safe coordination for one user-initiated armed study trial,
 * ported from `caddie.study.coordinator` (Python).
 *
 * Owns the in-memory lifecycle and exactly-once claim for one trial.
 * All mutations are guarded by an intrinsic lock; reads are consistent.
 */
class ArmedTrialCoordinator(
    private val router: StudyTaskRouter = StudyTaskRouter(),
    private val taskClassifier: ((String) -> String?)? = null,
) {
    /** Lifecycle of an armed trial. */
    enum class ArmedState(val wireValue: String) {
        ARMED("armed"),
        RUNNING("running"),
        COMPLETED("completed"),
        FAILED("failed"),
        ABORTED("aborted");

        companion object {
            fun fromWire(v: String): ArmedState = entries.first { it.wireValue == v }
        }
    }

    /** Decision returned by [routeAndClaim]. */
    enum class CoordinatorDecision(val wireValue: String) {
        PASS_THROUGH("pass_through"),
        RETRY("retry"),
        CLAIMED("claimed"),
        RUNNING_INPUT("running_input");

        companion object {
            fun fromWire(v: String): CoordinatorDecision = entries.first { it.wireValue == v }
        }
    }

    /** Live vs test scope of a study run. */
    enum class StudyRunScope(val wireValue: String) {
        TEST("test"),
        LIVE("live");

        companion object {
            fun fromWire(v: String): StudyRunScope = entries.first { it.wireValue == v }
        }
    }

    enum class RunScopeWire { TEST, LIVE }

    data class ArmedTrialConfig(
        val participantId: String,
        val trialIndex: Int,
        val taskId: String,
        val condition: RuntimeStudyCondition,
        val injectError: Boolean,
        val runScope: StudyRunScope = StudyRunScope.LIVE,
        val studyRunId: String? = null,
        val trialAttemptId: String = "trial-attempt-${java.util.UUID.randomUUID()}",
    ) {
        init {
            require(participantId.isNotBlank()) { "participant_id must be non-empty" }
            require(trialIndex >= 0) { "trial_index must be nonnegative" }
            require(taskId.isNotBlank()) { "task_id must be non-empty" }
            studyRunId?.let { require(it.isNotBlank()) { "study_run_id must be non-empty or null" } }
            require(trialAttemptId.isNotBlank()) { "trial_attempt_id must be non-empty" }
        }
    }

    data class ClaimToken(val generation: Int)

    data class ClaimedTrial(
        val config: ArmedTrialConfig,
        val spec: TrialSpec,
        val participantUtterance: String,
        val token: ClaimToken,
    )

    data class RoutingAttempt(
        val wallTime: Instant,
        val utterance: String,
        val match: StudyRouting.MatchResult?,
        val decision: CoordinatorDecision,
        val reason: String,
    )

    data class CoordinatorRouteResult(
        val decision: CoordinatorDecision,
        val match: StudyRouting.MatchResult?,
        val claim: ClaimedTrial?,
        val reason: String,
    )

    data class CoordinatorStatus(
        val state: ArmedState?,
        val participantId: String?,
        val trialIndex: Int?,
        val taskId: String?,
        val condition: RuntimeStudyCondition?,
        val injectError: Boolean?,
        val reason: String?,
        val attemptCount: Int,
        val runScope: StudyRunScope?,
        val studyRunId: String?,
    )

    class CoordinatorConflictError(message: String) : RuntimeException(message)
    class InvalidTransitionError(message: String) : RuntimeException(message)

    @Volatile private var state: ArmedState? = null
    @Volatile private var config: ArmedTrialConfig? = null
    @Volatile private var spec: TrialSpec? = null
    @Volatile private var claim: ClaimedTrial? = null
    private var claimGeneration = 0
    @Volatile private var reason: String? = null
    @Volatile private var studyModeActive = false
    private val attempts = mutableListOf<RoutingAttempt>()

    /** Prevents live-study utterances from escaping into the unrestricted normal agent. */
    @Synchronized
    fun setStudyMode(active: Boolean) {
        studyModeActive = active
    }

    /** Prevents a delayed normal dispatch from crossing into an active study mode. */
    @Synchronized
    fun isStudyModeActive(): Boolean = studyModeActive

    @Synchronized
    fun hasActiveTrial(): Boolean = state == ArmedState.ARMED || state == ArmedState.RUNNING

    @Synchronized
    fun arm(cfg: ArmedTrialConfig, trialSpec: TrialSpec) {
        require(cfg.taskId == trialSpec.id) { "config task_id must equal spec.id" }
        requireNotNull(trialSpec.trigger) { "spec must have a trigger contract" }
        if (state == ArmedState.ARMED || state == ArmedState.RUNNING) {
            throw CoordinatorConflictError("a trial is already active")
        }
        state = ArmedState.ARMED
        config = cfg
        spec = trialSpec
        claim = null
        reason = null
        attempts.clear()
    }

    @Synchronized
    fun routeAndClaim(text: String): CoordinatorRouteResult {
        val curState = state ?: return record(
            text,
            CoordinatorRouteResult(
                if (studyModeActive) CoordinatorDecision.RETRY else CoordinatorDecision.PASS_THROUGH,
                null,
                null,
                if (studyModeActive) "study_not_armed" else "idle",
            ),
        )
        if (curState == ArmedState.COMPLETED || curState == ArmedState.FAILED || curState == ArmedState.ABORTED) {
            return record(text, CoordinatorRouteResult(
                if (studyModeActive) CoordinatorDecision.RETRY else CoordinatorDecision.PASS_THROUGH,
                null,
                null,
                "trial_${curState.wireValue}",
            ))
        }
        if (curState == ArmedState.RUNNING) {
            val running = claim ?: error("running state without claim")
            return record(text, CoordinatorRouteResult(
                CoordinatorDecision.RUNNING_INPUT, null, running, "trial_running",
            ))
        }
        val armedSpec = spec ?: error("armed state without spec")
        val routed = router.route(text, armedSpec)
        var classified = false
        if (routed.decision == StudyRouting.RouteDecision.RETRY && taskClassifier != null) {
            classified = try {
                taskClassifier.invoke(text) == armedSpec.id
            } catch (e: Exception) {
                false
            }
        }
        val claimed = routed.decision == StudyRouting.RouteDecision.CLAIMED || classified
        val decision = if (claimed) CoordinatorDecision.CLAIMED else CoordinatorDecision.fromWire(routed.decision.wireValue)
        var newClaim: ClaimedTrial? = null
        if (claimed) {
            val cfg = config ?: error("claimed without config")
            claimGeneration += 1
            newClaim = ClaimedTrial(cfg, armedSpec, text, ClaimToken(claimGeneration))
            claim = newClaim
            state = ArmedState.RUNNING
            reason = null
        }
        val reasonStr = when {
            classified -> "classified"
            claimed -> "claimed"
            routed.match != null -> routed.match.reason
            else -> routed.decision.wireValue
        }
        return record(text, CoordinatorRouteResult(decision, routed.match, newClaim, reasonStr))
    }

    @Synchronized
    fun finishSuccess(claimed: ClaimedTrial) {
        requireActiveClaim(claimed)
        state = ArmedState.COMPLETED
        reason = null
    }

    /** Completes only while the claim still owns a running, non-aborted trial. */
    @Synchronized
    fun finishSuccessIfActive(claimed: ClaimedTrial): Boolean {
        if (!isClaimActive(claimed)) return false
        finishSuccess(claimed)
        return true
    }

    @Synchronized
    fun finishFailure(claimed: ClaimedTrial, failReason: String) {
        validateReason(failReason)
        requireActiveClaim(claimed)
        state = ArmedState.FAILED
        reason = failReason
    }

    /** Fails only while the claim still owns a running, non-aborted trial. */
    @Synchronized
    fun finishFailureIfActive(claimed: ClaimedTrial, failReason: String): Boolean {
        if (!isClaimActive(claimed)) return false
        finishFailure(claimed, failReason)
        return true
    }

    /** Releases an unstarted claim while preserving the armed trial for a retry. */
    @Synchronized
    fun releaseClaimForRetry(claimed: ClaimedTrial, retryReason: String) {
        validateReason(retryReason)
        requireActiveClaim(claimed)
        claim = null
        state = ArmedState.ARMED
        reason = retryReason
    }

    /** Releases only while the claim still owns a running, non-aborted trial. */
    @Synchronized
    fun releaseClaimForRetryIfActive(claimed: ClaimedTrial, retryReason: String): Boolean {
        if (!isClaimActive(claimed)) return false
        releaseClaimForRetry(claimed, retryReason)
        return true
    }

    @Synchronized
    fun abort(abortReason: String): Boolean {
        validateReason(abortReason)
        if (state == ArmedState.ABORTED) return false
        if (state != ArmedState.ARMED && state != ArmedState.RUNNING) {
            throw InvalidTransitionError("abort requires an armed or running trial")
        }
        state = ArmedState.ABORTED
        reason = abortReason
        return true
    }

    /** Aborts only a currently active trial, allowing portal recovery after process loss. */
    @Synchronized
    fun abortIfActive(abortReason: String): Boolean {
        validateReason(abortReason)
        if (state != ArmedState.ARMED && state != ArmedState.RUNNING) return false
        state = ArmedState.ABORTED
        reason = abortReason
        return true
    }

    @Synchronized
    fun clear() {
        if (state == ArmedState.ARMED || state == ArmedState.RUNNING) {
            throw InvalidTransitionError("cannot clear an active trial")
        }
        state = null
        config = null
        spec = null
        claim = null
        reason = null
        attempts.clear()
    }

    @Synchronized
    fun status(): CoordinatorStatus {
        val cfg = config
        return CoordinatorStatus(
            state = state,
            participantId = cfg?.participantId,
            trialIndex = cfg?.trialIndex,
            taskId = cfg?.taskId,
            condition = cfg?.condition,
            injectError = cfg?.injectError,
            reason = reason,
            attemptCount = attempts.size,
            runScope = cfg?.runScope,
            studyRunId = cfg?.studyRunId,
        )
    }

    /** True only while this exact claimed trial is still allowed to execute actions. */
    @Synchronized
    fun isClaimActive(claimed: ClaimedTrial): Boolean =
        state == ArmedState.RUNNING &&
            claim?.token?.generation == claimed.token.generation

    @Synchronized
    fun attempts(): List<RoutingAttempt> = attempts.toList()

    private fun requireActiveClaim(claimed: ClaimedTrial) {
        if (state != ArmedState.RUNNING || claim == null) {
            throw InvalidTransitionError("completion requires a running claim")
        }
        if (claimed.token.generation != claim!!.token.generation) {
            throw InvalidTransitionError("claim does not own the running trial")
        }
    }

    private fun record(text: String, result: CoordinatorRouteResult): CoordinatorRouteResult {
        attempts.add(RoutingAttempt(
            wallTime = Instant.now(),
            utterance = text,
            match = result.match,
            decision = result.decision,
            reason = result.reason,
        ))
        return result
    }

    private fun validateReason(r: String) {
        require(r.isNotBlank()) { "reason must be non-empty" }
    }

}
