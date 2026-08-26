package com.caddie.study.portal.http

import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install

private val StudyPortalSecurityHeaders = createApplicationPlugin("StudyPortalSecurityHeaders") {
    onCall { call ->
        call.response.headers.append(
            "Content-Security-Policy",
            "default-src 'self'; script-src 'self'; style-src 'self'; " +
                "img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; " +
                "base-uri 'none'; form-action 'self'",
        )
        call.response.headers.append("Referrer-Policy", "no-referrer")
        call.response.headers.append("X-Content-Type-Options", "nosniff")
        call.response.headers.append("X-Frame-Options", "DENY")
        call.response.headers.append(HttpHeaders.CacheControl, "no-store")
    }
}

/** Applies the hardened no-cache browser policy from the existing study portal. */
fun Application.installStudyPortalSecurityHeaders() {
    install(StudyPortalSecurityHeaders)
}
