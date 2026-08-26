package com.caddie.studyportal

import com.caddie.study.portal.ConsentStatus
import com.caddie.study.portal.ParticipantResponseResult
import com.caddie.study.portal.PreparedTrialResult
import com.caddie.study.portal.StudyPortalApi
import com.caddie.study.portal.http.installStudyRoutes
import com.caddie.study.portal.http.installStudyPortalErrorHandling
import com.caddie.study.runtime.model.ErrorObservation
import com.caddie.study.store.entity.StudySessionEntity
import io.ktor.client.request.header
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies the exact routes installed by the embedded native portal server. */
class StudyPortalServerTest {
    @Test
    fun nativeExportsAndCourseBonusUseTheFrontendRoutes() = testApplication {
        val api = FakeStudyPortalApi()
        application {
            install(ContentNegotiation) { json() }
            routing { installStudyRoutes(api) }
        }

        val saved = client.post("/study/app/api/course-bonus") {
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"full_name":"Student","matriculation_number":"123","consented_at":"2026-08-02"}""")
        }
        val deleted = client.post("/study/app/api/course-bonus/9/delete")
        val active = client.get("/study/app/api/course-bonus")
        val research = client.get("/study/app/api/exports/research")
        val consent = client.get("/study/app/api/exports/consent")
        val bonus = client.get("/study/app/api/exports/course_bonus")
        val backup = client.post("/study/app/api/backups")

        assertEquals(HttpStatusCode.Created, saved.status)
        assertEquals("Student", api.courseBonusName)
        assertEquals(HttpStatusCode.OK, deleted.status)
        assertEquals(9L, api.deletedCourseBonusId)
        assertTrue(active.bodyAsText().contains("Student"))
        assertEquals("application/zip", research.headers[HttpHeaders.ContentType])
        assertTrue(consent.headers[HttpHeaders.ContentDisposition]!!.contains("study-consent.csv"))
        assertTrue(bonus.headers[HttpHeaders.ContentDisposition]!!.contains("study-course-bonus.csv"))
        assertEquals(HttpStatusCode.Created, backup.status)
        assertTrue(backup.bodyAsText().contains("study-backups/test"))
    }


    @Test
    fun reachableUrlPrefersTheTailscaleAddress() {
        assertEquals(
            "http://100.64.0.42:8787/study/app",
            StudyPortalServer.reachableUrl(
                port = 8787,
                addresses = listOf("192.0.2.36", "100.64.0.42"),
            ),
        )
    }

    @Test
    fun reachableUrlDoesNotAdvertiseTheWifiAddress() {
        assertEquals(
            "http://127.0.0.1:8787/study/app",
            StudyPortalServer.reachableUrl(8787, listOf("192.168.2.36")),
        )
    }

    @Test
    fun portalPeerBoundaryAllowsOnlyLoopbackAndTailscale() {
        assertTrue(StudyPortalServer.isPortalPeerAllowed("127.0.0.1"))
        assertTrue(StudyPortalServer.isPortalPeerAllowed("::1"))
        assertTrue(StudyPortalServer.isPortalPeerAllowed("100.64.0.42"))
        assertTrue(StudyPortalServer.isPortalPeerAllowed("::ffff:100.64.0.42"))
        assertTrue(StudyPortalServer.isPortalPeerAllowed("0:0:0:0:0:ffff:6449:340d"))
        assertTrue(StudyPortalServer.isPortalPeerAllowed("fd7a:115c:a1e0::1"))
        assertTrue(!StudyPortalServer.isPortalPeerAllowed("192.168.2.36"))
        assertTrue(!StudyPortalServer.isPortalPeerAllowed("::ffff:192.168.2.36"))
        assertTrue(!StudyPortalServer.isPortalPeerAllowed("10.0.0.4"))
        assertTrue(!StudyPortalServer.isPortalPeerAllowed("8.8.8.8"))
    }

    @Test
    fun serverOwnsItsEngineLifecycle() {
        val server = StudyPortalServer(0)

        assertTrue(!server.isRunning)
        server.start()
        server.start()
        assertTrue(server.isRunning)
        server.stop()
        assertTrue(!server.isRunning)
        server.start()
        assertTrue(server.isRunning)
        server.stop()
    }

    @Test
    fun sessionCreationUsesTheProductionRoute() = testApplication {
        val api = FakeStudyPortalApi()
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing { installStudyRoutes(api) }
        }

        val response = client.post("/study/app/api/sessions") {
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"participant_id":"01","mode":"live"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals("P01", api.createdParticipant)
        assertTrue(response.bodyAsText().contains("\"workflow_state\":\"consent\""))
        assertTrue(response.headers.getAll(HttpHeaders.SetCookie).orEmpty().any {
            it.startsWith("caddie_handoff=")
        })
    }

    @Test
    fun participantResponsePreservesNumbersAndRankingLists() = testApplication {
        val api = FakeStudyPortalApi()
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing { installStudyRoutes(api) }
        }

        val response = client.post("/study/app/api/sessions/1/response") {
            header(HttpHeaders.ContentType, "application/json")
            setBody(
                """{
                    "answers":{"age":30,"condition_ranking":["C1","C2","C3"]},
                    "expected_response_revision":0,
                    "expected_workflow_revision":2
                }""".trimIndent(),
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(30, api.submittedAnswers?.get("age"))
        assertEquals(listOf("C1", "C2", "C3"), api.submittedAnswers?.get("condition_ranking"))
    }

    @Test
    fun pathSessionIdCannotBeReplacedByAQueryParameter() = testApplication {
        val api = FakeStudyPortalApi()
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing { installStudyRoutes(api) }
        }

        val response = client.post("/study/app/api/sessions/1/response?sessionId=2") {
            header(HttpHeaders.ContentType, "application/json")
            setBody(
                """{
                    "answers":{},
                    "expected_response_revision":0,
                    "expected_workflow_revision":2
                }""".trimIndent(),
            )
        }

        assertTrue(response.status.value !in 200..299)
        assertEquals(null, api.submittedSessionId)
    }

    @Test
    fun existingFrontendInvestigatorPathsUseTheProductionApi() = testApplication {
        val api = FakeStudyPortalApi()
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing { installStudyRoutes(api) }
        }

        val event = client.post("/study/app/api/sessions/1/events") {
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"event":"start_consent","expected_revision":0}""")
        }
        val prepare = client.post("/study/app/api/sessions/1/prepare") {
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"expected_revision":1}""")
        }
        val assignment = client.get("/study/app/api/sessions/1/assignment")
        val debrief = client.get("/study/app/api/sessions/1/debrief")

        assertEquals(HttpStatusCode.OK, event.status)
        assertEquals("start_consent", api.transitionEvent)
        assertEquals(HttpStatusCode.OK, prepare.status)
        assertEquals(
            "T1",
            Json.parseToJsonElement(prepare.bodyAsText())
                .jsonObject["assignment"]!!.jsonObject["task_id"]!!.jsonPrimitive.content,
        )
        assertTrue(assignment.bodyAsText().contains("\"assignment\""))
        assertTrue(debrief.bodyAsText().contains("\"error_variants\""))
    }

    @Test
    fun investigatorStateRestoresTheLatestDurableSession() = testApplication {
        val api = FakeStudyPortalApi()
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing { installStudyRoutes(api) }
        }

        val response = client.get("/study/app/api/investigator/state")
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("P01", body["session"]!!.jsonObject["participant_id"]!!.jsonPrimitive.content)
        assertEquals("armed", body["study"]!!.jsonObject["trial"]!!.jsonObject["state"]!!.jsonPrimitive.content)
        assertTrue(body["trial_prepared"]!!.jsonPrimitive.content.toBoolean())
        assertTrue(response.bodyAsText().contains("overall_experience"))
        assertTrue(response.bodyAsText().contains("delegation_boundary"))
    }

    @Test
    fun interviewRoutePreservesAnswersByQuestion() = testApplication {
        val api = FakeStudyPortalApi()
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing { installStudyRoutes(api) }
        }

        val response = client.post("/study/app/api/sessions/1/interview") {
            header(HttpHeaders.ContentType, "application/json")
            setBody(
                """{"expected_revision":9,"answers":{"overall_experience":"Gut","delegation_boundary":"Bei Überweisungen fragen"}}""",
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Gut", api.interviewAnswers?.get("overall_experience"))
        assertEquals("Bei Überweisungen fragen", api.interviewAnswers?.get("delegation_boundary"))
    }

    @Test
    fun existingFrontendResultResetAndAbortActionsAreWired() = testApplication {
        val api = FakeStudyPortalApi()
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing { installStudyRoutes(api) }
        }

        client.post("/study/app/api/sessions/1/result") {
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"expected_revision":3}""")
        }
        val reset = client.post("/study/app/api/sessions/1/reset") {
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"confirmed":true,"expected_revision":4}""")
        }
        val abort = client.post("/study/app/api/sessions/1/abort") {
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"confirmed":true,"expected_revision":5,"reason":"investigator_abort"}""")
        }

        assertEquals("result_reviewed", api.transitionEvent)
        assertEquals(HttpStatusCode.OK, reset.status)
        assertEquals(HttpStatusCode.OK, abort.status)
        assertEquals("investigator_abort", api.abortReason)
    }

    @Test
    fun handoffCookieGrantsOnlyTheParticipantStateRoute() = testApplication {
        val api = FakeStudyPortalApi().apply { workflowState = "consent" }
        val handoffs = com.caddie.study.portal.http.StudyPortalHandoffs()
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing { installStudyRoutes(api, handoffs) }
        }

        val handoff = client.post("/study/app/api/sessions/1/handoff") {
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"expected_revision":0}""")
        }
        val cookie = handoff.headers.getAll(HttpHeaders.SetCookie)!!.single().substringBefore(';')
        val participant = client.get("/study/app/api/participant/state") {
            header(HttpHeaders.Cookie, cookie)
        }

        assertEquals(HttpStatusCode.OK, handoff.status)
        assertEquals(HttpStatusCode.OK, participant.status)
        assertTrue(participant.bodyAsText().contains("\"participant_id\":\"P01\""))
    }

    @Test
    fun participantDraftRouteUsesTheFrontendPayloadAndHandoffScope() = testApplication {
        val api = FakeStudyPortalApi().apply { workflowState = "task_questionnaire" }
        val handoffs = com.caddie.study.portal.http.StudyPortalHandoffs()
        val capability = handoffs.issue(1, "task_questionnaire")
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing { installStudyRoutes(api, handoffs) }
        }

        val response = client.post("/study/app/api/drafts") {
            header(HttpHeaders.ContentType, "application/json")
            header(HttpHeaders.Cookie, "caddie_handoff=${capability.token}")
            setBody("""{"answers":{"age":30},"expected_revision":0}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(30, api.savedDraftAnswers?.get("age"))
        assertTrue(response.bodyAsText().contains("\"revision\":1"))
    }

    @Test
    fun participantDeletionRequiresConfirmationAndTargetsOneCanonicalId() = testApplication {
        val api = FakeStudyPortalApi()
        application {
            installStudyPortalErrorHandling()
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing { installStudyRoutes(api) }
        }

        val rejected = client.post("/study/app/api/participants/P1/delete") {
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"confirmed":false}""")
        }
        val deleted = client.post("/study/app/api/participants/P1/delete") {
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"confirmed":true}""")
        }

        assertEquals(HttpStatusCode.BadRequest, rejected.status)
        assertEquals(HttpStatusCode.OK, deleted.status)
        assertEquals("P01", api.deletedParticipantId)
        assertTrue(deleted.bodyAsText().contains("\"deleted_sessions\":1"))
    }

    @Test
    fun participantConsentAcceptsTheExistingCheckboxObject() = testApplication {
        val api = FakeStudyPortalApi().apply { workflowState = "consent" }
        val handoffs = com.caddie.study.portal.http.StudyPortalHandoffs()
        val capability = handoffs.issue(1, "consent")
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing { installStudyRoutes(api, handoffs) }
        }

        val response = client.post("/study/app/api/participant/consent") {
            header(HttpHeaders.ContentType, "application/json")
            header(HttpHeaders.Cookie, "caddie_handoff=${capability.token}")
            setBody(
                """{"full_name":"Participant One","acknowledgements":{
                    "study_information_read":true,
                    "voluntary_participation":true,
                    "data_processing_agreed":true,
                    "withdrawal_understood":true
                }}""".trimIndent(),
            )
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(4, api.recordedAcknowledgements?.size)
        assertEquals(1, api.recordAndConfirmConsentCalls)
        assertEquals("training", api.workflowState)
        assertTrue(
            !Json.parseToJsonElement(response.bodyAsText())
                .jsonObject["handoff_required"]!!.jsonPrimitive.content.toBoolean(),
        )

        val rotatedCookie = response.headers.getAll(HttpHeaders.SetCookie)
            .orEmpty()
            .single { it.startsWith("caddie_handoff=") }
            .substringBefore(';')
        val oldState = client.get("/study/app/api/participant/state") {
            header(HttpHeaders.Cookie, "caddie_handoff=${capability.token}")
        }
        val continuedState = client.get("/study/app/api/participant/state") {
            header(HttpHeaders.Cookie, rotatedCookie)
        }

        assertEquals(HttpStatusCode.OK, oldState.status)
        assertEquals(HttpStatusCode.OK, continuedState.status)
        assertEquals("caddie_handoff=${capability.token}", rotatedCookie)
    }

    @Test
    fun participantStartRotatesTheWorkflowScopedCookie() = testApplication {
        val api = FakeStudyPortalApi().apply { workflowState = "task_card" }
        val handoffs = com.caddie.study.portal.http.StudyPortalHandoffs()
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing { installStudyRoutes(api, handoffs) }
        }
        val issued = handoffs.issue(1, "task_card")
        val oldCookie = "caddie_handoff=${issued.token}"

        val started = client.post("/study/app/api/participant/start") {
            header(HttpHeaders.ContentType, "application/json")
            header(HttpHeaders.Cookie, oldCookie)
            setBody("""{"expected_revision":0}""")
        }
        val newCookie = started.headers.getAll(HttpHeaders.SetCookie)!!.single().substringBefore(';')
        val oldState = client.get("/study/app/api/participant/state") {
            header(HttpHeaders.Cookie, oldCookie)
        }
        val newState = client.get("/study/app/api/participant/state") {
            header(HttpHeaders.Cookie, newCookie)
        }

        assertEquals(HttpStatusCode.OK, started.status)
        assertEquals(HttpStatusCode.OK, oldState.status)
        assertEquals(HttpStatusCode.OK, newState.status)
        assertEquals(oldCookie, newCookie)
    }

    @Test
    fun participantCanPrepareFreePracticeWithoutLeavingTraining() = testApplication {
        val api = FakeStudyPortalApi().apply { workflowState = "training" }
        val handoffs = com.caddie.study.portal.http.StudyPortalHandoffs()
        val capability = handoffs.issue(1, "training")
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing { installStudyRoutes(api, handoffs) }
        }

        val response = client.post("/study/app/api/participant/training/start") {
            header(HttpHeaders.ContentType, "application/json")
            header(HttpHeaders.Cookie, "caddie_handoff=${capability.token}")
            setBody("""{"expected_revision":1}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(1, api.trainingStartCalls)
        assertEquals("training", api.workflowState)
    }

    @Test
    fun participantSubmissionKeepsTheParticipantSessionActive() = testApplication {
        val api = FakeStudyPortalApi().apply { workflowState = "task_questionnaire" }
        val handoffs = com.caddie.study.portal.http.StudyPortalHandoffs()
        val capability = handoffs.issue(1, "task_questionnaire")
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing { installStudyRoutes(api, handoffs) }
        }

        val submitted = client.post("/study/app/api/participant/submit") {
            header(HttpHeaders.ContentType, "application/json")
            header(HttpHeaders.Cookie, "caddie_handoff=${capability.token}")
            setBody("""{"answers":{},"expected_revision":0,"expected_workflow_revision":1}""")
        }
        val newCookie = submitted.headers.getAll(HttpHeaders.SetCookie)
            .orEmpty()
            .single { it.startsWith("caddie_handoff=") }
            .substringBefore(';')

        assertEquals(HttpStatusCode.Created, submitted.status)
        assertTrue(!newCookie.endsWith("="))
        assertEquals(
            HttpStatusCode.OK,
            client.get("/study/app/api/participant/state") {
                header(HttpHeaders.Cookie, newCookie)
            }.status,
        )
    }
}

internal class FakeStudyPortalApi : StudyPortalApi {
    var workflowState: String = "setup"
    var createdParticipant: String? = null
    var submittedAnswers: Map<String, Any>? = null
    var submittedSessionId: Long? = null
    var transitionEvent: String? = null
    var abortReason: String? = null
    var savedDraftAnswers: Map<String, Any>? = null
    var recordedAcknowledgements: List<String>? = null
    var recordAndConfirmConsentCalls: Int = 0
    var consentConfirmed: Boolean = false
    var consentConfirmationActor: String = "investigator"
    var courseBonusName: String? = null
    var deletedCourseBonusId: Long? = null
    var correctedResponseAnswers: kotlinx.serialization.json.JsonObject? = null
    var startedTestTask: String? = null
    var interviewAnswers: Map<String, String>? = null
    var interviewActor: String? = null
    var deletedParticipantId: String? = null
    var resumeState: String? = null
    var sessionStatus: String = "in_progress"
    var getSessionFailure: Throwable? = null
    var participantContinueCalls: Int = 0
    var trainingStartCalls: Int = 0

    override suspend fun deleteParticipant(participantId: String): Int {
        deletedParticipantId = participantId
        return 1
    }

    override suspend fun createLiveSession(participantId: String): Long {
        createdParticipant = participantId
        return 1
    }

    override suspend fun getSession(sessionId: Long): StudySessionEntity {
        getSessionFailure?.let { throw it }
        return session(sessionId)
    }

    override suspend fun correctParticipantResponse(
        sessionId: Long,
        instrumentId: String,
        position: Int,
        answers: kotlinx.serialization.json.JsonObject,
        missing: kotlinx.serialization.json.JsonObject,
        expectedRevision: Int,
    ): com.caddie.study.portal.StudyStoredResponse {
        correctedResponseAnswers = answers
        return com.caddie.study.portal.StudyStoredResponse(
            instrument_id = instrumentId,
            position = position,
            answers = answers,
            missing = missing,
            submitted_at = "2026-08-05T10:00:00Z",
            revision = expectedRevision + 1,
        )
    }

    override fun studyTestOptions() = com.caddie.study.portal.StudyTestOptionsResponse(
        tasks = listOf(
            com.caddie.study.portal.StudyTestTaskDto(
                "task_maps_messenger",
                "Route ermitteln",
                "low",
            ),
        ),
        conditions = listOf("c1_stepwise", "c2_final_checkpoint", "c3_voluntary_intervention"),
    )

    override suspend fun startStudyTest(
        taskId: String,
        condition: String,
        injectError: Boolean,
    ): com.caddie.study.portal.StudyTestRunResponse {
        startedTestTask = taskId
        return com.caddie.study.portal.StudyTestRunResponse(
            session_id = 9,
            participant_id = "TEST-ROUTE",
            instruction = "Route ermitteln",
            condition = condition,
            inject_error = injectError,
            runtime = kotlinx.serialization.json.JsonObject(emptyMap()),
        )
    }

    override suspend fun transition(
        sessionId: Long,
        event: String,
        expectedRevision: Int,
        actor: String,
    ): StudySessionEntity {
        transitionEvent = event
        if (event == "start_consent") workflowState = "consent"
        if (event == "participant_started") workflowState = "trial_running"
        return session(sessionId)
    }

    override suspend fun prepareNextTrial(sessionId: Long, expectedRevision: Int) =
        PreparedTrialResult(sessionId, mapOf("task_id" to "T1"), emptyMap())

    override suspend fun saveObservation(
        sessionId: Long,
        observation: ErrorObservation?,
        expectedRevision: Int,
        actor: String,
    ): StudySessionEntity = session(sessionId)

    override suspend fun recordLiveConsent(
        sessionId: Long,
        fullName: String,
        acknowledgements: List<String>,
    ): Long {
        recordedAcknowledgements = acknowledgements
        return 1
    }

    override suspend fun confirmLiveConsent(
        sessionId: Long,
        expectedRevision: Int,
        actor: String,
    ): StudySessionEntity {
        consentConfirmed = true
        consentConfirmationActor = actor
        workflowState = "training"
        return session(sessionId)
    }

    override suspend fun recordAndConfirmLiveConsent(
        sessionId: Long,
        fullName: String,
        acknowledgements: List<String>,
    ): StudySessionEntity {
        recordedAcknowledgements = acknowledgements
        recordAndConfirmConsentCalls += 1
        workflowState = "training"
        return session(sessionId)
    }

    override suspend fun submitParticipantResponse(
        sessionId: Long,
        answers: Map<String, Any>,
        expectedResponseRevision: Int,
        expectedWorkflowRevision: Int,
    ): ParticipantResponseResult {
        submittedSessionId = sessionId
        submittedAnswers = answers
        return ParticipantResponseResult(1, sessionId)
    }

    override suspend fun resetAfterTrial(sessionId: Long, expectedRevision: Int) = session(sessionId)
    override suspend fun abortSession(sessionId: Long, reason: String, expectedRevision: Int): StudySessionEntity {
        abortReason = reason
        return session(sessionId)
    }
    override suspend fun submitLiveInterview(
        sessionId: Long,
        answers: Map<String, String>,
        expectedRevision: Int,
        actor: String,
    ): StudySessionEntity {
        interviewAnswers = answers
        interviewActor = actor
        return session(sessionId)
    }

    override suspend fun continueParticipantFlow(
        sessionId: Long,
        expectedRevision: Int,
    ): com.caddie.study.portal.ParticipantStateResponse {
        participantContinueCalls += 1
        workflowState = when (workflowState) {
            "training" -> "task_card"
            "debrief" -> "completed"
            "technical_hold" -> "task_card"
            else -> workflowState
        }
        if (workflowState == "task_card") resumeState = null
        return participantState(sessionId)
    }
    override suspend fun startParticipantTraining(
        sessionId: Long,
        expectedRevision: Int,
    ): com.caddie.study.portal.ParticipantStateResponse {
        trainingStartCalls += 1
        return participantState(sessionId)
    }
    override suspend fun getSessionAssignment(sessionId: Long): Map<String, Any> =
        mapOf("participant_id" to "P01")
    override suspend fun participantDebrief(sessionId: Long): List<Map<String, String>> =
        listOf(mapOf("id" to "error-1"))
    override suspend fun consentStatus(sessionId: Long): ConsentStatus? = null
    override suspend fun latestSession(): StudySessionEntity? = session(1)
    override fun studySnapshot(): Map<String, Any> =
        mapOf("mode" to "live", "trial" to mapOf("state" to "armed"))
    override fun trialPrepared(session: StudySessionEntity, study: Map<String, Any>): Boolean = true
    override suspend fun participantState(sessionId: Long): com.caddie.study.portal.ParticipantStateResponse =
        com.caddie.study.portal.ParticipantStateResponse(
            session = com.caddie.study.portal.ParticipantSessionSummary(
                sessionId, "P01", workflowState, 1, 0, resumeState,
            ),
        )
    override suspend fun saveParticipantDraft(
        sessionId: Long,
        answers: Map<String, Any>,
        expectedRevision: Int,
    ): com.caddie.study.portal.ParticipantDraftResult {
        savedDraftAnswers = answers
        return com.caddie.study.portal.ParticipantDraftResult("task", 0, expectedRevision + 1)
    }

    override suspend fun saveCourseBonus(fullName: String, matriculationNumber: String, consentedAt: String): Long {
        courseBonusName = fullName
        return 9
    }

    override suspend fun deleteCourseBonus(id: Long) {
        deletedCourseBonusId = id
    }

    override suspend fun activeCourseBonus() = listOf(
        com.caddie.study.portal.CourseBonusSummary(9, "Student", "123", "2026-08-02"),
    )

    override suspend fun researchExport(): ByteArray = "zip".encodeToByteArray()
    override suspend fun consentExport(): ByteArray = "consent".encodeToByteArray()
    override suspend fun courseBonusExport(): ByteArray = "bonus".encodeToByteArray()
    override suspend fun createBackup() = com.caddie.study.portal.StudyBackup("study-backups/test")

    private fun session(id: Long) = StudySessionEntity(
        id = id,
        participantId = "P01",
        mode = "live",
        source = "live_digital",
        enteredAt = "2026-08-01T00:00:00Z",
        status = sessionStatus,
        assignmentJson = "{\"tasks\":[]}",
        assignmentHash = "sha256:test",
        workflowState = workflowState,
        resumeState = resumeState,
    )
}
