package com.caddie.agent.core

/** Describes one assistant tool request retained in conversation history. */
data class AgentToolCall(
    val id: ToolCallId,
    val name: String,
    val argumentsJson: String,
)

/** Represents one role-tagged message in an agent conversation. */
data class AgentMessage(
    val role: Role,
    val content: String,
    val toolCall: AgentToolCall? = null,
    val toolCallId: ToolCallId? = null,
) {
    init {
        require(toolCall == null || role == Role.ASSISTANT) {
            "Only assistant messages may contain a tool call"
        }
        require(toolCallId == null || role == Role.TOOL) {
            "Only tool messages may reference a tool call"
        }
        require(role != Role.TOOL || toolCallId != null) {
            "Tool messages must reference a tool call"
        }
    }

    /** Identifies who produced an agent message. */
    enum class Role {
        SYSTEM,
        USER,
        ASSISTANT,
        TOOL,
    }
}

/** Contains the conversation and tool definitions sent to a model. */
data class ModelRequest(
    val runId: RunId,
    val messages: List<AgentMessage>,
    val tools: List<ToolDefinition>,
)

/** Represents one streamed piece of model output. */
sealed interface ModelDelta {
    /** Contains streamed assistant text. */
    data class Text(val value: String) : ModelDelta

    /** Contains a complete tool request emitted by the model. */
    data class ToolCall(
        val id: ToolCallId,
        val name: String,
        val argumentsJson: String,
    ) : ModelDelta

    /** Marks the end of a model stream. */
    data object Completed : ModelDelta
}

/** Describes a tool that the model may call. */
data class ToolDefinition(
    val name: String,
    val description: String,
    val inputSchemaJson: String,
)

/** Tells the agent loop whether a returned result is safe to continue from. */
enum class ToolContinuation {
    CONTINUE,
    PAUSE_FOR_VERIFICATION,
    COMPLETE_RUN,
    FAIL_RUN,
}

/** Contains the serialized result returned by a tool. */
data class ToolResult(
    val callId: ToolCallId,
    val contentJson: String,
    val isError: Boolean = false,
    val continuation: ToolContinuation = ToolContinuation.CONTINUE,
    val terminalMessage: String? = null,
)

/** Records whether oversight approved a proposed action and why. */
data class OversightDecision(
    val approved: Boolean,
    val reason: String? = null,
    val responseLatencyMs: Double? = null,
)
