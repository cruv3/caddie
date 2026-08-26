package com.caddie.app.diagnostics.snapshot

import com.caddie.executor.accessibility.SnapshotCompleteness
import com.caddie.executor.accessibility.UiAction
import com.caddie.executor.accessibility.UiBounds
import com.caddie.executor.accessibility.UiNode
import com.caddie.executor.accessibility.UiObservation
import com.caddie.executor.accessibility.UiRange
import com.caddie.executor.accessibility.UiWindow
import com.caddie.executor.accessibility.UiWindowType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeUiSnapshotSerializerTest {
    @Test
    fun `serializer exposes partial interactive windows completeness`() {
        val observation =
            UiObservation(
                id = "partial",
                capturedAtElapsedRealtimeMillis = 1L,
                completeness =
                    SnapshotCompleteness.PARTIAL_INTERACTIVE_WINDOWS,
                inputWindows = emptyList(),
            )

        assertEquals(
            "PARTIAL_INTERACTIVE_WINDOWS",
            JSONObject(
                NativeUiSnapshotSerializer.toJson(observation)
            ).getString("completeness"),
        )
    }

    @Test
    fun `serializer keeps deterministic lossless window and node semantics`() {
        val observation =
            UiObservation(
                id = "snapshot-1",
                capturedAtElapsedRealtimeMillis = 123L,
                completeness =
                    SnapshotCompleteness.ALL_INTERACTIVE_WINDOWS,
                inputWindows =
                    listOf(
                        UiWindow(
                            id = 7,
                            type = UiWindowType.APPLICATION,
                            layer = 4,
                            active = true,
                            focused = true,
                            interactionBarrier = true,
                            bounds = UiBounds(1, 2, 101, 202),
                            nodes =
                                listOf(
                                    UiNode(
                                        observationNodeId = "7:0",
                                        windowId = 7,
                                        parentObservationNodeId = "7:parent",
                                        packageName = "com.example",
                                        resourceId = "com.example:id/send",
                                        text = "Send text",
                                        contentDescription = "Send description",
                                        className = "android.widget.Button",
                                        enabled = true,
                                        visibleToUser = true,
                                        clickable = true,
                                        longClickable = true,
                                        checkable = true,
                                        checked = true,
                                        selected = true,
                                        editable = true,
                                        focused = true,
                                        scrollable = true,
                                        password = true,
                                        bounds = UiBounds(10, 20, 30, 40),
                                        range =
                                            UiRange(
                                                type = 1,
                                                min = 0.25f,
                                                max = 9.75f,
                                                current = 4.5f,
                                            ),
                                        actions =
                                            linkedSetOf(
                                                UiAction.SET_TEXT,
                                                UiAction.CLICK,
                                                UiAction.LONG_CLICK,
                                            ),
                                    ),
                                    UiNode(
                                        observationNodeId = "7:1",
                                        windowId = 7,
                                    ),
                                ),
                        ),
                    ),
            )

        val first = NativeUiSnapshotSerializer.toJson(observation)
        val second = NativeUiSnapshotSerializer.toJson(observation)
        val json = JSONObject(first)
        val window = json.getJSONArray("windows").getJSONObject(0)
        val node = window.getJSONArray("nodes").getJSONObject(0)
        val absent = window.getJSONArray("nodes").getJSONObject(1)

        assertEquals(first, second)
        assertEquals(1, json.getInt("schemaVersion"))
        assertEquals("snapshot-1", json.getString("snapshotId"))
        assertEquals(123L, json.getLong("capturedAtElapsedRealtimeMillis"))
        assertEquals("ALL_INTERACTIVE_WINDOWS", json.getString("completeness"))
        assertEquals(7, window.getInt("id"))
        assertEquals("APPLICATION", window.getString("type"))
        assertEquals(4, window.getInt("layer"))
        assertTrue(window.getBoolean("active"))
        assertTrue(window.getBoolean("focused"))
        assertTrue(window.getBoolean("interactionBarrier"))
        assertBounds(window.getJSONObject("bounds"), 1, 2, 101, 202)

        assertEquals("7:0", node.getString("observationNodeId"))
        assertEquals(7, node.getInt("windowId"))
        assertEquals("7:parent", node.getString("parentObservationNodeId"))
        assertEquals("com.example", node.getString("packageName"))
        assertEquals("com.example:id/send", node.getString("resourceId"))
        assertEquals("Send text", node.getString("text"))
        assertEquals("Send description", node.getString("contentDescription"))
        assertEquals("android.widget.Button", node.getString("className"))
        assertTrue(node.getBoolean("enabled"))
        assertTrue(node.getBoolean("visibleToUser"))
        assertTrue(node.getBoolean("clickable"))
        assertTrue(node.getBoolean("longClickable"))
        assertTrue(node.getBoolean("checkable"))
        assertTrue(node.getBoolean("checked"))
        assertTrue(node.getBoolean("selected"))
        assertTrue(node.getBoolean("editable"))
        assertTrue(node.getBoolean("focused"))
        assertTrue(node.getBoolean("scrollable"))
        assertTrue(node.getBoolean("password"))
        assertBounds(node.getJSONObject("bounds"), 10, 20, 30, 40)
        val range = node.getJSONObject("range")
        assertEquals(1, range.getInt("type"))
        assertEquals(0.25, range.getDouble("min"), 0.0)
        assertEquals(9.75, range.getDouble("max"), 0.0)
        assertEquals(4.5, range.getDouble("current"), 0.0)
        assertEquals(
            listOf("CLICK", "LONG_CLICK", "SET_TEXT"),
            (0 until node.getJSONArray("actions").length())
                .map(node.getJSONArray("actions")::getString),
        )

        listOf(
            "parentObservationNodeId",
            "packageName",
            "resourceId",
            "text",
            "contentDescription",
            "className",
            "bounds",
            "range",
        ).forEach { field ->
            assertTrue("$field must be explicit JSON null", absent.isNull(field))
        }
        listOf(
            "enabled",
            "visibleToUser",
            "clickable",
            "longClickable",
            "checkable",
            "checked",
            "selected",
            "editable",
            "focused",
            "scrollable",
            "password",
        ).forEach { field ->
            assertFalse(field, absent.getBoolean(field))
        }
        assertEquals(0, absent.getJSONArray("actions").length())
    }

    private fun assertBounds(
        bounds: JSONObject,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        assertEquals(left, bounds.getInt("left"))
        assertEquals(top, bounds.getInt("top"))
        assertEquals(right, bounds.getInt("right"))
        assertEquals(bottom, bounds.getInt("bottom"))
    }
}
