package com.caddie.studyportal

import com.caddie.study.portal.http.installStudyPortalErrorHandling
import com.caddie.study.store.RevisionConflict
import com.caddie.study.store.SessionNotFoundException
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.application.call
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies stable frontend-safe status codes for native portal failures. */
class StudyPortalErrorHandlingTest {
    @Test
    fun domainFailuresUseTheExistingPortalErrorContract() = testApplication {
        application {
            install(ContentNegotiation) { json(Json) }
            installStudyPortalErrorHandling()
            routing {
                get("/revision") { throw RevisionConflict("secret internal detail") }
                get("/missing") { throw SessionNotFoundException(42) }
                get("/invalid") { throw IllegalArgumentException("bad input detail") }
                get("/unexpected") { throw RuntimeException("database path must not leak") }
                get("/ok") { call.respondText("ok") }
            }
        }

        val revision = client.get("/revision")
        val missing = client.get("/missing")
        val invalid = client.get("/invalid")
        val unexpected = client.get("/unexpected")

        assertEquals(HttpStatusCode.Conflict, revision.status)
        assertTrue(revision.bodyAsText().contains("revision_conflict"))
        assertFalse(revision.bodyAsText().contains("secret internal detail"))
        assertEquals(HttpStatusCode.NotFound, missing.status)
        assertTrue(missing.bodyAsText().contains("session_not_found"))
        assertEquals(HttpStatusCode.BadRequest, invalid.status)
        assertTrue(invalid.bodyAsText().contains("invalid_request"))
        assertEquals(HttpStatusCode.InternalServerError, unexpected.status)
        assertTrue(unexpected.bodyAsText().contains("internal_error"))
        assertFalse(unexpected.bodyAsText().contains("database path"))
    }
}
