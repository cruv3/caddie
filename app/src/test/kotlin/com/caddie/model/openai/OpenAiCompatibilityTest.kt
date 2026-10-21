package com.caddie.model.openai

import com.caddie.agent.core.AgentMessage
import com.caddie.agent.core.AgentToolCall
import com.caddie.agent.core.ModelDelta
import com.caddie.agent.core.ModelRequest
import com.caddie.agent.core.RunId
import com.caddie.agent.core.ToolCallId
import com.caddie.agent.core.ToolDefinition
import com.caddie.model.gateway.GatewayFailureException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiCompatibilityTest {
    @Test
    fun `Luna always disables reasoning and omits Qwen arguments on OpenAI`() {
        val request = request(listOf("caddie.complete"))
        listOf(null, false, true).forEach { thinking ->
            val root = Json.parseToJsonElement(
                ChatCompletionCodec.forOpenAi(request)
                    .encodeRequest(request, "gpt-6-luna", thinking),
            ).jsonObject
            assertEquals("none", root.getValue("reasoning_effort").jsonPrimitive.content)
            assertNull(root["chat_template_kwargs"])
            assertEquals("true", root.getValue("stream").jsonPrimitive.content)
        }
    }

    @Test
    fun `other OpenAI models omit Qwen arguments without forcing unsupported reasoning`() {
        val request = request(emptyList())
        val root = Json.parseToJsonElement(
            ChatCompletionCodec.forOpenAi(request)
                .encodeRequest(request, "another-openai-model", true),
        ).jsonObject
        assertNull(root["chat_template_kwargs"])
        assertNull(root["reasoning_effort"])
    }

    @Test
    fun `schema and complete history use the same aliases including retired tools`() {
        val id = ToolCallId("historical-call")
        val request = request(listOf("android.click", "caddie.complete")).copy(
            messages = listOf(
                AgentMessage(AgentMessage.Role.USER, "Use android.click"),
                AgentMessage(
                    AgentMessage.Role.ASSISTANT, "",
                    toolCall = AgentToolCall(id, "android.click", "{}"),
                ),
                AgentMessage(AgentMessage.Role.TOOL, "result", toolCallId = id),
                AgentMessage(
                    AgentMessage.Role.ASSISTANT, "",
                    toolCall = AgentToolCall(ToolCallId("retired"), "old.tool", "{}"),
                ),
                AgentMessage(AgentMessage.Role.TOOL, "old result", toolCallId = ToolCallId("retired")),
            ),
        )
        val codec = ChatCompletionCodec.forOpenAi(request)
        val root = Json.parseToJsonElement(codec.encodeRequest(request, "gpt-6-luna")).jsonObject
        val toolAlias = root.getValue("tools").jsonArray.first().jsonObject
            .getValue("function").jsonObject.getValue("name").jsonPrimitive.content
        val messages = root.getValue("messages").jsonArray
        fun historyAlias(index: Int) = messages[index].jsonObject.getValue("tool_calls")
            .jsonArray.single().jsonObject.getValue("function").jsonObject
            .getValue("name").jsonPrimitive.content
        assertEquals(toolAlias, historyAlias(1))
        assertEquals("android.click", codec.localToolName(toolAlias))
        assertEquals("old.tool", codec.localToolName(historyAlias(3)))
        assertEquals("historical-call", messages[2].jsonObject.getValue("tool_call_id").jsonPrimitive.content)
        assertEquals("result", messages[2].jsonObject.getValue("content").jsonPrimitive.content)
        assertEquals("Use android.click", messages[0].jsonObject.getValue("content").jsonPrimitive.content)
    }

    @Test
    fun `aliases are valid stable and distinguish punctuation from existing underscores`() {
        val names = listOf("android.click", "android_click", "valid-name", "x".repeat(90), "ä.tool", "")
        val mapping = OpenAiToolNames(request(names))
        val reordered = OpenAiToolNames(request(names.reversed()))
        val aliases = names.map(mapping::toWire)
        assertEquals(names.size, aliases.toSet().size)
        aliases.forEach { assertTrue(it.matches(Regex("[A-Za-z0-9_-]{1,64}"))) }
        names.forEach { name ->
            assertEquals(mapping.toWire(name), reordered.toWire(name))
            assertEquals(mapping.toWire(name), OpenAiToolNames(request(listOf(name))).toWire(name))
            assertEquals(name, mapping.toLocal(mapping.toWire(name)))
        }
        assertEquals("android_click", mapping.toWire("android_click"))
        assertEquals("valid-name", mapping.toWire("valid-name"))
    }

    @Test
    fun `collision between an alias and an original name fails before encoding`() {
        val alias = OpenAiToolNames(request(listOf("android.click"))).toWire("android.click")
        assertThrows(GatewayFailureException::class.java) {
            ChatCompletionCodec.forOpenAi(request(listOf("android.click", alias)))
        }
        val historyCollision = request(listOf("android.click")).copy(
            messages = listOf(
                AgentMessage(
                    AgentMessage.Role.ASSISTANT, "",
                    toolCall = AgentToolCall(ToolCallId("old"), alias, "{}"),
                ),
            ),
        )
        assertThrows(GatewayFailureException::class.java) {
            ChatCompletionCodec.forOpenAi(historyCollision)
        }
    }

    @Test
    fun `duplicate OpenAI tool definitions fail explicitly`() {
        assertThrows(GatewayFailureException::class.java) {
            ChatCompletionCodec.forOpenAi(request(listOf("android.click", "android.click")))
        }
    }

    @Test
    fun `fragmented interleaved streaming names restore original calls only after assembly`() {
        val request = request(listOf("android.click", "caddie.complete"))
        val codec = ChatCompletionCodec.forOpenAi(request)
        val mapping = OpenAiToolNames(request)
        val first = mapping.toWire("android.click")
        val second = mapping.toWire("caddie.complete")
        val stream = ChatCompletionStream(codec)
        fun event(data: String): List<ModelDelta> = stream.acceptLine("data: $data") + stream.acceptLine("")
        assertTrue(event("""{"choices":[{"delta":{"tool_calls":[{"index":1,"id":"c2","function":{"name":"${second.take(9)}","arguments":"{"}},{"index":0,"id":"c1","function":{"name":"${first.take(5)}","arguments":"{"}}]}}]}""").isEmpty())
        assertTrue(event("""{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"name":"${first.drop(5)}","arguments":"}"}},{"index":1,"function":{"name":"${second.drop(9)}","arguments":"}"}}]},"finish_reason":"tool_calls"}]}""").isEmpty())
        assertTrue(event("""{"choices":[],"usage":{"completion_tokens":20}}""").isEmpty())
        assertEquals(
            listOf(
                ModelDelta.ToolCall(ToolCallId("c1"), "android.click", "{}"),
                ModelDelta.ToolCall(ToolCallId("c2"), "caddie.complete", "{}"),
                ModelDelta.Completed,
            ),
            event("[DONE]"),
        )
        assertTrue(stream.endOfInput().isEmpty())
    }

    @Test
    fun `unknown OpenAI response names fail closed`() {
        val stream = ChatCompletionStream(ChatCompletionCodec.forOpenAi(request(listOf("android.click"))))
        stream.acceptLine("""data: {"choices":[{"delta":{"tool_calls":[{"id":"c1","function":{"name":"unknown_alias","arguments":"{}"}}]}}]}""")
        stream.acceptLine("")
        stream.acceptLine("data: [DONE]")
        assertThrows(GatewayFailureException::class.java) { stream.acceptLine("") }
    }

    @Test
    fun `legacy codec preserves dotted names and thinking for local Luna and study models`() {
        val request = request(listOf("android.click"))
        listOf("gpt-6-luna", "study-model").forEach { model ->
            val root = Json.parseToJsonElement(
                ChatCompletionCodec().encodeRequest(request, model, false),
            ).jsonObject
            assertEquals("false", root.getValue("chat_template_kwargs").jsonObject.getValue("enable_thinking").jsonPrimitive.content)
            assertNull(root["reasoning_effort"])
            val name = root.getValue("tools").jsonArray.single().jsonObject
                .getValue("function").jsonObject.getValue("name").jsonPrimitive.content
            assertEquals("android.click", name)
        }
        assertFalse(ChatCompletionCodec().localToolName("android.click").contains("__"))
    }

    private fun request(names: List<String>) = ModelRequest(
        RunId("synthetic-openai-test"),
        listOf(AgentMessage(AgentMessage.Role.USER, "Synthetic integration test")),
        names.map { ToolDefinition(it, "Synthetic tool", """{"type":"object"}""") },
    )
}
