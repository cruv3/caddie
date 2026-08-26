package com.caddie.tool.mcp.mapping

import com.caddie.agent.core.ToolCallId
import com.caddie.agent.core.ToolDefinition
import com.caddie.agent.core.ToolResult
import com.caddie.tool.mcp.client.McpCallResult
import com.caddie.tool.mcp.client.McpRemoteTool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/** Binds a model-facing tool definition to its exact MCP server identity. */
data class McpToolBinding(
    val serverId: String,
    val remoteName: String,
    val definition: ToolDefinition,
)

/** Converts between MCP connection values and the agent tool protocol. */
object McpToolMapper {
    private val json = Json { ignoreUnknownKeys = true }
    private val identifier = Regex("[A-Za-z0-9_-]+")

    fun bindings(
        serverId: String,
        tools: List<McpRemoteTool>,
    ): List<McpToolBinding> {
        val bindings = tools.map { binding(serverId, it) }
        require(
            bindings
                .map { it.definition.name }
                .toSet()
                .size == bindings.size,
        ) {
            "MCP server advertised duplicate tool names"
        }
        return bindings.sortedBy { it.definition.name }
    }

    fun binding(
        serverId: String,
        remote: McpRemoteTool,
    ): McpToolBinding {
        require(identifier.matches(serverId)) {
            "serverId must match [A-Za-z0-9_-]+"
        }
        require(identifier.matches(remote.name)) {
            "MCP tool name must match [A-Za-z0-9_-]+"
        }
        require(remote.description.isNotBlank()) {
            "MCP tool description must not be blank"
        }
        try {
            json.parseToJsonElement(remote.inputSchemaJson).jsonObject
        } catch (error: Exception) {
            throw IllegalArgumentException(
                "MCP tool schema must be one JSON object",
                error,
            )
        }
        return McpToolBinding(
            serverId = serverId,
            remoteName = remote.name,
            definition =
                ToolDefinition(
                    name = "mcp__${serverId}__${remote.name}",
                    description = remote.description,
                    inputSchemaJson = remote.inputSchemaJson,
                ),
        )
    }

    fun result(
        callId: ToolCallId,
        result: McpCallResult,
    ): ToolResult =
        ToolResult(
            callId = callId,
            contentJson = result.contentJson,
            isError = result.isError,
        )
}
