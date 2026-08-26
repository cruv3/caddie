package com.caddie.app.runtime

import com.caddie.agent.core.ModelDelta
import com.caddie.agent.core.ToolCallValidationException
import com.caddie.executor.accessibility.ExecutionGateway
import com.caddie.executor.accessibility.NodeResolver
import com.caddie.executor.accessibility.TargetResolution
import com.caddie.executor.accessibility.WindowTargetResolver
import com.caddie.executor.accessibility.requiredUiAction
import com.caddie.tool.android.AndroidToolCodec
import org.json.JSONObject

/** Binds resource-only normal actions to the visible node label seen before oversight. */
internal class NormalSemanticCallBinder(
    private val gateway: ExecutionGateway,
) {
    suspend fun bind(call: ModelDelta.ToolCall): ModelDelta.ToolCall {
        if (call.name !in NormalActionRiskClassifier.SEMANTIC_TARGET_ACTIONS) return call
        val arguments = JSONObject(call.argumentsJson)
        val targetJson = arguments.optJSONObject("target") ?: return call
        if (targetJson.optString("text").isNotBlank() ||
            targetJson.optString("content_description").isNotBlank()
        ) return call

        val mutation = AndroidToolCodec.parseMutation(call.name, call.argumentsJson)
        val observation = gateway.observe()
        val resolution = WindowTargetResolver.resolve(
            target = mutation.target,
            requiredAction = mutation.action.requiredUiAction(),
            observation = observation,
        )
        val actionableNode = (resolution as? TargetResolution.Found)?.node
            ?: throw ToolCallValidationException(
                "resource-only normal action is not one visible actionable node; observe and retry with a label",
            )
        val node = actionableNode.takeIf { NodeResolver.matches(mutation.target, it) }
            ?: observation.nodes.filter { candidate ->
                candidate.enabled && candidate.visibleToUser &&
                    NodeResolver.matches(mutation.target, candidate)
            }.singleOrNull()
            ?: throw ToolCallValidationException(
                "resource-only normal action has no unambiguous visible label; observe and retry",
            )
        val text = node.text?.trim()?.takeIf(String::isNotBlank)
        val description = node.contentDescription?.trim()?.takeIf(String::isNotBlank)
        if (text == null && description == null) {
            throw ToolCallValidationException(
                "resource-only normal action requires a visible accessible label",
            )
        }
        if (text != null) targetJson.put("text", text) else targetJson.put("content_description", description)
        return call.copy(argumentsJson = arguments.toString())
    }
}
