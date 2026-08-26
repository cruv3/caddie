package com.caddie.agent.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProtocolTest {
    @Test
    fun `tool call keeps stable id and JSON arguments`() {
        val call = ModelDelta.ToolCall(
            id = ToolCallId("call-1"),
            name = "smartphone_open_app",
            argumentsJson = """{"package_name":"com.android.settings"}""",
        )

        assertEquals("call-1", call.id.value)
        assertEquals("smartphone_open_app", call.name)
        assertEquals(
            """{"package_name":"com.android.settings"}""",
            call.argumentsJson,
        )
    }

    @Test
    fun `tool result is successful by default`() {
        val result = ToolResult(
            callId = ToolCallId("call-1"),
            contentJson = """{"ok":true}""",
        )

        assertFalse(result.isError)
    }
}
