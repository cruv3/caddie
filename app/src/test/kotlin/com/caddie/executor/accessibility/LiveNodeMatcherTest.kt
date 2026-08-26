package com.caddie.executor.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveNodeMatcherTest {
    @Test
    fun `checked focused editable state remains in live descriptor`() {
        val live =
            node(
                actions = setOf(UiAction.CLICK, UiAction.SET_TEXT),
                editable = true,
                focused = true,
                checked = true,
            )

        assertTrue(live.visibleToUser)
        assertTrue(live.editable)
        assertTrue(live.focused)
        assertTrue(live.checked)
    }

    @Test
    fun `all supplied live semantic fields must match exactly`() {
        val target = SemanticTarget(
            packageName = "com.example",
            resourceId = "com.example:id/send",
            text = "Send",
            contentDescription = "Send message",
            className = "android.widget.Button",
        )
        val exact = node(id = "1")
        val wrongDescription = exact.copy(
            observationNodeId = "2",
            contentDescription = "Attach file",
        )

        assertEquals(
            Resolution.Found(exact),
            LiveNodeMatcher.resolve(
                target,
                listOf(exact, wrongDescription),
            ),
        )
    }

    @Test
    fun `two live matches are ambiguous`() {
        val target = SemanticTarget(resourceId = "com.example:id/send")

        assertEquals(
            Resolution.Ambiguous(2),
            LiveNodeMatcher.resolve(
                target,
                listOf(node(id = "1"), node(id = "2")),
            ),
        )
    }

    @Test
    fun `disabled live node is not actionable`() {
        val target = SemanticTarget(resourceId = "com.example:id/send")

        assertEquals(
            Resolution.Missing,
            LiveNodeMatcher.resolve(
                target,
                listOf(node(id = "1", enabled = false)),
            ),
        )
    }

    private fun node(
        id: String = "1",
        enabled: Boolean = true,
        actions: Set<UiAction> = setOf(UiAction.CLICK),
        editable: Boolean = false,
        focused: Boolean = false,
        checked: Boolean = false,
    ) = UiNode(
        observationNodeId = id,
        windowId = 1,
        packageName = "com.example",
        resourceId = "com.example:id/send",
        text = "Send",
        contentDescription = "Send message",
        className = "android.widget.Button",
        enabled = enabled,
        visibleToUser = true,
        editable = editable,
        focused = focused,
        checked = checked,
        bounds = UiBounds(0, 0, 100, 100),
        actions = actions,
    )
}
