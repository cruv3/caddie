package com.caddie.tool.registry

import com.caddie.agent.core.ModelDelta
import com.caddie.agent.core.RunId
import com.caddie.agent.core.ToolDefinition
import com.caddie.agent.core.ToolRegistry
import com.caddie.agent.core.ToolResult

/** Combines small local tool groups while preserving one unambiguous owner per name. */
class CompositeToolRegistry(
    private vararg val registries: ToolRegistry,
) : ToolRegistry {
    override fun definitions(): List<ToolDefinition> = routes().values.map { it.definition }

    override suspend fun execute(runId: RunId, call: ModelDelta.ToolCall): ToolResult {
        val route = routes()[call.name] ?: error("Unknown local tool: ${call.name}")
        return route.registry.execute(runId, call)
    }

    private fun routes(): Map<String, Route> {
        val routes = linkedMapOf<String, Route>()
        registries.forEach { registry ->
            registry.definitions().forEach { definition ->
                require(routes.put(definition.name, Route(definition, registry)) == null) {
                    "Duplicate local tool name: ${definition.name}"
                }
            }
        }
        return routes
    }

    private data class Route(val definition: ToolDefinition, val registry: ToolRegistry)
}
