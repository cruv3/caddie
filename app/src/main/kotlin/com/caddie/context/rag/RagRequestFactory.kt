package com.caddie.context.rag

import com.caddie.agent.core.AgentMessage
import com.caddie.agent.core.ModelRequest
import com.caddie.agent.core.RequestFactory
import com.caddie.agent.core.RunSnapshot
import com.caddie.agent.core.ToolDefinition

/** Decorates an existing request with one bounded reference-only context message. */
class RagRequestFactory(
    private val delegate: RequestFactory,
    private val bundleProvider: (RunSnapshot) -> ContextBundle?,
) : RequestFactory {
    override fun create(
        snapshot: RunSnapshot,
        tools: List<ToolDefinition>,
    ): ModelRequest {
        val request = delegate.create(snapshot, tools)
        val bundle = bundleProvider(snapshot) ?: return request
        val leadingSystemMessages = request.messages.takeWhile {
            it.role == AgentMessage.Role.SYSTEM
        }
        val combinedSystemText = buildList {
            addAll(leadingSystemMessages.map(AgentMessage::content))
            add(bundle.renderedText)
        }.joinToString("\n\n")
        val messages = listOf(
            AgentMessage(AgentMessage.Role.SYSTEM, combinedSystemText),
        ) + request.messages.drop(leadingSystemMessages.size)
        return request.copy(messages = messages)
    }
}
