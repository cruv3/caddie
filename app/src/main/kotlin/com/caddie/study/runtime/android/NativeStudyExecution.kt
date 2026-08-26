package com.caddie.study.runtime.android

import android.util.Log
import com.caddie.agent.core.ModelDelta
import com.caddie.agent.core.RunId
import com.caddie.agent.core.ToolCallId
import com.caddie.app.runtime.NativeRuntimeHost
import com.caddie.app.runtime.NativeTaskResult
import com.caddie.app.runtime.ClaimedStudyRunner
import com.caddie.executor.accessibility.ExecutionGateway
import com.caddie.study.ResolvedConfirmationCancellation
import com.caddie.study.StudyGate
import com.caddie.study.runtime.audit.ControlledErrorEvent
import com.caddie.study.runtime.audit.ControlledErrorRecorder
import com.caddie.study.runtime.audit.StudyRuntimeEvent
import com.caddie.study.runtime.audit.StudyRuntimeEventRecorder
import com.caddie.study.runtime.coordinator.ArmedTrialCoordinator
import com.caddie.study.runtime.executor.C2Decision
import com.caddie.study.runtime.executor.CorrectionClassifier
import com.caddie.study.runtime.executor.CorrectionDecision
import com.caddie.study.runtime.executor.CorrectionInterrupted
import com.caddie.study.runtime.executor.CorrectionSource
import com.caddie.study.runtime.executor.OversightGateDecision
import com.caddie.study.runtime.executor.ParticipantResponseCancellation
import com.caddie.study.runtime.executor.StudyLogger
import com.caddie.study.runtime.executor.TrialExecutor
import com.caddie.study.runtime.executor.TrialOversightGate
import com.caddie.study.runtime.model.StudyStep
import com.caddie.study.runtime.model.TrialOutcome
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/** Executes claimed C1/C2/C3 trials from their fixed specs without calling a language model. */
class NativeDeterministicStudyRunner(
    private val host: NativeRuntimeHost,
    private val gateway: ExecutionGateway,
    private val gate: StudyGate,
    private val errorRecorder: ControlledErrorRecorder,
    private val runtimeEventRecorder: StudyRuntimeEventRecorder = StudyRuntimeEventRecorder { },
    private val claimActive: (ArmedTrialCoordinator.ClaimedTrial) -> Boolean = { true },
) : ClaimedStudyRunner {
    override suspend fun run(claim: ArmedTrialCoordinator.ClaimedTrial): NativeTaskResult =
        host.runDeterministic(
            task = claim.participantUtterance,
            completionText = claim.spec.completionMessageDe.ifBlank {
                "Aufgabe abgeschlossen."
            },
        ) { runId ->
            val runner = host.runner
            val backend = AndroidStudyBackend(gateway) {
                val allowed = runner.awaitActionPermission(runId)
                if (runner.hasPendingCorrection(runId)) throw CorrectionInterrupted()
                allowed
            }
            val logger = NativeTrialLogger(
                claim,
                runId,
                runner::publishCurrentAction,
                errorRecorder,
                runtimeEventRecorder,
            )
            val executor = TrialExecutor(
                backend = backend,
                logger = logger,
                oversight = NativeTrialOversightGate(gate) {
                    runner.isStopRequested(runId) || !claimActive(claim)
                },
                spec = claim.spec,
                condition = claim.config.condition,
                errorTasks = if (claim.config.injectError) setOf(claim.spec.id) else emptySet(),
                correctionClassifier = SingleInjectedErrorCorrectionClassifier,
                correctionSource = NativeRunCorrectionSource(
                    take = { runner.takeCorrection(runId) },
                    pause = { runner.pauseForIntervention(runId) },
                ),
                verificationBackend = AccessibilityStudyVerification(backend),
            )
            executor.run().outcome in setOf(
                TrialOutcome.SUCCESS,
                TrialOutcome.ERROR_INJECTED,
            )
        }
}

/** Bridges deterministic C1/C2 decisions to the same confirmation overlay as normal runs. */
private class NativeTrialOversightGate(
    private val gate: StudyGate,
    private val cancelled: () -> Boolean,
) : TrialOversightGate {
    override suspend fun confirmConsequentialStep(
        step: StudyStep,
        confirmationText: String,
    ): OversightGateDecision {
        val decision = try {
            gate.confirmStep(step.asToolCall(), confirmationText)
        } catch (cancelled: ResolvedConfirmationCancellation) {
            throw cancelled.asParticipantResponseCancellation()
        }
        return OversightGateDecision(
            confirmed = decision.approved,
            declined = !decision.approved,
            reason = decision.reason,
            responseLatencyMs = decision.responseLatencyMs,
        )
    }

    override suspend fun showC2Summary(
        steps: List<StudyStep>,
        narrations: List<String>,
    ): C2Decision {
        val decision = try {
            gate.confirmFinal(steps.map { it.asToolCall() }, narrations)
        } catch (cancelled: ResolvedConfirmationCancellation) {
            throw cancelled.asParticipantResponseCancellation()
        }
        return C2Decision(
            confirmed = decision.approved,
            declined = !decision.approved,
            reason = decision.reason,
            responseLatencyMs = decision.responseLatencyMs,
        )
    }

    override fun isCancelled(): Boolean = cancelled()

    private fun ResolvedConfirmationCancellation.asParticipantResponseCancellation() =
        ParticipantResponseCancellation(
            confirmed = resolvedDecision.approved,
            declined = !resolvedDecision.approved,
            reason = resolvedDecision.reason,
            responseLatencyMs = resolvedDecision.responseLatencyMs,
            cause = this,
        )

    private fun StudyStep.asToolCall() = ModelDelta.ToolCall(
        id = ToolCallId("study-$id"),
        name = "study.$id",
        argumentsJson = "{}",
    )
}

/** Accepts an explicit correction only when exactly one injected variant can be meant. */
internal object SingleInjectedErrorCorrectionClassifier : CorrectionClassifier {
    override fun classify(context: com.caddie.study.runtime.executor.CorrectionContext): CorrectionDecision {
        val variant = context.activeVariants.singleOrNull()
            ?: return CorrectionDecision(false, reason = "ambiguous_variant")
        return CorrectionDecision(true, variant.id, "accepted_explicit_correction")
    }
}

/** Reads explicit voice corrections from the process-owned active run. */
private class NativeRunCorrectionSource(
    private val take: () -> String?,
    private val pause: () -> Boolean,
) : CorrectionSource {
    override fun takeCorrection(): String? = take()

    override suspend fun waitForCorrection(timeoutS: Double): String? {
        val deadline = System.nanoTime() + (timeoutS * 1_000_000_000).toLong()
        while (System.nanoTime() < deadline) {
            take()?.let { return it }
            delay(50)
        }
        return null
    }

    override fun pauseForUnclearCorrection() {
        pause()
    }
}

/** Sends fixed study narration to the overlay and controlled errors to durable study storage. */
private class NativeTrialLogger(
    private val claim: ArmedTrialCoordinator.ClaimedTrial,
    private val runId: RunId,
    private val publishAction: (RunId, String) -> Boolean,
    private val errorRecorder: ControlledErrorRecorder,
    private val runtimeEventRecorder: StudyRuntimeEventRecorder,
) : StudyLogger {
    override val participantId: String = claim.config.participantId
    private val trialId = claim.config.trialAttemptId

    override fun trialStart(taskId: String, block: String, variant: String): String {
        record("trial_started", mapOf("block" to block, "variant" to variant))
        return trialId
    }
    override fun trialComplete(trialId: String, outcome: String, totalSteps: Int, errorsInjected: Int, durationMs: Double) {
        record(
            "trial_completed",
            trialCompletionDetails(outcome, totalSteps, errorsInjected, durationMs),
        )
    }
    override fun trialComplete(
        trialId: String,
        outcome: String,
        totalSteps: Int,
        errorsInjected: Int,
        durationMs: Double,
        confirmationResponseTimeMs: Double,
        confirmationCount: Int,
    ) {
        record(
            "trial_completed",
            trialCompletionDetails(
                outcome = outcome,
                totalSteps = totalSteps,
                errorsInjected = errorsInjected,
                durationMs = durationMs,
                confirmationResponseTimeMs = confirmationResponseTimeMs,
                confirmationCount = confirmationCount,
            ),
        )
    }
    override fun confirmationResolved(
        trialId: String,
        gateType: String,
        stepId: String?,
        decision: String,
        responseTimeMs: Double,
        participantResponse: Boolean,
    ) {
        record(
            "confirmation_resolved",
            confirmationResolvedDetails(
                gateType, stepId, decision, responseTimeMs, participantResponse,
            ),
        )
    }
    override fun stepStart(stepId: String, narration: String, trialId: String) {
        publishAction(runId, narration)
    }
    override fun stepFinish(stepId: String, trialId: String, action: String) {
        record("step_completed", mapOf("step_id" to stepId))
    }
    override fun stepAlreadySatisfied(stepId: String, trialId: String) {
        record("step_already_satisfied", mapOf("step_id" to stepId))
    }
    override fun errorInjected(stepId: String, trialId: String, errorVariantId: String, field: String, wrongValue: String, correctValue: String) {
        val event = controlledErrorEvent(
            config = claim.config,
            stepId = stepId,
            errorVariantId = errorVariantId,
            field = field,
            wrongValue = wrongValue,
            correctValue = correctValue,
        ) ?: return
        runBlocking {
            errorRecorder.record(event)
        }
    }
    override fun screenshotCaptured(label: String, trialId: String): String = ""
    override fun technicalFailure(error: String) {
        Log.e("NativeStudy", error)
        record("technical_failure", mapOf("reason" to error.take(500)))
    }
    override fun writeSummary(outcome: String, totalSteps: Int, errorsInjected: Int, durationMs: Double, trialIds: List<String>, verificationResults: Map<String, Any?>?, screenshots: List<String>) = Unit
    override fun intervention(text: String, trialId: String, taskId: String, accepted: Boolean, reason: String, errorVariantId: String?) {
        record("participant_intervention", mapOf("accepted" to accepted, "reason" to reason, "error_variant_id" to errorVariantId))
    }
    override fun correctionRoute(errorVariantId: String, route: String, phase: String, trialId: String, taskId: String) {
        record("correction_routed", mapOf("error_variant_id" to errorVariantId, "route" to route, "phase" to phase))
    }
    override fun correctionIrreversible(errorVariantId: String, commitStepId: String, message: String, trialId: String, taskId: String) {
        record("correction_rejected_irreversible", mapOf("error_variant_id" to errorVariantId, "commit_step_id" to commitStepId))
    }
    override fun correctionRewind(errorVariantId: String, fromStepId: String?, toStepId: String, trialId: String, taskId: String) {
        record("correction_rewound", mapOf("error_variant_id" to errorVariantId, "from_step_id" to fromStepId, "to_step_id" to toStepId))
    }
    override fun compensationStep(stepId: String, errorVariantId: String, outcome: String, trialId: String, taskId: String) {
        record("compensation_step_completed", mapOf("step_id" to stepId, "error_variant_id" to errorVariantId, "outcome" to outcome))
    }
    override fun verificationStart(ruleCount: Int, trialId: String, taskId: String) = Unit
    override fun verificationComplete(passed: Int, failed: Int, timedOut: Int, skipped: Int, trialId: String, taskId: String) {
        record("verification_completed", mapOf("passed" to passed, "failed" to failed, "timed_out" to timedOut, "skipped" to skipped))
    }
    override fun verificationResult(ruleId: String, passed: Boolean, trialId: String, taskId: String, screenshotPath: String?, details: Map<String, Any?>) {
        record(if (passed) "verification_passed" else "verification_failed", mapOf("rule_id" to ruleId))
    }

    private fun record(eventType: String, details: Map<String, Any?> = emptyMap()) {
        val studyRunId = claim.config.studyRunId ?: return
        recordRuntimeEventBestEffort(
            runtimeEventRecorder,
            StudyRuntimeEvent(
                studyRunId = studyRunId,
                participantId = claim.config.participantId,
                trialIndex = claim.config.trialIndex,
                taskId = claim.config.taskId,
                eventType = eventType,
                details = mapOf("trial_attempt_id" to trialId) + details,
            ),
        )
    }
}

internal fun confirmationResolvedDetails(
    gateType: String,
    stepId: String?,
    decision: String,
    responseTimeMs: Double,
    participantResponse: Boolean,
): Map<String, Any?> = mapOf(
    "gate_type" to gateType,
    "step_id" to stepId,
    "decision" to decision,
    "response_latency_ms" to responseTimeMs,
    "participant_response" to participantResponse,
)

internal fun trialCompletionDetails(
    outcome: String,
    totalSteps: Int,
    errorsInjected: Int,
    durationMs: Double,
    confirmationResponseTimeMs: Double? = null,
    confirmationCount: Int? = null,
): Map<String, Any?> = buildMap {
    put("outcome", outcome)
    put("total_steps", totalSteps)
    put("errors_injected", errorsInjected)
    put("duration_ms", durationMs)
    if (confirmationResponseTimeMs != null && confirmationCount != null) {
        put("confirmation_response_latency_total_ms", confirmationResponseTimeMs)
        put("confirmation_response_count", confirmationCount)
        if (confirmationCount > 0) {
            put(
                "confirmation_response_latency_mean_ms",
                confirmationResponseTimeMs / confirmationCount,
            )
        }
    }
}

/** Audit outages must never turn an already executed phone action into a retryable failure. */
internal fun recordRuntimeEventBestEffort(
    recorder: StudyRuntimeEventRecorder,
    event: StudyRuntimeEvent,
) {
    runCatching {
        runBlocking { recorder.record(event) }
    }.onFailure { error ->
        runCatching { Log.e("NativeStudy", "runtime event persistence failed: ${event.eventType}", error) }
    }
}

/** Creates durable controlled-error data for live runs; local test runs intentionally stay ephemeral. */
internal fun controlledErrorEvent(
    config: ArmedTrialCoordinator.ArmedTrialConfig,
    stepId: String,
    errorVariantId: String,
    field: String,
    wrongValue: String,
    correctValue: String,
): ControlledErrorEvent? {
    if (config.runScope == ArmedTrialCoordinator.StudyRunScope.TEST) return null
    return ControlledErrorEvent(
        studyRunId = requireNotNull(config.studyRunId) {
            "controlled error requires a durable live study run"
        },
        trialAttemptId = config.trialAttemptId,
        trialIndex = config.trialIndex,
        taskId = config.taskId,
        stepId = stepId,
        errorVariantId = errorVariantId,
        field = field,
        correctValue = correctValue,
        wrongValue = wrongValue,
    )
}
