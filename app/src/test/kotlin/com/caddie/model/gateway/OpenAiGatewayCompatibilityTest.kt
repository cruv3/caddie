package com.caddie.model.gateway

import com.caddie.agent.core.AgentMessage
import com.caddie.agent.core.AgentToolCall
import com.caddie.agent.core.ModelDelta
import com.caddie.agent.core.ModelRequest
import com.caddie.agent.core.ModelUnavailableException
import com.caddie.agent.core.RunId
import com.caddie.agent.core.ToolCallId
import com.caddie.agent.core.ToolDefinition
import com.caddie.model.openai.OpenAiToolNames
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiGatewayCompatibilityTest {
    @Test
    fun `OpenAI transport maps each request and replayed history and restores streamed names`() = runTest {
        val bodies = mutableListOf<JsonObject>()
        val client = client("https://api.openai.com", bodies)
        val first = request("android.click")
        assertEquals(expected("android.click"), client.stream(first).toList())
        val second = request("caddie.complete").copy(
            messages = first.messages + listOf(
                AgentMessage(AgentMessage.Role.ASSISTANT, "", toolCall = AgentToolCall(ToolCallId("c1"), "android.click", "{}")),
                AgentMessage(AgentMessage.Role.TOOL, "synthetic result", toolCallId = ToolCallId("c1")),
                AgentMessage(AgentMessage.Role.USER, "Finish"),
            ),
        )
        assertEquals(expected("caddie.complete"), client.stream(second).toList())
        assertEquals(2, bodies.size)
        bodies.forEach {
            assertEquals("none", it.getValue("reasoning_effort").jsonPrimitive.content)
            assertNull(it["chat_template_kwargs"])
        }
        val firstAlias = toolName(bodies[0])
        assertFalse(firstAlias.contains('.'))
        val historicalAlias = bodies[1].getValue("messages").jsonArray[1].jsonObject
            .getValue("tool_calls").jsonArray.single().jsonObject
            .getValue("function").jsonObject.getValue("name").jsonPrimitive.content
        assertEquals(firstAlias, historicalAlias)
    }

    @Test
    fun `same Luna model on a local backend preserves its existing wire protocol`() = runTest {
        val bodies = mutableListOf<JsonObject>()
        val client = client("http://127.0.0.1:8000", bodies)
        assertEquals(expected("android.click"), client.stream(request("android.click")).toList())
        val body = bodies.single()
        assertEquals("android.click", toolName(body))
        assertEquals("false", body.getValue("chat_template_kwargs").jsonObject.getValue("enable_thinking").jsonPrimitive.content)
        assertNull(body["reasoning_effort"])
    }

    @Test
    fun `alias collision is a protocol failure before any HTTP attempt`() = runTest {
        val request = request("android.click")
        val alias = OpenAiToolNames(request).toWire("android.click")
        val colliding = request.copy(tools = request.tools + ToolDefinition(alias, "Collision", """{"type":"object"}"""))
        val bodies = mutableListOf<JsonObject>()
        val error = runCatching { client("https://api.openai.com", bodies).stream(colliding).toList() }.exceptionOrNull()
        assertTrue(error is ModelUnavailableException)
        assertTrue(error?.message.orEmpty().contains("invalid_gateway_protocol"))
        assertTrue(bodies.isEmpty())
    }

    private fun client(baseUrl: String, bodies: MutableList<JsonObject>): GatewayModelClient {
        val transport = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals("/v1/chat/completions", request.url.encodedPath)
            assertEquals("POST", request.method)
            val buffer = Buffer()
            requireNotNull(request.body).writeTo(buffer)
            val body = Json.parseToJsonElement(buffer.readUtf8()).jsonObject
            bodies += body
            val name = toolName(body)
            val stream = """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"c1","function":{"name":"${name.take(4)}","arguments":"{"}}]}}]}""" + "\n\n" +
                """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"name":"${name.drop(4)}","arguments":"}"}}]},"finish_reason":"tool_calls"}]}""" + "\n\ndata: [DONE]\n\n"
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(stream.toResponseBody("text/event-stream".toMediaType())).build()
        }.build()
        return GatewayModelClient(
            GatewayConfiguration(baseUrl, ModelProfile("normal", "gpt-6-luna"), thinkingEnabled = false, retryPolicy = RetryPolicy(maxAttempts = 1)),
            client = transport,
        )
    }

    private fun toolName(body: JsonObject): String = body.getValue("tools").jsonArray.single()
        .jsonObject.getValue("function").jsonObject.getValue("name").jsonPrimitive.content

    private fun request(name: String) = ModelRequest(
        RunId("synthetic-transport-test"),
        listOf(AgentMessage(AgentMessage.Role.USER, "Synthetic request")),
        listOf(ToolDefinition(name, "Synthetic tool", """{"type":"object"}""")),
    )

    private fun expected(name: String) = listOf(ModelDelta.ToolCall(ToolCallId("c1"), name, "{}"), ModelDelta.Completed)
}
