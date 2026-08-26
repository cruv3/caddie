package com.caddie.app.composition

import com.caddie.tool.mcp.config.McpServerConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class McpCompositionTest {
    @Test
    fun `disabled composition stores servers without creating a manager`() {
        var factoryCalls = 0
        val configuration =
            McpServerConfiguration(
                serverId = "homeserver",
                endpoint = "http://100.64.0.42:3000/mcp",
            )

        val composition =
            McpComposition.disabled(listOf(configuration)) {
                factoryCalls += 1
                error("MCP manager must remain disabled")
            }

        assertFalse(composition.productionEnabled)
        assertEquals("android-mcp-disabled", composition.variant)
        assertEquals(listOf(configuration), composition.servers)
        assertEquals(0, factoryCalls)
    }

    @Test
    fun `enabled creates its process manager lazily`() {
        val expected = manager()
        var calls = 0
        val composition = McpComposition.enabled(emptyList()) {
            calls += 1
            expected
        }

        assertEquals(0, calls)
        assertSame(expected, composition.createManager())
        assertEquals(1, calls)
    }

    private fun manager() = com.caddie.tool.mcp.client.McpClientManager(
        connectionFactory = { error("not used") },
        scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Job()),
    )
}
