package com.caddie.studyportal

import com.caddie.study.portal.auth.PortalPinCredential
import com.caddie.study.portal.auth.PortalPinStore
import com.caddie.study.portal.auth.StudyPortalAuth
import com.caddie.study.portal.http.INVESTIGATOR_COOKIE
import com.caddie.study.portal.http.HANDOFF_RECOVERY_HEADER
import com.caddie.study.portal.http.StudyPortalHandoffs
import com.caddie.study.portal.http.installStudyPortalAccessRoutes
import com.caddie.study.portal.http.installStudyPortalSecurityHeaders
import com.caddie.study.portal.http.installStudyPortalErrorHandling
import com.caddie.study.portal.http.isLoopbackAddress
import com.caddie.study.portal.http.installStudyRoutes
import io.ktor.client.request.get
import io.ktor.client.request.header
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies the unchanged PIN, cookie, and CSRF contract used by the portal frontend. */
class StudyPortalAuthenticationRoutesTest {
    @Test
    fun passwordlessPortalCanRecoverOrReleaseALostParticipantHandoff() = testApplication {
        val handoffs = StudyPortalHandoffs()
        val api = FakeStudyPortalApi().apply { workflowState = "task_card" }
        application {
            installStudyPortalErrorHandling()
            install(ContentNegotiation) { json(Json { encodeDefaults = true }) }
            routing {
                installStudyPortalAccessRoutes(api = api, auth = null, handoffs = handoffs)
                installStudyRoutes(api, handoffs, auth = null)
            }
        }
        val lost = handoffs.issue(sessionId = 1, workflowState = "task_card")

        assertEquals(
            HttpStatusCode.Forbidden,
            client.post("/study/app/api/participant/recover-access").status,
        )
        assertNotNull(handoffs.resolve(lost.token))

        val recovered = client.post("/study/app/api/participant/recover-access") {
            header(HANDOFF_RECOVERY_HEADER, "1")
        }
        val recoveredCookie = recovered.headers.getAll(HttpHeaders.SetCookie)
            .orEmpty()
            .single { it.startsWith("caddie_handoff=") }
            .substringBefore(';')

        assertEquals(HttpStatusCode.OK, recovered.status)
        assertNull(handoffs.resolve(lost.token))
        assertEquals(
            "participant",
            client.get("/study/app/api/bootstrap") {
                header(HttpHeaders.Cookie, recoveredCookie)
            }.json()["role"]!!.jsonPrimitive.content,
        )

        val released = client.post("/study/app/api/participant/release-access") {
            header(HttpHeaders.Cookie, recoveredCookie)
            header(HANDOFF_RECOVERY_HEADER, "1")
        }

        assertEquals(HttpStatusCode.OK, released.status)
        assertTrue(released.headers.getAll(HttpHeaders.SetCookie).orEmpty().any {
            it.startsWith("caddie_handoff=") && it.contains("Max-Age=0", ignoreCase = true)
        })
        assertEquals(
            "investigator",
            client.get("/study/app/api/bootstrap").json()["role"]!!.jsonPrimitive.content,
        )
        assertEquals("task_card", api.workflowState)
    }

    @Test
    fun transientRecoveryFailureKeepsTheExistingHandoff() = testApplication {
        val handoffs = StudyPortalHandoffs()
        val api = FakeStudyPortalApi().apply {
            workflowState = "task_card"
            getSessionFailure = IllegalStateException("temporary database failure")
        }
        application {
            installStudyPortalErrorHandling()
            install(ContentNegotiation) { json(Json { encodeDefaults = true }) }
            routing { installStudyPortalAccessRoutes(api = api, auth = null, handoffs = handoffs) }
        }
        val active = handoffs.issue(sessionId = 1, workflowState = "task_card")

        val response = client.post("/study/app/api/participant/recover-access") {
            header(HANDOFF_RECOVERY_HEADER, "1")
        }

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertNotNull(handoffs.resolve(active.token))
    }

    @Test
    fun digitalConsentKeepsTheBrowserInParticipantMode() = testApplication {
        val handoffs = StudyPortalHandoffs()
        val api = FakeStudyPortalApi().apply { workflowState = "consent" }
        application {
            installStudyPortalSecurityHeaders()
            installStudyPortalErrorHandling()
            install(ContentNegotiation) { json(Json { encodeDefaults = true }) }
            routing {
                installStudyPortalAccessRoutes(api = api, auth = null, handoffs = handoffs)
                installStudyRoutes(api, handoffs, auth = null)
            }
        }
        val capability = handoffs.issue(sessionId = 1, workflowState = "consent")

        val consent = client.post("/study/app/api/participant/consent") {
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
        val participantCookie = consent.headers.getAll(HttpHeaders.SetCookie)
            .orEmpty()
            .single { it.startsWith("caddie_handoff=") }
            .substringBefore(';')
        val bootstrap = client.get("/study/app/api/bootstrap") {
            header(HttpHeaders.Cookie, participantCookie)
        }

        assertEquals(HttpStatusCode.Created, consent.status)
        assertEquals("participant", bootstrap.json()["role"]!!.jsonPrimitive.content)
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.get("/study/app/api/investigator/state") {
                header(HttpHeaders.Cookie, participantCookie)
            }.status,
        )
    }

    @Test
    fun passwordlessPortalOpensAsInvestigatorAndAllowsSessionCreation() = testApplication {
        val handoffs = StudyPortalHandoffs()
        val api = FakeStudyPortalApi()
        application {
            installStudyPortalSecurityHeaders()
            installStudyPortalErrorHandling()
            install(ContentNegotiation) { json(Json { encodeDefaults = true }) }
            routing {
                installStudyPortalAccessRoutes(api = api, auth = null, handoffs = handoffs)
                installStudyRoutes(api, handoffs, auth = null)
            }
        }

        val bootstrap = client.get("/study/app/api/bootstrap")

        assertEquals(HttpStatusCode.OK, bootstrap.status)
        assertEquals("investigator", bootstrap.json()["role"]!!.jsonPrimitive.content)
        assertEquals(false, bootstrap.json()["pin_configured"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(
            HttpStatusCode.OK,
            client.get("/study/app/api/investigator/state").status,
        )
        assertEquals(
            HttpStatusCode.OK,
            client.get("/study/app/api/participants").status,
        )
        assertEquals(
            HttpStatusCode.OK,
            client.get("/study/app/api/preview").status,
        )
        assertEquals(
            HttpStatusCode.OK,
            client.get("/study/app/api/participants/P01").status,
        )
        assertEquals(
            HttpStatusCode.OK,
            client.get("/study/app/api/participants/P99").status,
        )
        assertEquals(
            HttpStatusCode.BadRequest,
            client.get("/study/app/api/participants/P00").status,
        )
        assertEquals(
            HttpStatusCode.Created,
            client.post("/study/app/api/sessions") {
                header(HttpHeaders.ContentType, "application/json")
                setBody("""{"participant_id":"P01","mode":"live"}""")
            }.status,
        )
        handoffs.revokeAll()
        val corrected = client.post(
            "/study/app/api/sessions/1/responses/task_questionnaire/0/correct",
        ) {
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"answers":{"trust":6},"missing":{},"expected_revision":1}""")
        }
        assertEquals(HttpStatusCode.OK, corrected.status)
        assertEquals(
            6,
            api.correctedResponseAnswers!!["trust"]!!.jsonPrimitive.content.toInt(),
        )
        assertEquals(HttpStatusCode.OK, client.get("/study/app/api/test-runs/options").status)
        val testRun = client.post("/study/app/api/test-runs") {
            header(HttpHeaders.ContentType, "application/json")
            setBody(
                """{"task_id":"task_maps_messenger","condition":"c1_stepwise","inject_error":true}""",
            )
        }
        assertEquals(HttpStatusCode.Created, testRun.status)
        assertEquals("task_maps_messenger", api.startedTestTask)
        assertEquals(HttpStatusCode.NotFound, client.post("/study/app/api/setup-pin").status)
        assertEquals(HttpStatusCode.NotFound, client.post("/study/app/api/login").status)

        api.workflowState = "consent"
        val handoff = handoffs.issue(sessionId = 1, workflowState = "consent")
        assertEquals(
            "participant_expired",
            client.get("/study/app/api/bootstrap").json()["role"]!!.jsonPrimitive.content,
        )
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.get("/study/app/api/exports/research").status,
        )
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.post("/study/app/api/participants/P01/delete") {
                header(HttpHeaders.ContentType, "application/json")
                setBody("""{"confirmed":true}""")
            }.status,
        )
        val participantBootstrap = client.get("/study/app/api/bootstrap") {
            header(HttpHeaders.Cookie, "caddie_handoff=${handoff.token}")
        }
        assertEquals(
            "participant",
            participantBootstrap.json()["role"]!!.jsonPrimitive.content,
        )
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.get("/study/app/api/investigator/state") {
                header(HttpHeaders.Cookie, "caddie_handoff=${handoff.token}")
            }.status,
        )
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.get("/study/app/api/preview") {
                header(HttpHeaders.Cookie, "caddie_handoff=${handoff.token}")
            }.status,
        )
        api.workflowState = "technical_hold"
        api.resumeState = "trial_running"
        val returned = client.post("/study/app/api/participant/return-to-investigator") {
            header(HttpHeaders.Cookie, "caddie_handoff=${handoff.token}")
        }
        assertEquals(
            HttpStatusCode.OK,
            returned.status,
        )
        assertTrue(returned.headers.getAll(HttpHeaders.SetCookie).orEmpty().any {
            it.startsWith("caddie_handoff=") && it.contains("Max-Age=0", ignoreCase = true)
        })
        assertEquals(HttpStatusCode.OK, client.get("/study/app/api/investigator/state").status)

        api.workflowState = "training"
        api.resumeState = null
        val recoveredHandoff = handoffs.issue(sessionId = 1, workflowState = "consent")
        val recovered = client.get("/study/app/api/bootstrap") {
            header(HttpHeaders.Cookie, "caddie_handoff=${recoveredHandoff.token}")
        }
        assertEquals("participant", recovered.json()["role"]!!.jsonPrimitive.content)
        assertFalse(
            recovered.headers.getAll(HttpHeaders.SetCookie).orEmpty().any {
                it.startsWith("caddie_handoff=")
            },
        )
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.get("/study/app/api/investigator/state") {
                header(HttpHeaders.Cookie, "caddie_handoff=${recoveredHandoff.token}")
            }.status,
        )
    }

    @Test
    fun completedParticipantFlowReleasesInvestigatorAccessAutomatically() = testApplication {
        val handoffs = StudyPortalHandoffs()
        val api = FakeStudyPortalApi().apply { workflowState = "debrief" }
        application {
            installStudyPortalErrorHandling()
            install(ContentNegotiation) { json(Json { encodeDefaults = true }) }
            routing { installStudyRoutes(api, handoffs, auth = null) }
        }
        val capability = handoffs.issue(sessionId = 1, workflowState = "debrief")

        val completed = client.post("/study/app/api/participant/continue") {
            header(HttpHeaders.ContentType, "application/json")
            header(HttpHeaders.Cookie, "caddie_handoff=${capability.token}")
            setBody("""{"expected_revision":0}""")
        }

        assertEquals(HttpStatusCode.OK, completed.status)
        assertEquals("completed", completed.json()["session"]!!.jsonObject["workflow_state"]!!.jsonPrimitive.content)
        assertTrue(completed.headers.getAll(HttpHeaders.SetCookie).orEmpty().any {
            it.startsWith("caddie_handoff=") && it.contains("Max-Age=0", ignoreCase = true)
        })
        assertEquals(null, handoffs.resolve(capability.token))
        assertEquals(HttpStatusCode.OK, client.get("/study/app/api/investigator/state").status)
    }

    @Test
    fun failedTrialCanBeRetriedFromParticipantModeWithoutDeletingTheSession() = testApplication {
        val handoffs = StudyPortalHandoffs()
        val api = FakeStudyPortalApi().apply {
            workflowState = "technical_hold"
            resumeState = "trial_running"
        }
        application {
            installStudyPortalErrorHandling()
            install(ContentNegotiation) { json(Json { encodeDefaults = true }) }
            routing { installStudyRoutes(api, handoffs, auth = null) }
        }
        val capability = handoffs.issue(sessionId = 1, workflowState = "technical_hold")

        val retried = client.post("/study/app/api/participant/continue") {
            header(HttpHeaders.ContentType, "application/json")
            header(HttpHeaders.Cookie, "caddie_handoff=${capability.token}")
            setBody("""{"expected_revision":0}""")
        }

        assertEquals(HttpStatusCode.OK, retried.status)
        assertEquals(
            "task_card",
            retried.json()["session"]!!.jsonObject["workflow_state"]!!.jsonPrimitive.content,
        )
        assertEquals(1, api.participantContinueCalls)
        assertEquals(null, api.deletedParticipantId)
    }

    @Test
    fun bootstrapClearsAnInvalidParticipantCookie() = testApplication {
        val handoffs = StudyPortalHandoffs()
        val api = FakeStudyPortalApi()
        application {
            install(ContentNegotiation) { json(Json { encodeDefaults = true }) }
            routing { installStudyPortalAccessRoutes(api, auth = null, handoffs = handoffs) }
        }

        val bootstrap = client.get("/study/app/api/bootstrap") {
            header(HttpHeaders.Cookie, "caddie_handoff=expired")
        }

        assertEquals("investigator", bootstrap.json()["role"]!!.jsonPrimitive.content)
        assertTrue(bootstrap.headers.getAll(HttpHeaders.SetCookie).orEmpty().any {
            it.startsWith("caddie_handoff=") && it.contains("Max-Age=0", ignoreCase = true)
        })
    }

    @Test
    fun loopbackSetupRecognizesCompressedAndExpandedIpv6() {
        assertTrue(isLoopbackAddress("127.0.0.1"))
        assertTrue(isLoopbackAddress("::1"))
        assertTrue(isLoopbackAddress("0:0:0:0:0:0:0:1"))
        assertFalse(isLoopbackAddress("100.64.0.42"))
        assertFalse(isLoopbackAddress("0.0.0.0"))
    }

    @Test
    fun pinProtectedRecoveryRequiresCsrfAndTheRecoveryHeader() = testApplication {
        val auth = StudyPortalAuth(MemoryPinStore())
        val handoffs = StudyPortalHandoffs()
        val api = FakeStudyPortalApi().apply { workflowState = "consent" }
        application {
            installStudyPortalErrorHandling()
            install(ContentNegotiation) { json(Json { encodeDefaults = true }) }
            routing {
                installStudyPortalAccessRoutes(
                    api = api,
                    auth = auth,
                    handoffs = handoffs,
                    allowPinSetup = { true },
                )
            }
        }
        val setup = client.post("/study/app/api/setup-pin") {
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"pin":"123456"}""")
        }
        val csrf = setup.json()["csrf_token"]!!.jsonPrimitive.content
        val cookies = setup.headers.getAll(HttpHeaders.SetCookie).orEmpty()
        val investigatorCookie = cookies.first { it.startsWith("$INVESTIGATOR_COOKIE=") }
            .substringBefore(';')
        val csrfCookie = cookies.first { it.startsWith("caddie_csrf=") }.substringBefore(';')
        handoffs.issue(sessionId = 1, workflowState = "consent")

        assertEquals(
            HttpStatusCode.Unauthorized,
            client.post("/study/app/api/participant/recover-access") {
                header(HANDOFF_RECOVERY_HEADER, "1")
            }.status,
        )
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.post("/study/app/api/participant/recover-access") {
                header(HttpHeaders.Cookie, "$investigatorCookie; $csrfCookie")
                header("X-CSRF-Token", "wrong")
                header(HANDOFF_RECOVERY_HEADER, "1")
            }.status,
        )
        assertEquals(
            HttpStatusCode.OK,
            client.post("/study/app/api/participant/recover-access") {
                header(HttpHeaders.Cookie, "$investigatorCookie; $csrfCookie")
                header("X-CSRF-Token", csrf)
                header(HANDOFF_RECOVERY_HEADER, "1")
            }.status,
        )
        assertEquals(
            HttpStatusCode.Forbidden,
            client.post("/study/app/api/participant/release-access") {
                header(HttpHeaders.Cookie, "$investigatorCookie; $csrfCookie")
                header("X-CSRF-Token", csrf)
            }.status,
        )
        assertTrue(handoffs.hasActiveCapability())
        assertEquals(
            HttpStatusCode.OK,
            client.post("/study/app/api/participant/release-access") {
                header(HttpHeaders.Cookie, "$investigatorCookie; $csrfCookie")
                header("X-CSRF-Token", csrf)
                header(HANDOFF_RECOVERY_HEADER, "1")
            }.status,
        )
        assertFalse(handoffs.hasActiveCapability())
    }

    @Test
    fun setupLoginAndCsrfProtectInvestigatorRoutes() = testApplication {
        val auth = StudyPortalAuth(MemoryPinStore())
        val handoffs = StudyPortalHandoffs()
        val api = FakeStudyPortalApi()
        application {
            installStudyPortalSecurityHeaders()
            install(ContentNegotiation) { json(Json { encodeDefaults = true }) }
            routing {
                installStudyPortalAccessRoutes(
                    api = api,
                    auth = auth,
                    handoffs = handoffs,
                    allowPinSetup = { true },
                )
                installStudyRoutes(api, handoffs, auth)
            }
        }

        val initial = client.get("/study/app/api/bootstrap")
        assertEquals("no-store", initial.headers[HttpHeaders.CacheControl])
        assertEquals(false, initial.json()["pin_configured"]!!.jsonPrimitive.content.toBoolean())
        assertEquals("null", initial.json()["role"].toString())
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.get("/study/app/api/investigator/state").status,
        )

        val setup = client.post("/study/app/api/setup-pin") {
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"pin":"123456"}""")
        }
        val csrf = setup.json()["csrf_token"]!!.jsonPrimitive.content
        val cookies = setup.headers.getAll(HttpHeaders.SetCookie).orEmpty()
        val investigatorCookie = cookies.first { it.startsWith("$INVESTIGATOR_COOKIE=") }
            .substringBefore(';')
        val csrfCookie = cookies.first { it.startsWith("caddie_csrf=") }.substringBefore(';')

        assertEquals(HttpStatusCode.OK, setup.status)
        assertNotNull(csrf)
        assertEquals(
            HttpStatusCode.OK,
            client.get("/study/app/api/investigator/state") {
                header(HttpHeaders.Cookie, investigatorCookie)
            }.status,
        )
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.post("/study/app/api/sessions") {
                header(HttpHeaders.ContentType, "application/json")
                header(HttpHeaders.Cookie, "$investigatorCookie; $csrfCookie")
                setBody("""{"participant_id":"P01","mode":"live"}""")
            }.status,
        )
        assertEquals(
            HttpStatusCode.Created,
            client.post("/study/app/api/sessions") {
                header(HttpHeaders.ContentType, "application/json")
                header(HttpHeaders.Cookie, "$investigatorCookie; $csrfCookie")
                header("X-CSRF-Token", csrf)
                setBody("""{"participant_id":"P01","mode":"live"}""")
            }.status,
        )

        api.workflowState = "consent"
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.get("/study/app/api/investigator/state") {
                header(HttpHeaders.Cookie, investigatorCookie)
            }.status,
        )

        val lostHandoff = handoffs.issue(sessionId = 1, workflowState = "consent")
        assertEquals(
            HttpStatusCode.Forbidden,
            client.post("/study/app/api/participant/recover-access").status,
        )
        val recoveryWithRevokedInvestigatorSession = client.post("/study/app/api/participant/recover-access") {
            header(HttpHeaders.Cookie, "$investigatorCookie; $csrfCookie")
            header("X-CSRF-Token", csrf)
            header(HANDOFF_RECOVERY_HEADER, "1")
        }
        assertEquals(HttpStatusCode.Unauthorized, recoveryWithRevokedInvestigatorSession.status)
        assertNotNull(handoffs.resolve(lostHandoff.token))
    }
}

private suspend fun io.ktor.client.statement.HttpResponse.json() =
    Json.parseToJsonElement(bodyAsText()).jsonObject

private class MemoryPinStore : PortalPinStore {
    private var credential: PortalPinCredential? = null
    override fun read(): PortalPinCredential? = credential
    override fun writeIfAbsent(credential: PortalPinCredential): Boolean {
        if (this.credential != null) return false
        this.credential = credential
        return true
    }
}
