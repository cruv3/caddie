package com.caddie.tool.android

import com.caddie.agent.core.AttemptId
import com.caddie.agent.core.ModelDelta
import com.caddie.agent.core.RunId
import com.caddie.agent.core.ToolDefinition
import com.caddie.agent.core.ToolContinuation
import com.caddie.agent.core.ToolRegistry
import com.caddie.agent.core.ToolResult
import com.caddie.executor.accessibility.ActionRecoveryMetadata
import com.caddie.executor.accessibility.ActionRequest
import com.caddie.executor.accessibility.ActionOutcome
import com.caddie.executor.accessibility.ExecutionGateway
import com.caddie.executor.accessibility.RequestedAction
import com.caddie.executor.accessibility.VerifiedActionExecutor

/** Exposes verified semantic Accessibility operations to the native agent. */
class AndroidToolRegistry(
    private val gateway: ExecutionGateway,
    private val executor: VerifiedActionExecutor,
    private val resultReporter: (String) -> Unit = {},
    private val requireHumanNarration: Boolean = false,
) : ToolRegistry {
    override fun definitions(): List<ToolDefinition> = definitions(requireHumanNarration)

    override suspend fun execute(
        runId: RunId,
        call: ModelDelta.ToolCall,
    ): ToolResult {
        if (call.name == "android.observe") {
            AndroidToolCodec.requireObserveArguments(call.argumentsJson)
            return ToolResult(call.id, AndroidToolCodec.observationJson(gateway.observe()))
        }

        val mutation = AndroidToolCodec.parseMutation(call.name, call.argumentsJson)
        val destinationPackages = gateway.resolveDestinationPackages(mutation.action)
            .takeIf {
                it.isNotEmpty() &&
                    (mutation.action is RequestedAction.OpenApp || mutation.action is RequestedAction.OpenUrl)
            }
        val selectorFingerprint = destinationPackages
            ?.let(AndroidToolCodec::fingerprintPackages)
            ?: AndroidToolCodec.fingerprint(mutation.target)
        val postconditionFingerprint = destinationPackages
            ?.let(AndroidToolCodec::fingerprintPackages)
            ?: AndroidToolCodec.fingerprint(mutation.postcondition)
        val result = executor.execute(
            ActionRequest(
                recovery = ActionRecoveryMetadata(
                    runId = runId,
                    attemptId = AttemptId("${runId.value}:${call.id.value}"),
                ),
                target = mutation.target,
                action = mutation.action,
                selectorFingerprint = selectorFingerprint,
                postconditionFingerprint = postconditionFingerprint,
                recoverySpecId = RECOVERY_SPEC_ID,
            ),
        ) {
            val observation = gateway.observe()
            gateway.verifyNavigation(mutation.action, observation)
                ?: destinationPackages?.let { packages ->
                    AndroidToolCodec.verifyActivePackage(packages, observation)
                }
                ?: AndroidToolCodec.verify(mutation.postcondition, observation)
        }
        val toolResult = ToolResult(
            callId = call.id,
            contentJson = AndroidToolCodec.executionJson(result),
            isError = AndroidToolCodec.isError(result.status),
            continuation =
                if (AndroidToolCodec.isError(result.status) &&
                    !result.dispatchOutcome.isSafeToReplan()
                ) {
                    ToolContinuation.PAUSE_FOR_VERIFICATION
                } else {
                    ToolContinuation.CONTINUE
                },
        )
        resultReporter(
            "tool=${call.name} status=${result.status} continuation=${toolResult.continuation}",
        )
        return toolResult
    }

    /** UI drift before platform acceptance is safe to feed back to the model. */
    private fun ActionOutcome.isSafeToReplan(): Boolean =
        when (this) {
            ActionOutcome.TargetMissing,
            ActionOutcome.TargetAmbiguous,
            ActionOutcome.TargetNotVisible,
            ActionOutcome.TargetDisabled,
            ActionOutcome.BlockedByWindow,
            ActionOutcome.ActionUnavailable,
            ActionOutcome.StaleObservation,
            ActionOutcome.AccessibilityUnavailable,
            ActionOutcome.InvalidTarget,
            -> true
            ActionOutcome.Accepted,
            ActionOutcome.AlreadySatisfied,
            ActionOutcome.ActionRejected,
            -> false
        }

    private companion object {
        const val RECOVERY_SPEC_ID = "android.semantic-postcondition.v1"
        const val TARGET =
            """{"type":"object","additionalProperties":false,"properties":{"package_name":{"type":"string"},"resource_id":{"type":"string"},"text":{"type":"string"},"content_description":{"type":"string"},"class_name":{"type":"string"},"focused":{"type":"boolean"},"expected_state":{"type":"object","additionalProperties":false,"properties":{"checked":{"type":"boolean"},"selected":{"type":"boolean"},"text":{"type":"string"}}}},"minProperties":1}"""
        const val POSTCONDITION =
            """{"type":"object","additionalProperties":false,"properties":{"target":$TARGET,"exists":{"type":"boolean"}},"required":["target"]}"""

        const val WHY =
            """"why":{"type":"string","minLength":1,"maxLength":80}"""

        fun mutationSchema(
            extraProperties: String = "",
            extraRequired: String = "",
            narrated: Boolean = false,
        ): String {
            val whyProperty = if (narrated) ",$WHY" else ""
            val whyRequired = if (narrated) ",\"why\"" else ""
            return """{"type":"object","additionalProperties":false,"properties":{"target":$TARGET,"postcondition":$POSTCONDITION$extraProperties$whyProperty},"required":["target","postcondition"$extraRequired$whyRequired]}"""
        }

        fun definitions(narrated: Boolean): List<ToolDefinition> {
            val whyProperty = if (narrated) ",$WHY" else ""
            val whyRequired = if (narrated) ",\"why\"" else ""
            val narrationHint = if (narrated) {
                " Include why: a natural English action phrase of at most 80 characters for the user."
            } else {
                ""
            }
            return listOf(
                ToolDefinition(
                    "android.observe",
                    "Observe all currently interactive Android Accessibility windows.",
                    """{"type":"object","additionalProperties":false,"properties":{}}""",
                ),
                ToolDefinition(
                    "android.back",
                    "Perform Android's global back action and verify the resulting screen.$narrationHint",
                    """{"type":"object","additionalProperties":false,"properties":{"dismiss_input_method_only":{"type":"boolean"},"postcondition":$POSTCONDITION$whyProperty},"required":["postcondition"$whyRequired]}""",
                ),
                ToolDefinition(
                    "android.open_app",
                    "Open one installed Android package or explicit activity and verify it.$narrationHint",
                    """{"type":"object","additionalProperties":false,"properties":{"package_name":{"type":"string"},"postcondition":$POSTCONDITION$whyProperty},"required":["package_name","postcondition"$whyRequired]}""",
                ),
                ToolDefinition(
                    "android.open_url",
                    "Open one absolute HTTPS URL and verify the resulting screen.$narrationHint",
                    """{"type":"object","additionalProperties":false,"properties":{"url":{"type":"string"},"postcondition":$POSTCONDITION$whyProperty},"required":["url","postcondition"$whyRequired]}""",
                ),
                ToolDefinition(
                    "android.click",
                    "Click one uniquely identified semantic UI target and verify its postcondition.$narrationHint",
                    mutationSchema(narrated = narrated),
                ),
                ToolDefinition(
                    "android.long_click",
                    "Long-click one uniquely identified semantic UI target and verify its postcondition.$narrationHint",
                    mutationSchema(narrated = narrated),
                ),
                ToolDefinition(
                    "android.set_text",
                    "Replace text in one semantic editable target and verify its postcondition.$narrationHint",
                    mutationSchema(
                        ",\"value\":{\"type\":\"string\"},\"submit\":{\"type\":\"boolean\"}",
                        ",\"value\"",
                        narrated,
                    ),
                ),
                ToolDefinition(
                    "android.set_checked",
                    "Set one semantic checkable target and verify its postcondition.$narrationHint",
                    mutationSchema(
                        ",\"checked\":{\"type\":\"boolean\"}",
                        ",\"checked\"",
                        narrated,
                    ),
                ),
                ToolDefinition(
                    "android.scroll",
                    "Scroll one semantic container and verify its postcondition.$narrationHint",
                    mutationSchema(
                        ",\"forward\":{\"type\":\"boolean\"}",
                        ",\"forward\"",
                        narrated,
                    ),
                ),
            )
        }
    }
}
