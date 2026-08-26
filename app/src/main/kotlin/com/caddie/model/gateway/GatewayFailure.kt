package com.caddie.model.gateway

/** Categorizes model-gateway failures by retry behavior. */
enum class GatewayFailureKind(
    val code: String,
) {
    NETWORK("network_unavailable"),
    STREAM("stream_failed"),
    AUTHENTICATION("authentication_failed"),
    RATE_LIMITED("rate_limited"),
    SERVER("gateway_server_failed"),
    HTTP("gateway_http_failed"),
    MEDIA_TYPE("invalid_media_type"),
    PROTOCOL("invalid_gateway_protocol"),
    TOOL_CALL("invalid_tool_call"),
}

/** Contains the normalized details of a failed gateway request. */
data class GatewayFailure(
    val kind: GatewayFailureKind,
    val safeMessage: String,
    val retryable: Boolean,
)

/** Exposes a normalized gateway failure as an exception. */
class GatewayFailureException(
    val failure: GatewayFailure,
    cause: Throwable? = null,
) : RuntimeException(failure.safeMessage, cause)
