package com.caddie.app.runtime

import com.caddie.agent.core.AgentMessage
import com.caddie.agent.core.ModelDelta
import com.caddie.agent.core.ModelRequest
import com.caddie.agent.core.RequestFactory
import com.caddie.agent.core.RunSnapshot
import com.caddie.agent.core.ToolDefinition
import com.caddie.agent.core.ToolCallTransformer
import com.caddie.agent.core.ToolContinuation
import com.caddie.study.StudyActionClassifier
import com.caddie.study.StudyActionAssessment
import com.caddie.study.StudyActionKind
import com.caddie.study.StudyCondition
import com.caddie.study.StudyGate
import com.caddie.study.StudyOversightPolicy
import com.caddie.study.runtime.coordinator.ArmedTrialCoordinator
import com.caddie.study.runtime.model.RuntimeStudyCondition
import com.caddie.study.runtime.model.ErrorVariant
import com.caddie.study.runtime.model.StepType
import com.caddie.study.runtime.model.TrialSpec
import com.caddie.study.runtime.executor.StudyTextHelpers
import com.caddie.study.runtime.audit.ControlledErrorEvent
import com.caddie.study.runtime.audit.ControlledErrorRecorder
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import org.json.JSONArray

/** Starts the exact study claim selected by the coordinator. */
fun interface ClaimedStudyRunner {
    suspend fun run(claim: ArmedTrialCoordinator.ClaimedTrial): NativeTaskResult
}

/** Builds an isolated agent profile from the coordinator-owned study claim. */
class StudyAgentProfileFactory private constructor(
    private val gate: StudyGate,
    private val classifierFactory: (ArmedTrialCoordinator.ClaimedTrial) -> StudyActionClassifier,
) {
    constructor(
        gate: StudyGate,
        classifier: StudyActionClassifier,
    ) : this(gate, { classifier })

    fun create(claim: ArmedTrialCoordinator.ClaimedTrial): NativeRunProfile {
        val classifier = classifierFactory(claim)
        return NativeRunProfile(
            oversight = StudyOversightPolicy(
                condition = claim.config.condition.toAgentCondition(),
                gate = gate,
                classifier = classifier,
            ),
            requestFactory = StudyProgressRequestFactory(
                basePrompt = studySystemPrompt(claim.spec),
                currentDirective = {
                    (classifier as? TrialSpecStudyActionClassifier)?.currentDirective()
                },
                allowedToolNames = {
                    (classifier as? TrialSpecStudyActionClassifier)?.allowedToolNames()
                },
            ),
            transformer = classifier,
        )
    }

    private fun studySystemPrompt(spec: TrialSpec): String = buildString {
        appendLine("You are Caddie executing a fixed Android study task.")
        appendLine("Task: ${spec.instructionDe}")
        appendLine("Execute the following actions exactly in this order:")
        spec.steps.forEachIndexed { index, step ->
            appendLine("${index + 1}. ${step.action}")
        }
        append(
            "Use android.observe exactly once for each listed capture step. " +
                "Return exactly one tool call per response and wait for its result before " +
                "choosing the next action. A capture step is completed by reading the latest " +
                "android.observe result; remember that value and continue with the next " +
                "numbered action instead of returning final text. " +
                "Do not skip, reorder, replace, or reinterpret an action. In particular, " +
                "never replace android.open_url with android.open_app. Stop only after all " +
                "listed actions are complete.",
        )
    }

    companion object {
        fun fromTrialSpec(
            gate: StudyGate,
            recorder: ControlledErrorRecorder,
        ): StudyAgentProfileFactory =
            StudyAgentProfileFactory(gate) { claim ->
                TrialSpecStudyActionClassifier(
                    spec = claim.spec,
                    unmatchedMutation = if (
                        claim.config.condition == RuntimeStudyCondition.FINAL_CHECKPOINT
                    ) {
                        StudyActionKind.COMMIT
                    } else {
                        StudyActionKind.PARTICIPANT_MEANINGFUL
                    },
                    injectError = claim.config.injectError,
                    onControlledError = { stepId, variant, correct, wrong ->
                        recorder.record(
                            ControlledErrorEvent(
                                studyRunId = requireNotNull(claim.config.studyRunId) {
                                    "controlled error requires a durable study run"
                                },
                                trialAttemptId = claim.config.trialAttemptId,
                                trialIndex = claim.config.trialIndex,
                                taskId = claim.config.taskId,
                                stepId = stepId,
                                errorVariantId = variant.id,
                                field = variant.field,
                                correctValue = correct,
                                wrongValue = wrong,
                            ),
                        )
                    },
                )
            }
    }
}

/** Classifies native tool calls against the immutable steps of one trial. */
class TrialSpecStudyActionClassifier(
    private val spec: TrialSpec,
    private val unmatchedMutation: StudyActionKind = StudyActionKind.PARTICIPANT_MEANINGFUL,
    private val injectError: Boolean = false,
    private val onControlledError: suspend (String, ErrorVariant, String, String) -> Unit = { _, _, _, _ -> },
) : StudyActionClassifier, ToolCallTransformer {
    private var nextStepIndex = 0
    private val variables = mutableMapOf<String, String>()
    private val injectedVariants = mutableListOf<ErrorVariant>()
    private val correctedVariantIds = mutableSetOf<String>()
    private val executedStepIndexes = mutableSetOf<Int>()
    private var pending: PendingMatch? = null
    private var lastAssessed: PendingMatch? = null
    private var inspectedStepIndex: Int? = null
    private var lastInputText: String? = null
    private val recordedCallIds = mutableSetOf<String>()

    override fun transform(call: ModelDelta.ToolCall): ModelDelta.ToolCall {
        if (call.name == "android.observe" || call.name == "android.scroll") return call
        val expected = nextExecutableStep()
            ?: throw StudyPlanViolationException("study plan is already complete")
        val frozenCall = withFrozenActionArguments(call, expected.value.action, expected.index)
        val matched = findMatch(frozenCall)
            ?: throw StudyPlanViolationException(
                "expected study step ${expected.value.id}: ${expected.value.action}",
            )
        val step = matched.value
        rememberVariables(step.action, frozenCall)
        val expandedAction = expandVariables(step.action)
        val variant = step.errorVariant?.takeIf {
            injectError && step.id in spec.errorSteps &&
                it !in injectedVariants && it.id !in correctedVariantIds
        }
        if (variant == null) {
            pending = PendingMatch(call.id.value, matched.index, expandedAction, null)
            return withReadyPostcondition(frozenCall, step)
        }
        val (effectiveAction, actionChanged) = StudyTextHelpers.injectError(
            expandedAction,
            variant,
            ::expandVariables,
        )
        val effectiveArguments = rewriteArguments(frozenCall.argumentsJson, variant)
        check(actionChanged && effectiveArguments != call.argumentsJson) {
            "controlled error could not be applied safely"
        }
        rememberVariables(effectiveAction, call.copy(argumentsJson = effectiveArguments))
        pending = PendingMatch(call.id.value, matched.index, effectiveAction, variant)
        return withReadyPostcondition(
            call.copy(argumentsJson = effectiveArguments),
            step,
        )
    }

    override fun validateCompletion() {
        val expected = nextExecutableStep() ?: return
        throw StudyPlanViolationException(
            "study ended before step ${expected.value.id}: ${expected.value.action}",
        )
    }

    override suspend fun beforeOversight(call: ModelDelta.ToolCall) {
        val matched = pending?.takeIf { it.callId == call.id.value }
        val variant = matched?.errorVariant ?: return
        if (!recordedCallIds.add(call.id.value)) return
        try {
            onControlledError(
                spec.steps[matched.index].id,
                variant,
                expandVariables(variant.correctValue),
                expandVariables(variant.wrongValue),
            )
            injectedVariants += variant
        } catch (error: Throwable) {
            recordedCallIds.remove(call.id.value)
            throw error
        }
    }

    override fun afterExecution(call: ModelDelta.ToolCall, result: com.caddie.agent.core.ToolResult) {
        val assessed = lastAssessed?.takeIf { it.callId == call.id.value } ?: return
        if (result.isError && result.continuation == ToolContinuation.CONTINUE) {
            nextStepIndex = assessed.index
            inspectedStepIndex = null
        } else if (!result.isError) {
            executedStepIndexes += assessed.index
        }
        lastAssessed = null
    }

    override fun onParticipantCorrection(text: String) {
        if (text.isBlank()) return
        val variant = injectedVariants.asReversed()
            .firstOrNull { it.id !in correctedVariantIds }
            ?: return
        val errorIndex = spec.steps.indexOfFirst { it.errorVariant?.id == variant.id }
        if (errorIndex < 0) return
        if (executedStepIndexes.any { index -> spec.steps.getOrNull(index)?.commit == true }) {
            return
        }
        val rewindIndex = if (errorIndex in executedStepIndexes) {
            variant.correction?.rewindToStepId
                ?.let { stepId -> spec.steps.indexOfFirst { it.id == stepId } }
                ?.takeIf { it >= 0 }
                ?: errorIndex
        } else {
            errorIndex
        }
        correctedVariantIds += variant.id
        nextStepIndex = rewindIndex
        pending = null
        lastAssessed = null
        inspectedStepIndex = null
    }

    override fun assess(call: ModelDelta.ToolCall): StudyActionAssessment {
        if (call.name == "android.observe") {
            if (spec.steps.getOrNull(nextStepIndex)?.action?.let(::isCaptureAction) == true) {
                nextStepIndex += 1
                inspectedStepIndex = null
            } else {
                inspectedStepIndex = nextStepIndex
            }
            return StudyActionAssessment(StudyActionKind.PREPARATORY)
        }
        if (call.name == "android.scroll") {
            return StudyActionAssessment(StudyActionKind.PREPARATORY)
        }
        val pendingMatch = pending?.takeIf { it.callId == call.id.value }
        pending = null
        val matched = pendingMatch ?: findMatch(call)?.let {
            PendingMatch(call.id.value, it.index, it.value.action, null)
        } ?: return StudyActionAssessment(unmatchedMutation)
        lastAssessed = matched
        nextStepIndex = matched.index + 1
        inspectedStepIndex = null
        val step = spec.steps[matched.index]
        rememberVariables(matched.effectiveAction, call)
        val kind = when (step.stepType) {
            StepType.NORMAL -> StudyActionKind.PREPARATORY
            StepType.CONSEQUENTIAL -> StudyActionKind.PARTICIPANT_MEANINGFUL
            StepType.COMMIT -> StudyActionKind.COMMIT
        }
        val finalSummary = if (kind == StudyActionKind.COMMIT) {
            val summary = spec.c2SummaryLines.map(::expandVariables).map(::applySummaryErrors)
            summary + listOfNotNull(step.confirmationText)
                .map(::expandVariables)
                .map { applyConfirmationError(it, matched.errorVariant) }
                .filterNot(summary::contains)
        } else {
            emptyList()
        }
        return StudyActionAssessment(
            kind = kind,
            confirmationText = step.confirmationText
                ?.let(::expandVariables)
                ?.let { applyConfirmationError(it, matched.errorVariant) },
            finalSummary = finalSummary,
        )
    }

    private fun findMatch(call: ModelDelta.ToolCall): IndexedValue<com.caddie.study.runtime.model.StudyStep>? {
        val expected = nextExecutableStep() ?: return null
        if (!matchesTool(expected.value.action, call.name)) return null
        return expected.takeIf { matchesArguments(expected.value.action, call) }
    }

    private fun nextExecutableStep(): IndexedValue<com.caddie.study.runtime.model.StudyStep>? =
        spec.steps.withIndex().firstOrNull { (index, step) ->
            index >= nextStepIndex && !isCaptureAction(step.action)
        }

    internal fun currentDirective(): String {
        val next = spec.steps.getOrNull(nextStepIndex)
            ?: return "Current required study step: finish with a short completion message."
        return if (isCaptureAction(next.action)) {
            "Current required study step: ${next.action}. Call android.observe exactly once, " +
                "read and remember the requested value, then wait for the next turn."
        } else {
            "Current required study step: ${next.action}. Return exactly the single tool call " +
                "that implements this action and no other action."
        }
    }

    internal fun allowedToolNames(): Set<String> {
        val action = spec.steps.getOrNull(nextStepIndex)?.action ?: return emptySet()
        if (isCaptureAction(action)) return setOf("android.observe")
        val expected = toolNameForAction(action) ?: return emptySet()
        return setOf(expected)
    }

    private fun toolNameForAction(action: String): String? {
        val normalized = action.lowercase()
        return when {
            normalized.startsWith("press back") -> "android.back"
            normalized.startsWith("open_url ") -> "android.open_url"
            normalized.startsWith("open ") -> "android.open_app"
            normalized.startsWith("click ") -> "android.click"
            normalized.startsWith("input text ") || normalized.startsWith("replace text ") ->
                "android.set_text"
            else -> null
        }
    }

    /** Capture steps describe information read from observations; every other step must dispatch. */
    private fun isCaptureAction(action: String): Boolean =
        action.trimStart().startsWith("capture ", ignoreCase = true)

    private fun matchesTool(
        action: String,
        toolName: String,
    ): Boolean {
        val normalizedAction = action.lowercase()
        return when {
            normalizedAction.startsWith("press back") ->
                toolName.endsWith(".back") || toolName.endsWith("_back")
            normalizedAction.startsWith("open_url ") -> toolName.endsWith(".open_url")
            normalizedAction.startsWith("open ") -> toolName.endsWith(".open_app")
            normalizedAction.startsWith("click ") -> toolName.endsWith(".click")
            normalizedAction.startsWith("input text ") ||
                normalizedAction.startsWith("replace text ") -> toolName.endsWith(".set_text")
            else -> false
        }
    }

    private fun matchesArguments(action: String, call: ModelDelta.ToolCall): Boolean {
        val expanded = expandVariables(action)
        val normalized = expanded.lowercase()
        val root = runCatching { JSONObject(call.argumentsJson) }.getOrNull() ?: return false
        return when {
            normalized.startsWith("open_url ") ->
                root.optString("url") == expanded.substringAfter(' ').trim()
            normalized.startsWith("open ") ->
                root.optString("package_name") == expanded.substringAfter(' ').trim()
            normalized.startsWith("click ") -> {
                val expected = QUOTED_VALUE.find(expanded)?.groupValues?.get(1) ?: return false
                val target = root.optJSONObject("target") ?: return false
                listOf("resource_id", "text", "content_description")
                    .map(target::optString)
                    .any { actual -> templateMatches(expected, actual) }
            }
            normalized.startsWith("input text ") || normalized.startsWith("replace text ") -> {
                val expected = QUOTED_VALUE.find(expanded)?.groupValues?.get(1) ?: return false
                templateMatches(expected, root.optString("value"))
            }
            normalized.startsWith("press back") -> true
            else -> false
        }
    }

    private fun templateMatches(expected: String, actual: String): Boolean {
        if (actual.isEmpty()) return false
        val placeholders = TEMPLATE_VALUE.findAll(expected).toList()
        if (placeholders.isEmpty()) return actual == expected
        var expectedOffset = 0
        var actualOffset = 0
        placeholders.forEach { placeholder ->
            val fixed = expected.substring(expectedOffset, placeholder.range.first)
            if (!actual.startsWith(fixed, actualOffset)) return false
            actualOffset += fixed.length
            val nextFixedStart = placeholder.range.last + 1
            val nextPlaceholder = placeholders.getOrNull(placeholders.indexOf(placeholder) + 1)
            val nextFixedEnd = nextPlaceholder?.range?.first ?: expected.length
            val nextFixed = expected.substring(nextFixedStart, nextFixedEnd)
            if (nextFixed.isEmpty()) {
                actualOffset = actual.length
            } else {
                val found = actual.indexOf(nextFixed, actualOffset)
                if (found < actualOffset) return false
                actualOffset = found
            }
            expectedOffset = nextFixedStart
        }
        val suffix = expected.substring(expectedOffset)
        return actual.substring(actualOffset) == suffix
    }

    private fun rememberVariables(
        action: String,
        call: ModelDelta.ToolCall,
    ) {
        StudyTextHelpers.googleMapsRoute(action)?.let { (origin, destination) ->
            variables["route_origin"] = origin
            variables["route_destination"] = destination
        }
        if (!call.name.endsWith(".set_text")) return
        val value = runCatching {
            JSONObject(call.argumentsJson).optString("value").takeIf(String::isNotEmpty)
        }.getOrNull() ?: return
        lastInputText = value
        val captured = inputVariable(action, value) ?: return
        variables[captured.first] = captured.second
    }

    private fun inputVariable(
        action: String,
        value: String,
    ): Pair<String, String>? {
        val template = QUOTED_VALUE.find(action)?.groupValues?.get(1) ?: return null
        val placeholder = TEMPLATE_VALUE.findAll(template).singleOrNull() ?: return null
        val prefix = template.substring(0, placeholder.range.first)
        val suffix = template.substring(placeholder.range.last + 1)
        if (!value.startsWith(prefix) || !value.endsWith(suffix)) return null
        val end = value.length - suffix.length
        if (end < prefix.length) return null
        return placeholder.value.removeSurrounding("{", "}") to
            value.substring(prefix.length, end)
    }

    private fun expandVariables(text: String): String =
        TEMPLATE_VALUE.replace(text) { match ->
            variables[match.value.removeSurrounding("{", "}")] ?: match.value
        }

    private fun rewriteArguments(
        argumentsJson: String,
        variant: ErrorVariant,
    ): String {
        val root = JSONObject(argumentsJson)
        var changed = false

        fun rewrite(value: Any?): Any? = when (value) {
            is JSONObject -> value.apply {
                keys().asSequence().toList().forEach { key -> put(key, rewrite(get(key))) }
            }
            is JSONArray -> value.apply {
                for (index in 0 until length()) put(index, rewrite(get(index)))
            }
            is String -> {
                val (replacement, replaced) = StudyTextHelpers.injectError(
                    value,
                    variant,
                    ::expandVariables,
                )
                changed = changed || replaced
                replacement
            }
            else -> value
        }

        rewrite(root)
        check(changed) { "controlled error target missing from tool arguments" }
        return root.toString()
    }

    private fun withReadyPostcondition(
        call: ModelDelta.ToolCall,
        step: com.caddie.study.runtime.model.StudyStep,
    ): ModelDelta.ToolCall {
        val target = when {
            step.readyResourceId != null ->
                JSONObject().put("resource_id", step.readyResourceId)
            readyPackageFor(step) != null ->
                JSONObject().put("package_name", readyPackageFor(step))
            else -> return call
        }
        val root = JSONObject(call.argumentsJson)
        root.put(
            "postcondition",
            JSONObject()
                .put("target", target)
                .put("exists", true),
        )
        return call.copy(argumentsJson = root.toString())
    }

    private fun withFrozenActionArguments(
        call: ModelDelta.ToolCall,
        action: String,
        stepIndex: Int,
    ): ModelDelta.ToolCall {
        val root = JSONObject(call.argumentsJson)
        when {
            action.startsWith("open_url ", ignoreCase = true) ->
                root.put("url", action.substringAfter(' ').trim())
            action.startsWith("open ", ignoreCase = true) ->
                root.put("package_name", action.substringAfter(' ').trim())
            action.startsWith("click ", ignoreCase = true) -> {
                val expected = QUOTED_VALUE.find(expandVariables(action))
                    ?.groupValues?.get(1) ?: return call
                val key = if (RESOURCE_ID.matches(expected)) "resource_id" else "text"
                root.put("target", JSONObject().put(key, expected))
                root.put(
                    "postcondition",
                    frozenClickPostcondition(stepIndex, key, expected),
                )
            }
            action.startsWith("input text ", ignoreCase = true) ||
                action.startsWith("replace text ", ignoreCase = true) -> {
                val expandedTemplate = QUOTED_VALUE.find(expandVariables(action))
                    ?.groupValues?.get(1) ?: return call
                val proposedValue = root.optString("value")
                val value = if (!TEMPLATE_VALUE.containsMatchIn(expandedTemplate)) {
                    expandedTemplate
                } else {
                    if (!matchesArguments(action, call)) return call
                    val variableName = inputVariable(action, proposedValue)?.first
                    variableName?.let {
                        StudyTextHelpers.normalizeCapturedValue(it, proposedValue)
                    } ?: proposedValue
                }
                root.put("value", value)
                root.put(
                    "target",
                    JSONObject()
                        .put("class_name", "android.widget.EditText")
                        .put("focused", true),
                )
                val submit = action.contains("(submit)", ignoreCase = true)
                root.put("submit", submit)
                root.put(
                    "postcondition",
                    frozenTextPostcondition(stepIndex, action, value, submit),
                )
            }
            action.startsWith("press back", ignoreCase = true) -> {
                val value = lastInputText ?: return call
                val postcondition = if (isKeyboardDismissStep(stepIndex)) {
                    root.put("dismiss_input_method_only", true)
                    JSONObject()
                        .put(
                            "target",
                            JSONObject()
                                .put("class_name", "android.widget.EditText")
                                .put("expected_state", JSONObject().put("text", value)),
                        )
                        .put("exists", true)
                } else if (spec.steps[stepIndex].commit) {
                    root.remove("dismiss_input_method_only")
                    JSONObject()
                        .put("target", JSONObject().put("text", value))
                        .put("exists", true)
                } else {
                    return call
                }
                root.put("postcondition", postcondition)
            }
            else -> return call
        }
        return call.copy(argumentsJson = root.toString())
    }

    private fun isKeyboardDismissStep(stepIndex: Int): Boolean {
        val step = spec.steps[stepIndex]
        return step.narration.contains("Tastatur", ignoreCase = true) ||
            step.confirmationText?.contains("Tastatur", ignoreCase = true) == true
    }

    private fun frozenClickPostcondition(
        stepIndex: Int,
        currentTargetKey: String,
        currentTargetValue: String,
    ): JSONObject {
        val nextAction = spec.steps.drop(stepIndex + 1)
            .firstOrNull { !isCaptureAction(it.action) }
            ?.action
        val (target, exists) = when {
            nextAction?.startsWith("input text ", ignoreCase = true) == true ||
                nextAction?.startsWith("replace text ", ignoreCase = true) == true ->
                JSONObject()
                    .put("class_name", "android.widget.EditText")
                    .put("focused", true) to true
            nextAction?.startsWith("click ", ignoreCase = true) == true -> {
                val expected = QUOTED_VALUE.find(expandVariables(nextAction))
                    ?.groupValues?.get(1)
                if (expected == null) {
                    JSONObject().put(currentTargetKey, currentTargetValue) to false
                } else {
                    val key = if (RESOURCE_ID.matches(expected)) "resource_id" else "text"
                    JSONObject().put(key, expected) to true
                }
            }
            nextAction == null && lastInputText != null ->
                JSONObject().put("text", lastInputText) to true
            else ->
                JSONObject().put(currentTargetKey, currentTargetValue) to false
        }
        return JSONObject().put("target", target).put("exists", exists)
    }

    private fun frozenTextPostcondition(
        stepIndex: Int,
        action: String,
        value: String,
        submit: Boolean,
    ): JSONObject {
        if (submit) {
            val captured = inputVariable(action, value)
            val nextAction = spec.steps.drop(stepIndex + 1)
                .firstOrNull { !isCaptureAction(it.action) }
                ?.action
                ?.let(::expandVariables)
                ?.let { expanded ->
                    captured?.let { (name, capturedValue) ->
                        expanded.replace("{$name}", capturedValue)
                    } ?: expanded
                }
            val nextTarget = nextAction
                ?.takeIf { it.startsWith("click ", ignoreCase = true) }
                ?.let(QUOTED_VALUE::find)
                ?.groupValues
                ?.get(1)
            if (nextTarget != null) {
                val key = if (RESOURCE_ID.matches(nextTarget)) "resource_id" else "text"
                return JSONObject()
                    .put("target", JSONObject().put(key, nextTarget))
                    .put("exists", true)
            }
        }
        return JSONObject()
            .put(
                "target",
                JSONObject()
                    .put("class_name", "android.widget.EditText")
                    .put("expected_state", JSONObject().put("text", value)),
            )
            .put("exists", true)
    }

    private fun readyPackageFor(step: com.caddie.study.runtime.model.StudyStep): String? =
        (step.readyPackage ?: step.action
            .takeIf { it.startsWith("open ", ignoreCase = true) }
            ?.substringAfter(' ')
            ?.trim())
            ?.substringBefore('/')

    private fun applyConfirmationError(
        text: String,
        variant: ErrorVariant?,
    ): String = variant?.let {
        StudyTextHelpers.injectError(
            text,
            it,
            ::expandVariables,
            participantCopy = true,
        ).first
    } ?: text

    private fun applySummaryErrors(text: String): String =
        injectedVariants.filterNot { it.id in correctedVariantIds }.fold(text) { current, variant ->
            val correct = variant.summaryCorrectValue ?: expandVariables(variant.correctValue)
            val wrong = variant.summaryWrongValue ?: expandVariables(variant.wrongValue)
            StudyTextHelpers.replaceExactErrorValue(current, correct, wrong).first
        }

    private data class PendingMatch(
        val callId: String,
        val index: Int,
        val effectiveAction: String,
        val errorVariant: ErrorVariant?,
    )

    private companion object {
        val QUOTED_VALUE = Regex("'([^']+)'")
        val TEMPLATE_VALUE = Regex("\\{[^}]+\\}")
        val RESOURCE_ID = Regex("[A-Za-z0-9_.]+:id/[A-Za-z0-9_]+")
    }
}

/** Stops a study run before dispatch when the model deviates from its frozen action plan. */
class StudyPlanViolationException(message: String) : IllegalArgumentException(message)

/** Adds the classifier's current frozen step to every model request. */
private class StudyProgressRequestFactory(
    private val basePrompt: String,
    private val currentDirective: () -> String?,
    private val allowedToolNames: () -> Set<String>?,
) : RequestFactory {
    override fun create(snapshot: RunSnapshot, tools: List<ToolDefinition>): ModelRequest {
        val directive = currentDirective()
        val prompt = if (directive == null) basePrompt else "$basePrompt\n$directive"
        return ModelRequest(
            runId = snapshot.runId,
            messages = listOf(AgentMessage(AgentMessage.Role.SYSTEM, prompt)) + snapshot.messages,
            tools = allowedToolNames()?.let { allowed ->
                tools.filter { it.name in allowed }
            } ?: tools,
        )
    }
}

/** Runs a claimed study utterance through the process-owned native host. */
class NativeClaimedStudyRunner(
    private val host: NativeRuntimeHost,
    private val profiles: StudyAgentProfileFactory,
) : ClaimedStudyRunner {
    override suspend fun run(claim: ArmedTrialCoordinator.ClaimedTrial): NativeTaskResult =
        host.run(claim.participantUtterance, profiles.create(claim))
}

/** Describes how one participant utterance was routed. */
sealed interface StudyRouteOutcome {
    data object PassThrough : StudyRouteOutcome
    data object Retry : StudyRouteOutcome
    data object RunningInput : StudyRouteOutcome
    data class Started(
        val claim: ArmedTrialCoordinator.ClaimedTrial,
        val result: NativeTaskResult,
    ) : StudyRouteOutcome
}

/** Routes armed study utterances without adding study logic to the general runner. */
class StudyAgentRouter(
    private val coordinator: ArmedTrialCoordinator,
    private val runner: ClaimedStudyRunner,
) {
    suspend fun route(utterance: String): StudyRouteOutcome {
        val routed = coordinator.routeAndClaim(utterance)
        return when (routed.decision) {
            ArmedTrialCoordinator.CoordinatorDecision.PASS_THROUGH ->
                StudyRouteOutcome.PassThrough
            ArmedTrialCoordinator.CoordinatorDecision.RETRY ->
                StudyRouteOutcome.Retry
            ArmedTrialCoordinator.CoordinatorDecision.RUNNING_INPUT ->
                StudyRouteOutcome.RunningInput
            ArmedTrialCoordinator.CoordinatorDecision.CLAIMED -> {
                val claim = requireNotNull(routed.claim)
                val result = try {
                    runner.run(claim)
                } catch (cancelled: CancellationException) {
                    coordinator.finishFailureIfActive(claim, "native study runtime cancelled")
                    throw cancelled
                }
                when (result) {
                    is NativeTaskResult.Completed -> coordinator.finishSuccessIfActive(claim)
                    is NativeTaskResult.Aborted ->
                        coordinator.abortIfActive("participant stopped native agent")
                    NativeTaskResult.AccessibilityUnavailable ->
                        coordinator.releaseClaimForRetryIfActive(claim, "accessibility unavailable")
                    NativeTaskResult.RuntimeClosed ->
                        coordinator.releaseClaimForRetryIfActive(claim, "native runtime closed")
                    NativeTaskResult.Busy ->
                        coordinator.releaseClaimForRetryIfActive(claim, "native runtime busy")
                    is NativeTaskResult.Paused ->
                        if (result.safeToRetry) {
                            coordinator.releaseClaimForRetryIfActive(claim, "failed safely before dispatch")
                        } else {
                            coordinator.finishFailureIfActive(
                                claim,
                                "native agent paused after action dispatch",
                            )
                        }
                }
                StudyRouteOutcome.Started(claim, result)
            }
        }
    }
}

private fun RuntimeStudyCondition.toAgentCondition(): StudyCondition =
    when (this) {
        RuntimeStudyCondition.STEPWISE -> StudyCondition.C1_STEPWISE
        RuntimeStudyCondition.FINAL_CHECKPOINT -> StudyCondition.C2_FINAL_CHECKPOINT
        RuntimeStudyCondition.VOLUNTARY_INTERVENTION ->
            StudyCondition.C3_VOLUNTARY_INTERVENTION
    }
