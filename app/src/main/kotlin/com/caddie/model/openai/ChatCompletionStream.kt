package com.caddie.model.openai

import com.caddie.agent.core.ModelDelta
import com.caddie.agent.core.ToolCallId
import com.caddie.model.gateway.GatewayFailure
import com.caddie.model.gateway.GatewayFailureException
import com.caddie.model.gateway.GatewayFailureKind

/** Converts an HTTP response body into a flow of model deltas. */
internal class ChatCompletionStream(
    private val codec: ChatCompletionCodec,
) {
    /** Tracks fragments belonging to one streamed tool call. */
    private data class Slot(
        val id: StringBuilder = StringBuilder(),
        val name: StringBuilder = StringBuilder(),
        val arguments: StringBuilder = StringBuilder(),
    )

    private val eventData = mutableListOf<String>()
    private val slots = sortedMapOf<Int, Slot>()
    private var completed = false

    fun acceptLine(line: String): List<ModelDelta> {
        if (line.isEmpty()) {
            return flushEvent()
        }
        if (completed) {
            throw failure("stream contained data after completion")
        }
        if (line.startsWith(":")) {
            return emptyList()
        }
        if (line.startsWith("data:")) {
            eventData += line.removePrefix("data:").trimStart()
        }
        return emptyList()
    }

    fun endOfInput(): List<ModelDelta> {
        val finalDeltas =
            if (eventData.isNotEmpty()) {
                flushEvent()
            } else {
                emptyList()
            }
        if (!completed) {
            throw failure("model stream ended before [DONE]")
        }
        return finalDeltas
    }

    private fun flushEvent(): List<ModelDelta> {
        if (eventData.isEmpty()) {
            return emptyList()
        }
        val data = eventData.joinToString("\n")
        eventData.clear()
        if (data == "[DONE]") {
            completed = true
            return buildList {
                slots.forEach { (_, slot) ->
                    val id = slot.id.toString()
                    val name = slot.name.toString()
                    val arguments = slot.arguments.toString()
                    if (id.isBlank() || name.isBlank()) {
                        throw failure(
                            "completed tool call is missing id or name",
                        )
                    }
                    codec.requireArgumentsObject(arguments)
                    add(
                        ModelDelta.ToolCall(
                            ToolCallId(id),
                            name,
                            arguments,
                        ),
                    )
                }
                add(ModelDelta.Completed)
            }
        }

        val chunk = codec.decodeChunk(data)
        chunk.toolFragments.forEach { fragment ->
            val slot =
                slots.getOrPut(fragment.index) {
                    Slot()
                }
            fragment.idPart?.let(slot.id::append)
            fragment.namePart?.let(slot.name::append)
            fragment.argumentsPart?.let(slot.arguments::append)
        }
        return buildList {
            chunk.text
                ?.takeIf(String::isNotEmpty)
                ?.let { add(ModelDelta.Text(it)) }
        }
    }

    private fun failure(message: String) =
        GatewayFailureException(
            GatewayFailure(
                GatewayFailureKind.PROTOCOL,
                message,
                retryable = false,
            ),
        )
}
