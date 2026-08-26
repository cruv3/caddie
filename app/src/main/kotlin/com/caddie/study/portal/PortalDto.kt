package com.caddie.study.portal

import com.caddie.study.store.entity.StudySessionEntity
import com.caddie.study.runtime.model.DetectionStage
import com.caddie.study.runtime.model.EvidenceType
import com.caddie.study.runtime.model.ErrorObservation
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

@Serializable
data class CreateSessionRequest(
    val participant_id: String = "",
    val mode: String = "live",
)

@Serializable
data class StudyTestTaskDto(
    val id: String,
    val instruction: String,
    val criticality: String,
)

@Serializable
data class StudyTestOptionsResponse(
    val ok: Boolean = true,
    val tasks: List<StudyTestTaskDto>,
    val conditions: List<String>,
)

@Serializable
data class StartStudyTestRequest(
    val task_id: String = "",
    val condition: String = "",
    val inject_error: Boolean = false,
)

@Serializable
data class StudyTestRunResponse(
    val ok: Boolean = true,
    val session_id: Long,
    val participant_id: String,
    val instruction: String,
    val condition: String,
    val inject_error: Boolean,
    val runtime: JsonObject,
)

@Serializable
data class TransitionRequest(
    val event: String = "",
    val expected_revision: Int = -1,
)

@Serializable
data class ObservationRequest(
    val expected_revision: Int = -1,
    val observation: ErrorObservationDto? = null,
)

@Serializable
data class ErrorObservationDto(
    val spontaneous_detection: Boolean = false,
    val detection_stage: String = "",
    val evidence_type: String = "",
    val moderator_prompt_given: Boolean = false,
    val detected_only_after_prompt: Boolean = false,
    val notes: String = "",
) {
    fun toModel(): ErrorObservation = ErrorObservation(
        spontaneousDetection = spontaneous_detection,
        detectionStage = DetectionStage.fromWire(detection_stage),
        evidenceType = EvidenceType.fromWire(evidence_type),
        moderatorPromptGiven = moderator_prompt_given,
        detectedOnlyAfterPrompt = detected_only_after_prompt,
        notes = notes,
    )
}

@Serializable
data class InterviewRequest(
    val expected_revision: Int = -1,
    val answers: Map<String, String> = emptyMap(),
)

@Serializable
data class ConsentRequest(
    val full_name: String = "",
    val acknowledgements: List<String> = emptyList(),
)

@Serializable
data class CourseBonusRequest(
    val full_name: String = "",
    val matriculation_number: String = "",
    val consented_at: String = "",
)

@Serializable
data class CourseBonusRecordedResponse(
    val ok: Boolean = true,
    val id: Long,
)

@Serializable
data class CourseBonusSummary(
    val id: Long,
    val full_name: String,
    val matriculation_number: String,
    val consented_at: String,
)

@Serializable
data class CourseBonusListResponse(
    val ok: Boolean = true,
    val records: List<CourseBonusSummary>,
)

@Serializable
data class StudyBackup(val directory: String)

@Serializable
data class StudyBackupResponse(
    val ok: Boolean = true,
    val backup: StudyBackup,
)

@Serializable
data class ResponseSubmitRequest(
    val answers: JsonObject = JsonObject(emptyMap()),
    val expected_response_revision: Int = 0,
    val expected_workflow_revision: Int = -1,
)

@Serializable
data class InvestigatorResponseCorrectionRequest(
    val answers: JsonObject = JsonObject(emptyMap()),
    val missing: JsonObject = JsonObject(emptyMap()),
    val expected_revision: Int = -1,
)

@Serializable
data class ParticipantConsentRequest(
    val full_name: String = "",
    val acknowledgements: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class ParticipantDraftRequest(
    val answers: JsonObject = JsonObject(emptyMap()),
    val expected_revision: Int = 0,
)

@Serializable
data class ParticipantSubmitRequest(
    val answers: JsonObject = JsonObject(emptyMap()),
    val expected_revision: Int = 0,
    val expected_workflow_revision: Int = -1,
)

@Serializable
data class ActionRequest(
    val confirmed: Boolean? = null,
    val expected_revision: Int? = null,
    val reason: String? = null,
)

@Serializable
data class ErrorResponse(
    val ok: Boolean = false,
    val error: String = "unknown_error",
)

@Serializable
data class SuccessSessionResponse(
    val ok: Boolean = true,
    val session: SessionSummary,
)

@Serializable
data class SessionSummary(
    val id: Long,
    val participant_id: String,
    val mode: String,
    val source: String,
    val original_study_at: String?,
    val original_time_precision: String?,
    val entered_at: String,
    val status: String,
    val workflow_state: String,
    val workflow_revision: Int,
    val resume_state: String?,
    val current_trial_index: Int?,
    val assignment: JsonObject,
    val assignment_hash: String,
    val debrief_handed_off: Boolean = false,
)

fun toDto(session: StudySessionEntity): SessionSummary =
    SessionSummary(
        id = session.id,
        participant_id = session.participantId,
        mode = session.mode,
        source = session.source,
        original_study_at = session.originalStudyAt,
        original_time_precision = session.originalTimePrecision,
        entered_at = session.enteredAt,
        status = session.status,
        workflow_state = session.workflowState,
        workflow_revision = session.workflowRevision,
        resume_state = session.resumeState,
        current_trial_index = session.currentTrialIndex,
        assignment = Json.parseToJsonElement(session.assignmentJson).jsonObject,
        assignment_hash = session.assignmentHash,
    )

@Serializable
data class ParticipantSessionSummary(
    val id: Long,
    val participant_id: String,
    val workflow_state: String,
    val workflow_revision: Int,
    val current_trial_index: Int?,
    val resume_state: String? = null,
)

@Serializable
data class ParticipantStateResponse(
    val ok: Boolean = true,
    val session: ParticipantSessionSummary,
    val task: JsonObject? = null,
    val instrument: InstrumentDto? = null,
    val draft: DraftDto? = null,
    val error_variants: List<Map<String, String>>? = null,
    val interview_guide: List<StudyInterviewQuestion>? = null,
)

@Serializable
data class ParticipantContinueRequest(
    val expected_revision: Int = -1,
)

@Serializable
data class InstrumentDto(
    val id: String,
    val version: String = INSTRUMENT_VERSION,
    val title: String,
    val items: List<InstrumentItemDto>,
)

@Serializable
data class InstrumentItemDto(
    val id: String,
    val prompt: String,
    val response_type: String,
    val response_anchors: List<String>,
    val minimum: Int?,
    val maximum: Int?,
    val step: Int?,
    val required: Boolean,
    val section: String,
)

@Serializable
data class DraftDto(
    val answers: JsonObject,
    val missing: JsonObject,
    val revision: Int,
)

fun instrumentDto(
    instrument: Instrument,
    title: String,
    itemIds: Set<String>,
): InstrumentDto = InstrumentDto(
    id = instrument.id,
    title = title,
    items = instrument.items.filter { it.id in itemIds }.map { item ->
        InstrumentItemDto(
            id = item.id,
            prompt = item.prompt,
            response_type = when (item.type) {
                InstrumentType.AGREEMENT_SCALE, InstrumentType.RATING_SCALE -> "scale"
                InstrumentType.SINGLE_CHOICE -> "single_choice"
                InstrumentType.RANKING -> "ranking"
                InstrumentType.TEXT -> "free_text"
                InstrumentType.INTEGER -> "integer"
            },
            response_anchors = item.options,
            minimum = item.min,
            maximum = item.max,
            step = item.step,
            required = item.required,
            section = item.section,
        )
    },
)
