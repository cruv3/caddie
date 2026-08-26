package com.caddie.tool.interaction

import com.caddie.agent.core.ModelDelta
import com.caddie.agent.core.RunId
import com.caddie.agent.core.ToolCallId
import com.caddie.agent.core.ToolContinuation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalToolRegistryTest {
    private val registry = TerminalToolRegistry()

    @Test
    fun `complete returns a successful terminal message`() = runTest {
        val result = registry.execute(
            RunId("run"),
            ModelDelta.ToolCall(
                ToolCallId("complete"),
                "caddie.complete",
                """{"message":"Nachricht wurde gesendet."}""",
            ),
        )

        assertEquals(ToolContinuation.COMPLETE_RUN, result.continuation)
        assertEquals("Nachricht wurde gesendet.", result.terminalMessage)
        assertFalse(result.isError)
    }

    @Test
    fun `fail returns an aborted terminal message`() = runTest {
        val result = registry.execute(
            RunId("run"),
            ModelDelta.ToolCall(
                ToolCallId("fail"),
                "caddie.fail",
                """{"reason":"Ziel-App fehlt."}""",
            ),
        )

        assertEquals(ToolContinuation.FAIL_RUN, result.continuation)
        assertEquals("Ziel-App fehlt.", result.terminalMessage)
    }

    @Test
    fun `invalid terminal arguments are recoverable model feedback`() = runTest {
        val result = registry.execute(
            RunId("run"),
            ModelDelta.ToolCall(
                ToolCallId("complete"),
                "caddie.complete",
                "{}",
            ),
        )

        assertTrue(result.isError)
        assertEquals(ToolContinuation.CONTINUE, result.continuation)
    }
}
