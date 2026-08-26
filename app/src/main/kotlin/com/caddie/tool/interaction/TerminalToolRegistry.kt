package com.caddie.tool.interaction

import com.caddie.agent.core.ModelDelta
import com.caddie.agent.core.RunId
import com.caddie.agent.core.ToolContinuation
import com.caddie.agent.core.ToolDefinition
import com.caddie.agent.core.ToolRegistry
import com.caddie.agent.core.ToolResult
import org.json.JSONObject

/** Lets the normal model end a run explicitly as verified success or honest failure. */
class TerminalToolRegistry : ToolRegistry {
    override fun definitions(): List<ToolDefinition> = DEFINITIONS

    override suspend fun execute(runId: RunId, call: ModelDelta.ToolCall): ToolResult {
        val key = when (call.name) {
            COMPLETE -> "message"
            FAIL -> "reason"
            else -> error("Unknown terminal tool: ${call.name}")
        }
        val message = runCatching { JSONObject(call.argumentsJson).optString(key).trim() }
            .getOrDefault("")
        if (message.isEmpty()) {
            return ToolResult(
                call.id,
                JSONObject()
                    .put("ok", false)
                    .put("error", "$key must not be blank")
                    .toString(),
                isError = true,
            )
        }
        return when (call.name) {
            COMPLETE -> ToolResult(
                call.id,
                JSONObject().put("ok", true).toString(),
                continuation = ToolContinuation.COMPLETE_RUN,
                terminalMessage = message,
            )
            FAIL -> ToolResult(
                call.id,
                JSONObject().put("ok", false).toString(),
                isError = true,
                continuation = ToolContinuation.FAIL_RUN,
                terminalMessage = message,
            )
            else -> error("unreachable terminal tool: ${call.name}")
        }
    }

    private companion object {
        const val COMPLETE = "caddie.complete"
        const val FAIL = "caddie.fail"
        val DEFINITIONS = listOf(
            ToolDefinition(
                COMPLETE,
                "Finish only after the requested outcome is verified. After Android actions, call android.observe immediately before this tool.",
                """{"type":"object","additionalProperties":false,"properties":{"message":{"type":"string"}},"required":["message"]}""",
            ),
            ToolDefinition(
                FAIL,
                "Stop honestly when the task cannot be completed safely or reliably.",
                """{"type":"object","additionalProperties":false,"properties":{"reason":{"type":"string"}},"required":["reason"]}""",
            ),
        )
    }
}
