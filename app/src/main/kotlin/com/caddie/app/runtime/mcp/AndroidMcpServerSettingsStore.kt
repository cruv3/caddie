package com.caddie.app.runtime.mcp

import android.content.Context
import com.caddie.tool.mcp.config.McpServerSettings
import com.caddie.tool.mcp.config.McpServerSettingsCodec
import com.caddie.tool.mcp.config.McpServerSettingsStore

/** Stores non-secret MCP endpoints in the app's private preferences. */
class AndroidMcpServerSettingsStore(
    context: Context,
) : McpServerSettingsStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES,
        Context.MODE_PRIVATE,
    )

    override fun load(): List<McpServerSettings> {
        val encoded = preferences.getString(KEY_SERVERS, null) ?: return emptyList()
        return McpServerSettingsCodec.decode(encoded)
    }

    override fun replace(settings: List<McpServerSettings>) {
        val encoded = McpServerSettingsCodec.encode(settings)
        check(preferences.edit().putString(KEY_SERVERS, encoded).commit()) {
            "Could not persist MCP server settings"
        }
    }

    private companion object {
        const val PREFERENCES = "caddie_mcp_servers"
        const val KEY_SERVERS = "servers_json"
    }
}
