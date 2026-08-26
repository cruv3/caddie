package com.caddie.tool.registry

import com.caddie.agent.core.ModelDelta
import com.caddie.agent.core.RunId
import com.caddie.agent.core.ToolCallId
import com.caddie.agent.core.ToolDefinition
import com.caddie.agent.core.ToolRegistry
import com.caddie.agent.core.ToolResult
import com.caddie.tool.mcp.client.McpCallResult
import com.caddie.tool.mcp.client.McpClientManager
import com.caddie.tool.mcp.client.McpConnection
import com.caddie.tool.mcp.client.McpConnectionFactory
import com.caddie.tool.mcp.client.McpRemoteTool
import com.caddie.tool.mcp.client.McpToolPage
import com.caddie.tool.mcp.config.McpServerConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicToolRegistryTest {
    @Test
    fun `local and available MCP definitions are name sorted`() = runTest {
        val remote = FakeConnection(tool("remote"))
        val manager = connectedManager(backgroundScope, "server", remote)
        val local = FakeLocalRegistry(definition("local"))
        val registry = DynamicToolRegistry(local, manager)

        assertEquals(
            listOf("local", "mcp__server__remote"),
            registry.definitions().map { it.name },
        )
    }

    @Test
    fun `local collision excludes only the affected remote server`() = runTest {
        val manager =
            connectedManager(
                backgroundScope,
                "first" to FakeConnection(tool("conflict"), tool("hidden")),
                "second" to FakeConnection(tool("visible")),
            )
        val local = FakeLocalRegistry(definition("mcp__first__conflict"))
        val registry = DynamicToolRegistry(local, manager)

        assertEquals(
            listOf("mcp__first__conflict", "mcp__second__visible"),
            registry.definitions().map { it.name },
        )
    }

    @Test
    fun `dispatch uses stored server binding and exact remote name`() = runTest {
        val connection = FakeConnection(tool("create_event"))
        val manager = connectedManager(backgroundScope, "calendar", connection)
        val registry = DynamicToolRegistry(FakeLocalRegistry(), manager)
        val call =
            ModelDelta.ToolCall(
                id = ToolCallId("call-1"),
                name = "mcp__calendar__create_event",
                argumentsJson = """{"title":"Review"}""",
            )
        registry.definitions()

        val result = registry.execute(RunId("run-1"), call)

        assertEquals(listOf("create_event" to call.argumentsJson), connection.calls)
        assertEquals(call.id, result.callId)
    }

    @Test
    fun `invalid JSON arguments never reach the MCP connection`() = runTest {
        val connection = FakeConnection(tool("remote"))
        val manager = connectedManager(backgroundScope, "server", connection)
        val registry = DynamicToolRegistry(FakeLocalRegistry(), manager)
        registry.definitions()

        assertFails<IllegalArgumentException> {
            registry.execute(RunId("run-1"), call("mcp__server__remote", "[]"))
        }

        assertTrue(connection.calls.isEmpty())
    }

    @Test
    fun `unavailable binding is rejected before a remote call`() = runTest {
        val connection = FakeConnection(tool("remote"))
        val manager = connectedManager(backgroundScope, "server", connection)
        val registry = DynamicToolRegistry(FakeLocalRegistry(), manager)
        assertEquals(1, registry.definitions().size)
        manager.disconnect("server")

        assertFails<IllegalStateException> {
            registry.execute(RunId("run-1"), call("mcp__server__remote"))
        }

        assertTrue(connection.calls.isEmpty())
    }

    @Test
    fun `MCP error result is preserved`() = runTest {
        val connection =
            FakeConnection(
                tool("remote"),
                result = McpCallResult("""[{"type":"text","text":"rejected"}]""", true),
            )
        val manager = connectedManager(backgroundScope, "server", connection)
        val registry = DynamicToolRegistry(FakeLocalRegistry(), manager)
        registry.definitions()

        val result = registry.execute(RunId("run-1"), call("mcp__server__remote"))

        assertTrue(result.isError)
    }

    @Test
    fun `remote failure is never retried`() = runTest {
        val connection = FakeConnection(tool("remote"), failure = IllegalStateException("lost"))
        val manager = connectedManager(backgroundScope, "server", connection)
        val registry = DynamicToolRegistry(FakeLocalRegistry(), manager)
        registry.definitions()

        assertFails<IllegalStateException> {
            registry.execute(RunId("run-1"), call("mcp__server__remote"))
        }

        assertEquals(1, connection.calls.size)
    }

    @Test
    fun `old advertised generation cannot route through a reconnect`() = runTest {
        val old = FakeConnection(tool("remote"))
        val replacement = FakeConnection(tool("remote"))
        val connections = ArrayDeque(listOf<McpConnection>(old, replacement))
        val manager =
            McpClientManager(
                McpConnectionFactory { connections.removeFirst() },
                backgroundScope,
            )
        val configuration =
            McpServerConfiguration(
                serverId = "server",
                endpoint = "https://example.test/mcp",
            )
        manager.connect(configuration)
        val registry = DynamicToolRegistry(FakeLocalRegistry(), manager)
        registry.definitions()
        manager.connect(configuration)

        assertFails<IllegalStateException> {
            registry.execute(RunId("run-1"), call("mcp__server__remote"))
        }

        assertTrue(old.calls.isEmpty())
        assertTrue(replacement.calls.isEmpty())
    }

    @Test
    fun `known local calls delegate exactly once`() = runTest {
        val local = FakeLocalRegistry(definition("local"))
        val registry =
            DynamicToolRegistry(
                local,
                McpClientManager(
                    McpConnectionFactory { error("unused") },
                    backgroundScope,
                ),
            )

        registry.execute(RunId("run-1"), call("local"))

        assertEquals(1, local.executionCount)
    }

    private suspend fun connectedManager(
        scope: CoroutineScope,
        serverId: String,
        connection: McpConnection,
    ) = connectedManager(scope, serverId to connection)

    private suspend fun connectedManager(
        scope: CoroutineScope,
        vararg servers: Pair<String, McpConnection>,
    ): McpClientManager {
        val connections = ArrayDeque(servers.map { it.second })
        val manager =
            McpClientManager(
                McpConnectionFactory { connections.removeFirst() },
                scope,
            )
        servers.forEach { (serverId, _) ->
            manager.connect(
                McpServerConfiguration(
                    serverId = serverId,
                    endpoint = "https://example.test/mcp",
                ),
            )
        }
        return manager
    }

    private fun definition(name: String) =
        ToolDefinition(
            name = name,
            description = "$name tool",
            inputSchemaJson = """{"type":"object"}""",
        )

    private fun tool(name: String) =
        McpRemoteTool(
            name = name,
            description = "$name tool",
            inputSchemaJson = """{"type":"object"}""",
        )

    private fun call(
        name: String,
        argumentsJson: String = "{}",
    ) = ModelDelta.ToolCall(
        id = ToolCallId("call-1"),
        name = name,
        argumentsJson = argumentsJson,
    )

    private suspend inline fun <reified T : Throwable> assertFails(
        crossinline block: suspend () -> Unit,
    ) {
        val failure = runCatching { block() }.exceptionOrNull()
        assertTrue(
            "Expected ${T::class.java.simpleName}, got $failure",
            failure is T,
        )
    }

    private class FakeLocalRegistry(
        private vararg val available: ToolDefinition,
    ) : ToolRegistry {
        var executionCount = 0

        override fun definitions(): List<ToolDefinition> = available.toList()

        override suspend fun execute(
            runId: RunId,
            call: ModelDelta.ToolCall,
        ): ToolResult {
            executionCount += 1
            return ToolResult(call.id, """{"local":true}""")
        }
    }

    private class FakeConnection(
        vararg tools: McpRemoteTool,
        private val result: McpCallResult = McpCallResult("""{"ok":true}""", false),
        private val failure: Throwable? = null,
    ) : McpConnection {
        private val tools = tools.toList()
        val calls = mutableListOf<Pair<String, String>>()

        override suspend fun listTools(cursor: String?) =
            McpToolPage(tools, null)

        override suspend fun callTool(
            name: String,
            argumentsJson: String,
        ): McpCallResult {
            calls += name to argumentsJson
            failure?.let { throw it }
            return result
        }

        override suspend fun close() = Unit
    }
}
