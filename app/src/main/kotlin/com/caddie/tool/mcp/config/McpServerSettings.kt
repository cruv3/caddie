package com.caddie.tool.mcp.config

import java.net.URI
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Stores one non-secret MCP endpoint selected by the device owner. */
@Serializable
data class McpServerSettings(
    val serverId: String,
    val endpoint: String,
    val enabled: Boolean = true,
    val requiredForStudy: Boolean = false,
) {
    init {
        require(SERVER_ID.matches(serverId)) {
            "serverId must match [A-Za-z0-9_-]+"
        }
        val uri = runCatching { URI(normalizedEndpoint) }.getOrNull()
        require(
            uri != null &&
                uri.scheme in setOf("http", "https") &&
                !uri.host.isNullOrBlank() &&
                uri.rawUserInfo == null &&
                uri.rawQuery == null &&
                uri.rawFragment == null,
        ) {
            "endpoint must be an absolute HTTP(S) URL without credentials, query, or fragment"
        }
    }

    val normalizedEndpoint: String get() = endpoint.trimEnd('/')

    fun normalized(): McpServerSettings = copy(endpoint = normalizedEndpoint)

    fun toConfiguration(): McpServerConfiguration =
        McpServerConfiguration(serverId, normalizedEndpoint)

    companion object {
        val SERVER_ID = Regex("[A-Za-z0-9_-]+")
    }
}

/** Persists complete MCP endpoint settings without credentials or tokens. */
interface McpServerSettingsStore {
    fun load(): List<McpServerSettings>

    fun replace(settings: List<McpServerSettings>)
}

/** Encodes a deterministic, validated MCP settings document. */
object McpServerSettingsCodec {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    fun encode(settings: List<McpServerSettings>): String =
        json.encodeToString(normalize(settings))

    fun decode(encoded: String): List<McpServerSettings> =
        normalize(json.decodeFromString<List<McpServerSettings>>(encoded))

    fun normalize(settings: List<McpServerSettings>): List<McpServerSettings> {
        require(settings.map { it.serverId }.toSet().size == settings.size) {
            "MCP server IDs must be unique"
        }
        return settings.map(McpServerSettings::normalized).sortedBy { it.serverId }
    }
}
