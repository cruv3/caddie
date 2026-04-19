package com.llmcompanion.agent

import android.util.Log
import com.llmcompanion.action.AgentAction
import com.llmcompanion.model.IndexedUiNode
import org.json.JSONObject

/**
 * Parses a raw LLM response string into an [AgentAction].
 *
 * Handles the two main quirks of qwen3-family models:
 *  1. <think>…</think> reasoning blocks — stripped before JSON extraction
 *  2. JSON wrapped in markdown code fences — unwrapped automatically
 *
 * When [indexedNodes] is provided (Set-of-Marks mode), actions that carry an
 * `"element": N` key resolve the index to the matching node's text /
 * content-description / id instead of expecting a plain `"target"` string.
 */
object ActionParser {

    private const val TAG = "ActionParser"

    /**
     * Parse [raw] into an [AgentAction].
     * Returns [AgentAction.Fail] if the response can't be parsed.
     *
     * @param indexedNodes  Nodes visible in the current SoM screenshot, keyed by index.
     *                      Pass an empty list (default) when SoM is not active.
     */
    fun parse(raw: String, indexedNodes: List<IndexedUiNode> = emptyList()): AgentAction {
        return try {
            val json = extractJson(raw)
            Log.d(TAG, "Parsed JSON: $json")
            mapToAction(json, indexedNodes)
        } catch (e: Exception) {
            Log.e(TAG, "Parse failed: ${e.message}  raw='${raw.take(300)}'")
            AgentAction.Fail("Could not parse LLM response: ${e.message}")
        }
    }

    // ─── Extraction ────────────────────────────────────────────────────────────

    /**
     * Strip think blocks, unwrap code fences, then parse as JSON.
     */
    private fun extractJson(raw: String): JSONObject {
        var text = raw.trim()

        // 1. Remove <think>…</think> blocks (qwen3 "extended thinking")
        text = text.replace(Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE), "").trim()

        // 2. Unwrap markdown code fences  ```json … ``` or ``` … ```
        val fenceMatch = Regex("```(?:json)?\\s*([\\s\\S]*?)\\s*```").find(text)
        if (fenceMatch != null) {
            text = fenceMatch.groupValues[1].trim()
        }

        // 3. Find the first {...} block in whatever remains
        val start = text.indexOf('{')
        val end   = text.lastIndexOf('}')
        if (start == -1 || end == -1 || end <= start) {
            throw IllegalArgumentException("No JSON object found in: '${text.take(200)}'")
        }
        text = text.substring(start, end + 1)

        return JSONObject(text)
    }

    // ─── SoM index resolution ──────────────────────────────────────────────────

    /**
     * Resolve the action target from either an `"element": N` index (SoM mode)
     * or a plain `"target"` string (classic mode).
     *
     * Priority when `"element"` is present:
     *   1. node.text (non-blank)
     *   2. node.contentDescription (non-blank)
     *   3. node.id
     *
     * Falls back to `json.getString("target")` when no matching node is found
     * or when neither key is present.
     */
    private fun resolveTarget(json: JSONObject, indexedNodes: List<IndexedUiNode>): String {
        if (json.has("element") && indexedNodes.isNotEmpty()) {
            val idx  = json.getInt("element")
            val node = indexedNodes.find { it.index == idx }?.node
            if (node != null) {
                return node.text?.takeIf { it.isNotBlank() }
                    ?: node.contentDescription?.takeIf { it.isNotBlank() }
                    ?: node.id
            }
        }
        return json.getString("target")
    }

    // ─── Mapping ───────────────────────────────────────────────────────────────

    private fun mapToAction(json: JSONObject, indexedNodes: List<IndexedUiNode>): AgentAction {
        return when (val type = json.getString("type").lowercase().trim()) {

            "click"         -> AgentAction.Click(resolveTarget(json, indexedNodes))
            "long_click"    -> AgentAction.LongClick(resolveTarget(json, indexedNodes))

            "set_text"      -> AgentAction.SetText(
                                   target = resolveTarget(json, indexedNodes),
                                   text   = json.getString("text")
                               )

            "scroll"        -> AgentAction.Scroll(
                                   target  = if (json.has("element") || json.has("target"))
                                                 resolveTarget(json, indexedNodes)
                                             else "",
                                   forward = json.optBoolean("forward", true)
                               )

            "swipe"         -> AgentAction.Swipe(
                                   startX     = json.getDouble("startX").toFloat(),
                                   startY     = json.getDouble("startY").toFloat(),
                                   endX       = json.getDouble("endX").toFloat(),
                                   endY       = json.getDouble("endY").toFloat(),
                                   durationMs = json.optLong("durationMs", 300L)
                               )

            "tap_at"        -> AgentAction.TapAt(
                                   x = json.getDouble("x").toFloat(),
                                   y = json.getDouble("y").toFloat()
                               )

            "back"          -> AgentAction.Back
            "home"          -> AgentAction.Home
            "recents"       -> AgentAction.Recents
            "notifications" -> AgentAction.Notifications
            "quick_settings"-> AgentAction.QuickSettings

            "launch_intent" -> AgentAction.LaunchIntent(
                                   action  = json.optString("action").ifBlank { json.optString("target") },
                                   dataUri = json.optString("dataUri").ifBlank { null }
                               )

            "launch_app"    -> AgentAction.LaunchApp(
                                   // Accept both "packageName" and "target" — LLMs sometimes use the wrong key
                                   json.optString("packageName").ifBlank {
                                       json.optString("target").ifBlank {
                                           throw IllegalArgumentException("launch_app missing packageName or target")
                                       }
                                   }
                               )

            "press_enter"   -> AgentAction.PressEnter

            "wait"          -> AgentAction.Wait(json.optLong("millis", 1_000L))

            "done"          -> AgentAction.Done
            "fail"          -> AgentAction.Fail(json.optString("reason", "LLM gave up"))

            else            -> AgentAction.Fail("Unknown action type: '$type'")
        }
    }
}
