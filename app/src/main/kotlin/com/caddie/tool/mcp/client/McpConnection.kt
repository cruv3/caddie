package com.caddie.tool.mcp.client

import com.caddie.tool.mcp.config.McpServerConfiguration

/** Describes one tool advertised by an MCP server. */
data class McpRemoteTool(
    val name: String,
    val description: String,
    val inputSchemaJson: String,
)

/** Contains the normalized result of one MCP tool call. */
data class McpCallResult(
    val contentJson: String,
    val isError: Boolean,
)

/** Contains one page of tools discovered from an MCP server. */
data class McpToolPage(
    val tools: List<McpRemoteTool>,
    val nextCursor: String?,
)

/** Provides discovery and tool calls for one initialized MCP connection. */
interface McpConnection {
    suspend fun listTools(cursor: String?): McpToolPage

    suspend fun callTool(
        name: String,
        argumentsJson: String,
    ): McpCallResult

    fun setToolsListChangedHandler(handler: () -> Unit) = Unit

    suspend fun close()
}

/** Opens an initialized MCP connection from validated server settings. */
fun interface McpConnectionFactory {
    suspend fun connect(
        configuration: McpServerConfiguration,
    ): McpConnection
}
