package com.caddie.tool.interaction

import com.caddie.agent.core.ModelDelta
import com.caddie.agent.core.RunId
import com.caddie.agent.core.ToolDefinition
import com.caddie.agent.core.ToolRegistry
import com.caddie.agent.core.ToolResult
import org.json.JSONObject

/** Lets the normal agent ask one spoken clarification without ending its run. */
class UserInteractionToolRegistry(
    private val askUser: suspend (RunId, String) -> String?,
) : ToolRegistry {
    override fun definitions(): List<ToolDefinition> = listOf(DEFINITION)

    override suspend fun execute(runId: RunId, call: ModelDelta.ToolCall): ToolResult {
        require(call.name == TOOL_NAME) { "Unknown interaction tool: ${call.name}" }
        val question = JSONObject(call.argumentsJson).optString("question").trim()
        require(question.isNotBlank()) { "Question must not be blank" }
        val answer = askUser(runId, question)?.trim()?.takeIf(String::isNotBlank)
        val content = JSONObject().put("answered", answer != null)
        if (answer != null) content.put("answer", answer)
        return ToolResult(call.id, content.toString())
    }

    companion object {
        const val TOOL_NAME = "caddie.ask_user"
        private val DEFINITION = ToolDefinition(
            name = TOOL_NAME,
            description =
                "Ask the user one short spoken clarification and continue this same task with the answer. " +
                    "Use only when required information cannot be inferred safely.",
            inputSchemaJson =
                """{"type":"object","additionalProperties":false,"properties":{"question":{"type":"string","minLength":1}},"required":["question"]}""",
        )
    }
}
