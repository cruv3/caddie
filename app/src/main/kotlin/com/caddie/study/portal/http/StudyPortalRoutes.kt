package com.caddie.study.portal.http

import com.caddie.study.portal.ConsentRequest
import com.caddie.study.portal.CourseBonusRequest
import com.caddie.study.portal.CourseBonusRecordedResponse
import com.caddie.study.portal.CourseBonusListResponse
import com.caddie.study.portal.StudyBackupResponse
import com.caddie.study.portal.CreateSessionRequest
import com.caddie.study.portal.InterviewRequest
import com.caddie.study.portal.InvestigatorResponseCorrectionRequest
import com.caddie.study.portal.StartStudyTestRequest
import com.caddie.study.portal.ObservationRequest
import com.caddie.study.portal.ParticipantConsentRequest
import com.caddie.study.portal.ParticipantContinueRequest
import com.caddie.study.portal.ParticipantDraftRequest
import com.caddie.study.portal.ParticipantSessionSummary
import com.caddie.study.portal.ParticipantSubmitRequest
import com.caddie.study.portal.ResponseSubmitRequest
import com.caddie.study.portal.ActionRequest
import com.caddie.study.portal.SessionSummary
import com.caddie.study.portal.StudyPortalApi
import com.caddie.study.portal.studyPreviewResponse
import com.caddie.study.portal.auth.StudyPortalAuth
import com.caddie.study.portal.SuccessSessionResponse
import com.caddie.study.portal.TransitionRequest
import com.caddie.study.portal.toDto
import com.caddie.study.serialization.JsonValueCodec
import com.caddie.study.runtime.model.ParticipantId
import io.ktor.http.HttpStatusCode
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.header
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Installs the native live-study API used by the embedded portal frontend. */
fun Routing.installStudyRoutes(
    api: StudyPortalApi,
    handoffs: StudyPortalHandoffs = StudyPortalHandoffs(),
    auth: StudyPortalAuth? = null,
) {
    get("/study/app/api/participants") {
        if (!call.requireInvestigator(auth, handoffs, mutate = false)) return@get
        call.respond(api.participantCatalog())
    }

    get("/study/app/api/preview") {
        if (!call.requireInvestigator(auth, handoffs, mutate = false)) return@get
        call.respond(studyPreviewResponse())
    }

    get("/study/app/api/participants/{participantId}") {
        if (!call.requireInvestigator(auth, handoffs, mutate = false)) return@get
        val participantId = ParticipantId.canonical(call.parameters["participantId"].orEmpty())
        call.respond(api.participantDetail(participantId))
    }

    post("/study/app/api/participants/{participantId}/delete") {
        if (!call.requireInvestigator(auth, handoffs, mutate = true)) return@post
        val request = call.receive<ActionRequest>()
        require(request.confirmed == true) { "participant deletion confirmation required" }
        val participantId = ParticipantId.canonical(call.parameters["participantId"].orEmpty())
        val deletedSessions = api.deleteParticipant(participantId)
        require(deletedSessions > 0) { "participant not found" }
        call.respond(
            ParticipantDeletedResponse(
                participant_id = participantId,
                deleted_sessions = deletedSessions,
            ),
        )
    }

    get("/study/app/api/sessions/{sessionId}/details") {
        if (!call.requireInvestigator(auth, handoffs, mutate = false)) return@get
        call.respond(api.sessionDetail(call.pathSessionId()))
    }

    get("/study/app/api/test-runs/options") {
        if (!call.requireInvestigator(auth, handoffs, mutate = false)) return@get
        call.respond(api.studyTestOptions())
    }

    post("/study/app/api/test-runs") {
        if (!call.requireInvestigator(auth, handoffs, mutate = true)) return@post
        val request = call.receive<StartStudyTestRequest>()
        call.respond(
            HttpStatusCode.Created,
            api.startStudyTest(request.task_id, request.condition, request.inject_error),
        )
    }

    post("/study/app/api/sessions/{sessionId}/responses/{instrumentId}/{position}/correct") {
        if (!call.requireInvestigator(auth, handoffs, mutate = true)) return@post
        val instrumentId = call.parameters["instrumentId"].orEmpty()
        require(instrumentId.isNotBlank()) { "invalid instrument id" }
        val position = call.parameters["position"]?.toIntOrNull()
            ?: throw IllegalArgumentException("invalid response position")
        require(position >= 0) { "invalid response position" }
        val request = call.receive<InvestigatorResponseCorrectionRequest>()
        require(request.expected_revision >= 0) { "expected_revision required" }
        call.respond(
            api.correctParticipantResponse(
                sessionId = call.pathSessionId(),
                instrumentId = instrumentId,
                position = position,
                answers = request.answers,
                missing = request.missing,
                expectedRevision = request.expected_revision,
            ),
        )
    }

    get("/study/app/api/exports/research") {
        if (!call.requireInvestigator(auth, handoffs, mutate = false)) return@get
        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "study-research.zip").toString(),
        )
        call.respondBytes(api.researchExport(), ContentType.Application.Zip)
    }

    get("/study/app/api/exports/consent") {
        if (!call.requireInvestigator(auth, handoffs, mutate = false)) return@get
        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "study-consent.csv").toString(),
        )
        call.respondBytes(api.consentExport(), ContentType.Text.CSV)
    }

    get("/study/app/api/exports/course_bonus") {
        if (!call.requireInvestigator(auth, handoffs, mutate = false)) return@get
        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "study-course-bonus.csv").toString(),
        )
        call.respondBytes(api.courseBonusExport(), ContentType.Text.CSV)
    }

    post("/study/app/api/course-bonus") {
        if (!call.requireInvestigator(auth, handoffs, mutate = true)) return@post
        val request = call.receive<CourseBonusRequest>()
        val id = api.saveCourseBonus(
            request.full_name,
            request.matriculation_number,
            request.consented_at,
        )
        call.respond(HttpStatusCode.Created, CourseBonusRecordedResponse(id = id))
    }

    get("/study/app/api/course-bonus") {
        if (!call.requireInvestigator(auth, handoffs, mutate = false)) return@get
        call.respond(CourseBonusListResponse(records = api.activeCourseBonus()))
    }

    post("/study/app/api/course-bonus/{recordId}/delete") {
        if (!call.requireInvestigator(auth, handoffs, mutate = true)) return@post
        val id = call.parameters["recordId"]?.toLongOrNull()
            ?: throw IllegalArgumentException("invalid course bonus record id")
        api.deleteCourseBonus(id)
        call.respond(HttpStatusCode.OK, mapOf("ok" to true))
    }

    post("/study/app/api/backups") {
        if (!call.requireInvestigator(auth, handoffs, mutate = true)) return@post
        call.respond(HttpStatusCode.Created, StudyBackupResponse(backup = api.createBackup()))
    }

    get("/study/app/api/investigator/state") {
        if (!call.requireInvestigator(auth, handoffs, mutate = false)) return@get
        val requested = call.request.queryParameters["session_id"]
        val session = if (requested == null) {
            api.latestSession()
        } else {
            api.getSession(requested.toLongOrNull() ?: throw IllegalArgumentException("invalid session_id"))
        }
        val study = api.studySnapshot()
        val consent = session?.let { api.consentStatus(it.id) }
        call.respond(
            InvestigatorStateResponse(
                session = session?.let(::toDto),
                study = JsonValueCodec.encode(study),
                trial_prepared = session?.let { api.trialPrepared(it, study) } ?: false,
                consent = consent?.let { ConsentStatusResponse(it.recorded, it.confirmed) },
                interview_guide = com.caddie.study.portal.STUDY_INTERVIEW_QUESTIONS,
            ),
        )
    }

    post("/study/app/api/sessions") {
        if (!call.requireInvestigator(auth, handoffs, mutate = true)) return@post
        val request = call.receive<CreateSessionRequest>()
        val sessionId = api.createLiveSession(ParticipantId.canonical(request.participant_id))
        val created = api.getSession(sessionId)
        val consent = api.transition(
            sessionId,
            "start_consent",
            created.workflowRevision,
            actor = "system",
        )
        val capability = synchronized(handoffs) {
            auth?.revokeAllSessions()
            handoffs.issue(consent.id, consent.workflowState)
        }
        call.setHandoffCookie(capability.token)
        call.respond(HttpStatusCode.Created, api.participantState(sessionId))
    }

    post("/study/app/api/sessions/{sessionId}/transition") {
        if (!call.requireInvestigator(auth, handoffs, mutate = true)) return@post
        val request = call.receive<TransitionRequest>()
        val session = api.transition(
            call.pathSessionId(),
            request.event,
            request.expected_revision,
        )
        call.respond(SuccessSessionResponse(session = toDto(session)))
    }

    post("/study/app/api/sessions/{sessionId}/events") {
        if (!call.requireInvestigator(auth, handoffs, mutate = true)) return@post
        val request = call.receive<TransitionRequest>()
        val session = api.transition(
            call.pathSessionId(),
            request.event,
            request.expected_revision,
        )
        call.respond(SuccessSessionResponse(session = toDto(session)))
    }

    post("/study/app/api/sessions/{sessionId}/prepare-trial") {
        if (!call.requireInvestigator(auth, handoffs, mutate = true)) return@post
        val request = call.receive<TransitionRequest>()
        val result = api.prepareNextTrial(call.pathSessionId(), request.expected_revision)
        call.respond(
            PreparedTrialResponse(
                session = toDto(api.getSession(result.sessionId)),
                assignment = JsonValueCodec.encode(result.assignment),
            ),
        )
    }

    post("/study/app/api/sessions/{sessionId}/prepare") {
        if (!call.requireInvestigator(auth, handoffs, mutate = true)) return@post
        val request = call.receive<ActionRequest>()
        val revision = requireNotNull(request.expected_revision) { "expected_revision required" }
        val result = api.prepareNextTrial(call.pathSessionId(), revision)
        call.respond(
            PreparedTrialResponse(
                session = toDto(api.getSession(result.sessionId)),
                assignment = JsonValueCodec.encode(result.assignment),
            ),
        )
    }

    post("/study/app/api/sessions/{sessionId}/retry-failed-trial") {
        if (!call.requireInvestigator(auth, handoffs, mutate = true)) return@post
        val request = call.receive<TransitionRequest>()
        val result = api.retryFailedTrial(call.pathSessionId(), request.expected_revision)
        call.respond(
            PreparedTrialResponse(
                session = toDto(api.getSession(result.sessionId)),
                assignment = JsonValueCodec.encode(result.assignment),
            ),
        )
    }

    post("/study/app/api/sessions/{sessionId}/observe") {
        if (!call.requireInvestigator(auth, handoffs, mutate = true)) return@post
        val request = call.receive<ObservationRequest>()
        val session = api.saveObservation(
            call.pathSessionId(),
            request.observation?.toModel(),
            request.expected_revision,
        )
        call.respond(SuccessSessionResponse(session = toDto(session)))
    }

    post("/study/app/api/sessions/{sessionId}/observation") {
        if (!call.requireInvestigator(auth, handoffs, mutate = true)) return@post
        val request = call.receive<ObservationRequest>()
        val session = api.saveObservation(
            call.pathSessionId(),
            request.observation?.toModel(),
            request.expected_revision,
        )
        call.respond(SuccessSessionResponse(session = toDto(session)))
    }

    post("/study/app/api/sessions/{sessionId}/result") {
        if (!call.requireInvestigator(auth, handoffs, mutate = true)) return@post
        val request = call.receive<ActionRequest>()
        val revision = requireNotNull(request.expected_revision) { "expected_revision required" }
        val session = api.transition(call.pathSessionId(), "result_reviewed", revision)
        call.respond(SuccessSessionResponse(session = toDto(session)))
    }

    post("/study/app/api/sessions/{sessionId}/consent") {
        if (!call.requireInvestigator(auth, handoffs, mutate = true)) return@post
        val request = call.receive<ConsentRequest>()
        val consentId = api.recordLiveConsent(
            call.pathSessionId(),
            request.full_name,
            request.acknowledgements,
        )
        call.respond(ConsentRecordedResponse(consent_id = consentId))
    }

    post("/study/app/api/sessions/{sessionId}/confirm-consent") {
        if (!call.requireInvestigator(auth, handoffs, mutate = true)) return@post
        val request = call.receive<TransitionRequest>()
        val session = api.confirmLiveConsent(call.pathSessionId(), request.expected_revision)
        call.respond(SuccessSessionResponse(session = toDto(session)))
    }

    post("/study/app/api/sessions/{sessionId}/response") {
        if (!call.requireInvestigator(auth, handoffs, mutate = true)) return@post
        val request = call.receive<ResponseSubmitRequest>()
        val result = api.submitParticipantResponse(
            sessionId = call.pathSessionId(),
            answers = JsonValueCodec.decodeObject(request.answers),
            expectedResponseRevision = request.expected_response_revision,
            expectedWorkflowRevision = request.expected_workflow_revision,
        )
        call.respond(
            ParticipantResponseSubmitted(
                response_id = result.responseId,
                session_id = result.sessionId,
            ),
        )
    }

    post("/study/app/api/sessions/{sessionId}/reset") {
        if (!call.requireInvestigator(auth, handoffs, mutate = true)) return@post
        val request = call.receive<ActionRequest>()
        require(request.confirmed == true) { "reset confirmation required" }
        val revision = requireNotNull(request.expected_revision) { "expected_revision required" }
        val session = api.resetAfterTrial(call.pathSessionId(), revision)
        call.respond(SuccessSessionResponse(session = toDto(session)))
    }

    post("/study/app/api/sessions/{sessionId}/abort") {
        if (!call.requireInvestigator(auth, handoffs, mutate = true)) return@post
        val request = call.receive<ActionRequest>()
        require(request.confirmed == true) { "abort confirmation required" }
        val revision = requireNotNull(request.expected_revision) { "expected_revision required" }
        val session = api.abortSession(
            call.pathSessionId(),
            request.reason ?: "investigator_abort",
            revision,
        )
        call.respond(SuccessSessionResponse(session = toDto(session)))
    }

    post("/study/app/api/sessions/{sessionId}/handoff") {
        if (!call.requireInvestigator(auth, handoffs, mutate = true)) return@post
        val request = call.receive<ActionRequest>()
        val session = api.getSession(call.pathSessionId())
        if (request.expected_revision != session.workflowRevision) {
            throw com.caddie.study.store.RevisionConflict("stale workflow revision")
        }
        require(session.workflowState in PARTICIPANT_HANDOFF_STATES) { "handoff unavailable" }
        val capability = synchronized(handoffs) {
            auth?.revokeAllSessions()
            handoffs.issue(session.id, session.workflowState)
        }
        call.response.cookies.append(
            name = HANDOFF_COOKIE,
            value = capability.token,
            path = "/study/app",
            httpOnly = true,
            extensions = mapOf("SameSite" to "Strict"),
        )
        call.respond(api.participantState(session.id))
    }

    get("/study/app/api/participant/state") {
        val capability = call.participantCapability(api, handoffs) ?: return@get
        val state = api.reconcileParticipantFlow(capability.sessionId)
        call.respondParticipantState(state, capability, handoffs)
    }

    post("/study/app/api/participant/start") {
        val capability = call.participantCapability(api, handoffs) ?: return@post
        val request = call.receive<TransitionRequest>()
        val session = api.transition(
            capability.sessionId,
            "participant_started",
            request.expected_revision,
            actor = "participant",
        )
        val rotated = handoffs.rotate(capability.token, session.workflowState)
        call.setHandoffCookie(rotated.token)
        call.respond(api.participantState(session.id))
    }

    post("/study/app/api/participant/consent") {
        val capability = call.participantCapability(api, handoffs) ?: return@post
        val request = call.receive<ParticipantConsentRequest>()
        val acknowledgements = request.acknowledgements.entries
            .filter { it.value.toString().toBooleanStrictOrNull() == true }
            .map { it.key }
        api.recordAndConfirmLiveConsent(
            capability.sessionId,
            request.full_name,
            acknowledgements,
        )
        val state = api.participantState(capability.sessionId)
        val rotated = handoffs.rotate(capability.token, state.session.workflow_state)
        call.setHandoffCookie(rotated.token)
        call.respond(
            HttpStatusCode.Created,
            ParticipantConsentSubmitted(
                session = state.session,
                consent = ConsentStatusResponse(recorded = true, confirmed = true),
                handoff_required = false,
            ),
        )
    }

    post("/study/app/api/participant/continue") {
        val capability = call.participantCapability(api, handoffs) ?: return@post
        val request = call.receive<ParticipantContinueRequest>()
        val state = api.continueParticipantFlow(
            capability.sessionId,
            request.expected_revision,
        )
        call.respondParticipantState(state, capability, handoffs)
    }

    post("/study/app/api/participant/training/start") {
        val capability = call.participantCapability(api, handoffs) ?: return@post
        val request = call.receive<ParticipantContinueRequest>()
        val state = api.startParticipantTraining(
            capability.sessionId,
            request.expected_revision,
        )
        call.respondParticipantState(state, capability, handoffs)
    }

    post("/study/app/api/drafts") {
        val capability = call.participantCapability(api, handoffs) ?: return@post
        val request = call.receive<ParticipantDraftRequest>()
        val draft = api.saveParticipantDraft(
            capability.sessionId,
            JsonValueCodec.decodeObject(request.answers),
            request.expected_revision,
        )
        call.respond(
            DraftSavedResponse(
                draft = DraftSavedSummary(
                    instrument_id = draft.instrumentId,
                    position = draft.position,
                    revision = draft.revision,
                ),
            ),
        )
    }

    post("/study/app/api/participant/submit") {
        val capability = call.participantCapability(api, handoffs) ?: return@post
        val request = call.receive<ParticipantSubmitRequest>()
        val result = api.submitParticipantResponse(
            capability.sessionId,
            JsonValueCodec.decodeObject(request.answers),
            request.expected_revision,
            request.expected_workflow_revision,
        )
        val state = api.reconcileParticipantFlow(result.sessionId)
        call.respondParticipantState(state, capability, handoffs, HttpStatusCode.Created)
    }

    post("/study/app/api/participant/interview") {
        val capability = call.participantCapability(api, handoffs) ?: return@post
        val request = call.receive<InterviewRequest>()
        api.submitLiveInterview(
            capability.sessionId,
            request.answers,
            request.expected_revision,
            actor = "participant",
        )
        val state = api.reconcileParticipantFlow(capability.sessionId)
        call.respondParticipantState(state, capability, handoffs)
    }

    post("/study/app/api/participant/return-to-investigator") {
        val capability = call.participantCapability(api, handoffs) ?: return@post
        val session = api.getSession(capability.sessionId)
        require(session.workflowState == "technical_hold" && session.resumeState == "trial_running") {
            "investigator recovery is not required"
        }
        handoffs.revoke(capability.token)
        call.clearHandoffCookie()
        call.respond(mapOf("ok" to true))
    }

    post("/study/app/api/participant/finish") {
        val capability = call.participantCapability(api, handoffs) ?: return@post
        val session = api.getSession(capability.sessionId)
        require(session.workflowState == "completed") { "study is not completed" }
        handoffs.revoke(capability.token)
        call.clearHandoffCookie()
        call.respond(mapOf("ok" to true))
    }

    post("/study/app/api/sessions/{sessionId}/interview") {
        if (!call.requireInvestigator(auth, handoffs, mutate = true)) return@post
        val request = call.receive<InterviewRequest>()
        val session = api.submitLiveInterview(
            call.pathSessionId(),
            request.answers,
            request.expected_revision,
        )
        call.respond(SuccessSessionResponse(session = toDto(session)))
    }

    get("/study/app/api/sessions/{sessionId}/assignment") {
        if (!call.requireInvestigator(auth, handoffs, mutate = false)) return@get
        val assignment = api.getSessionAssignment(call.pathSessionId())
        call.respond(AssignmentResponse(assignment = JsonValueCodec.encode(assignment)))
    }

    get("/study/app/api/sessions/{sessionId}/consent-status") {
        if (!call.requireInvestigator(auth, handoffs, mutate = false)) return@get
        val status = api.consentStatus(call.pathSessionId())
        if (status == null) {
            call.respond(HttpStatusCode.NotFound, ErrorBody(error = "consent_not_found"))
        } else {
            call.respond(ConsentStatusResponse(status.recorded, status.confirmed))
        }
    }

    get("/study/app/api/sessions/{sessionId}/debrief") {
        if (!call.requireInvestigator(auth, handoffs, mutate = false)) return@get
        call.respond(DebriefResponse(error_variants = api.participantDebrief(call.pathSessionId())))
    }
}

private suspend fun ApplicationCall.participantCapability(
    api: StudyPortalApi,
    handoffs: StudyPortalHandoffs,
): StudyPortalHandoffs.Capability? {
    val capability = handoffs.resolve(request.cookies[HANDOFF_COOKIE])
    if (capability == null) {
        respond(HttpStatusCode.Unauthorized, ErrorBody(error = "unauthorized"))
        return null
    }
    val session = api.getSession(capability.sessionId)
    require(session.status == "in_progress" || session.workflowState == "completed") {
        "participant session is not active"
    }
    return capability
}

private suspend fun ApplicationCall.respondParticipantState(
    state: com.caddie.study.portal.ParticipantStateResponse,
    capability: StudyPortalHandoffs.Capability,
    handoffs: StudyPortalHandoffs,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    if (state.session.workflow_state == "completed") {
        handoffs.revoke(capability.token)
        clearHandoffCookie()
    } else {
        setHandoffCookie(handoffs.rotate(capability.token, state.session.workflow_state).token)
    }
    respond(status, state)
}

internal fun ApplicationCall.setHandoffCookie(token: String) {
    response.cookies.append(
        name = HANDOFF_COOKIE,
        value = token,
        path = "/study/app",
        httpOnly = true,
        extensions = mapOf("SameSite" to "Strict"),
    )
}

internal fun ApplicationCall.clearHandoffCookie() {
    response.cookies.append(
        name = HANDOFF_COOKIE,
        value = "",
        maxAge = 0,
        path = "/study/app",
        httpOnly = true,
        extensions = mapOf("SameSite" to "Strict"),
    )
}

private fun ApplicationCall.pathSessionId(): Long =
    parameters.getAll("sessionId")?.singleOrNull()?.toLongOrNull()
        ?: throw IllegalArgumentException("missing or invalid sessionId")

@Serializable
private data class PreparedTrialResponse(
    val ok: Boolean = true,
    val session: SessionSummary,
    val assignment: JsonElement,
)

@Serializable
private data class AssignmentResponse(val ok: Boolean = true, val assignment: JsonElement)

@Serializable
private data class DebriefResponse(
    val ok: Boolean = true,
    val error_variants: List<Map<String, String>>,
)

@Serializable
private data class ConsentRecordedResponse(val ok: Boolean = true, val consent_id: Long)

@Serializable
private data class ParticipantResponseSubmitted(
    val ok: Boolean = true,
    val response_id: Long,
    val session_id: Long,
)

@Serializable
private data class ConsentStatusResponse(val recorded: Boolean, val confirmed: Boolean)

@Serializable
private data class ParticipantConsentSubmitted(
    val ok: Boolean = true,
    val session: ParticipantSessionSummary,
    val consent: ConsentStatusResponse,
    val handoff_required: Boolean,
)

@Serializable
private data class DraftSavedResponse(
    val ok: Boolean = true,
    val draft: DraftSavedSummary,
)

@Serializable
private data class DraftSavedSummary(
    val instrument_id: String,
    val position: Int,
    val revision: Int,
)

@Serializable
private data class InvestigatorStateResponse(
    val ok: Boolean = true,
    val session: SessionSummary?,
    val study: JsonElement,
    val trial_prepared: Boolean,
    val consent: ConsentStatusResponse?,
    val interview_guide: List<com.caddie.study.portal.StudyInterviewQuestion>,
)

@Serializable
private data class ErrorBody(val ok: Boolean = false, val error: String)

@Serializable
private data class ParticipantDeletedResponse(
    val ok: Boolean = true,
    val participant_id: String,
    val deleted_sessions: Int,
)

internal const val HANDOFF_COOKIE = "caddie_handoff"

private val PARTICIPANT_HANDOFF_STATES = setOf(
    "consent",
    "task_card",
    "trial_running",
    "task_questionnaire",
    "block_questionnaire",
    "demographics",
    "preference_ranking",
    "debrief",
)
