package com.caddie.study.runtime.model

/**
 * Portal session + observation models ported from caddie.study.portal.models.
 */

/** A live/paper portal session. */
data class PortalSession(
    val id: String,
    val participantId: String,
    val mode: PortalMode,
    val source: RecordSource,
    val originalStudyAt: String?,
    val originalTimePrecision: String?,
    val enteredAt: String,
    val status: String,
    val workflowState: WorkflowState,
    val workflowRevision: Int,
    val resumeState: WorkflowState?,
    val currentTrialIndex: Int?,
    val assignmentJson: String,
    val assignmentHash: String,
)

/** Error observation recorded by the investigator after a trial. */
data class ErrorObservation(
    val spontaneousDetection: Boolean,
    val detectionStage: DetectionStage,
    val evidenceType: EvidenceType,
    val moderatorPromptGiven: Boolean,
    val detectedOnlyAfterPrompt: Boolean,
    val notes: String = "",
)

/** Enforce consistency between error detection, prompting, and evidence. */
fun validateErrorObservation(value: ErrorObservation): ErrorObservation {
    val stage = value.detectionStage
    val evidence = value.evidenceType
    if (value.spontaneousDetection && stage in setOf(
            DetectionStage.AFTER_MODERATOR_PROMPT,
            DetectionStage.NOT_DETECTED,
        )
    ) {
        require(false) { "spontaneous_detection conflicts with detection_stage" }
    }
    if (stage != DetectionStage.NOT_DETECTED && evidence == EvidenceType.NONE) {
        require(false) { "detection_stage other than not_detected requires evidence_type other than none" }
    }
    if (stage in setOf(
            DetectionStage.STEPWISE_GATE,
            DetectionStage.FINAL_CHECKPOINT,
            DetectionStage.DURING_EXECUTION,
            DetectionStage.AFTER_EXECUTION_BEFORE_PROMPT,
        ) && !value.spontaneousDetection
    ) {
        require(false) { "pre-prompt detection_stage requires spontaneous_detection=true" }
    }
    if (stage == DetectionStage.AFTER_MODERATOR_PROMPT) {
        require(value.moderatorPromptGiven) { "after_moderator_prompt requires moderatorPromptGiven=true" }
        require(value.detectedOnlyAfterPrompt) { "after_moderator_prompt requires detectedOnlyAfterPrompt=true" }
    }
    if (value.spontaneousDetection && evidence == EvidenceType.NONE) {
        require(false) { "spontaneous_detection=true conflicts with evidence_type=none" }
    }
    if (value.detectedOnlyAfterPrompt && (!value.moderatorPromptGiven || stage != DetectionStage.AFTER_MODERATOR_PROMPT)) {
        require(false) { "detected_only_after_prompt=true requires moderatorPromptGiven and after_moderator_prompt" }
    }
    if (stage == DetectionStage.NOT_DETECTED && evidence != EvidenceType.NONE) {
        require(false) { "detection_stage=not_detected requires evidence_type=none" }
    }
    if (stage == DetectionStage.NOT_DETECTED && !value.moderatorPromptGiven) {
        require(false) { "detection_stage=not_detected requires moderatorPromptGiven=true" }
    }
    return value
}
