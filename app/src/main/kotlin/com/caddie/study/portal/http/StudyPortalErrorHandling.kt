package com.caddie.study.portal.http

import com.caddie.study.runtime.coordinator.ArmedTrialCoordinator
import com.caddie.study.runtime.workflow.PortalWorkflow
import com.caddie.study.store.ConsentNotReadyException
import com.caddie.study.store.RevisionConflict
import com.caddie.study.store.SessionNotFoundException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.application.hooks.CallFailed
import io.ktor.server.response.respond
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

private val StudyPortalErrorHandling = createApplicationPlugin("StudyPortalErrorHandling") {
    on(CallFailed) { call, cause ->
        if (cause is CancellationException) throw cause
        val (status, error) = when (cause) {
            is SessionNotFoundException -> HttpStatusCode.NotFound to "session_not_found"
            is RevisionConflict -> HttpStatusCode.Conflict to "revision_conflict"
            is ConsentNotReadyException -> HttpStatusCode.Conflict to "consent_not_ready"
            is PortalWorkflow.InvalidPortalTransition ->
                HttpStatusCode.Conflict to "invalid_transition"
            is ArmedTrialCoordinator.CoordinatorConflictError,
            is ArmedTrialCoordinator.InvalidTransitionError ->
                HttpStatusCode.Conflict to "state_conflict"
            is IllegalArgumentException -> HttpStatusCode.BadRequest to "invalid_request"
            else -> HttpStatusCode.InternalServerError to "internal_error"
        }
        call.respond(status, PortalErrorResponse(error = error))
    }
}

/** Converts native domain failures into stable, non-sensitive portal responses. */
fun Application.installStudyPortalErrorHandling() {
    install(StudyPortalErrorHandling)
}

@Serializable
private data class PortalErrorResponse(
    val ok: Boolean = false,
    val error: String,
)
