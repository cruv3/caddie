package com.caddie.study.runtime.model

// ── Enums ported from caddie.study.model + caddie.study.portal.models ──
// All wireValue strings match the Python StrEnum values exactly so the
// on-device JSON contract stays identical to the V1 portal.

/** Oversight condition for a trial. Re-exported here for the runtime layer. */
enum class RuntimeStudyCondition(val wireValue: String) {
    STEPWISE("c1_stepwise"),
    FINAL_CHECKPOINT("c2_final_checkpoint"),
    VOLUNTARY_INTERVENTION("c3_voluntary_intervention");

    companion object {
        fun fromWire(value: String?): RuntimeStudyCondition =
            entries.firstOrNull { it.wireValue == value }
                ?: error("unknown study condition: $value")
    }
}

/** Classification of a deterministic study step. */
enum class StepType(val wireValue: String) {
    NORMAL("normal"),
    CONSEQUENTIAL("consequential"),
    COMMIT("commit");

    companion object {
        fun fromWire(value: String?): StepType =
            entries.firstOrNull { it.wireValue == value }
                ?: error("unknown step_type: $value")
    }
}

/** Task criticality for counterbalancing and error injection. */
enum class CriticalityClass(val wireValue: String) {
    LOW("low"),
    HIGH("high");

    companion object {
        fun fromWire(value: String?): CriticalityClass =
            entries.firstOrNull { it.wireValue == value }
                ?: error("unknown criticality: $value")
    }
}

/** Initiation policy for the screen-off block. */
enum class ScreenOffMode(val wireValue: String) {
    NOTIFY_ONLY("notify_only"),
    WAKE_ASK("wake_ask"),
    WAKE_EXECUTE("wake_execute");

    companion object {
        fun fromWire(value: String?): ScreenOffMode =
            entries.firstOrNull { it.wireValue == value }
                ?: error("unknown screen_off mode: $value")
    }
}

/** Outcome of a screen-off initiation attempt. */
enum class InitiationResult(val wireValue: String) {
    SUCCESS("success"),
    WAKE_FAILED("wake_failed"),
    NO_RESPONSE("no_response");
}

/** Terminal state of a trial or micro-trial. */
enum class TrialOutcome(val wireValue: String) {
    SUCCESS("success"),
    ERROR_INJECTED("error_injected"),
    VERIFICATION_FAILED("verification_failed"),
    CONFIRMATION_TIMEOUT("confirmation_timeout"),
    ABORTED("aborted"),
    TECHNICAL_FAILURE("technical_failure"),
    PARTICIPANT_STOP("participant_stop"),
    REPEATED("repeated");
}

/** How a known error may be corrected after the trial commit. */
enum class PostCommitPolicy(val wireValue: String) {
    COMPENSATE("compensate"),
    REJECT_IRREVERSIBLE("reject_irreversible");

    companion object {
        fun fromWire(value: String?): PostCommitPolicy =
            entries.firstOrNull { it.wireValue == value }
                ?: error("unknown post-commit policy: $value")
    }
}

/** Point in the trial lifecycle at which a correction was received. */
enum class CorrectionPhase(val wireValue: String) {
    RUNNING("running"),
    RECENTLY_COMPLETED("recently_completed");
}

/** Deterministic route selected from journal state and policy. */
enum class CorrectionRoute(val wireValue: String) {
    REWIND("rewind"),
    COMPENSATE("compensate"),
    ALREADY_CORRECT("already_correct"),
    REJECT_IRREVERSIBLE("reject_irreversible");
}

// ── Portal domain enums (caddie.study.portal.models) ──

enum class PortalMode(val wireValue: String) {
    LIVE("live"),
    PAPER_TRANSCRIPTION("paper_transcription");

    companion object {
        fun fromWire(value: String?): PortalMode =
            entries.firstOrNull { it.wireValue == value }
                ?: error("unknown portal mode: $value")
    }
}

enum class RecordSource(val wireValue: String) {
    LIVE_DIGITAL("live_digital"),
    PAPER_TRANSCRIPTION("paper_transcription");
}

enum class MissingReason(val wireValue: String) {
    NOT_ANSWERED("not_answered"),
    ILLEGIBLE("illegible"),
    PAPER_MISSING("paper_missing");
}

enum class DetectionStage(val wireValue: String) {
    STEPWISE_GATE("stepwise_gate"),
    FINAL_CHECKPOINT("final_checkpoint"),
    DURING_EXECUTION("during_execution"),
    AFTER_EXECUTION_BEFORE_PROMPT("after_execution_before_prompt"),
    AFTER_MODERATOR_PROMPT("after_moderator_prompt"),
    NOT_DETECTED("not_detected");

    companion object {
        fun fromWire(value: String?): DetectionStage =
            entries.firstOrNull { it.wireValue == value }
                ?: error("unknown detection stage: $value")
    }
}

enum class EvidenceType(val wireValue: String) {
    SPOKEN_IDENTIFICATION("spoken_identification"),
    REJECTION("rejection"),
    CORRECTION("correction"),
    INTERVENTION("intervention"),
    NONE("none"),
    OTHER("other");

    companion object {
        fun fromWire(value: String?): EvidenceType =
            entries.firstOrNull { it.wireValue == value }
                ?: error("unknown evidence type: $value")
    }
}

/** Portal workflow state machine states. */
enum class WorkflowState(val wireValue: String) {
    SETUP("setup"),
    CONSENT("consent"),
    TRAINING("training"),
    TRIAL_READY("trial_ready"),
    TASK_CARD("task_card"),
    TRIAL_RUNNING("trial_running"),
    INVESTIGATOR_OBSERVATION("investigator_observation"),
    TASK_QUESTIONNAIRE("task_questionnaire"),
    INVESTIGATOR_RESULT_REVIEW("investigator_result_review"),
    TRIAL_RESET("trial_reset"),
    BLOCK_QUESTIONNAIRE("block_questionnaire"),
    DEMOGRAPHICS("demographics"),
    PREFERENCE_RANKING("preference_ranking"),
    INTERVIEW("interview"),
    DEBRIEF("debrief"),
    COMPLETED("completed"),
    TECHNICAL_HOLD("technical_hold"),
    ABORTED("aborted");

    companion object {
        fun fromWire(value: String?): WorkflowState =
            entries.firstOrNull { it.wireValue == value }
                ?: error("unknown workflow state: $value")
    }
}
