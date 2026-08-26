package com.caddie.model.openai

import com.caddie.agent.core.AgentMessage
import com.caddie.agent.core.AgentToolCall
import com.caddie.agent.core.ModelRequest
import com.caddie.agent.core.RunId
import com.caddie.agent.core.ToolDefinition
import com.caddie.agent.core.ToolCallId
import com.caddie.model.gateway.GatewayFailureException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ChatCompletionCodecTest {
    private val codec = ChatCompletionCodec()

    @Test
    fun `request encodes assistant tool call and matching tool result`() {
        val callId = ToolCallId("c1")
        val encoded = codec.encodeRequest(
            ModelRequest(
                runId = RunId("run-1"),
                messages = listOf(
                    AgentMessage(
                        role = AgentMessage.Role.ASSISTANT,
                        content = "",
                        toolCall = AgentToolCall(
                            callId,
                            "android.click",
                            """{"text":"Settings"}""",
                        ),
                    ),
                    AgentMessage(
                        role = AgentMessage.Role.TOOL,
                        content = """{"ok":true}""",
                        toolCallId = callId,
                    ),
                ),
                tools = emptyList(),
            ),
            modelId = "qwen3.6-35b-a3b",
        )

        val messages = Json.parseToJsonElement(encoded)
            .jsonObject.getValue("messages").jsonArray
        val assistant = messages[0].jsonObject
        val toolCall = assistant.getValue("tool_calls").jsonArray.single().jsonObject
        val function = toolCall.getValue("function").jsonObject
        val tool = messages[1].jsonObject

        assertEquals("assistant", assistant.getValue("role").jsonPrimitive.content)
        assertEquals("null", assistant.getValue("content").toString())
        assertEquals("c1", toolCall.getValue("id").jsonPrimitive.content)
        assertEquals("function", toolCall.getValue("type").jsonPrimitive.content)
        assertEquals("android.click", function.getValue("name").jsonPrimitive.content)
        assertEquals(
            """{"text":"Settings"}""",
            function.getValue("arguments").jsonPrimitive.content,
        )
        assertEquals("tool", tool.getValue("role").jsonPrimitive.content)
        assertEquals("c1", tool.getValue("tool_call_id").jsonPrimitive.content)
        assertEquals("""{"ok":true}""", tool.getValue("content").jsonPrimitive.content)
    }

    @Test
    fun `request maps messages tools model and streaming flag`() {
        val encoded =
            codec.encodeRequest(
                request =
                    ModelRequest(
                        runId = RunId("run-1"),
                        messages =
                            listOf(
                                AgentMessage(
                                    AgentMessage.Role.SYSTEM,
                                    "Follow policy",
                                ),
                                AgentMessage(
                                    AgentMessage.Role.USER,
                                    "Open settings",
                                ),
                            ),
                        tools =
                            listOf(
                                ToolDefinition(
                                    name = "phone.open",
                                    description = "Open an app",
                                    inputSchemaJson =
                                        """{"type":"object","properties":{"app":{"type":"string"}}}""",
                                ),
                            ),
                    ),
                modelId = "qwen3.6-35b-a3b",
            )
        val root = Json.parseToJsonElement(encoded).jsonObject

        assertEquals(
            "qwen3.6-35b-a3b",
            root.getValue("model").jsonPrimitive.content,
        )
        assertEquals(
            true,
            root.getValue("stream").jsonPrimitive.content.toBoolean(),
        )
        assertEquals(
            listOf("system", "user"),
            root.getValue("messages").jsonArray.map {
                it.jsonObject.getValue("role").jsonPrimitive.content
            },
        )
        val function =
            root
                .getValue("tools")
                .jsonArray
                .single()
                .jsonObject
                .getValue("function")
                .jsonObject
        assertEquals(
            "phone.open",
            function.getValue("name").jsonPrimitive.content,
        )
        assertEquals(
            "object",
            function
                .getValue("parameters")
                .jsonObject
                .getValue("type")
                .jsonPrimitive
                .content,
        )
        assertNull(root["runId"])
    }

    @Test
    fun `request can disable gateway thinking for action execution`() {
        val encoded = codec.encodeRequest(
            request = ModelRequest(
                runId = RunId("run-1"),
                messages = listOf(AgentMessage(AgentMessage.Role.USER, "Open Maps")),
                tools = emptyList(),
            ),
            modelId = "qwen3.6-35b-a3b",
            thinkingEnabled = false,
        )

        val root = Json.parseToJsonElement(encoded).jsonObject
        val templateArguments = root.getValue("chat_template_kwargs").jsonObject

        assertEquals(
            false,
            templateArguments.getValue("enable_thinking").jsonPrimitive.content.toBoolean(),
        )
    }

    @Test
    fun `invalid tool schema fails before network dispatch`() {
        val request =
            ModelRequest(
                RunId("run-1"),
                emptyList(),
                listOf(ToolDefinition("broken", "", "[]")),
            )

        assertThrows(GatewayFailureException::class.java) {
            codec.encodeRequest(request, "qwen3.6-35b-a3b")
        }
    }

    @Test
    fun `chunk decoder keeps reasoning separate from visible text`() {
        val chunk =
            codec.decodeChunk(
                """
                {
                  "choices":[{
                    "delta":{
                      "content":"Visible",
                      "reasoning_content":"Private thought",
                      "tool_calls":[{
                        "index":0,
                        "id":"call_",
                        "function":{"name":"phone.","arguments":"{\"app\":"}
                      }]
                    },
                    "finish_reason":null
                  }]
                }
                """.trimIndent(),
            )

        assertEquals("Visible", chunk.text)
        assertEquals("Private thought", chunk.reasoning)
        assertEquals("call_", chunk.toolFragments.single().idPart)
        assertEquals("phone.", chunk.toolFragments.single().namePart)
        assertEquals(
            """{"app":""",
            chunk.toolFragments.single().argumentsPart,
        )
    }
}
