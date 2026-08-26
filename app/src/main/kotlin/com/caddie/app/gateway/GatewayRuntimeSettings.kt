package com.caddie.app.gateway

import com.caddie.model.gateway.GatewayConfiguration
import com.caddie.model.gateway.GatewayCredentialProvider
import com.caddie.model.gateway.GatewayEndpoint
import com.caddie.model.gateway.ModelProfile

/**
 * Device-owner settings for the OpenAI-compatible model endpoint used by normal mode.
 *
 * The bearer credential is deliberately not part of this value. Callers can safely render
 * and persist this object without accidentally exposing a secret.
 */
data class GatewayRuntimeSettings(
    val baseUrl: String = "",
    val modelId: String = "",
    val thinkingEnabled: Boolean = false,
    val credentialConfigured: Boolean = false,
) {
    val isConfigured: Boolean
        get() = normalizedBaseUrl != null && modelId.isNotBlank()

    /** Canonical form persisted and used for requests after validation succeeds. */
    val normalizedBaseUrl: String?
        get() = GatewayEndpoint.normalize(baseUrl)

    fun gatewayConfiguration(
        credentialProvider: GatewayCredentialProvider,
    ): GatewayConfiguration? {
        val normalizedBaseUrl = normalizedBaseUrl ?: return null
        if (modelId.isBlank()) return null
        return GatewayConfiguration(
            baseUrl = normalizedBaseUrl,
            profile = ModelProfile(profileId = "normal", modelId = modelId.trim()),
            thinkingEnabled = thinkingEnabled,
            credentialProvider = credentialProvider,
        )
    }

    fun validationMessage(): String? = when {
        baseUrl.isBlank() -> "Enter the model server URL."
        normalizedBaseUrl == null ->
            "Use an absolute http:// or https:// URL without credentials, query parameters, or fragments."
        modelId.isBlank() -> "Enter the model ID served by that endpoint."
        else -> null
    }

}

/** Single source shared by the model client and its connectivity check. */
interface GatewayRuntimeSettingsSource {
    fun snapshot(): GatewayRuntimeSettings

    fun authorizationHeader(): String?

    fun gatewayConfiguration(): GatewayConfiguration? =
        snapshot().gatewayConfiguration(
            GatewayCredentialProvider { authorizationHeader() },
        )
}
