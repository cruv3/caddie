package com.caddie.model.gateway

/** Identifies one model and its logical use within the app. */
data class ModelProfile(
    val profileId: String,
    val modelId: String,
) {
    init {
        require(profileId.isNotBlank()) { "profileId must not be blank" }
        require(modelId.isNotBlank()) { "modelId must not be blank" }
    }
}

/** Defines retry limits and backoff timing for gateway requests. */
data class RetryPolicy(
    val maxAttempts: Int = 3,
    val initialBackoffMillis: Long = 250,
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be at least one" }
        require(initialBackoffMillis >= 0) {
            "initialBackoffMillis must not be negative"
        }
    }

    fun backoffMillisAfter(failedAttempt: Int): Long =
        initialBackoffMillis * failedAttempt
}

/** Holds and validates the endpoint, model, timeout, and retry settings. */
class GatewayConfiguration(
    baseUrl: String,
    val profile: ModelProfile,
    val connectTimeoutMillis: Long = 10_000,
    val writeTimeoutMillis: Long = 10_000,
    val streamIdleTimeoutMillis: Long = 180_000,
    val retryPolicy: RetryPolicy = RetryPolicy(),
    val thinkingEnabled: Boolean? = null,
    val credentialProvider: GatewayCredentialProvider =
        GatewayCredentialProvider.None,
) {
    val baseUrl: String = requireNotNull(GatewayEndpoint.normalize(baseUrl)) {
        "baseUrl must be an absolute HTTP(S) URL without credentials, query parameters, or fragments"
    }
    val chatCompletionsUrl: String =
        "${this.baseUrl}/v1/chat/completions"

    init {
        require(connectTimeoutMillis > 0) {
            "connectTimeoutMillis must be positive"
        }
        require(writeTimeoutMillis > 0) {
            "writeTimeoutMillis must be positive"
        }
        require(streamIdleTimeoutMillis > 0) {
            "streamIdleTimeoutMillis must be positive"
        }
    }

    override fun toString(): String =
        "GatewayConfiguration(" +
            "baseUrl=$baseUrl, " +
            "profile=$profile, " +
            "connectTimeoutMillis=$connectTimeoutMillis, " +
            "writeTimeoutMillis=$writeTimeoutMillis, " +
            "streamIdleTimeoutMillis=$streamIdleTimeoutMillis, " +
            "retryPolicy=$retryPolicy, " +
            "thinkingEnabled=$thinkingEnabled, " +
            "credentialProvider=<redacted>)"
}
