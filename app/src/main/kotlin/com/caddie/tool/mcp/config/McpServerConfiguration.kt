package com.caddie.tool.mcp.config

import java.net.URI

/** Supplies an optional authorization header when an MCP connection is opened. */
fun interface McpCredentialProvider {
    suspend fun authorizationHeader(): String?

    companion object {
        val None = McpCredentialProvider { null }
    }
}

/** Holds and validates the connection settings for one Streamable HTTP MCP server. */
class McpServerConfiguration(
    val serverId: String,
    endpoint: String,
    val connectTimeoutMillis: Long = 10_000,
    val requestTimeoutMillis: Long = 60_000,
    val maxDiscoveryPages: Int = 100,
    val credentialProvider: McpCredentialProvider =
        McpCredentialProvider.None,
) {
    val endpoint: String = endpoint.trimEnd('/')

    init {
        require(SERVER_ID.matches(serverId)) {
            "serverId must match [A-Za-z0-9_-]+"
        }
        val uri = runCatching { URI(this.endpoint) }.getOrNull()
        require(
            uri != null &&
                uri.scheme in setOf("http", "https") &&
                !uri.host.isNullOrBlank(),
        ) {
            "endpoint must be an absolute HTTP(S) URL"
        }
        require(connectTimeoutMillis > 0) {
            "connectTimeoutMillis must be positive"
        }
        require(requestTimeoutMillis > 0) {
            "requestTimeoutMillis must be positive"
        }
        require(maxDiscoveryPages > 0) {
            "maxDiscoveryPages must be positive"
        }
    }

    override fun toString(): String =
        "McpServerConfiguration(" +
            "serverId=$serverId, " +
            "endpoint=$endpoint, " +
            "connectTimeoutMillis=$connectTimeoutMillis, " +
            "requestTimeoutMillis=$requestTimeoutMillis, " +
            "maxDiscoveryPages=$maxDiscoveryPages, " +
            "credentialProvider=<redacted>)"

    private companion object {
        val SERVER_ID = Regex("[A-Za-z0-9_-]+")
    }
}
