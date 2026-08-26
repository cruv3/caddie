package com.caddie.tool.mcp.mapping

import com.caddie.agent.core.ToolCallId
import com.caddie.agent.core.ToolDefinition
import com.caddie.tool.mcp.client.McpCallResult
import com.caddie.tool.mcp.client.McpRemoteTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class McpToolMapperTest {
    @Test
    fun `maps a remote tool without losing its original identity`() {
        val remote =
            McpRemoteTool(
                name = "create_event",
                description = "Creates an event",
                inputSchemaJson =
                    """{"type":"object","properties":{"title":{"type":"string"}}}""",
            )

        val binding = McpToolMapper.binding("calendar", remote)

        assertEquals("calendar", binding.serverId)
        assertEquals("create_event", binding.remoteName)
        assertEquals(
            ToolDefinition(
                name = "mcp__calendar__create_event",
                description = "Creates an event",
                inputSchemaJson = remote.inputSchemaJson,
            ),
            binding.definition,
        )
    }

    @Test
    fun `rejects a remote name that would require normalization`() {
        val remote =
            McpRemoteTool(
                name = "create event",
                description = "Creates an event",
                inputSchemaJson = """{"type":"object"}""",
            )

        assertThrows(IllegalArgumentException::class.java) {
            McpToolMapper.binding("calendar", remote)
        }
    }

    @Test
    fun `rejects a schema that is not one json object`() {
        val remote =
            McpRemoteTool(
                name = "create_event",
                description = "Creates an event",
                inputSchemaJson = "[]",
            )

        assertThrows(IllegalArgumentException::class.java) {
            McpToolMapper.binding("calendar", remote)
        }
    }

    @Test
    fun `rejects duplicate exposed names in one server snapshot`() {
        val remote =
            McpRemoteTool(
                name = "create_event",
                description = "Creates an event",
                inputSchemaJson = """{"type":"object"}""",
            )

        assertThrows(IllegalArgumentException::class.java) {
            McpToolMapper.bindings("calendar", listOf(remote, remote))
        }
    }

    @Test
    fun `preserves remote error status in the agent result`() {
        val result =
            McpToolMapper.result(
                callId = ToolCallId("call-1"),
                result =
                    McpCallResult(
                        contentJson = """{"content":[{"type":"text","text":"denied"}]}""",
                        isError = true,
                    ),
            )

        assertEquals(ToolCallId("call-1"), result.callId)
        assertTrue(result.isError)
        assertEquals(
            """{"content":[{"type":"text","text":"denied"}]}""",
            result.contentJson,
        )
    }
}
