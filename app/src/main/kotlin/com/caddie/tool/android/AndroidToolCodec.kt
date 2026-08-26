package com.caddie.tool.android

import com.caddie.executor.accessibility.ExecutionResult
import com.caddie.executor.accessibility.ExecutionStatus
import com.caddie.executor.accessibility.ExpectedNodeState
import com.caddie.executor.accessibility.NodeResolver
import com.caddie.executor.accessibility.RequestedAction
import com.caddie.executor.accessibility.SemanticTarget
import com.caddie.executor.accessibility.SnapshotCompleteness
import com.caddie.executor.accessibility.UiBounds
import com.caddie.executor.accessibility.UiNode
import com.caddie.executor.accessibility.UiObservation
import com.caddie.executor.accessibility.UiRange
import com.caddie.executor.accessibility.UiWindow
import com.caddie.executor.accessibility.UiWindowType
import com.caddie.executor.accessibility.VerificationDecision
import java.security.MessageDigest
import java.net.URI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Parses semantic tool arguments and serializes stable Android tool results. */
internal object AndroidToolCodec {
    private val json = Json { ignoreUnknownKeys = false }
    private const val MAX_PROMPT_NODES = 200
    private const val MAX_PROMPT_TEXT_CHARS = 1_000

    data class Postcondition(
        val target: SemanticTarget,
        val exists: Boolean,
    )

    data class Mutation(
        val target: SemanticTarget,
        val action: RequestedAction,
        val postcondition: Postcondition,
    )

    fun parseMutation(name: String, argumentsJson: String): Mutation {
        val root = parseObject(argumentsJson, "tool arguments")
        if (name == "android.back") {
            requireKeys(
                root,
                setOf("postcondition", "dismiss_input_method_only", "why"),
                "tool arguments",
            )
            val postcondition = parsePostcondition(root.requiredObject("postcondition"))
            val action = if (root.optionalBoolean("dismiss_input_method_only") == true) {
                RequestedAction.DismissInputMethod
            } else {
                RequestedAction.Back
            }
            return Mutation(postcondition.target, action, postcondition)
        }
        if (name == "android.open_app" || name == "android.open_url") {
            val valueKey = if (name == "android.open_app") "package_name" else "url"
            requireKeys(root, setOf(valueKey, "postcondition", "why"), "tool arguments")
            val value = root.requiredString(valueKey)
            val action = if (name == "android.open_app") {
                require(APP_OR_COMPONENT.matches(value)) { "package_name is invalid" }
                RequestedAction.OpenApp(value)
            } else {
                val uri = runCatching { URI(value) }.getOrNull()
                require(uri?.scheme == "https" && !uri.host.isNullOrBlank()) {
                    "url must be one absolute HTTPS URL"
                }
                RequestedAction.OpenUrl(value)
            }
            val postcondition = parsePostcondition(root.requiredObject("postcondition"))
            return Mutation(postcondition.target, action, postcondition)
        }
        val actionKeys = when (name) {
            "android.click", "android.long_click" -> emptySet()
            "android.set_text" -> setOf("value", "submit")
            "android.set_checked" -> setOf("checked")
            "android.scroll" -> setOf("forward")
            else -> throw IllegalArgumentException("Unknown Android tool: $name")
        }
        requireKeys(root, setOf("target", "postcondition", "why") + actionKeys, "tool arguments")
        val target = parseTarget(root.requiredObject("target"))
        val postcondition = parsePostcondition(root.requiredObject("postcondition"))
        val action = when (name) {
            "android.click" -> RequestedAction.Click
            "android.long_click" -> RequestedAction.LongClick
            "android.set_text" -> RequestedAction.SetText(
                text = root.requiredString("value"),
                submit = root.optionalBoolean("submit") ?: false,
            )
            "android.set_checked" ->
                RequestedAction.SetChecked(root.requiredBoolean("checked"))
            "android.scroll" ->
                RequestedAction.Scroll(root.requiredBoolean("forward"))
            else -> error("validated above")
        }
        return Mutation(target, action, postcondition)
    }

    fun requireObserveArguments(argumentsJson: String) {
        val root = parseObject(argumentsJson, "observe arguments")
        require(root.isEmpty()) { "android.observe does not accept arguments" }
    }

    fun verify(
        postcondition: Postcondition,
        observation: UiObservation,
    ): VerificationDecision {
        if (observation.completeness != SnapshotCompleteness.ALL_INTERACTIVE_WINDOWS) {
            return VerificationDecision.Contradicted
        }
        val matchingNodes = observation.nodes.filter { node ->
            node.enabled && NodeResolver.matches(postcondition.target, node)
        }
        val satisfied = if (postcondition.exists) {
            matchingNodes.any { node ->
                matchesExpectedState(postcondition.target.expectedState, node)
            }
        } else {
            matchingNodes.isEmpty()
        }
        return if (satisfied) {
            VerificationDecision.Satisfied
        } else {
            VerificationDecision.Contradicted
        }
    }

    fun verifyActivePackage(
        packageNames: Set<String>,
        observation: UiObservation,
    ): VerificationDecision {
        val visibleInActiveApp = observation.windows
            .asSequence()
            .filter { window ->
                window.type == UiWindowType.APPLICATION &&
                    (window.active || window.focused)
            }
            .flatMap { it.nodes.asSequence() }
            .any { node ->
                node.enabled && node.visibleToUser && node.packageName in packageNames
            }
        return if (visibleInActiveApp) {
            VerificationDecision.Satisfied
        } else {
            VerificationDecision.Contradicted
        }
    }

    fun fingerprint(target: SemanticTarget): String =
        "sha256:" + sha256(target.stableValue())

    fun fingerprint(postcondition: Postcondition): String =
        "sha256:" + sha256("${postcondition.exists}|${postcondition.target.stableValue()}")

    fun fingerprintPackages(packageNames: Set<String>): String =
        "sha256:" + sha256("exists|${packageNames.sorted().joinToString("|")}")

    fun observationJson(observation: UiObservation): String =
        buildJsonObject {
            put("schemaVersion", 1)
            put("snapshotId", observation.id)
            put("capturedAtElapsedRealtimeMillis", observation.capturedAtElapsedRealtimeMillis)
            put("completeness", observation.completeness.name)
            var remainingNodes = MAX_PROMPT_NODES
            put("windows", buildJsonArray {
                observation.windows.forEach { window ->
                    val promptNodes = window.promptNodes(remainingNodes)
                    remainingNodes -= promptNodes.nodes.size
                    add(window.toJson(promptNodes))
                }
            })
        }.toString()

    fun executionJson(result: ExecutionResult): String =
        buildJsonObject {
            put("attemptId", result.attemptId)
            put("status", result.status.name)
        }.toString()

    fun isError(status: ExecutionStatus): Boolean =
        status !in setOf(ExecutionStatus.VERIFIED, ExecutionStatus.ALREADY_SATISFIED)

    private fun parsePostcondition(value: JsonObject): Postcondition {
        requireKeys(value, setOf("target", "exists"), "postcondition")
        return Postcondition(
            target = parseTarget(value.requiredObject("target")),
            exists = value.optionalBoolean("exists") ?: true,
        )
    }

    private fun parseTarget(value: JsonObject): SemanticTarget {
        requireKeys(
            value,
            setOf(
                "package_name",
                "resource_id",
                "text",
                "content_description",
                "class_name",
                "focused",
                "expected_state",
            ),
            "semantic target",
        )
        val expected = value["expected_state"]?.let { element ->
            val state = element.jsonObject
            requireKeys(state, setOf("checked", "selected", "text"), "expected state")
            ExpectedNodeState(
                checked = state.optionalBoolean("checked"),
                selected = state.optionalBoolean("selected"),
                text = state.optionalString("text"),
            )
        }
        return SemanticTarget(
            packageName = value.optionalString("package_name"),
            resourceId = value.optionalString("resource_id"),
            text = value.optionalString("text"),
            contentDescription = value.optionalString("content_description"),
            className = value.optionalString("class_name"),
            focused = value.optionalBoolean("focused"),
            expectedState = expected,
        ).also {
            require(it.hasSemanticIdentity()) { "semantic target requires identity" }
        }
    }

    private fun matchesExpectedState(expected: ExpectedNodeState?, node: UiNode): Boolean =
        expected == null ||
            (expected.checked == null || expected.checked == node.checked) &&
            (expected.selected == null || expected.selected == node.selected) &&
            (expected.text == null || expected.text == node.text)

    private fun SemanticTarget.stableValue(): String =
        listOf(
            packageName,
            resourceId,
            text,
            contentDescription,
            className,
            focused,
            expectedState?.checked,
            expectedState?.selected,
            expectedState?.text,
        ).joinToString("|") { it?.toString().orEmpty() }

    private fun UiWindow.toJson(promptNodes: PromptNodes): JsonObject = buildJsonObject {
        put("id", id)
        put("type", type.name)
        put("layer", layer)
        put("active", active)
        put("focused", focused)
        put("interactionBarrier", interactionBarrier)
        put("bounds", bounds?.toJson() ?: JsonNull)
        put("totalNodeCount", nodes.size)
        put("returnedNodeCount", promptNodes.nodes.size)
        put("truncated", promptNodes.truncated)
        put("nodes", buildJsonArray { promptNodes.nodes.forEach { add(it.toJson()) } })
    }

    private fun UiNode.toJson(): JsonObject = buildJsonObject {
        put("observationNodeId", observationNodeId)
        put("windowId", windowId)
        putNullable("parentObservationNodeId", parentObservationNodeId)
        putNullable("packageName", packageName)
        putNullable("resourceId", resourceId)
        putNullable("text", if (password) null else text.promptText())
        putNullable("contentDescription", if (password) null else contentDescription.promptText())
        putNullable("className", className)
        put("enabled", enabled)
        put("visibleToUser", visibleToUser)
        put("clickable", clickable)
        put("longClickable", longClickable)
        put("checkable", checkable)
        put("checked", checked)
        put("selected", selected)
        put("editable", editable)
        put("focused", focused)
        put("scrollable", scrollable)
        put("password", password)
        put("bounds", bounds?.toJson() ?: JsonNull)
        put("range", range?.toJson() ?: JsonNull)
        put("actions", buildJsonArray {
            actions.map { it.name }.sorted().forEach { add(JsonPrimitive(it)) }
        })
    }

    private fun UiBounds.toJson(): JsonObject = buildJsonObject {
        put("left", left)
        put("top", top)
        put("right", right)
        put("bottom", bottom)
    }

    private fun UiRange.toJson(): JsonObject = buildJsonObject {
        put("type", type)
        put("min", min)
        put("max", max)
        put("current", current)
    }

    private fun UiWindow.promptNodes(limit: Int): PromptNodes {
        val relevant = nodes.filter { it.isPromptRelevant() }
        val prioritized = (
            relevant.filter { it.isActionable() } + relevant
        ).distinctBy(UiNode::observationNodeId)
        val selectedIds = prioritized.take(limit.coerceAtLeast(0))
            .mapTo(mutableSetOf(), UiNode::observationNodeId)
        return PromptNodes(
            nodes = nodes.filter { it.observationNodeId in selectedIds },
            truncated = relevant.size > selectedIds.size,
        )
    }

    private fun UiNode.isPromptRelevant(): Boolean =
        visibleToUser && (
            !text.isNullOrBlank() ||
                !contentDescription.isNullOrBlank() ||
                !resourceId.isNullOrBlank() ||
                isActionable() ||
                checked || selected || focused || range != null || password
        )

    private fun UiNode.isActionable(): Boolean =
        actions.isNotEmpty() || clickable || longClickable || checkable || editable || scrollable

    private fun String?.promptText(): String? =
        this?.let { value ->
            if (value.length <= MAX_PROMPT_TEXT_CHARS) value
            else value.take(MAX_PROMPT_TEXT_CHARS - 1) + "…"
        }

    private data class PromptNodes(
        val nodes: List<UiNode>,
        val truncated: Boolean,
    )

    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(
        key: String,
        value: String?,
    ) {
        put(key, value?.let(::JsonPrimitive) ?: JsonNull)
    }

    private fun parseObject(value: String, label: String): JsonObject =
        try {
            json.parseToJsonElement(value).jsonObject
        } catch (error: Exception) {
            throw IllegalArgumentException("$label must be one JSON object", error)
        }

    private fun requireKeys(value: JsonObject, allowed: Set<String>, label: String) {
        val unknown = value.keys - allowed
        require(unknown.isEmpty()) { "$label contains unknown fields: ${unknown.sorted()}" }
    }

    private fun JsonObject.requiredObject(key: String): JsonObject =
        this[key]?.jsonObject ?: throw IllegalArgumentException("$key must be an object")

    private fun JsonObject.optionalString(key: String): String? =
        this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull

    private fun JsonObject.requiredString(key: String): String =
        optionalString(key)?.takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("$key must be a non-empty string")

    private fun JsonObject.optionalBoolean(key: String): Boolean? =
        this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.booleanOrNull
            ?: if (containsKey(key) && this[key] !is JsonNull) {
                throw IllegalArgumentException("$key must be a boolean")
            } else {
                null
            }

    private fun JsonObject.requiredBoolean(key: String): Boolean =
        optionalBoolean(key) ?: throw IllegalArgumentException("$key must be a boolean")

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private val APP_OR_COMPONENT = Regex(
        "[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+(?:/[A-Za-z0-9_.$]+)?",
    )
}
