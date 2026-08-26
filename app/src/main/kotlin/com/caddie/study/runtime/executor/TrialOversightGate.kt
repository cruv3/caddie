package com.caddie.study.runtime.executor

import com.caddie.study.runtime.model.StudyStep
import kotlinx.coroutines.CancellationException

/**
 * Oversight gate for the deterministic trial executor.
 *
 * Ported from `caddie.study.oversight.OversightProtocol`. This is the
 * executor-side counterpart of [com.caddie.study.StudyGate] (which serves
 * the agent-loop path). A native implementation bridges to
 * [com.caddie.study.StudyGateDispatcher] / [com.caddie.study.NativeStudyGate].
 *
 * All methods are suspend because the real gate shows an overlay and waits
 * for the user to confirm/decline.
 */
interface TrialOversightGate {

    /** Confirm a single consequential step before it executes (C1). */
    suspend fun confirmConsequentialStep(step: StudyStep, confirmationText: String): OversightGateDecision

    /** Show a final summary checkpoint before the commit step (C2). */
    suspend fun showC2Summary(steps: List<StudyStep>, narrations: List<String>): C2Decision

    /** Whether the user has cancelled the trial. */
    fun isCancelled(): Boolean
}

/** Decision from a C1 step gate. */
data class OversightGateDecision(
    val confirmed: Boolean,
    val declined: Boolean = false,
    val reason: String? = null,
    val responseLatencyMs: Double? = null,
)

/** Decision from a C2 final-checkpoint summary. */
data class C2Decision(
    val confirmed: Boolean,
    val declined: Boolean = false,
    val modifiedSteps: List<StudyStep> = emptyList(),
    val reason: String? = null,
    val responseLatencyMs: Double? = null,
)

/** Run cancellation delivered after the participant's confirmation was already captured. */
class ParticipantResponseCancellation(
    val confirmed: Boolean,
    val declined: Boolean,
    val reason: String?,
    val responseLatencyMs: Double?,
    cause: CancellationException,
) : CancellationException(cause.message) {
    init {
        initCause(cause)
    }
}

/**
 * Minimal event logger for the trial executor.
 *
 * Ported from `caddie.study.logger.StudyLogger` (interface subset). The real
 * implementation writes JSONL to device storage; tests use an in-memory fake.
 * Only the methods the executor actually calls are defined here.
 */
interface StudyLogger {
    val participantId: String

    fun trialStart(taskId: String, block: String, variant: String): String
    fun trialComplete(trialId: String, outcome: String, totalSteps: Int, errorsInjected: Int, durationMs: Double)
    fun trialComplete(
        trialId: String,
        outcome: String,
        totalSteps: Int,
        errorsInjected: Int,
        durationMs: Double,
        confirmationResponseTimeMs: Double,
        confirmationCount: Int,
    ) = trialComplete(trialId, outcome, totalSteps, errorsInjected, durationMs)
    fun confirmationResolved(
        trialId: String,
        gateType: String,
        stepId: String?,
        decision: String,
        responseTimeMs: Double,
        participantResponse: Boolean,
    ) = Unit
    fun stepStart(stepId: String, narration: String, trialId: String)
    fun stepFinish(stepId: String, trialId: String, action: String)
    fun stepAlreadySatisfied(stepId: String, trialId: String) = Unit
    fun errorInjected(stepId: String, trialId: String, errorVariantId: String, field: String, wrongValue: String, correctValue: String)
    fun screenshotCaptured(label: String, trialId: String): String
    fun technicalFailure(error: String)
    fun writeSummary(outcome: String, totalSteps: Int, errorsInjected: Int, durationMs: Double, trialIds: List<String>, verificationResults: Map<String, Any?>?, screenshots: List<String>)

    // ── Correction logging ──

    fun intervention(text: String, trialId: String, taskId: String, accepted: Boolean, reason: String, errorVariantId: String?)
    fun correctionRoute(errorVariantId: String, route: String, phase: String, trialId: String, taskId: String)
    fun correctionIrreversible(errorVariantId: String, commitStepId: String, message: String, trialId: String, taskId: String)
    fun correctionRewind(errorVariantId: String, fromStepId: String?, toStepId: String, trialId: String, taskId: String)
    fun compensationStep(stepId: String, errorVariantId: String, outcome: String, trialId: String, taskId: String)

    // ── Verification logging ──

    fun verificationStart(ruleCount: Int, trialId: String, taskId: String)
    fun verificationComplete(passed: Int, failed: Int, timedOut: Int, skipped: Int, trialId: String, taskId: String)
    fun verificationResult(ruleId: String, passed: Boolean, trialId: String, taskId: String, screenshotPath: String?, details: Map<String, Any?>)
}
