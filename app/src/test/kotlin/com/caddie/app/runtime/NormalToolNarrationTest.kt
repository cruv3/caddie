package com.caddie.app.runtime

import com.caddie.agent.core.ModelDelta
import com.caddie.agent.core.ToolCallId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NormalToolNarrationTest {
    @Test
    fun `why is shown as the human English action`() {
        assertEquals(
            "Opening the pizzeria search results",
            NormalToolNarration.humanLabel(
                call(
                    "android.open_url",
                    """{"why":"Opening the pizzeria search results"}""",
                ),
            ),
        )
    }

    @Test
    fun `fallback describes Android action without exposing tool syntax`() {
        assertEquals(
            "Tapping “Pizzeria Roma”",
            NormalToolNarration.humanLabel(
                call(
                    "android.click",
                    """{"target":{"text":"Pizzeria Roma"}}""",
                ),
            ),
        )
    }

    @Test
    fun `observation and terminal tools remain silent`() {
        assertNull(NormalToolNarration.humanLabel(call("android.observe", "{}")))
        assertNull(NormalToolNarration.humanLabel(call("caddie.complete", "{}")))
    }

    private fun call(name: String, arguments: String) = ModelDelta.ToolCall(
        id = ToolCallId("call"),
        name = name,
        argumentsJson = arguments,
    )
}
