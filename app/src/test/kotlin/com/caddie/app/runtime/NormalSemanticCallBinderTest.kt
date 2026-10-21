package com.caddie.app.runtime

import com.caddie.agent.core.ModelDelta
import com.caddie.agent.core.ToolCallId
import com.caddie.agent.core.ToolCallValidationException
import com.caddie.executor.accessibility.ActionOutcome
import com.caddie.executor.accessibility.ExecutionGateway
import com.caddie.executor.accessibility.RequestedAction
import com.caddie.executor.accessibility.SemanticTarget
import com.caddie.executor.accessibility.SnapshotCompleteness
import com.caddie.executor.accessibility.UiNode
import com.caddie.executor.accessibility.UiObservation
import com.caddie.executor.accessibility.UiBounds
import com.caddie.executor.accessibility.UiAction
import com.caddie.executor.accessibility.UiWindow
import com.caddie.executor.accessibility.UiWindowType
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class NormalSemanticCallBinderTest {
    @Test
    fun `resource-only target is bound to current visible label`() = runTest {
        val binder = NormalSemanticCallBinder(gatewayWith("confirm_button", "Überweisung ausführen"))

        val bound = binder.bind(call("confirm_button"))

        assertEquals(
            "Überweisung ausführen",
            JSONObject(bound.argumentsJson).getJSONObject("target").getString("text"),
        )
    }

    @Test
    fun `resource-only target ignores a hidden duplicate`() = runTest {
        val visible = node("search_box", "Search", visible = true)
        val hidden = node("search_box", "Hidden search", visible = false)
        val binder = NormalSemanticCallBinder(gatewayWith(visible, hidden))

        val bound = binder.bind(call("search_box"))

        assertEquals(
            "Search",
            JSONObject(bound.argumentsJson).getJSONObject("target").getString("text"),
        )
    }

    @Test
    fun `resource-only child keeps its label when click resolves to its parent`() = runTest {
        val parent = UiNode(
            observationNodeId = "button",
            windowId = 1,
            enabled = true,
            visibleToUser = true,
            clickable = true,
            actions = setOf(UiAction.CLICK),
            bounds = UiBounds(0, 0, 100, 100),
        )
        val label = UiNode(
            observationNodeId = "label",
            windowId = 1,
            parentObservationNodeId = "button",
            resourceId = "button_label",
            text = "Weiter",
            enabled = true,
            visibleToUser = true,
            bounds = UiBounds(10, 10, 90, 90),
        )
        val binder = NormalSemanticCallBinder(gatewayWith(parent, label))

        val bound = binder.bind(call("button_label"))

        assertEquals(
            "Weiter",
            JSONObject(bound.argumentsJson).getJSONObject("target").getString("text"),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `resource-only target fails before oversight when it cannot be resolved`() = runTest {
        NormalSemanticCallBinder(gatewayWith("other_button", "Weiter")).bind(call("missing"))
    }

    @Test
    fun `empty editable fields keep exact ID targets without submitting`() = runTest {
        for (resourceId in listOf("recipient_input", "subject_input", "message_input")) {
            for (emptyLabel in listOf(null, "", "   ")) {
                val field = editableNode(resourceId).copy(text = emptyLabel, contentDescription = emptyLabel)
                val original = setTextCall(resourceId)

                val bound = NormalSemanticCallBinder(gatewayWith(field)).bind(original)

                assertEquals(original, bound)
                assertEquals(false, JSONObject(bound.argumentsJson).getBoolean("submit"))
            }
        }
    }

    @Test
    fun `empty editable field ignores a hidden duplicate`() = runTest {
        val field = editableNode("recipient_input")
        val hidden = field.copy(observationNodeId = "hidden", visibleToUser = false)
        val original = setTextCall("recipient_input")

        assertEquals(original, NormalSemanticCallBinder(gatewayWith(field, hidden)).bind(original))
    }

    @Test(expected = ToolCallValidationException::class)
    fun `empty editable field rejects ambiguous exact IDs`() = runTest {
        val field = editableNode("recipient_input")
        val duplicate = field.copy(observationNodeId = "duplicate")
        NormalSemanticCallBinder(gatewayWith(field, duplicate)).bind(setTextCall("recipient_input"))
    }

    @Test(expected = ToolCallValidationException::class)
    fun `empty field rejects duplicate ID even if only one match supports SET_TEXT`() = runTest {
        val field = editableNode("recipient_input")
        val duplicate = field.copy(observationNodeId = "duplicate", editable = false, actions = emptySet())
        NormalSemanticCallBinder(gatewayWith(field, duplicate)).bind(setTextCall("recipient_input"))
    }

    @Test(expected = ToolCallValidationException::class)
    fun `empty editable field rejects a missing resource ID selector`() = runTest {
        NormalSemanticCallBinder(gatewayWith(editableNode("recipient_input"))).bind(setTextCall(null))
    }

    @Test(expected = ToolCallValidationException::class)
    fun `empty editable field rejects an unmatched resource ID`() = runTest {
        NormalSemanticCallBinder(gatewayWith(editableNode("recipient_input"))).bind(setTextCall("missing"))
    }

    @Test(expected = ToolCallValidationException::class)
    fun `empty SET_TEXT node must be editable`() = runTest {
        val field = editableNode("recipient_input").copy(editable = false)
        NormalSemanticCallBinder(gatewayWith(field)).bind(setTextCall("recipient_input"))
    }

    @Test(expected = ToolCallValidationException::class)
    fun `empty editable field must support SET_TEXT`() = runTest {
        val field = editableNode("recipient_input").copy(actions = emptySet())
        NormalSemanticCallBinder(gatewayWith(field)).bind(setTextCall("recipient_input"))
    }

    @Test(expected = ToolCallValidationException::class)
    fun `empty editable field still rejects submission`() = runTest {
        NormalSemanticCallBinder(gatewayWith(editableNode("recipient_input")))
            .bind(setTextCall("recipient_input", submit = true))
    }

    @Test(expected = ToolCallValidationException::class)
    fun `empty resource-only click still requires an accessible label`() = runTest {
        NormalSemanticCallBinder(gatewayWith(node("send_button", ""))).bind(call("send_button"))
    }

    private fun setTextCall(resourceId: String?, submit: Boolean = false): ModelDelta.ToolCall {
        val target = JSONObject()
            .put("package_name", "com.android.chrome")
            .put("class_name", "android.widget.EditText")
        if (resourceId != null) target.put("resource_id", resourceId)
        return ModelDelta.ToolCall(
            ToolCallId("call-edit"),
            "android.set_text",
            JSONObject()
                .put("target", target)
                .put("value", "draft value")
                .put("submit", submit)
                .toString(),
        )
    }

    private fun editableNode(resourceId: String) = UiNode(
        observationNodeId = resourceId,
        windowId = 1,
        packageName = "com.android.chrome",
        resourceId = resourceId,
        className = "android.widget.EditText",
        text = "",
        contentDescription = "",
        enabled = true,
        visibleToUser = true,
        editable = true,
        actions = setOf(UiAction.SET_TEXT),
    )

    private fun call(resourceId: String) = ModelDelta.ToolCall(
        ToolCallId("call-1"),
        "android.click",
        """{"target":{"resource_id":"$resourceId"}}""",
    )

    private fun gatewayWith(resourceId: String, text: String) = gatewayWith(node(resourceId, text))

    private fun gatewayWith(vararg nodes: UiNode) = object : ExecutionGateway {
        override suspend fun observe() = UiObservation(
            id = "snapshot",
            capturedAtElapsedRealtimeMillis = 1,
            completeness = SnapshotCompleteness.ALL_INTERACTIVE_WINDOWS,
            inputWindows = listOf(
                UiWindow(
                    id = 1,
                    type = UiWindowType.APPLICATION,
                    layer = 1,
                    active = true,
                    focused = true,
                    interactionBarrier = false,
                    nodes = nodes.toList(),
                ),
            ),
        )

        override suspend fun performSemantic(
            target: SemanticTarget,
            action: RequestedAction,
        ) = ActionOutcome.Accepted
    }

    private fun node(resourceId: String, text: String, visible: Boolean = true) = UiNode(
        observationNodeId = "$resourceId-$text",
        windowId = 1,
        resourceId = resourceId,
        text = text,
        enabled = true,
        visibleToUser = visible,
        clickable = true,
        actions = setOf(UiAction.CLICK),
    )
}
