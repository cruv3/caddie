package com.caddie.app.composition

import com.caddie.tool.mcp.client.McpManager
import com.caddie.tool.mcp.config.McpServerConfiguration

/** Describes whether the native MCP runtime is enabled in this app build. */
class McpComposition private constructor(
    val variant: String,
    val productionEnabled: Boolean,
    val servers: List<McpServerConfiguration>,
    private val managerFactory: () -> McpManager,
) {
    private val manager by lazy(managerFactory)

    fun createManager(): McpManager {
        check(productionEnabled) { "Android-native MCP is disabled" }
        return manager
    }

    companion object {
        fun disabled(
            servers: List<McpServerConfiguration>,
            managerFactory: () -> McpManager,
        ): McpComposition =
            McpComposition(
                variant = "android-mcp-disabled",
                productionEnabled = false,
                servers = servers.toList(),
                managerFactory = managerFactory,
            )

        fun enabled(
            servers: List<McpServerConfiguration>,
            managerFactory: () -> McpManager,
        ): McpComposition =
            McpComposition(
                variant = "android-mcp-enabled",
                productionEnabled = true,
                servers = servers.toList(),
                managerFactory = managerFactory,
            )
    }
}
