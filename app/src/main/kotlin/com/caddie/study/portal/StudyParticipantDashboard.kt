package com.caddie.study.portal

import com.caddie.study.runtime.matrix.StudyMatrix
import com.caddie.study.runtime.model.ParticipantId
import com.caddie.study.store.entity.StudyAuditEntity
import com.caddie.study.store.entity.StudyInterviewNoteEntity
import com.caddie.study.store.entity.StudyObservationEntity
import com.caddie.study.store.entity.StudyResponseEntity
import com.caddie.study.store.entity.StudySessionEntity
import com.caddie.study.store.entity.StudyTrialMarkerEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** One live participant or isolated rehearsal row shown on the study dashboard. */
@Serializable
data class StudyParticipantSummary(
    val participant_id: String,
    val session_id: Long? = null,
    val workflow_state: String = "not_started",
    val outcome: String = "not_started",
    val completed_trials: Int = 0,
    val total_trials: Int = StudyMatrix.NUM_TASKS_PER_PARTICIPANT,
    val last_activity: String? = null,
    val has_problem: Boolean = false,
    val test_data: Boolean = false,
)

/** Passwordless investigator landing-page data with strict live/test separation. */
@Serializable
data class StudyParticipantCatalogResponse(
    val ok: Boolean = true,
    val participants: List<StudyParticipantSummary> = emptyList(),
    val test_runs: List<StudyParticipantSummary> = emptyList(),
    val available_participant_ids: List<String> = emptyList(),
)

@Serializable
data class StudyParticipantDetailResponse(
    val ok: Boolean = true,
    val participant_id: String,
    val sessions: List<StudySessionDetail> = emptyList(),
)

@Serializable
data class StudySessionDetail(
    val session: SessionSummary,
    val responses: List<StudyStoredResponse> = emptyList(),
    val observations: List<StudyStoredObservation> = emptyList(),
    val trials: List<StudyStoredTrial> = emptyList(),
    val interviews: List<StudyStoredInterview> = emptyList(),
    val events: List<StudyStoredEvent> = emptyList(),
)

@Serializable
data class StudyStoredResponse(
    val instrument_id: String,
    val position: Int,
    val answers: JsonObject,
    val missing: JsonObject,
    val submitted_at: String,
    val revision: Int,
)

@Serializable
data class StudyStoredObservation(
    val trial_index: Int,
    val task_id: String,
    val condition: String,
    val error_variant_id: String? = null,
    val spontaneous_detection: Boolean,
    val detection_stage: String,
    val evidence_type: String,
    val notes: String,
    val submitted_at: String,
)

@Serializable
data class StudyStoredTrial(
    val trial_index: Int,
    val assigned_error: Boolean,
    val outcome: String,
    val submitted_at: String,
)

@Serializable
data class StudyStoredInterview(
    val question_id: String,
    val answer: String,
    val submitted_at: String,
)

@Serializable
data class StudyStoredEvent(
    val id: Long,
    val actor: String,
    val event_type: String,
    val details: JsonObject,
    val created_at: String,
)

/** Builds the small dashboard read model without mutating study storage. */
object StudyParticipantDashboard {
    private val json = Json { ignoreUnknownKeys = true }
    private val problemEvents = setOf(
        "technical_failure",
        "trial_failed",
        "verification_failed",
        "run_aborted",
    )

    fun catalog(
        sessions: List<StudySessionEntity>,
        markers: List<StudyTrialMarkerEntity>,
        audits: List<StudyAuditEntity>,
    ): StudyParticipantCatalogResponse {
        val sessionsById = sessions.associateBy { it.id }
        val markersByParticipant = markers.groupBy { marker ->
            sessionsById[marker.sessionId]?.participantId.orEmpty()
        }
        val auditsByParticipant = audits.groupBy { audit ->
            audit.sessionId?.let(sessionsById::get)?.participantId.orEmpty()
        }
        val liveSessions = sessions
            .filter { it.mode == "live" && it.source == "live_digital" }
            .groupBy { it.participantId }

        val participantIds = liveSessions.keys
            .mapNotNull { id -> ParticipantId.number(id)?.let { number -> number to id } }
            .sortedBy { it.first }
        val participants = participantIds.map { (_, participantId) ->
            summary(
                participantId = participantId,
                sessions = liveSessions[participantId].orEmpty(),
                markers = markersByParticipant[participantId].orEmpty(),
                audits = auditsByParticipant[participantId].orEmpty(),
                testData = false,
            )
        }
        val testRuns = sessions
            .filter { it.mode == "test" || it.source.startsWith("test_") }
            .sortedByDescending { it.id }
            .map { session ->
                summary(
                    participantId = session.participantId,
                    sessions = listOf(session),
                    markers = markers.filter { it.sessionId == session.id },
                    audits = audits.filter { it.sessionId == session.id },
                    testData = true,
                )
            }

        return StudyParticipantCatalogResponse(
            participants = participants,
            test_runs = testRuns,
            available_participant_ids = listOf(
                ParticipantId.format((participantIds.maxOfOrNull { it.first } ?: 0) + 1),
            ),
        )
    }

    fun detail(
        participantId: String,
        sessions: List<StudySessionEntity>,
        responses: List<StudyResponseEntity>,
        observations: List<StudyObservationEntity>,
        markers: List<StudyTrialMarkerEntity>,
        interviews: List<StudyInterviewNoteEntity>,
        audits: List<StudyAuditEntity>,
    ): StudyParticipantDetailResponse {
        val matching = sessions
            .filter { it.participantId == participantId && it.mode == "live" && it.source == "live_digital" }
            .sortedByDescending { it.id }
        return assembleDetail(
            participantId,
            matching,
            responses,
            observations,
            markers,
            interviews,
            audits,
        )
    }

    fun sessionDetail(
        sessionId: Long,
        sessions: List<StudySessionEntity>,
        responses: List<StudyResponseEntity>,
        observations: List<StudyObservationEntity>,
        markers: List<StudyTrialMarkerEntity>,
        interviews: List<StudyInterviewNoteEntity>,
        audits: List<StudyAuditEntity>,
    ): StudyParticipantDetailResponse {
        val matching = sessions.filter { it.id == sessionId }
        val participantId = matching.singleOrNull()?.participantId
            ?: throw IllegalArgumentException("session not found")
        return assembleDetail(
            participantId,
            matching,
            responses,
            observations,
            markers,
            interviews,
            audits,
        )
    }

    private fun assembleDetail(
        participantId: String,
        matching: List<StudySessionEntity>,
        responses: List<StudyResponseEntity>,
        observations: List<StudyObservationEntity>,
        markers: List<StudyTrialMarkerEntity>,
        interviews: List<StudyInterviewNoteEntity>,
        audits: List<StudyAuditEntity>,
    ): StudyParticipantDetailResponse {
        return StudyParticipantDetailResponse(
            participant_id = participantId,
            sessions = matching.map { session ->
                StudySessionDetail(
                    session = toDto(session),
                    responses = responses.filter { it.sessionId == session.id }.map { response ->
                        StudyStoredResponse(
                            instrument_id = response.instrumentId,
                            position = response.position,
                            answers = response.answersJson.asObject(),
                            missing = response.missingJson.asObject(),
                            submitted_at = response.submittedAt,
                            revision = response.revision,
                        )
                    },
                    observations = observations.filter { it.sessionId == session.id }.map { observation ->
                        StudyStoredObservation(
                            trial_index = observation.trialIndex,
                            task_id = observation.taskId,
                            condition = observation.condition,
                            error_variant_id = observation.errorVariantId,
                            spontaneous_detection = observation.spontaneousDetection,
                            detection_stage = observation.detectionStage,
                            evidence_type = observation.evidenceType,
                            notes = observation.notes,
                            submitted_at = observation.submittedAt,
                        )
                    },
                    trials = markers.filter { it.sessionId == session.id }.map { marker ->
                        StudyStoredTrial(
                            trial_index = marker.trialIndex,
                            assigned_error = marker.assignedError,
                            outcome = marker.outcome,
                            submitted_at = marker.submittedAt,
                        )
                    },
                    interviews = interviews.filter { it.sessionId == session.id }.map { note ->
                        StudyStoredInterview(note.questionId, note.answer, note.submittedAt)
                    },
                    events = audits.filter { it.sessionId == session.id }
                        .sortedBy { it.id }
                        .map { event ->
                            StudyStoredEvent(
                                id = event.id,
                                actor = event.actor,
                                event_type = event.eventType,
                                details = event.detailsJson.asObject(),
                                created_at = event.createdAt,
                            )
                        },
                )
            },
        )
    }

    private fun summary(
        participantId: String,
        sessions: List<StudySessionEntity>,
        markers: List<StudyTrialMarkerEntity>,
        audits: List<StudyAuditEntity>,
        testData: Boolean,
    ): StudyParticipantSummary {
        val latest = sessions.maxByOrNull { it.id }
        val runtimeCompletions = audits.mapNotNull { event ->
            if (event.eventType != "trial_completed") return@mapNotNull null
            val details = event.detailsJson.asObject()
            if (details["outcome"]?.jsonPrimitive?.content != "success") return@mapNotNull null
            details["trial_index"]?.jsonPrimitive?.content?.toIntOrNull()
        }
        val completedTrials = (markers.map { it.trialIndex } + runtimeCompletions).distinct().size
        val hasProblem = sessions.any { it.status == "aborted" } ||
            audits.any { event ->
                event.eventType in problemEvents ||
                    "fail" in event.eventType ||
                    (event.eventType == "trial_completed" &&
                        event.detailsJson.asObject()["outcome"]?.jsonPrimitive?.content !in setOf(null, "success"))
            }
        val latestActivity = buildList {
            addAll(sessions.map { it.enteredAt })
            addAll(audits.map { it.createdAt })
            addAll(markers.map { it.submittedAt })
        }.maxOrNull()
        val outcome = when {
            latest == null -> "not_started"
            latest.status == "completed" || completedTrials >= if (testData) 1 else StudyMatrix.NUM_TASKS_PER_PARTICIPANT -> "completed"
            latest.status == "aborted" -> "aborted"
            else -> "in_progress"
        }
        return StudyParticipantSummary(
            participant_id = participantId,
            session_id = latest?.id,
            workflow_state = latest?.workflowState ?: "not_started",
            outcome = outcome,
            completed_trials = completedTrials.coerceAtMost(if (testData) 1 else StudyMatrix.NUM_TASKS_PER_PARTICIPANT),
            total_trials = if (testData) 1 else StudyMatrix.NUM_TASKS_PER_PARTICIPANT,
            last_activity = latestActivity,
            has_problem = hasProblem,
            test_data = testData,
        )
    }

    private fun String.asObject(): JsonObject =
        runCatching { json.parseToJsonElement(this).jsonObject }.getOrElse { JsonObject(emptyMap()) }
}
