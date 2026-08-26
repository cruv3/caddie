package com.caddie.study.runtime.android

import com.caddie.executor.accessibility.ActionOutcome
import com.caddie.executor.accessibility.ExecutionGateway
import com.caddie.executor.accessibility.RequestedAction
import com.caddie.executor.accessibility.SemanticTarget
import com.caddie.executor.accessibility.UiNode
import com.caddie.executor.accessibility.UiWindowType
import com.caddie.study.runtime.executor.StudyBackend
import com.caddie.study.runtime.executor.UiStateQuery
import com.caddie.study.runtime.executor.VerificationBackend
import kotlinx.coroutines.runBlocking

/** Adapts native Accessibility observations and actions to the deterministic study executor. */
class AndroidStudyBackend(
    private val gateway: ExecutionGateway,
    private val beforeAction: suspend () -> Boolean = { true },
) : StudyBackend {
    private var indexedNodes: List<UiNode> = emptyList()

    override fun listElements(): List<Map<String, Any?>> = runBlocking {
        val observation = gateway.observe()
        val indexedWindowsAndNodes = observation.windows
            .filterNot { it.type == UiWindowType.ACCESSIBILITY_OVERLAY }
            .flatMap { window -> window.nodes.map { node -> window to node } }
        val applicationWindows = observation.windows.filter { it.type == UiWindowType.APPLICATION }
        val markedForeground = applicationWindows.filter { it.active || it.focused }
        val foregroundWindowIds = markedForeground.ifEmpty {
            applicationWindows.firstOrNull { window ->
                window.nodes.any { node ->
                    node.enabled && node.visibleToUser && !node.packageName.isNullOrBlank()
                }
            }?.let(::listOf).orEmpty()
        }.map { it.id }.toSet()
        indexedNodes = indexedWindowsAndNodes.map { it.second }
        indexedWindowsAndNodes.mapIndexed { index, (window, node) ->
            node.toElement(
                index = index,
                windowForeground = window.id in foregroundWindowIds,
            )
        }
    }

    override fun openApp(packageName: String): Map<String, Any?> =
        perform(
            SemanticTarget(packageName = packageName.substringBefore('/')),
            RequestedAction.OpenApp(packageName),
        )

    override fun openUrl(url: String): Map<String, Any?> =
        perform(SemanticTarget(className = "android.view.View"), RequestedAction.OpenUrl(url))

    override fun tapElement(index: Int): Map<String, Any?> {
        val node = indexedNodes.getOrNull(index) ?: return failure("element index is stale")
        return perform(node.semanticTarget(), RequestedAction.Click)
    }

    override fun scroll(direction: String, amount: Double): Map<String, Any?> {
        val node = currentNodes().firstOrNull { it.visibleToUser && it.scrollable }
            ?: return failure("no visible scrollable element")
        val forward = direction.equals("down", true) || direction.equals("right", true)
        return perform(node.semanticTarget(), RequestedAction.Scroll(forward))
    }

    override fun pressButton(button: String): Map<String, Any?> =
        if (button.equals("BACK", true)) {
            perform(SemanticTarget(className = "android.view.View"), RequestedAction.Back)
        } else {
            failure("unsupported native button: $button")
        }

    override fun dismissInputMethod(): Map<String, Any?> =
        perform(
            SemanticTarget(className = "android.view.View"),
            RequestedAction.DismissInputMethod,
        )

    override fun typeText(text: String, submit: Boolean): Map<String, Any?> =
        setFocusedText(text, submit)

    override fun replaceText(text: String, submit: Boolean): Map<String, Any?> =
        setFocusedText(text, submit)

    private fun setFocusedText(text: String, submit: Boolean): Map<String, Any?> {
        val node = currentNodes().firstOrNull { it.visibleToUser && it.editable && it.focused }
            ?: return failure("no focused editable element")
        return perform(node.semanticTarget(), RequestedAction.SetText(text, submit))
    }

    private fun currentNodes(): List<UiNode> {
        listElements()
        return indexedNodes
    }

    private fun perform(target: SemanticTarget, action: RequestedAction): Map<String, Any?> =
        runBlocking {
            if (!beforeAction()) return@runBlocking failure("run stopped or paused")
            when (val outcome = gateway.performSemantic(target, action)) {
                ActionOutcome.Accepted,
                ActionOutcome.AlreadySatisfied,
                -> mapOf("success" to true)
                else -> failure(outcome::class.simpleName ?: "action rejected")
            }
        }

    private fun UiNode.semanticTarget(): SemanticTarget =
        SemanticTarget(
            packageName = packageName,
            resourceId = resourceId,
            text = text,
            contentDescription = contentDescription,
            className = className,
            focused = focused.takeIf { editable },
        )

    private fun UiNode.toElement(
        index: Int,
        windowForeground: Boolean,
    ): Map<String, Any?> = linkedMapOf(
        "index" to index,
        "package" to packageName,
        "package_name" to packageName,
        "resource_id" to resourceId,
        "text" to text,
        "content_description" to contentDescription,
        "class" to className,
        "class_name" to className,
        "enabled" to enabled,
        "visible" to visibleToUser,
        "window_foreground" to windowForeground,
        "clickable" to clickable,
        "editable" to editable,
        "focused" to focused,
        "scrollable" to scrollable,
        "bounds" to bounds?.let {
            mapOf("left" to it.left, "top" to it.top, "right" to it.right, "bottom" to it.bottom)
        },
    )

    private fun failure(error: String): Map<String, Any?> =
        mapOf("success" to false, "error" to error)
}

/** Verifies TrialSpec postconditions against a fresh Accessibility snapshot. */
class AccessibilityStudyVerification(
    private val backend: StudyBackend,
    private val maxAttempts: Int = 25,
    private val retryDelayMs: Long = 150,
    private val pause: (Long) -> Unit = { Thread.sleep(it) },
) : VerificationBackend {
    init {
        require(maxAttempts > 0) { "maxAttempts must be positive" }
        require(retryDelayMs >= 0) { "retryDelayMs must not be negative" }
    }

    override fun checkTextPresent(text: String): Boolean =
        awaitElements { elements ->
            elements.any { text.lowercase() in it["text"].toString().lowercase() }
        }

    override fun checkTextAbsent(text: String): Boolean =
        awaitElements { elements ->
            elements.none { text.lowercase() in it["text"].toString().lowercase() }
        }

    override fun checkUiState(query: UiStateQuery): Boolean =
        awaitElements { elements ->
            elements.any { element -> element.matches(query) }
        }

    override fun checkAccessibilityElement(label: String): Boolean {
        val needle = label.lowercase()
        return awaitElements { elements ->
            elements.any { element ->
                listOf("text", "content_description").any { field ->
                    needle in element[field].toString().lowercase()
                }
            }
        }
    }

    override fun checkFieldCount(containerLabel: String, expected: Int): Boolean {
        val needle = containerLabel.lowercase()
        return awaitElements { elements ->
            elements.count { needle in it["text"].toString().lowercase() } == expected
        }
    }

    override fun captureScreenshot(): String? = null

    private fun Map<String, Any?>.matches(query: UiStateQuery): Boolean =
        this["package_name"] == query.packageName &&
            this["resource_id"] == query.resourceId &&
            this["enabled"] == true &&
            this["visible"] == true &&
            this["window_foreground"] == true &&
            query.text.matchesOptionalSubstring(this["text"] as? String) &&
            query.contentDescription.matchesOptionalSubstring(this["content_description"] as? String)

    private fun String?.matchesOptionalSubstring(actual: String?): Boolean =
        this == null || actual?.contains(this, ignoreCase = true) == true

    private fun elements(): List<Map<String, Any?>> = backend.listElements()

    private fun awaitElements(
        predicate: (List<Map<String, Any?>>) -> Boolean,
    ): Boolean {
        repeat(maxAttempts) { attempt ->
            if (predicate(elements())) return true
            if (attempt < maxAttempts - 1 && retryDelayMs > 0) pause(retryDelayMs)
        }
        return false
    }
}
