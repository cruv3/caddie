package com.caddie.tool.registry

import com.caddie.agent.core.ModelDelta
import com.caddie.agent.core.RunId
import com.caddie.agent.core.ToolDefinition
import com.caddie.agent.core.ToolRegistry
import com.caddie.agent.core.ToolResult
import com.caddie.tool.mcp.client.McpManager
import com.caddie.tool.mcp.client.McpServerStatus
import com.caddie.tool.mcp.mapping.McpToolBinding
import com.caddie.tool.mcp.mapping.McpToolMapper
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/** Merges local and currently available MCP tools behind the agent tool port. */
class DynamicToolRegistry(
    private val local: ToolRegistry,
    private val mcp: McpManager,
) : ToolRegistry {
    @Volatile
    private var advertisedRoutes = emptyMap<String, RemoteRoute>()

    override fun definitions(): List<ToolDefinition> {
        val localDefinitions = localDefinitions()
        val remoteRoutes = remoteRoutes(localDefinitions.keys)
        advertisedRoutes = remoteRoutes
        return (localDefinitions.values + remoteRoutes.values.map { it.binding.definition })
            .sortedBy { it.name }
    }

    override suspend fun execute(
        runId: RunId,
        call: ModelDelta.ToolCall,
    ): ToolResult {
        val localDefinitions = localDefinitions()
        if (call.name in localDefinitions) {
            return local.execute(runId, call)
        }

        val route =
            advertisedRoutes[call.name]
                ?: error("Unknown or unavailable tool: ${call.name}")
        requireObjectArguments(call.argumentsJson)
        return McpToolMapper.result(
            callId = call.id,
            result =
                mcp.callTool(
                    binding = route.binding,
                    generation = route.generation,
                    argumentsJson = call.argumentsJson,
                ),
        )
    }

    private fun localDefinitions(): Map<String, ToolDefinition> {
        val definitions = local.definitions()
        require(definitions.map { it.name }.toSet().size == definitions.size) {
            "Local tool registry contains duplicate names"
        }
        return definitions.associateBy { it.name }
    }

    private fun remoteRoutes(localNames: Set<String>): Map<String, RemoteRoute> {
        val available =
            mcp.snapshots().filter { it.status == McpServerStatus.AVAILABLE }
        val collidingServers =
            available
                .filter { snapshot ->
                    snapshot.tools.any { it.definition.name in localNames }
                }.mapTo(mutableSetOf()) { it.serverId }

        available
            .flatMap { snapshot ->
                snapshot.tools.map { it.definition.name to snapshot }
            }.groupBy({ it.first }, { it.second })
            .filterValues { owners -> owners.size > 1 }
            .values
            .flatten()
            .mapTo(collidingServers) { it.serverId }

        return available
            .filterNot { it.serverId in collidingServers }
            .flatMap { snapshot ->
                snapshot.tools.map { binding ->
                    RemoteRoute(binding, snapshot.generation)
                }
            }.associateBy { it.binding.definition.name }
    }

    private fun requireObjectArguments(argumentsJson: String) {
        try {
            JSON.parseToJsonElement(argumentsJson).jsonObject
        } catch (error: Exception) {
            throw IllegalArgumentException(
                "Tool arguments must be one JSON object",
                error,
            )
        }
    }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
    }

    private data class RemoteRoute(
        val binding: McpToolBinding,
        val generation: Long,
    )
}
