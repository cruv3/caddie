package com.caddie.tool.interaction

import com.caddie.agent.core.ModelDelta
import com.caddie.agent.core.RunId
import com.caddie.agent.core.ToolCallId
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UserInteractionToolRegistryTest {
    @Test
    fun `ask user returns the spoken answer as a tool result`() = runTest {
        var receivedQuestion = ""
        val registry = UserInteractionToolRegistry { _, question ->
            receivedQuestion = question
            "Am Hauptbahnhof"
        }

        val result = registry.execute(
            RunId("run-1"),
            ModelDelta.ToolCall(
                ToolCallId("call-1"),
                "caddie.ask_user",
                """{"question":"Wo soll ich dich abholen?"}""",
            ),
        )

        assertEquals("Wo soll ich dich abholen?", receivedQuestion)
        assertEquals("Am Hauptbahnhof", JSONObject(result.contentJson).getString("answer"))
        assertFalse(result.isError)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `ask user rejects a blank question`() = runTest {
        UserInteractionToolRegistry { _, _ -> "unused" }.execute(
            RunId("run-1"),
            ModelDelta.ToolCall(
                ToolCallId("call-1"),
                "caddie.ask_user",
                """{"question":"  "}""",
            ),
        )
    }
}
