package com.caddie.model.gateway

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Validates and canonicalizes the base URL for an OpenAI-compatible model gateway.
 *
 * The configured URL identifies a server or a server path only. Credentials, query
 * parameters, and fragments are intentionally not accepted because Caddie builds the
 * API paths itself and stores credentials separately in the Android Keystore.
 */
object GatewayEndpoint {
    fun normalize(value: String): String? {
        val candidate = value.trim()
        if (candidate.isEmpty()) return null
        val url = candidate.toHttpUrlOrNull() ?: return null
        if (url.scheme !in SUPPORTED_SCHEMES) return null
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) return null
        if (url.query != null || url.fragment != null) return null
        if (runCatching { java.net.URI(candidate).rawUserInfo != null }.getOrDefault(true)) return null

        return url.toString().removeSuffix("/").takeIf { it.isNotEmpty() }
    }

    /** Returns the health-check endpoint only for a valid, normalized gateway base URL. */
    fun modelsUrl(baseUrl: String): HttpUrl? =
        normalize(baseUrl)
            ?.toHttpUrlOrNull()
            ?.newBuilder()
            ?.addPathSegments("v1/models")
            ?.build()

    private val SUPPORTED_SCHEMES = setOf("http", "https")
}
