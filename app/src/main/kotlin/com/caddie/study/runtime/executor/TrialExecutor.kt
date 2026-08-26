package com.caddie.study.runtime.executor

import com.caddie.study.runtime.model.CorrectionJournal
import com.caddie.study.runtime.model.CorrectionPhase
import com.caddie.study.runtime.model.CorrectionPolicy
import com.caddie.study.runtime.model.CorrectionRoute
import com.caddie.study.runtime.model.CorrectionStep
import com.caddie.study.runtime.model.PostCommitPolicy
import com.caddie.study.runtime.model.RuntimeStudyCondition
import com.caddie.study.runtime.model.StepType
import com.caddie.study.runtime.model.StudyStep
import com.caddie.study.runtime.model.TrialOutcome
import com.caddie.study.runtime.model.TrialSpec
import kotlinx.coroutines.CancellationException

/**
 * Deterministic step executor for a single study trial.
 *
 * Ported from `caddie.study.executor.TrialExecutor`. Walks [TrialSpec.steps],
 * resolves each action against the current Android UI state via [StudyBackend],
 * injects controlled errors at `error_steps`, fires oversight gates per
 * [RuntimeStudyCondition], captures study variables, and routes participant
 * corrections (rewind / compensate / reject) via [CorrectionClassifier].
 *
 * Post-trial verification is deferred to a later sub-increment — it is inert
 * when [verificationBackend] is null (matching the Python default).
 *
 * The executor never falls back to an LLM for step resolution. If a target
 * cannot be resolved the trial aborts immediately. Correction classification
 * is injected and the Android study runtime uses a deterministic classifier.
 *
 * @param backend Android device backend for executing actions.
 * @param logger Event logger.
 * @param oversight Oversight gate callback for consequential steps.
 * @param spec Trial specification (steps, errors, verification).
 * @param condition Study condition controlling gate behavior.
 * @param errorTasks Task IDs scheduled for controlled error injection.
 * @param correctionClassifier Classifier for participant corrections (null disables corrections).
 * @param correctionSource Source of participant corrections (null disables corrections).
 * @param verificationBackend Backend for post-trial verification checks (null skips verification).
 */
class TrialExecutor(
    private val backend: StudyBackend,
    private val logger: StudyLogger,
    private val oversight: TrialOversightGate,
    private val spec: TrialSpec,
    private val condition: RuntimeStudyCondition,
    private val errorTasks: Set<String> = emptySet(),
    private val correctionClassifier: CorrectionClassifier? = null,
    private val correctionSource: CorrectionSource? = null,
    private val verificationBackend: VerificationBackend? = null,
    private val confirmationNanoTime: () -> Long = System::nanoTime,
) {
    /** A study step resolved with error injection applied. */
    data class ResolvedStep(
        val source: StudyStep,
        val effectiveAction: String,
        val narration: String,
        val confirmationText: String,
        val errorInjected: Boolean,
        val errorVariantId: String?,
    )

    /** Result of a single step execution. */
    data class ExecutionResult(
        val success: Boolean,
        val step: StudyStep,
        val error: String? = null,
        val action: String? = null,
        val resolvedIndex: Int? = null,
        val elapsedMs: Double = 0.0,
        val errorVariantId: String? = null,
        val errorInjected: Boolean = false,
    )

    private data class TimedC1Decision(
        val decision: OversightGateDecision,
        val responseTimeMs: Double,
    )

    /** Result of executing a complete trial. */
    data class TrialResult(
        val outcome: TrialOutcome,
        val stepsExecuted: List<ExecutionResult> = emptyList(),
        val stepsDone: Int = 0,
        val durationMs: Double = 0.0,
        val reason: String = "",
        val verificationPassed: Boolean = false,
        val screenshots: List<String> = emptyList(),
    )

    private val stepsExecuted = mutableListOf<ExecutionResult>()
    private val screenshots = mutableListOf<String>()
    private val variables = mutableMapOf<String, String>()
    private var startMonoNs: Long = 0L
    private var trialId: String? = null
    private var confirmationResponseTimeMs: Double = 0.0
    private var confirmationCount: Int = 0

    // ── Correction state ──
    private val correctionJournals: Map<String, CorrectionJournal> =
        spec.steps.mapNotNull { it.errorVariant?.id }
            .associateWith { CorrectionJournal(it) }
    private val correctedVariantIds = mutableSetOf<String>()
    private var deferredCorrection: String? = null
    private val successfulStepIds = mutableListOf<String>()

    val taskId: String get() = spec.id

    /** Whether post-trial correction is available for this executor. */
    fun canCorrectAfterCompletion(): Boolean =
        correctionClassifier != null && correctionSource != null &&
            spec.steps.any { step ->
                step.errorVariant?.correction != null &&
                    step.errorVariant.id in correctionJournals
            }

    private fun elapsedMs(): Double =
        (System.nanoTime() - startMonoNs) / 1_000_000.0

    // ── Main run loop ──

    suspend fun run(): TrialResult {
        startMonoNs = System.nanoTime()
        stepsExecuted.clear()
        screenshots.clear()
        confirmationResponseTimeMs = 0.0
        confirmationCount = 0
        var outcome = TrialOutcome.TECHNICAL_FAILURE
        var reason = "Unexpected exception"
        var stepsDone = 0

        try {
            trialId = logger.trialStart(
                taskId = spec.id,
                block = "main",
                variant = if (spec.id !in errorTasks) "normal" else "error",
            )
            screenshots.add(logger.screenshotCaptured("pre_trial", trialId!!))

            val resolvedMap = mutableMapOf<String, ResolvedStep>()
            val variableSnapshots = mutableMapOf<Int, Map<String, String>>()
            val steps = spec.steps
            var c2GateShown = false
            var stepIdx = 0

            while (stepIdx < steps.size) {
                // Correction check at safe boundary (non-blocking)
                val (rewindIdx, correctionAbort) = handlePendingCorrection(
                    stepIdx, resolvedMap, variableSnapshots, waitForPending = false,
                )
                if (correctionAbort != null) {
                    return abortTrial(stepIdx, TrialOutcome.ABORTED, correctionAbort)
                }
                if (rewindIdx != null) {
                    stepIdx = rewindIdx
                    continue
                }

                val step = steps[stepIdx]
                variableSnapshots[stepIdx] = variables.toMap()

                // Check trial deadline before each step
                if (elapsedMs() > spec.maxDurationS * 1000) {
                    return abortTrial(stepIdx, TrialOutcome.CONFIRMATION_TIMEOUT, "Trial exceeded max_duration_s")
                }

                // Check cancellation
                if (oversight.isCancelled()) {
                    return abortTrial(stepIdx, TrialOutcome.ABORTED, "User cancelled the trial")
                }

                // The participant preparation deliberately opens source content before the run.
                // Skip only declared navigation states; commits and injected-error steps must run.
                val skipForbidden = step.commit ||
                    (step.id in spec.errorSteps && spec.id in errorTasks)
                if (!skipForbidden && backend.isStepAlreadySatisfied(step)) {
                    logger.stepAlreadySatisfied(step.id, trialId!!)
                    stepsDone = stepIdx + 1
                    successfulStepIds.add(step.id)
                    for (journal in correctionJournals.values) {
                        journal.recordStep(step.id, commit = false)
                    }
                    stepIdx++
                    continue
                }

                // C2: one final checkpoint immediately before the single commit
                if (step.commit && condition == RuntimeStudyCondition.FINAL_CHECKPOINT) {
                    if (c2GateShown) {
                        return abortTrial(stepIdx, TrialOutcome.TECHNICAL_FAILURE, "C2 encountered more than one commit step")
                    }
                    c2GateShown = true
                    val resolved = resolvedMap.getOrPut(step.id) { resolveStep(step) }
                    val c2Steps = listOf(resolved.source)
                    val c2Narrations = resolvedC2SummaryLines(resolvedMap, resolved)
                    val gateStartNs = confirmationNanoTime()
                    val c2Decision = try {
                        oversight.showC2Summary(c2Steps, c2Narrations)
                    } catch (resolved: ParticipantResponseCancellation) {
                        recordResolvedCancellation("c2", step.id, resolved, gateStartNs)
                        throw resolved
                    } catch (cancelled: CancellationException) {
                        recordConfirmation(
                            "c2", step.id, "cancelled", confirmationElapsedMs(gateStartNs), false,
                        )
                        throw cancelled
                    } catch (error: Exception) {
                        recordConfirmation(
                            "c2", step.id, "error", confirmationElapsedMs(gateStartNs), false,
                        )
                        throw error
                    }
                    val responseTimeMs = c2Decision.responseLatencyMs
                        ?: confirmationElapsedMs(gateStartNs)
                    val participantResponse = isParticipantResponse(c2Decision)
                    recordConfirmation(
                        gateType = "c2",
                        stepId = step.id,
                        decision = when {
                            c2Decision.reason?.contains("timeout", ignoreCase = true) == true -> "timeout"
                            !participantResponse &&
                                c2Decision.reason?.contains("error", ignoreCase = true) == true -> "error"
                            !participantResponse -> "cancelled"
                            c2Decision.confirmed -> "confirmed"
                            c2Decision.declined -> "declined"
                            else -> "cancelled"
                        },
                        responseTimeMs = responseTimeMs,
                        participantResponse = participantResponse,
                    )
                    if (!c2Decision.confirmed) {
                        // Check for pending correction before aborting (blocking)
                        val (rw, ab) = handlePendingCorrection(
                            stepIdx, resolvedMap, variableSnapshots, waitForPending = true,
                        )
                        if (ab != null) return abortTrial(stepIdx, TrialOutcome.ABORTED, ab)
                        if (rw != null) {
                            c2GateShown = false
                            stepIdx = rw
                            continue
                        }
                        return abortTrial(
                            stepIdx, TrialOutcome.ABORTED,
                            if (c2Decision.declined) "C2 summary declined" else "C2 summary cancelled",
                        )
                    }
                    // Apply any modified steps from C2
                    for (modified in c2Decision.modifiedSteps) {
                        if (modified.id == resolved.source.id) {
                            resolvedMap[modified.id] = resolveStepModified(modified)
                        }
                    }
                }

                // C1: confirm every participant-meaningful action before it executes
                if (condition == RuntimeStudyCondition.STEPWISE && step.confirmInC1) {
                    val resolved = resolvedMap.getOrPut(step.id) { resolveStep(step) }
                    val timedDecision = confirmC1(step, resolved.confirmationText)
                    val decision = timedDecision.decision
                    val gateElapsedMs = timedDecision.responseTimeMs
                    val gateElapsedS = gateElapsedMs / 1_000.0
                    if (!decision.confirmed) {
                        // Voice capture can resolve the gate just after its deadline. Consume an
                        // already-recorded correction before classifying the decision as timeout.
                        val (rw, ab) = handlePendingCorrection(
                            stepIdx, resolvedMap, variableSnapshots, waitForPending = true,
                        )
                        if (ab != null) return abortTrial(stepIdx, TrialOutcome.ABORTED, ab)
                        if (rw != null) {
                            stepIdx = rw
                            continue
                        }
                        if (gateElapsedS > spec.perGateTimeoutS) {
                            return abortTrial(
                                stepIdx,
                                TrialOutcome.CONFIRMATION_TIMEOUT,
                                "C1 gate timeout on step ${step.id}",
                            )
                        }
                        return abortTrial(stepIdx, TrialOutcome.ABORTED, "Step ${step.id} declined in C1 gate")
                    }
                    if (gateElapsedS > spec.perGateTimeoutS) {
                        return abortTrial(stepIdx, TrialOutcome.CONFIRMATION_TIMEOUT, "C1 gate timeout on step ${step.id}")
                    }
                }

                // Execute the step (single resolution)
                val resolved = resolvedMap.getOrPut(step.id) { resolveStep(step) }
                val result = try {
                    executeStep(step, resolved, stepIdx)
                } catch (_: CorrectionInterrupted) {
                    // A correction arrived mid-step; handle it and retry
                    val (rw, ab) = handlePendingCorrection(
                        stepIdx, resolvedMap, variableSnapshots, waitForPending = false,
                    )
                    if (ab != null) return abortTrial(stepIdx, TrialOutcome.ABORTED, ab)
                    if (rw != null) stepIdx = rw
                    continue
                }
                stepsExecuted.add(result)

                if (result.success) {
                    stepsDone = stepIdx + 1
                    successfulStepIds.add(step.id)
                    // Record in all correction journals
                    for (journal in correctionJournals.values) {
                        journal.recordStep(step.id, commit = step.commit)
                    }
                } else {
                    return abortTrial(stepIdx, TrialOutcome.TECHNICAL_FAILURE, result.error ?: "Step execution failed")
                }

                // Correction check after successful step (non-blocking)
                val (postRewind, postAbort) = handlePendingCorrection(
                    stepIdx + 1, resolvedMap, variableSnapshots, waitForPending = false,
                )
                if (postAbort != null) {
                    return abortTrial(stepIdx + 1, TrialOutcome.ABORTED, postAbort)
                }
                if (postRewind != null) {
                    stepIdx = postRewind
                    continue
                }

                stepIdx++
            }

            // Run post-trial verification if a backend is configured
            var verificationPassed = false
            var verificationResults: List<Map<String, Any?>> = emptyList()
            if (verificationBackend != null) {
                val summary = VerificationManager(
                    logger = logger,
                    backend = verificationBackend,
                    rules = spec.verification,
                    trialId = trialId!!,
                    taskId = spec.id,
                ).run()
                verificationPassed = summary.allPassed
                verificationResults = summary.results.map { result ->
                    linkedMapOf(
                        "rule_id" to result.ruleId,
                        "outcome" to result.outcome.wireValue,
                        "screenshot_path" to result.screenshotPath,
                        "details" to result.details,
                    )
                }
                summary.results.forEach { r ->
                    r.screenshotPath?.let { screenshots.add(it) }
                }
            }

            // Determine final outcome based on verification
            if (verificationPassed) {
                outcome = TrialOutcome.SUCCESS
                reason = "All steps completed successfully"
            } else if (
                verificationBackend != null &&
                verificationResults.none { it["outcome"] == VerificationOutcome.TIMEOUT.wireValue } &&
                hasUncorrectedInjectedError()
            ) {
                // A deliberately injected error that the participant did not correct is a
                // valid study observation, not an infrastructure/runtime failure.
                outcome = TrialOutcome.ERROR_INJECTED
                reason = "Controlled error remained uncorrected"
            } else if (verificationBackend != null) {
                outcome = TrialOutcome.VERIFICATION_FAILED
                reason = "Post-trial verification failed"
            } else {
                outcome = TrialOutcome.SUCCESS
                reason = "All steps completed successfully (no verification backend)"
            }

            logTrialComplete(
                trialId!!, outcome.wireValue,
                totalSteps = steps.size,
                errorsInjected = countErrorsInjected(),
                durationMs = elapsedMs(),
            )
            writeTerminalSummary(
                outcome, steps.size,
                verificationPassed.takeIf { verificationResults.isNotEmpty() },
            )

            return TrialResult(
                outcome = outcome,
                stepsExecuted = stepsExecuted.toList(),
                stepsDone = stepsDone,
                durationMs = elapsedMs(),
                reason = reason,
                verificationPassed = verificationPassed,
                screenshots = screenshots.toList(),
            )
        } catch (cancelled: CancellationException) {
            try {
                logger.technicalFailure("trial cancelled: ${cancelled.message ?: "runtime cancelled"}")
            } catch (_: Exception) {}
            val tid = trialId
            if (tid != null) {
                try {
                    logTrialComplete(
                        tid,
                        TrialOutcome.TECHNICAL_FAILURE.wireValue,
                        stepsDone,
                        countErrorsInjected(),
                        elapsedMs(),
                    )
                } catch (_: Exception) {}
                try {
                    writeTerminalSummary(TrialOutcome.TECHNICAL_FAILURE, stepsDone, null)
                } catch (_: Exception) {}
            }
            throw cancelled
        } catch (e: Exception) {
            try {
                logger.technicalFailure(e.message ?: e.toString())
            } catch (_: Exception) {}
            val tid = trialId
            if (tid != null) {
                try {
                    logTrialComplete(tid, TrialOutcome.TECHNICAL_FAILURE.wireValue, 0, 0, elapsedMs())
                } catch (_: Exception) {}
                try {
                    writeTerminalSummary(TrialOutcome.TECHNICAL_FAILURE, stepsDone, null)
                } catch (_: Exception) {}
            }
            return TrialResult(
                outcome = TrialOutcome.TECHNICAL_FAILURE,
                stepsExecuted = stepsExecuted.toList(),
                stepsDone = stepsDone,
                durationMs = elapsedMs(),
                reason = "Unexpected error: $e",
            )
        }
    }

    // ── Trial finalization ──

    private suspend fun abortTrial(stepIdx: Int, outcome: TrialOutcome, reason: String): TrialResult {
        val tid = trialId ?: "unknown"
        logTrialComplete(
            tid, outcome.wireValue,
            totalSteps = stepIdx,
            errorsInjected = countErrorsInjected(),
            durationMs = elapsedMs(),
        )
        writeTerminalSummary(outcome, stepIdx, null)
        return TrialResult(
            outcome = outcome,
            stepsExecuted = stepsExecuted.toList(),
            stepsDone = stepIdx,
            durationMs = elapsedMs(),
            reason = reason,
            screenshots = screenshots.toList(),
        )
    }

    private fun recordConfirmation(
        gateType: String,
        stepId: String?,
        decision: String,
        responseTimeMs: Double,
        participantResponse: Boolean,
    ): Double {
        if (participantResponse) {
            confirmationResponseTimeMs += responseTimeMs
            confirmationCount += 1
        }
        logger.confirmationResolved(
            trialId = trialId ?: "unknown",
            gateType = gateType,
            stepId = stepId,
            decision = decision,
            responseTimeMs = responseTimeMs,
            participantResponse = participantResponse,
        )
        return responseTimeMs
    }

    private suspend fun confirmC1(
        step: StudyStep,
        confirmationText: String,
    ): TimedC1Decision {
        val gateStartNs = confirmationNanoTime()
        val decision = try {
            oversight.confirmConsequentialStep(step, confirmationText)
        } catch (resolved: ParticipantResponseCancellation) {
            recordResolvedCancellation("c1", step.id, resolved, gateStartNs)
            throw resolved
        } catch (cancelled: CancellationException) {
            recordConfirmation(
                "c1", step.id, "cancelled", confirmationElapsedMs(gateStartNs), false,
            )
            throw cancelled
        } catch (error: Exception) {
            recordConfirmation(
                "c1", step.id, "error", confirmationElapsedMs(gateStartNs), false,
            )
            throw error
        }
        val responseTimeMs = decision.responseLatencyMs
            ?: confirmationElapsedMs(gateStartNs)
        val participantResponse = isParticipantResponse(decision)
        recordConfirmation(
            gateType = "c1",
            stepId = step.id,
            decision = when {
                decision.reason?.contains("timeout", ignoreCase = true) == true ||
                    responseTimeMs > spec.perGateTimeoutS * 1_000.0 -> "timeout"
                !participantResponse &&
                    decision.reason?.contains("error", ignoreCase = true) == true -> "error"
                !participantResponse -> "cancelled"
                decision.confirmed -> "confirmed"
                decision.declined -> "declined"
                else -> "cancelled"
            },
            responseTimeMs = responseTimeMs,
            participantResponse = participantResponse,
        )
        return TimedC1Decision(decision, responseTimeMs)
    }

    private fun recordResolvedCancellation(
        gateType: String,
        stepId: String,
        resolved: ParticipantResponseCancellation,
        gateStartNs: Long,
    ) {
        val responseTimeMs = resolved.responseLatencyMs
            ?: confirmationElapsedMs(gateStartNs)
        recordConfirmation(
            gateType = gateType,
            stepId = stepId,
            decision = if (resolved.confirmed) "confirmed" else "declined",
            responseTimeMs = responseTimeMs,
            participantResponse = resolved.responseLatencyMs != null,
        )
    }

    private fun isParticipantResponse(decision: OversightGateDecision): Boolean =
        decision.responseLatencyMs != null ||
            (decision.reason == null && (decision.confirmed || decision.declined))

    private fun isParticipantResponse(decision: C2Decision): Boolean =
        decision.responseLatencyMs != null ||
            (decision.reason == null && (decision.confirmed || decision.declined))

    private fun confirmationElapsedMs(gateStartNs: Long): Double =
        ((confirmationNanoTime() - gateStartNs) / 1_000_000.0).coerceAtLeast(0.0)

    private fun logTrialComplete(
        trialId: String,
        outcome: String,
        totalSteps: Int,
        errorsInjected: Int,
        durationMs: Double,
    ) {
        logger.trialComplete(
            trialId = trialId,
            outcome = outcome,
            totalSteps = totalSteps,
            errorsInjected = errorsInjected,
            durationMs = durationMs,
            confirmationResponseTimeMs = confirmationResponseTimeMs,
            confirmationCount = confirmationCount,
        )
    }

    private fun hasUncorrectedInjectedError(): Boolean = stepsExecuted.any { result ->
        result.errorInjected &&
            result.errorVariantId != null &&
            result.errorVariantId !in correctedVariantIds
    }

    private fun writeTerminalSummary(outcome: TrialOutcome, totalSteps: Int, verificationPassed: Boolean?) {
        logger.writeSummary(
            outcome = outcome.wireValue,
            totalSteps = totalSteps,
            errorsInjected = countErrorsInjected(),
            durationMs = elapsedMs(),
            trialIds = listOfNotNull(trialId),
            verificationResults = verificationPassed?.let { mapOf("all_passed" to it) },
            screenshots = screenshots.toList(),
        )
    }

    // ── Correction handling ──

    /**
     * Classify and apply one pending correction at a safe boundary.
     *
     * Returns (rewindIdx, abortReason). If [rewindIdx] is non-null, the run
     * loop should jump to that step index. If [abortReason] is non-null, the
     * trial should abort. If both are null, no correction was pending or it
     * was handled without needing a rewind.
     */
    private suspend fun handlePendingCorrection(
        currentStepIdx: Int,
        resolvedMap: MutableMap<String, ResolvedStep>,
        variableSnapshots: Map<Int, Map<String, String>>,
        waitForPending: Boolean,
    ): Pair<Int?, String?> {
        if (correctionClassifier == null || correctionSource == null) return null to null

        var correction = deferredCorrection
        deferredCorrection = null
        if (correction == null) {
            correction = correctionSource.takeCorrection()
        }
        if (correction == null && waitForPending) {
            correction = correctionSource.waitForCorrection(CORRECTION_TEXT_WAIT_S)
        }
        if (correction.isNullOrBlank()) return null to null

        val steps = spec.steps
        val activeVariants = steps.mapIndexedNotNull { index, step ->
            val resolvedHere = resolvedMap[step.id]?.errorInjected == true
            val executedHere = step.id in successfulStepIds
            if (index <= currentStepIdx && (resolvedHere || executedHere) &&
                step.id in spec.errorSteps && step.errorVariant != null &&
                spec.id in errorTasks &&
                step.errorVariant.id !in correctedVariantIds
            ) {
                step.errorVariant
            } else null
        }
        if (activeVariants.isEmpty()) {
            deferredCorrection = correction
            return null to null
        }

        val nextStepId = steps.getOrNull(currentStepIdx)?.id
        val context = correctionContext(
            correction = correction,
            nextStepId = nextStepId,
            activeVariants = activeVariants,
        )
        val decision = correctionClassifier.classify(context)

        logger.intervention(
            text = correction,
            trialId = trialId!!,
            taskId = spec.id,
            accepted = decision.accepted,
            reason = decision.reason,
            errorVariantId = decision.errorVariantId,
        )

        if (!decision.accepted || decision.errorVariantId == null) {
            correctionSource.pauseForUnclearCorrection()
            return currentStepIdx to null
        }

        // Find the step that owns this variant
        val variantStepIdx = steps.indexOfFirst { step ->
            step.errorVariant?.id == decision.errorVariantId && step.id in spec.errorSteps
        }
        if (variantStepIdx < 0) return currentStepIdx to null
        val variant = steps[variantStepIdx].errorVariant ?: return currentStepIdx to null
        val policy = variant.correction
        val journal = correctionJournals[decision.errorVariantId]

        if (policy != null && journal != null) {
            val route = journal.route(policy.afterCommit)
            logger.correctionRoute(
                errorVariantId = decision.errorVariantId,
                route = route.wireValue,
                phase = CorrectionPhase.RUNNING.wireValue,
                trialId = trialId!!,
                taskId = spec.id,
            )
            when (route) {
                CorrectionRoute.REJECT_IRREVERSIBLE -> {
                    val commitStepId = journal.entries.reversed()
                        .firstOrNull { it.commit }?.stepId.orEmpty()
                    val message = policy.irreversibleMessageDe
                        ?: "Die Aktion wurde bereits ausgeführt und kann nicht mehr geändert werden."
                    logger.correctionIrreversible(
                        errorVariantId = decision.errorVariantId,
                        commitStepId = commitStepId,
                        message = message,
                        trialId = trialId!!,
                        taskId = spec.id,
                    )
                    return null to "correction_rejected_irreversible"
                }
                CorrectionRoute.COMPENSATE -> {
                    val (success, reason) = executeCompensation(policy, decision.errorVariantId)
                    if (!success) return null to reason
                    correctedVariantIds.add(decision.errorVariantId)
                    return null to null
                }
                CorrectionRoute.ALREADY_CORRECT -> {
                    correctedVariantIds.add(decision.errorVariantId)
                    return null to null
                }
                CorrectionRoute.REWIND -> { /* fall through to rewind logic */ }
            }
        }

        // REWIND route
        val stepIndexes = steps.mapIndexed { index, step -> step.id to index }.toMap()
        var rewindIdx = variantStepIdx
        if (policy?.rewindToStepId != null && policy.rewindToStepId in stepIndexes) {
            rewindIdx = stepIndexes[policy.rewindToStepId]!!
        }
        if (rewindIdx > currentStepIdx) {
            correctionSource.pauseForUnclearCorrection()
            return currentStepIdx to null
        }
        if (policy?.rewindSteps?.isNotEmpty() == true) {
            val (success, reason) = executeRewindSteps(policy.rewindSteps, decision.errorVariantId)
            if (!success) return null to reason
        }

        correctedVariantIds.add(decision.errorVariantId)
        // Restore variables to snapshot at rewind point
        variableSnapshots[rewindIdx]?.let { snapshot ->
            variables.clear()
            variables.putAll(snapshot)
        }
        // Clear resolved steps at or after the rewind point
        val rewindStepId = steps[rewindIdx].id
        resolvedMap.keys.retainAll { stepId ->
            (stepIndexes[stepId] ?: steps.size) < rewindIdx
        }
        // Remove successful step IDs at or after rewind point
        successfulStepIds.retainAll { stepId ->
            (stepIndexes[stepId] ?: steps.size) < rewindIdx
        }
        logger.correctionRewind(
            errorVariantId = decision.errorVariantId,
            fromStepId = nextStepId,
            toStepId = rewindStepId,
            trialId = trialId!!,
            taskId = spec.id,
        )
        return rewindIdx to null
    }

    private fun correctionContext(
        correction: String,
        nextStepId: String?,
        activeVariants: List<com.caddie.study.runtime.model.ErrorVariant>,
        phase: CorrectionPhase = CorrectionPhase.RUNNING,
    ): CorrectionContext {
        val journal = activeVariants.firstOrNull()?.id?.let { correctionJournals[it] }
        return CorrectionContext(
            instruction = spec.instructionDe,
            taskId = spec.id,
            correction = correction,
            executedStepIds = successfulStepIds.toList(),
            nextStepId = nextStepId,
            activeVariants = activeVariants,
            phase = phase,
            journalEntries = journal?.entries ?: emptyList(),
            committed = journal?.committed ?: false,
            compensationComplete = journal?.let { it.route(PostCommitPolicy.COMPENSATE) == CorrectionRoute.ALREADY_CORRECT } ?: false,
        )
    }

    private suspend fun executeCompensation(
        policy: CorrectionPolicy,
        errorVariantId: String,
        requireConfirmation: Boolean = true,
    ): Pair<Boolean, String> {
        val journal = correctionJournals[errorVariantId] ?: return false to "no journal"
        val completed = mutableListOf<String>()

        for ((index, correctionStep) in policy.steps.withIndex()) {
            if (oversight.isCancelled()) return false to "User cancelled correction"

            if (correctionStep.runIfTargetPresent && !correctionTargetPresent(correctionStep)) {
                logger.compensationStep(
                    stepId = correctionStep.id,
                    errorVariantId = errorVariantId,
                    outcome = "skipped_already_correct",
                    trialId = trialId!!,
                    taskId = spec.id,
                )
                continue
            }

            val studyStep = studyStepFromCorrection(correctionStep)
            if (requireConfirmation && condition == RuntimeStudyCondition.STEPWISE) {
                val timedDecision = confirmC1(
                    studyStep, expandVariables(correctionStep.confirmationText),
                )
                if (timedDecision.responseTimeMs > spec.perGateTimeoutS * 1_000.0) {
                    return false to "Correction confirmation timeout: ${correctionStep.id}"
                }
                if (!timedDecision.decision.confirmed) {
                    return false to "Correction step declined: ${correctionStep.id}"
                }
            }

            val resolved = ResolvedStep(
                source = studyStep,
                effectiveAction = expandVariables(correctionStep.action),
                narration = expandVariables(correctionStep.narration),
                confirmationText = expandVariables(correctionStep.confirmationText),
                errorInjected = false,
                errorVariantId = null,
            )
            val result = executeStep(studyStep, resolved, index)
            stepsExecuted.add(result)
            logger.compensationStep(
                stepId = correctionStep.id,
                errorVariantId = errorVariantId,
                outcome = if (result.success) "success" else "failed",
                trialId = trialId!!,
                taskId = spec.id,
            )
            if (!result.success) return false to "Correction step failed: ${correctionStep.id}"
            completed.add(correctionStep.id)
        }

        if (!verifyCorrection(policy)) return false to "Correction verification failed"
        journal.finishCompensation(completed)
        return true to "Correction completed"
    }

    private suspend fun executeRewindSteps(
        steps: List<CorrectionStep>,
        errorVariantId: String,
    ): Pair<Boolean, String> {
        for ((index, correctionStep) in steps.withIndex()) {
            if (oversight.isCancelled()) return false to "User cancelled correction"
            if (correctionStep.runIfTargetPresent && !correctionTargetPresent(correctionStep)) {
                logger.compensationStep(
                    stepId = correctionStep.id,
                    errorVariantId = errorVariantId,
                    outcome = "skipped_not_visible",
                    trialId = trialId!!,
                    taskId = spec.id,
                )
                continue
            }
            val studyStep = studyStepFromCorrection(correctionStep)
            val resolved = ResolvedStep(
                source = studyStep,
                effectiveAction = expandVariables(correctionStep.action),
                narration = expandVariables(correctionStep.narration),
                confirmationText = expandVariables(correctionStep.confirmationText),
                errorInjected = false,
                errorVariantId = null,
            )
            val result = executeStep(studyStep, resolved, index)
            stepsExecuted.add(result)
            logger.compensationStep(
                stepId = correctionStep.id,
                errorVariantId = errorVariantId,
                outcome = if (result.success) "rewind_success" else "rewind_failed",
                trialId = trialId!!,
                taskId = spec.id,
            )
            if (!result.success) return false to "Correction rewind step failed: ${correctionStep.id}"
        }
        return true to "Correction rewind prepared"
    }

    private fun verifyCorrection(policy: CorrectionPolicy): Boolean {
        val elements = try {
            backend.listElements()
        } catch (_: Exception) {
            return false
        }
        val textValues = elements.mapNotNull { (it["text"] as? String).orEmpty().ifEmpty { null } }
        val accessibilityValues = elements.flatMap { el ->
            listOfNotNull(
                (el["text"] as? String).orEmpty(),
                (el["content_description"] as? String).orEmpty(),
            )
        }
        for (assertion in policy.assertions) {
            val values = if (assertion.checkType.startsWith("text_")) textValues else accessibilityValues
            val present = values.any { assertion.value.lowercase() in it.lowercase() }
            if (assertion.checkType.endsWith("_present") && !present) return false
            if (assertion.checkType.endsWith("_absent") && present) return false
        }
        return true
    }

    private fun correctionTargetPresent(step: CorrectionStep): Boolean {
        return try {
            val parsed = ActionParser.parse(step.action)
            if (parsed.type != ActionParser.ActionType.TAP &&
                parsed.type != ActionParser.ActionType.TAP_FIRST
            ) {
                return true
            }
            StudyTextHelpers.findElement(backend.listElements(), parsed.descriptor)?.let { target ->
                target["enabled"] == true &&
                    target["visible"] == true &&
                    target["window_foreground"] == true
            } == true
        } catch (_: Exception) {
            false
        }
    }

    private fun studyStepFromCorrection(step: CorrectionStep): StudyStep = StudyStep(
        id = step.id,
        action = step.action,
        narration = step.narration,
        confirmationText = step.confirmationText,
        stepType = StepType.NORMAL,
        minNarrationMs = step.minNarrationMs,
        readyPackage = step.readyPackage,
    )

    /**
     * Apply one fresh follow-up correction to a completed deterministic trial.
     * Ported from `caddie.study.executor.TrialExecutor.correct_after_completion`.
     */
    suspend fun correctAfterCompletion(text: String): RecentCorrectionResult {
        if (text.isBlank() || !canCorrectAfterCompletion()) {
            return RecentCorrectionResult(consumed = false, accepted = false, outcome = "not_correction", message = "")
        }
        val tid = trialId ?: return RecentCorrectionResult(consumed = false, accepted = false, outcome = "not_correction", message = "")

        val variants = spec.steps.mapNotNull { step ->
            if (step.id in spec.errorSteps && step.errorVariant?.correction != null &&
                step.errorVariant.id in correctionJournals
            ) {
                step.errorVariant
            } else null
        }
        if (variants.isEmpty()) {
            return RecentCorrectionResult(consumed = false, accepted = false, outcome = "not_correction", message = "")
        }

        val context = correctionContext(
            correction = text,
            nextStepId = null,
            activeVariants = variants,
            phase = CorrectionPhase.RECENTLY_COMPLETED,
        )
        val decision = correctionClassifier!!.classify(context)

        if (!decision.accepted || decision.errorVariantId == null) {
            val consumed = decision.reason != "not_correction"
            return RecentCorrectionResult(
                consumed = consumed,
                accepted = false,
                outcome = decision.reason,
                message = if (consumed) "Korrektur nicht eindeutig – bitte präzisieren." else "",
            )
        }

        val variant = variants.firstOrNull { it.id == decision.errorVariantId }
        if (variant?.correction == null) {
            return RecentCorrectionResult(
                consumed = true, accepted = false,
                outcome = "unknown_variant",
                message = "Korrektur konnte nicht zugeordnet werden.",
            )
        }

        val policy = variant.correction!!
        val journal = correctionJournals[variant.id]!!
        val route = journal.route(policy.afterCommit)
        logger.correctionRoute(
            errorVariantId = variant.id,
            route = route.wireValue,
            phase = CorrectionPhase.RECENTLY_COMPLETED.wireValue,
            trialId = tid,
            taskId = spec.id,
        )

        when (route) {
            CorrectionRoute.ALREADY_CORRECT -> return RecentCorrectionResult(
                consumed = true, accepted = true,
                outcome = "already_correct",
                message = "Das ist bereits korrekt.",
            )
            CorrectionRoute.REJECT_IRREVERSIBLE -> {
                val message = policy.irreversibleMessageDe
                    ?: "Die Aktion wurde bereits ausgeführt und kann nicht mehr geändert werden."
                return RecentCorrectionResult(
                    consumed = true, accepted = false,
                    outcome = "correction_rejected_irreversible",
                    message = message,
                )
            }
            CorrectionRoute.REWIND -> return RecentCorrectionResult(
                consumed = true, accepted = false,
                outcome = "correction_requires_active_trial",
                message = "Diese Korrektur ist nach Abschluss nicht möglich.",
            )
            CorrectionRoute.COMPENSATE -> {
                val (success, reason) = executeCompensation(policy, variant.id, requireConfirmation = false)
                if (!success) {
                    return RecentCorrectionResult(
                        consumed = true, accepted = false,
                        outcome = "correction_failed",
                        message = reason,
                    )
                }
                correctedVariantIds.add(variant.id)
                return RecentCorrectionResult(
                    consumed = true, accepted = true,
                    outcome = "corrected",
                    message = "Korrektur wurde erfolgreich ausgeführt.",
                )
            }
        }
    }

    // ── Step resolution ──

    private fun resolveStep(step: StudyStep): ResolvedStep {
        var action = expandVariables(step.action)
        val narration = expandVariables(step.narration.ifBlank { step.action })
        var effectiveAction = action
        var actuallyInjected = false
        var errorVariantId: String? = null

        // If variant was already corrected, convert "input text" → "replace text"
        val ev = step.errorVariant
        if (ev != null && ev.id in correctedVariantIds &&
            correctionJournals[ev.id]?.entries?.any { it.stepId == step.id } == true &&
            action.startsWith("input text ")
        ) {
            action = "replace text " + action.removePrefix("input text ")
            effectiveAction = action
        }

        // Inject error if this step is an error step for this task and not yet corrected
        if (step.id in spec.errorSteps && ev != null &&
            spec.id in errorTasks &&
            ev.id !in correctedVariantIds
        ) {
            val (injected, ok) = StudyTextHelpers.injectError(action, ev, ::expandVariables)
            if (ok) {
                effectiveAction = injected
                actuallyInjected = true
                errorVariantId = ev.id
            }
        }

        val confirmationText = if (step.confirmationText != null) {
            var ct = expandVariables(step.confirmationText)
            if (actuallyInjected && ev != null) {
                val (injectedCt, _) = StudyTextHelpers.injectError(ct, ev, ::expandVariables, participantCopy = true)
                ct = injectedCt
            }
            StudyTextHelpers.participantConfirmationText(ct, effectiveAction)
        } else {
            StudyTextHelpers.formatConfirmationText(effectiveAction, ::expandVariables)
        }

        return ResolvedStep(
            source = step,
            effectiveAction = effectiveAction,
            narration = narration,
            confirmationText = confirmationText,
            errorInjected = actuallyInjected,
            errorVariantId = errorVariantId,
        )
    }

    private fun resolveStepModified(modified: StudyStep): ResolvedStep {
        val effectiveAction = expandVariables(modified.action)
        val confirmationText = StudyTextHelpers.participantConfirmationText(
            expandVariables(modified.confirmationText ?: StudyTextHelpers.formatConfirmationText(effectiveAction, ::expandVariables)),
            effectiveAction,
        )
        return ResolvedStep(
            source = modified,
            effectiveAction = effectiveAction,
            narration = expandVariables(modified.narration.ifBlank { modified.action }),
            confirmationText = confirmationText,
            errorInjected = modified.errorVariant != null,
            errorVariantId = modified.errorVariant?.id,
        )
    }

    private fun resolvedC2SummaryLines(
        resolvedMap: Map<String, ResolvedStep>,
        commit: ResolvedStep,
    ): List<String> {
        var lines = spec.c2SummaryLines.map { expandVariables(it) }
        for (resolved in resolvedMap.values) {
            val variant = resolved.source.errorVariant ?: continue
            if (!resolved.errorInjected) continue
            val correct = variant.summaryCorrectValue ?: expandVariables(variant.correctValue)
            val wrong = variant.summaryWrongValue ?: expandVariables(variant.wrongValue)
            lines = lines.map { it.replace(correct, wrong) }
        }
        if (commit.confirmationText !in lines) lines = lines + commit.confirmationText
        return lines
    }

    // ── Step execution ──

    private suspend fun executeStep(step: StudyStep, resolved: ResolvedStep, stepIdx: Int): ExecutionResult {
        val stepStartNs = System.nanoTime()
        logger.stepStart(step.id, resolved.narration, trialId!!)

        // Log error exactly once, right before execution
        if (resolved.errorInjected && resolved.errorVariantId != null) {
            val ev = step.errorVariant
            if (ev != null) {
                logger.errorInjected(
                    step.id, trialId!!,
                    errorVariantId = resolved.errorVariantId,
                    field = ev.field,
                    wrongValue = ev.wrongValue,
                    correctValue = ev.correctValue,
                )
            }
        }

        // Capture pre-action screenshot for commit/error steps
        if (step.stepType == StepType.COMMIT || resolved.errorInjected) {
            screenshots.add(logger.screenshotCaptured("pre_action_$stepIdx", trialId!!))
        }

        // Keep the pill visible long enough to read and react before dispatch.
        // On Android, performAction then waits on the native intervention gate,
        // so a C3 touch during this interval cannot race the real-world action.
        val narrationWindowMs = StudyNarrationPacing.beforeActionMs(step)
        if (narrationWindowMs > 0) {
            kotlinx.coroutines.delay(narrationWindowMs)
        }

        // Check for pending correction just before backend action (mid-step interrupt)
        if (correctionClassifier != null && correctionSource != null) {
            val correction = correctionSource.takeCorrection()
            if (correction != null) {
                deferredCorrection = correction
                throw CorrectionInterrupted()
            }
        }

        val actionResult = performAction(resolved)
        if (actionResult == null) {
            return ExecutionResult(
                success = false, step = step,
                error = "Action could not be performed",
                errorVariantId = resolved.errorVariantId,
                errorInjected = resolved.errorInjected,
            )
        }

        val (actionType, actionDesc, resolvedIndex) = actionResult
        val elapsedMs = (System.nanoTime() - stepStartNs) / 1_000_000.0
        logger.stepFinish(step.id, trialId!!, actionDesc)

        return ExecutionResult(
            success = true, step = step,
            action = actionType,
            resolvedIndex = resolvedIndex,
            elapsedMs = elapsedMs,
            errorVariantId = resolved.errorVariantId,
            errorInjected = resolved.errorInjected,
        )
    }

    private fun performAction(resolved: ResolvedStep): Triple<String, String, Int?>? {
        val parsed = try {
            ActionParser.parse(resolved.effectiveAction)
        } catch (e: IllegalArgumentException) {
            logger.technicalFailure(e.message ?: "parse error")
            return null
        }
        val desc = parsed.descriptor

        try {
            when (parsed.type) {
                ActionParser.ActionType.OPEN_APP -> {
                    val result = backend.openApp(desc)
                    if (result["success"] == false) {
                        logger.technicalFailure(result["error"]?.toString() ?: "open_app returned success=False")
                        return null
                    }
                    if (!waitForAppReady(desc)) return null
                    return Triple("open_app", "App öffnen ($desc)", null)
                }

                ActionParser.ActionType.OPEN_URL -> {
                    val result = backend.openUrl(desc)
                    if (result["success"] == false) {
                        logger.technicalFailure(result["error"]?.toString() ?: "open_url returned success=False")
                        return null
                    }
                    val route = StudyTextHelpers.googleMapsRoute(resolved.effectiveAction)
                    if (route != null) {
                        variables["route_origin"] = route.first
                        variables["route_destination"] = route.second
                    }
                    if (resolved.source.readyPackage != null && !waitForAppReady(resolved.source.readyPackage)) {
                        return null
                    }
                    return Triple("open_url", "URL öffnen ($desc)", null)
                }

                ActionParser.ActionType.TAP -> {
                    val elements = backend.listElements()
                    val el = StudyTextHelpers.findElement(elements, desc) ?: return null
                    val index = (el["index"] as? Number)?.toInt() ?: return null
                    val result = backend.tapElement(index)
                    if (result["success"] == false) {
                        logger.technicalFailure(result["error"]?.toString() ?: "tap_element returned success=False")
                        return null
                    }
                    return Triple("tap", "Auf '$desc' tippen (index=$index)", index)
                }

                ActionParser.ActionType.TAP_FIRST -> {
                    val elements = backend.listElements()
                    val el = StudyTextHelpers.findFirstElement(elements, desc) ?: return null
                    val index = (el["index"] as? Number)?.toInt() ?: return null
                    val result = backend.tapElement(index)
                    if (result["success"] == false) {
                        logger.technicalFailure(result["error"]?.toString() ?: "tap_element returned success=False")
                        return null
                    }
                    return Triple("tap", "Auf erstes '$desc' tippen (index=$index)", index)
                }

                ActionParser.ActionType.TYPE -> {
                    val result = backend.typeText(desc, submit = parsed.submit)
                    if (result["success"] == false) {
                        logger.technicalFailure(result["error"]?.toString() ?: "type_text returned success=False")
                        return null
                    }
                    return Triple("type", "Text eingeben: '$desc'", null)
                }

                ActionParser.ActionType.REPLACE -> {
                    val result = backend.replaceText(desc, submit = parsed.submit)
                    if (result["success"] == false) {
                        logger.technicalFailure(result["error"]?.toString() ?: "replace_text returned success=False")
                        return null
                    }
                    return Triple("replace", "Text ersetzen: '$desc'", null)
                }

                ActionParser.ActionType.CAPTURE_TRANSIT_ARRIVAL -> {
                    if (!rememberTransitArrival(desc)) return null
                    return Triple("capture_transit_arrival", "Ankunftszeit merken ($desc)", null)
                }

                ActionParser.ActionType.CAPTURE_TRANSIT_DURATION -> {
                    if (!rememberTransitDuration(desc)) return null
                    return Triple("capture_transit_duration", "Reisedauer merken ($desc)", null)
                }

                ActionParser.ActionType.CAPTURE_SONG_RECOMMENDATION -> {
                    if (!rememberSongRecommendation(desc)) return null
                    return Triple("capture_song_recommendation", "Liedempfehlung merken ($desc)", null)
                }

                ActionParser.ActionType.SCROLL -> {
                    val result = backend.scroll(desc)
                    if (result["success"] == false) {
                        logger.technicalFailure(result["error"]?.toString() ?: "scroll($desc) returned success=False")
                        return null
                    }
                    return Triple("scroll", "Nach $desc scrollen", null)
                }

                ActionParser.ActionType.PRESS -> {
                    val dismissInputMethod =
                        desc.equals("BACK", ignoreCase = true) &&
                            isKeyboardDismissStep(resolved.source)
                    val result = if (dismissInputMethod) {
                        backend.dismissInputMethod()
                    } else {
                        backend.pressButton(desc)
                    }
                    if (result["success"] == false) {
                        logger.technicalFailure(result["error"]?.toString() ?: "press($desc) returned success=False")
                        return null
                    }
                    val description = if (dismissInputMethod) "Tastatur schließen" else "Taste $desc"
                    return Triple("press", description, null)
                }
            }
        } catch (e: CorrectionInterrupted) {
            throw e
        } catch (e: Exception) {
            logger.technicalFailure(e.message ?: e.toString())
            return null
        }
        return null
    }

    private fun isKeyboardDismissStep(step: StudyStep): Boolean =
        step.narration.contains("Tastatur", ignoreCase = true) ||
            step.confirmationText?.contains("Tastatur", ignoreCase = true) == true

    // ── Variable capture helpers ──

    private fun rememberTransitArrival(name: String): Boolean {
        val elements = backend.listElements()
        val pattern = Regex("\\b\\d{1,2}:\\d{2}(?:\\s*\\([^)]{1,12}\\))?\\s*[–-]\\s*(\\d{1,2}:\\d{2})\\b")
        for (el in elements) {
            for (field in listOf("text", "content_description")) {
                val value = (el[field] as? String).orEmpty()
                val match = pattern.find(value) ?: continue
                val arrival = match.groupValues[1]
                variables[name] = arrival
                variables["${name}_minus_10"] = StudyTextHelpers.shiftTimeMinutes(arrival, -10)
                variables["${name}_plus_10"] = StudyTextHelpers.shiftTimeMinutes(arrival, 10)
                return true
            }
        }
        return false
    }

    private fun rememberTransitDuration(name: String): Boolean {
        val elements = backend.listElements()
        val hoursMinutes = Regex(
            "\\b(\\d{1,2})\\s*(?:h|std\\.?|stunden?)\\s*(\\d{1,2})\\s*(?:min\\.?|minuten?)\\b",
            RegexOption.IGNORE_CASE,
        )
        val minutesOnly = Regex("\\b(\\d{1,3})\\s*(?:min\\.?|minuten?)\\b", RegexOption.IGNORE_CASE)

        data class Candidate(val duration: String, val position: Pair<Int, Int>?, val isTransit: Boolean)
        val candidates = mutableListOf<Candidate>()
        val seen = mutableSetOf<Pair<Any, String>>()

        for (el in elements) {
            for (field in listOf("text", "content_description")) {
                val value = (el[field] as? String).orEmpty().replace("\u00a0", " ")
                val hmMatch = hoursMinutes.find(value)
                val duration: String
                if (hmMatch != null) {
                    duration = "${hmMatch.groupValues[1].toInt()} Std. ${hmMatch.groupValues[2].toInt()} Min."
                } else {
                    val mmMatch = minutesOnly.find(value) ?: continue
                    duration = "${mmMatch.groupValues[1].toInt()} Min."
                }
                @Suppress("UNCHECKED_CAST")
                val index = el["index"] ?: el.hashCode()
                val identity = index to duration
                if (identity in seen) continue
                seen.add(identity)

                val bounds = el["bounds"]
                var position: Pair<Int, Int>? = null
                if (bounds is Map<*, *>) {
                    val top = bounds["top"]
                    val left = bounds["left"]
                    if (top is Int && left is Int) position = top to left
                }
                val isTransit = value.lowercase().let { lc ->
                    "öffentlichen verkehrsmitteln" in lc || "oeffentlichen verkehrsmitteln" in lc
                }
                candidates.add(Candidate(duration, position, isTransit))
            }
        }
        if (candidates.isEmpty()) return false
        val transitCandidates = candidates.filter { it.isTransit }
        val selected = (transitCandidates.ifEmpty { candidates })
            .let { list ->
                if (list.all { it.position != null }) {
                    list.sortedWith(compareBy({ it.position!!.first }, { it.position!!.second }))
                } else {
                    list
                }
            }
        variables[name] = selected[0].duration
        return true
    }

    private fun rememberSongRecommendation(name: String): Boolean {
        val elements = backend.listElements()
        val pattern = Regex("^Song-Tipp:\\s*(.+?)\\s+von\\s+[^.]+\\.$", RegexOption.IGNORE_CASE)
        val matches = mutableListOf<String>()
        for (el in elements) {
            for (field in listOf("text", "content_description")) {
                val value = (el[field] as? String).orEmpty().trim().replace(Regex("\\s+"), " ")
                val match = pattern.matchEntire(value) ?: continue
                matches.add(match.groupValues[1].trim())
            }
        }
        val unique = matches.distinct()
        if (unique.size != 1) return false
        variables[name] = unique[0]
        return true
    }

    // ── App readiness ──

    private fun waitForAppReady(appTarget: String): Boolean {
        val pkg = targetPackage(appTarget)
        val deadlineNs = System.nanoTime() + (APP_READY_TIMEOUT_S * 1_000_000_000).toLong()
        var previousSignature: String? = null
        var stableSamples = 0

        while (true) {
            if (System.nanoTime() >= deadlineNs) {
                logger.technicalFailure("Timed out waiting for app readiness: $pkg")
                return false
            }
            val signature = try {
                uiSignature(backend.listElements(), pkg)
            } catch (e: Exception) {
                logger.technicalFailure("App readiness check failed for $pkg: $e")
                return false
            }
            if (signature.isNotEmpty()) {
                stableSamples = if (signature == previousSignature) stableSamples + 1 else 1
                previousSignature = signature
                if (stableSamples >= APP_READY_STABLE_SAMPLES) return true
            } else {
                previousSignature = null
                stableSamples = 0
            }
            val remaining = deadlineNs - System.nanoTime()
            if (remaining <= 0) {
                logger.technicalFailure("Timed out waiting for app readiness: $pkg")
                return false
            }
            val sleepMs = minOf(APP_READY_POLL_MS, remaining / 1_000_000)
            try {
                Thread.sleep(sleepMs)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
    }

    private fun uiSignature(elements: List<Map<String, Any?>>, targetPackage: String): String {
        val fields = listOf("package", "resource_id", "text", "content_description", "label", "description", "class", "bounds")
        val serialized = elements
            .filter { it["package"] == targetPackage }
            .map { el -> fields.joinToString("\u0001") { (el[it] ?: "").toString() } }
            .sorted()
        return serialized.joinToString("\n")
    }

    // ── Misc helpers ──

    private fun expandVariables(text: String): String = StudyTextHelpers.expandVariables(text, variables)

    private fun countErrorsInjected(): Int =
        stepsExecuted.count { it.errorInjected && it.success }

    companion object {
        private const val APP_READY_TIMEOUT_S = 8.0
        private const val APP_READY_POLL_MS = 120L
        private const val APP_READY_STABLE_SAMPLES = 2
    }
}
