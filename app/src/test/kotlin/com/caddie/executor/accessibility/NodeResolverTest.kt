package com.caddie.executor.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NodeResolverTest {
    @Test
    fun `text selector tolerates Android display capitalization`() {
        val button = node(text = "SENDEN", actions = setOf(UiAction.CLICK))

        assertEquals(
            Resolution.Found(button),
            NodeResolver.resolve(SemanticTarget(text = "Senden"), listOf(button)),
        )
    }

    @Test
    fun `text selector also matches an Android accessible description`() {
        val button = node(
            text = null,
            contentDescription = "Zu Meine Bibliothek hinzufügen",
            actions = setOf(UiAction.CLICK),
        )

        assertEquals(
            Resolution.Found(button),
            NodeResolver.resolve(
                SemanticTarget(text = "Zu Meine Bibliothek hinzufügen"),
                listOf(button),
            ),
        )
    }

    @Test
    fun `focused selector disambiguates editable fields`() {
        val inactive = node(
            id = "inactive",
            className = "android.widget.EditText",
            focused = false,
        )
        val active = node(
            id = "active",
            className = "android.widget.EditText",
            focused = true,
        )

        assertEquals(
            Resolution.Found(active),
            NodeResolver.resolve(
                SemanticTarget(className = "android.widget.EditText", focused = true),
                listOf(inactive, active),
            ),
        )
    }

    @Test
    fun `snapshot flattens nodes without losing window identity`() {
        val lowerLayerNode = node(id = "lower", windowId = 10)
        val higherLayerNode = node(id = "higher", windowId = 20)
        val observation = UiObservation(
            id = "observation",
            capturedAtElapsedRealtimeMillis = 42L,
            completeness = SnapshotCompleteness.ALL_INTERACTIVE_WINDOWS,
            inputWindows = listOf(
                UiWindow(
                    id = 10,
                    type = UiWindowType.APPLICATION,
                    layer = 1,
                    active = true,
                    focused = true,
                    interactionBarrier = false,
                    nodes = listOf(lowerLayerNode),
                ),
                UiWindow(
                    id = 20,
                    type = UiWindowType.INPUT_METHOD,
                    layer = 3,
                    active = false,
                    focused = false,
                    interactionBarrier = false,
                    nodes = listOf(higherLayerNode),
                ),
            ),
        )

        assertEquals(listOf(20, 10), observation.windows.map(UiWindow::id))
        assertEquals(listOf("higher", "lower"), observation.nodes.map(UiNode::observationNodeId))
        assertEquals(listOf(20, 10), observation.nodes.map(UiNode::windowId))
    }

    @Test
    fun `snapshot owns input windows and their nodes`() {
        val sourceNodes = mutableListOf(node(id = "1:0", windowId = 1))
        val sourceWindows =
            mutableListOf(
                UiWindow(
                    id = 1,
                    type = UiWindowType.APPLICATION,
                    layer = 1,
                    active = true,
                    focused = true,
                    interactionBarrier = false,
                    nodes = sourceNodes,
                ),
            )
        val observation = UiObservation(
            id = "observation",
            capturedAtElapsedRealtimeMillis = 42L,
            completeness = SnapshotCompleteness.ALL_INTERACTIVE_WINDOWS,
            inputWindows = sourceWindows,
        )

        sourceNodes += node(id = "1:1", windowId = 1)
        sourceWindows.clear()

        assertEquals(listOf(1), observation.windows.map(UiWindow::id))
        assertEquals(
            listOf("1:0"),
            observation.windows.single().nodes.map(UiNode::observationNodeId),
        )
        assertEquals(
            listOf("1:0"),
            observation.nodes.map(UiNode::observationNodeId),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `window rejects nodes from another window`() {
        UiWindow(
            id = 1,
            type = UiWindowType.APPLICATION,
            layer = 1,
            active = true,
            focused = true,
            interactionBarrier = false,
            nodes = listOf(node(windowId = 2)),
        )
    }

    @Test
    fun `snapshot orders equal layers by ascending window id`() {
        val observation =
            snapshot(
                window(id = 20, layer = 2),
                window(id = 10, layer = 2),
            )

        assertEquals(listOf(10, 20), observation.windows.map(UiWindow::id))
    }

    @Test
    fun `snapshots compare canonical windows independent of construction order`() {
        val lower =
            window(
                id = 10,
                layer = 1,
                nodes = arrayOf(node(id = "10:0", windowId = 10)),
            )
        val higher =
            window(
                id = 20,
                layer = 3,
                nodes = arrayOf(node(id = "20:0", windowId = 20)),
            )
        val first = UiObservation(
            id = "observation",
            capturedAtElapsedRealtimeMillis = 42L,
            completeness = SnapshotCompleteness.ALL_INTERACTIVE_WINDOWS,
            inputWindows = listOf(lower, higher),
        )
        val reversed = UiObservation(
            id = "observation",
            capturedAtElapsedRealtimeMillis = 42L,
            completeness = SnapshotCompleteness.ALL_INTERACTIVE_WINDOWS,
            inputWindows = listOf(higher, lower),
        )

        assertEquals(first, reversed)
        assertEquals(first.hashCode(), reversed.hashCode())
    }

    @Test
    fun `snapshot exposes only canonical windows`() {
        assertFalse(
            UiObservation::class.java.methods.any { it.name == "getInputWindows" },
        )
    }

    @Test
    fun `snapshot owns node action sets`() {
        val sourceActions = mutableSetOf(UiAction.CLICK)
        val observation =
            snapshot(
                window(
                    nodes = arrayOf(
                        UiNode(
                            observationNodeId = "1:0",
                            windowId = 1,
                            enabled = true,
                            visibleToUser = true,
                            actions = sourceActions,
                        ),
                    ),
                ),
            )
        val initialHash = observation.hashCode()

        sourceActions += UiAction.LONG_CLICK

        assertEquals(setOf(UiAction.CLICK), observation.nodes.single().actions)
        assertEquals(initialHash, observation.hashCode())
    }

    @Test
    fun `minimal node is disabled invisible and actionless by default`() {
        val node = UiNode(observationNodeId = "node", windowId = 10)

        assertEquals(false, node.enabled)
        assertEquals(false, node.visibleToUser)
        assertEquals(emptySet<UiAction>(), node.actions)
    }

    @Test
    fun `resource id resolves one enabled node`() {
        val target = SemanticTarget(resourceId = "com.example:id/send")
        val nodes = listOf(
            node(
                id = "1",
                resourceId = "com.example:id/send",
                enabled = true,
            ),
            node(id = "2", text = "Cancel", enabled = true),
        )

        assertEquals(
            Resolution.Found(nodes[0]),
            NodeResolver.resolve(target, nodes),
        )
    }

    @Test
    fun `all supplied semantic fields must match`() {
        val target = SemanticTarget(
            packageName = "com.example",
            text = "Send",
            className = "android.widget.Button",
        )
        val exact = node(
            id = "1",
            packageName = "com.example",
            text = "Send",
            className = "android.widget.Button",
        )
        val wrongClass = exact.copy(
            observationNodeId = "2",
            className = "android.widget.TextView",
        )

        assertEquals(
            Resolution.Found(exact),
            NodeResolver.resolve(target, listOf(exact, wrongClass)),
        )
    }

    @Test
    fun `content description participates in conjunctive matching`() {
        val target = SemanticTarget(
            packageName = "com.example",
            contentDescription = "Send message",
        )
        val exact = node(
            id = "1",
            packageName = "com.example",
            contentDescription = "Send message",
        )
        val wrongDescription = exact.copy(
            observationNodeId = "2",
            contentDescription = "Attach file",
        )

        assertEquals(
            Resolution.Found(exact),
            NodeResolver.resolve(target, listOf(exact, wrongDescription)),
        )
    }

    @Test
    fun `null semantic field is a wildcard while supplied field is exact`() {
        val wildcardTarget =
            SemanticTarget(resourceId = "com.example:id/send")
        val exactTarget =
            wildcardTarget.copy(text = "Send")
        val matchingResource =
            node(
                id = "1",
                resourceId = "com.example:id/send",
                text = "Different label",
            )

        assertEquals(
            Resolution.Found(matchingResource),
            NodeResolver.resolve(
                wildcardTarget,
                listOf(matchingResource),
            ),
        )
        assertEquals(
            Resolution.Missing,
            NodeResolver.resolve(
                exactTarget,
                listOf(matchingResource),
            ),
        )
    }

    @Test
    fun `duplicate semantic matches are ambiguous`() {
        val target = SemanticTarget(text = "Send")
        val nodes = listOf(
            node(id = "1", text = "Send"),
            node(id = "2", text = "Send"),
        )

        assertEquals(
            Resolution.Ambiguous(2),
            NodeResolver.resolve(target, nodes),
        )
    }

    @Test
    fun `disabled matching node is not actionable`() {
        val target = SemanticTarget(text = "Send")

        assertEquals(
            Resolution.Missing,
            NodeResolver.resolve(
                target,
                listOf(node(id = "1", text = "Send", enabled = false)),
            ),
        )
    }

    @Test
    fun `bounds alone never identify a semantic target`() {
        val target = SemanticTarget()

        assertEquals(
            Resolution.InvalidTarget,
            NodeResolver.resolve(target, listOf(node(id = "1"))),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `bounds reject zero width`() {
        UiBounds(0, 0, 0, 100)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `bounds reject inverted vertical extent`() {
        UiBounds(0, 100, 100, 50)
    }

}
