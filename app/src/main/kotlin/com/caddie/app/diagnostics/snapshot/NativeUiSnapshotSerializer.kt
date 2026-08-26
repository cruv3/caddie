package com.caddie.app.diagnostics.snapshot

import com.caddie.executor.accessibility.UiBounds
import com.caddie.executor.accessibility.UiNode
import com.caddie.executor.accessibility.UiObservation
import com.caddie.executor.accessibility.UiRange
import com.caddie.executor.accessibility.UiWindow
import org.json.JSONArray
import org.json.JSONObject

/** Serializes native Accessibility observations into stable diagnostic JSON. */
object NativeUiSnapshotSerializer {
    @JvmStatic
    fun toJson(observation: UiObservation): String =
        JSONObject()
            .put("schemaVersion", 1)
            .put("snapshotId", observation.id)
            .put(
                "capturedAtElapsedRealtimeMillis",
                observation.capturedAtElapsedRealtimeMillis,
            )
            .put("completeness", observation.completeness.name)
            .put(
                "windows",
                JSONArray().apply {
                    observation.windows.forEach { put(it.toJson()) }
                },
            ).toString()

    private fun UiWindow.toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("type", type.name)
            .put("layer", layer)
            .put("active", active)
            .put("focused", focused)
            .put("interactionBarrier", interactionBarrier)
            .put("bounds", bounds?.toJson() ?: JSONObject.NULL)
            .put(
                "nodes",
                JSONArray().apply {
                    nodes.forEach { put(it.toJson()) }
                },
            )

    private fun UiNode.toJson(): JSONObject =
        JSONObject()
            .put("observationNodeId", observationNodeId)
            .put("windowId", windowId)
            .put(
                "parentObservationNodeId",
                parentObservationNodeId ?: JSONObject.NULL,
            )
            .put("packageName", packageName ?: JSONObject.NULL)
            .put("resourceId", resourceId ?: JSONObject.NULL)
            .put("text", text ?: JSONObject.NULL)
            .put(
                "contentDescription",
                contentDescription ?: JSONObject.NULL,
            )
            .put("className", className ?: JSONObject.NULL)
            .put("enabled", enabled)
            .put("visibleToUser", visibleToUser)
            .put("clickable", clickable)
            .put("longClickable", longClickable)
            .put("checkable", checkable)
            .put("checked", checked)
            .put("selected", selected)
            .put("editable", editable)
            .put("focused", focused)
            .put("scrollable", scrollable)
            .put("password", password)
            .put("bounds", bounds?.toJson() ?: JSONObject.NULL)
            .put("range", range?.toJson() ?: JSONObject.NULL)
            .put(
                "actions",
                JSONArray().apply {
                    actions
                        .map { it.name }
                        .sorted()
                        .forEach(::put)
                },
            )

    private fun UiBounds.toJson(): JSONObject =
        JSONObject()
            .put("left", left)
            .put("top", top)
            .put("right", right)
            .put("bottom", bottom)

    private fun UiRange.toJson(): JSONObject =
        JSONObject()
            .put("type", type)
            .put("min", min)
            .put("max", max)
            .put("current", current)
}
