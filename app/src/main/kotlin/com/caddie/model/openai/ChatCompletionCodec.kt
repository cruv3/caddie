package com.caddie.model.openai

import com.caddie.agent.core.AgentMessage
import com.caddie.agent.core.ModelRequest
import com.caddie.model.gateway.GatewayFailure
import com.caddie.model.gateway.GatewayFailureException
import com.caddie.model.gateway.GatewayFailureKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Holds one partial tool-call payload from a streamed response. */
internal data class ToolCallFragment(
    val index: Int,
    val idPart: String?,
    val namePart: String?,
    val argumentsPart: String?,
)

/** Contains decoded text, tool fragments, and completion state for one chunk. */
internal data class CompletionChunk(
    val text: String?,
    val reasoning: String?,
    val toolFragments: List<ToolCallFragment>,
    val finishReason: String?,
)

/** Encodes chat-completion requests and decodes streamed response chunks. */
internal class ChatCompletionCodec(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun encodeRequest(
        request: ModelRequest,
        modelId: String,
        thinkingEnabled: Boolean? = null,
    ): String =
        buildJsonObject {
            put("model", modelId)
            put("stream", true)
            thinkingEnabled?.let { enabled ->
                put(
                    "chat_template_kwargs",
                    buildJsonObject { put("enable_thinking", enabled) },
                )
            }
            put(
                "messages",
                buildJsonArray {
                    request.messages.forEach { message ->
                        add(
                            buildJsonObject {
                                put("role", message.role.wireName())
                                when {
                                    message.toolCall != null -> {
                                        if (message.content.isBlank()) {
                                            put("content", JsonNull)
                                        } else {
                                            put("content", message.content)
                                        }
                                        put(
                                            "tool_calls",
                                            buildJsonArray {
                                                add(
                                                    buildJsonObject {
                                                        put("id", message.toolCall.id.value)
                                                        put("type", "function")
                                                        put(
                                                            "function",
                                                            buildJsonObject {
                                                                put("name", message.toolCall.name)
                                                                put(
                                                                    "arguments",
                                                                    message.toolCall.argumentsJson,
                                                                )
                                                            },
                                                        )
                                                    },
                                                )
                                            },
                                        )
                                    }
                                    message.role == AgentMessage.Role.TOOL -> {
                                        put(
                                            "tool_call_id",
                                            requireNotNull(message.toolCallId).value,
                                        )
                                        put("content", message.content)
                                    }
                                    else -> put("content", message.content)
                                }
                            },
                        )
                    }
                },
            )
            if (request.tools.isNotEmpty()) {
                put(
                    "tools",
                    buildJsonArray {
                        request.tools.forEach { tool ->
                            val schema =
                                try {
                                    json
                                        .parseToJsonElement(
                                            tool.inputSchemaJson,
                                        ).jsonObject
                                } catch (error: Exception) {
                                    throw protocolFailure(
                                        "tool schema must be a JSON object",
                                        error,
                                    )
                                }
                            add(
                                buildJsonObject {
                                    put("type", "function")
                                    put(
                                        "function",
                                        buildJsonObject {
                                            put("name", tool.name)
                                            put(
                                                "description",
                                                tool.description,
                                            )
                                            put("parameters", schema)
                                        },
                                    )
                                },
                            )
                        }
                    },
                )
            }
        }.toString()

    fun decodeChunk(data: String): CompletionChunk {
        val root =
            try {
                json.parseToJsonElement(data).jsonObject
            } catch (error: Exception) {
                throw protocolFailure(
                    "completion chunk is not valid JSON",
                    error,
                )
            }
        val choice =
            root["choices"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?: return CompletionChunk(
                    null,
                    null,
                    emptyList(),
                    null,
                )
        val delta =
            choice["delta"]?.jsonObject
                ?: JsonObject(emptyMap())
        val fragments =
            delta["tool_calls"]
                ?.jsonArray
                ?.map { value ->
                    val item = value.jsonObject
                    val function = item["function"]?.jsonObject
                    ToolCallFragment(
                        index =
                            item["index"]
                                ?.jsonPrimitive
                                ?.content
                                ?.toIntOrNull()
                                ?: 0,
                        idPart =
                            item["id"]
                                ?.jsonPrimitive
                                ?.contentOrNull,
                        namePart =
                            function
                                ?.get("name")
                                ?.jsonPrimitive
                                ?.contentOrNull,
                        argumentsPart =
                            function
                                ?.get("arguments")
                                ?.jsonPrimitive
                                ?.contentOrNull,
                    )
                }.orEmpty()
        return CompletionChunk(
            text =
                delta["content"]
                    ?.jsonPrimitive
                    ?.contentOrNull,
            reasoning =
                delta["reasoning_content"]
                    ?.jsonPrimitive
                    ?.contentOrNull,
            toolFragments = fragments,
            finishReason =
                choice["finish_reason"]
                    ?.jsonPrimitive
                    ?.contentOrNull,
        )
    }

    fun requireArgumentsObject(arguments: String) {
        try {
            json.parseToJsonElement(arguments).jsonObject
        } catch (error: Exception) {
            throw GatewayFailureException(
                GatewayFailure(
                    GatewayFailureKind.TOOL_CALL,
                    "tool arguments must be one JSON object",
                    retryable = false,
                ),
                error,
            )
        }
    }

    private fun AgentMessage.Role.wireName(): String =
        when (this) {
            AgentMessage.Role.SYSTEM -> "system"
            AgentMessage.Role.USER -> "user"
            AgentMessage.Role.ASSISTANT -> "assistant"
            AgentMessage.Role.TOOL -> "tool"
        }

    private fun protocolFailure(
        message: String,
        cause: Throwable,
    ) = GatewayFailureException(
        GatewayFailure(
            GatewayFailureKind.PROTOCOL,
            message,
            retryable = false,
        ),
        cause,
    )
}
