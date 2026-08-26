package com.caddie.model.openai

import com.caddie.agent.core.ModelDelta
import com.caddie.agent.core.ToolCallId
import com.caddie.model.gateway.GatewayFailureException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ChatCompletionStreamTest {
    @Test
    fun `text is immediate but tool calls wait for done`() {
        val stream = ChatCompletionStream(ChatCompletionCodec())

        val first =
            stream.acceptLine(
                """data: {"choices":[{"delta":{"content":"Hi ","tool_calls":[{"index":0,"id":"call_","function":{"name":"phone.","arguments":"{\"app\":"}}]},"finish_reason":null}]}""",
            ) + stream.acceptLine("")
        val second =
            stream.acceptLine(
                """data: {"choices":[{"delta":{"content":"there","tool_calls":[{"index":0,"id":"1","function":{"name":"open","arguments":"\"Settings\"}"}}]},"finish_reason":"tool_calls"}]}""",
            ) + stream.acceptLine("")

        assertEquals(
            listOf(ModelDelta.Text("Hi ")),
            first,
        )
        assertEquals(
            listOf(ModelDelta.Text("there")),
            second,
        )

        val completed =
            stream.acceptLine("data: [DONE]") +
                stream.acceptLine("")

        assertEquals(
            listOf(
                ModelDelta.ToolCall(
                    id = ToolCallId("call_1"),
                    name = "phone.open",
                    argumentsJson = """{"app":"Settings"}""",
                ),
                ModelDelta.Completed,
            ),
            completed,
        )
    }

    @Test
    fun `multiple tool calls retain declared index order`() {
        val stream = ChatCompletionStream(ChatCompletionCodec())
        stream.acceptLine(
            """data: {"choices":[{"delta":{"tool_calls":[{"index":1,"id":"b","function":{"name":"second","arguments":"{}"}},{"index":0,"id":"a","function":{"name":"first","arguments":"{}"}}]},"finish_reason":"tool_calls"}]}""",
        )
        stream.acceptLine("")

        val completed =
            stream.acceptLine("data: [DONE]") +
                stream.acceptLine("")

        assertEquals(
            listOf("first", "second"),
            completed
                .filterIsInstance<ModelDelta.ToolCall>()
                .map { it.name },
        )
    }

    @Test
    fun `missing id fails closed`() {
        val stream = ChatCompletionStream(ChatCompletionCodec())
        stream.acceptLine(
            """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"name":"phone.open","arguments":"{}"}}]},"finish_reason":"tool_calls"}]}""",
        )
        stream.acceptLine("")
        stream.acceptLine("data: [DONE]")

        assertThrows(GatewayFailureException::class.java) {
            stream.acceptLine("")
        }
    }

    @Test
    fun `invalid argument JSON fails closed`() {
        val stream = ChatCompletionStream(ChatCompletionCodec())
        stream.acceptLine(
            """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"c","function":{"name":"phone.open","arguments":"{"}}]},"finish_reason":"tool_calls"}]}""",
        )
        stream.acceptLine("")
        stream.acceptLine("data: [DONE]")

        assertThrows(GatewayFailureException::class.java) {
            stream.acceptLine("")
        }
    }

    @Test
    fun `eof before done fails closed`() {
        val stream = ChatCompletionStream(ChatCompletionCodec())
        stream.acceptLine(
            """data: {"choices":[{"delta":{"content":"partial"},"finish_reason":null}]}""",
        )

        assertThrows(GatewayFailureException::class.java) {
            stream.endOfInput()
        }
    }

    @Test
    fun `done event may terminate directly at eof`() {
        val stream = ChatCompletionStream(ChatCompletionCodec())
        stream.acceptLine("data: [DONE]")

        assertEquals(
            listOf(ModelDelta.Completed),
            stream.endOfInput(),
        )
    }

    @Test
    fun `reasoning comments and unknown fields are not emitted`() {
        val stream = ChatCompletionStream(ChatCompletionCodec())

        assertEquals(
            emptyList<ModelDelta>(),
            stream.acceptLine(": heartbeat"),
        )
        stream.acceptLine(
            """data: {"unknown":"ignored","choices":[{"delta":{"reasoning_content":"private"},"finish_reason":null}]}""",
        )
        assertEquals(
            emptyList<ModelDelta>(),
            stream.acceptLine(""),
        )
    }
}
