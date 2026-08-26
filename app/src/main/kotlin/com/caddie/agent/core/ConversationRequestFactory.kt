package com.caddie.agent.core

/** Adds the runtime system prompt to the reconstructed run conversation. */
class ConversationRequestFactory(
    private val systemPrompt: String,
) : RequestFactory {
    init {
        require(systemPrompt.isNotBlank()) { "systemPrompt must not be blank" }
    }

    override fun create(
        snapshot: RunSnapshot,
        tools: List<ToolDefinition>,
    ): ModelRequest =
        ModelRequest(
            runId = snapshot.runId,
            messages =
                listOf(AgentMessage(AgentMessage.Role.SYSTEM, systemPrompt)) +
                    snapshot.messages,
            tools = tools,
        )
}
