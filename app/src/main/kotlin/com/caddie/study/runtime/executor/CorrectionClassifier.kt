package com.caddie.study.runtime.executor

import com.caddie.study.runtime.model.CorrectionJournal
import com.caddie.study.runtime.model.CorrectionPhase
import com.caddie.study.runtime.model.ErrorVariant

/**
 * Context passed to [CorrectionClassifier] to decide whether a participant's
 * spoken correction maps to a known injected error variant.
 *
 * Ported from `caddie.study.correction_classifier.CorrectionContext`.
 */
data class CorrectionContext(
    val instruction: String,
    val taskId: String,
    val correction: String,
    val executedStepIds: List<String>,
    val nextStepId: String?,
    val activeVariants: List<ErrorVariant>,
    val phase: CorrectionPhase = CorrectionPhase.RUNNING,
    val journalEntries: List<com.caddie.study.runtime.model.JournalEntry> = emptyList(),
    val committed: Boolean = false,
    val compensationComplete: Boolean = false,
)

/**
 * Result of classifying a participant correction.
 *
 * Ported from `caddie.study.correction_classifier.CorrectionDecision`.
 * Fail-closed: [accepted] is true only when the correction unambiguously
 * targets a known error variant with high confidence.
 *
 * @param accepted Whether the correction was accepted.
 * @param errorVariantId The variant ID to correct, or null if rejected.
 * @param reason Machine-readable rejection reason
 *   (not_correction, insufficient_confidence, unknown_variant, client_error, etc.).
 */
data class CorrectionDecision(
    val accepted: Boolean,
    val errorVariantId: String? = null,
    val reason: String = "rejected",
)

/**
 * Classifies participant corrections against known injected error variants.
 *
 * Implementations must be fail-closed. The Android study runtime maps an
 * explicit correction only when exactly one injected variant is active.
 *
 * Ported from `caddie.study.correction_classifier.StudyCorrectionClassifier`
 * (interface extracted for testability).
 */
fun interface CorrectionClassifier {
    fun classify(context: CorrectionContext): CorrectionDecision
}

/**
 * Source of participant corrections, abstracting the study session.
 *
 * The executor calls [takeCorrection] at safe boundaries (non-blocking) and
 * [waitForCorrection] when a gate has been declined and the participant may
 * still be speaking (blocking with timeout). [pauseForUnclearCorrection]
 * keeps the session paused when a correction cannot be classified.
 *
 * Ported from the subset of `caddie.study.session.StudySession` that the
 * executor calls for correction flow.
 */
interface CorrectionSource {

    /** Non-blocking: return and clear the pending correction, or null if none. */
    fun takeCorrection(): String?

    /** Blocking: wait up to [timeoutS] seconds for a correction still being transcribed. */
    suspend fun waitForCorrection(timeoutS: Double): String?

    /** Pause the session when a correction cannot be classified safely. */
    fun pauseForUnclearCorrection()
}

/**
 * Result of a post-trial correction attempt on a recently completed trial.
 *
 * Ported from `caddie.study.recent_correction.RecentCorrectionResult`.
 *
 * @param consumed Whether the text was consumed (matched a correction intent).
 * @param accepted Whether the correction was successfully applied.
 * @param outcome Machine-readable outcome string
 *   (corrected, already_correct, correction_rejected_irreversible,
 *   correction_failed, correction_requires_active_trial, unknown_variant,
 *   not_correction).
 * @param message Participant-facing German message.
 */
data class RecentCorrectionResult(
    val consumed: Boolean,
    val accepted: Boolean,
    val outcome: String,
    val message: String,
)

/** Internal control-flow signal: a correction arrived mid-step. */
internal class CorrectionInterrupted : RuntimeException() {
    override fun fillInStackTrace(): Throwable = this
}

/** Companion constant matching Python `_CORRECTION_TEXT_WAIT_S`. */
internal const val CORRECTION_TEXT_WAIT_S: Double = 8.0
