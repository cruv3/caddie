package com.caddie.study.portal.http

import com.caddie.study.portal.StudyPortalApi
import com.caddie.study.portal.auth.InvestigatorLoginException
import com.caddie.study.portal.auth.InvestigatorSession
import com.caddie.study.portal.auth.StudyPortalAuth
import com.caddie.study.store.SessionNotFoundException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.InetAddress

/** Installs bootstrap plus optional PIN login routes for the embedded portal. */
fun Routing.installStudyPortalAccessRoutes(
    api: StudyPortalApi,
    auth: StudyPortalAuth?,
    handoffs: StudyPortalHandoffs,
    runtimeSnapshot: () -> JsonObject = { JsonObject(emptyMap()) },
    allowPinSetup: (ApplicationCall) -> Boolean = { call ->
        isLoopbackAddress(call.request.local.remoteHost)
    },
) {
    get("/study/app/api/bootstrap") {
        val investigatorToken = call.request.cookies[INVESTIGATOR_COOKIE]
        val investigator = auth?.authorize(investigatorToken, mutate = false) ?: true
        val handoffToken = call.request.cookies[HANDOFF_COOKIE]
        val capability = handoffs.resolve(handoffToken)
        if (!handoffToken.isNullOrBlank() && capability == null) {
            call.clearHandoffCookie()
        }
        val participant = capability?.let {
            runCatching { api.getSession(it.sessionId) }.getOrNull()
        }
        val participantSessionActive = handoffs.hasActiveCapability()
        val role = when {
            participant != null -> JsonPrimitive("participant")
            participantSessionActive -> JsonPrimitive("participant_expired")
            investigator -> JsonPrimitive("investigator")
            else -> JsonNull
        }
        val payload = mutableMapOf<String, kotlinx.serialization.json.JsonElement>(
            "ok" to JsonPrimitive(true),
            "pin_configured" to JsonPrimitive(auth?.pinConfigured ?: false),
            "role" to role,
            "caddie_native" to runtimeSnapshot(),
        )
        if (investigator) auth?.csrfToken(investigatorToken)?.let {
            payload["csrf_token"] = JsonPrimitive(it)
        }
        call.respond(JsonObject(payload))
    }

    post("/study/app/api/participant/recover-access") {
        if (!call.requireHandoffRecoveryAuthorization(auth)) return@post
        val current = handoffs.activeCapability()
        if (current == null) {
            call.clearHandoffCookie()
            call.respond(HttpStatusCode.Conflict, AccessError(error = "no_active_handoff"))
            return@post
        }
        val session = try {
            api.getSession(current.sessionId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: SessionNotFoundException) {
            handoffs.revoke(current.token)
            call.clearHandoffCookie()
            call.respond(HttpStatusCode.Conflict, AccessError(error = "participant_session_inactive"))
            return@post
        } catch (_: Exception) {
            call.respond(HttpStatusCode.ServiceUnavailable, AccessError(error = "recovery_unavailable"))
            return@post
        }
        if (session.status != "in_progress" && session.workflowState != "completed") {
            handoffs.revoke(current.token)
            call.clearHandoffCookie()
            call.respond(HttpStatusCode.Conflict, AccessError(error = "participant_session_inactive"))
            return@post
        }
        val capability = handoffs.recoverActive(current.token)
        if (capability == null) {
            call.respond(HttpStatusCode.Conflict, AccessError(error = "handoff_changed"))
            return@post
        }
        call.setHandoffCookie(capability.token)
        call.respond(AccessOk())
    }

    post("/study/app/api/participant/release-access") {
        if (!call.requireHandoffRecoveryAuthorization(auth)) return@post
        handoffs.revokeAll()
        call.clearHandoffCookie()
        call.respond(AccessOk())
    }

    if (auth != null) {
        post("/study/app/api/setup-pin") {
            if (!allowPinSetup(call)) {
                call.respond(HttpStatusCode.Forbidden, AccessError(error = "loopback_required"))
                return@post
            }
            val pin = call.receive<PinRequest>().pin
            val session = try {
                withContext(Dispatchers.Default) {
                    synchronized(handoffs) {
                        auth.setupPin(pin, call.clientKey()).also { handoffs.revokeAll() }
                    }
                }
            } catch (_: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, AccessError(error = "invalid_pin"))
                return@post
            } catch (_: IllegalStateException) {
                call.respond(HttpStatusCode.Conflict, AccessError(error = "pin_already_configured"))
                return@post
            }
            call.respondLogin(session)
        }

        post("/study/app/api/login") {
            val pin = call.receive<PinRequest>().pin
            val session = try {
                withContext(Dispatchers.Default) {
                    synchronized(handoffs) {
                        auth.login(pin, call.clientKey()).also { handoffs.revokeAll() }
                    }
                }
            } catch (_: InvestigatorLoginException) {
                call.respond(HttpStatusCode.Unauthorized, AccessError(error = "invalid_credentials"))
                return@post
            }
            call.respondLogin(session)
        }

        post("/study/app/api/logout") {
            if (!call.requireInvestigator(auth, handoffs, mutate = true)) return@post
            auth.logout(call.request.cookies[INVESTIGATOR_COOKIE])
            call.clearAccessCookies()
            call.respond(AccessOk())
        }
    }
}

private suspend fun ApplicationCall.requireHandoffRecoveryAuthorization(
    auth: StudyPortalAuth?,
): Boolean {
    if (request.headers[HANDOFF_RECOVERY_HEADER] != "1") {
        respond(HttpStatusCode.Forbidden, AccessError(error = "same_origin_required"))
        return false
    }
    if (auth == null) return true
    val csrfCookie = request.cookies[CSRF_COOKIE]
    val csrfHeader = request.headers[CSRF_HEADER]
    val authorized = csrfCookie == csrfHeader &&
        auth.authorize(request.cookies[INVESTIGATOR_COOKIE], csrfHeader, mutate = true)
    if (!authorized) respond(HttpStatusCode.Unauthorized, AccessError(error = "unauthorized"))
    return authorized
}

internal suspend fun ApplicationCall.requireInvestigator(
    auth: StudyPortalAuth?,
    handoffs: StudyPortalHandoffs? = null,
    mutate: Boolean,
): Boolean {
    if (handoffs?.hasActiveCapability() == true) {
        respond(HttpStatusCode.Unauthorized, AccessError(error = "participant_mode"))
        return false
    }
    if (auth == null) {
        val participantMode = !request.cookies[HANDOFF_COOKIE].isNullOrBlank()
        if (participantMode) respond(HttpStatusCode.Unauthorized, AccessError(error = "participant_mode"))
        return !participantMode
    }
    val token = request.cookies[INVESTIGATOR_COOKIE]
    val csrfCookie = request.cookies[CSRF_COOKIE]
    val csrfHeader = request.headers[CSRF_HEADER]
    val authorized = if (mutate && csrfCookie != csrfHeader) {
        false
    } else {
        auth.authorize(token, csrfHeader, mutate)
    }
    if (!authorized) respond(HttpStatusCode.Unauthorized, AccessError(error = "unauthorized"))
    return authorized
}

private suspend fun ApplicationCall.respondLogin(session: InvestigatorSession) {
    response.cookies.append(
        INVESTIGATOR_COOKIE, session.token, path = "/study/app", httpOnly = true,
        extensions = mapOf("SameSite" to "Strict"),
    )
    response.cookies.append(
        CSRF_COOKIE, session.csrfToken, path = "/study/app", httpOnly = false,
        extensions = mapOf("SameSite" to "Strict"),
    )
    response.cookies.append(
        HANDOFF_COOKIE, "", path = "/study/app", maxAge = 0, httpOnly = true,
        extensions = mapOf("SameSite" to "Strict"),
    )
    respond(AccessLoginResponse(csrf_token = session.csrfToken))
}

private fun ApplicationCall.clearAccessCookies() {
    response.cookies.append(
        INVESTIGATOR_COOKIE, "", path = "/study/app", maxAge = 0, httpOnly = true,
        extensions = mapOf("SameSite" to "Strict"),
    )
    response.cookies.append(
        CSRF_COOKIE, "", path = "/study/app", maxAge = 0, httpOnly = false,
        extensions = mapOf("SameSite" to "Strict"),
    )
    response.cookies.append(
        HANDOFF_COOKIE, "", path = "/study/app", maxAge = 0, httpOnly = true,
        extensions = mapOf("SameSite" to "Strict"),
    )
}

private fun ApplicationCall.clientKey(): String = request.local.remoteHost

internal fun isLoopbackAddress(host: String): Boolean {
    val normalized = host.trim().removePrefix("[").removeSuffix("]")
    if (normalized.equals("localhost", ignoreCase = true)) return true
    if (normalized.contains(':')) {
        return runCatching { InetAddress.getByName(normalized).isLoopbackAddress }.getOrDefault(false)
    }
    val octets = normalized.split('.').mapNotNull(String::toIntOrNull)
    return octets.size == 4 && octets.all { it in 0..255 } && octets.first() == 127
}

@Serializable
private data class PinRequest(val pin: String)

@Serializable
private data class AccessOk(val ok: Boolean = true)

@Serializable
private data class AccessLoginResponse(
    val ok: Boolean = true,
    val role: String = "investigator",
    val csrf_token: String,
)

@Serializable
private data class AccessError(val ok: Boolean = false, val error: String)

internal const val INVESTIGATOR_COOKIE = "caddie_investigator"
internal const val CSRF_COOKIE = "caddie_csrf"
internal const val CSRF_HEADER = "X-CSRF-Token"
internal const val HANDOFF_RECOVERY_HEADER = "X-Caddie-Handoff-Recovery"
