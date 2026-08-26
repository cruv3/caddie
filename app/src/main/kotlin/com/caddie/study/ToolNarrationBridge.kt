package com.caddie.study

import com.caddie.agent.core.ModelDelta
import com.caddie.app.overlay.ToolNarration
import org.json.JSONObject

/** Bridges [ModelDelta.ToolCall] (agent core) to [ToolNarration] (overlay). */
object ToolNarrationBridge {

    /** Human-readable label for a single tool call. */
    fun humanLabel(call: ModelDelta.ToolCall): String {
        nativeHumanLabel(call)?.let { return it }
        val args = parseArgs(call.argumentsJson)
        return ToolNarration.humanLabel(call.name, args)
    }

    /** Multi-line label for a batch of tool calls (C2 final checkpoint). */
    fun batchLabel(calls: List<ModelDelta.ToolCall>): String {
        return batchLabel(calls.map(::humanLabel))
    }

    /** Formats frozen participant-facing summary lines for the C2 checkpoint. */
    fun batchLabel(lines: Collection<String>): String {
        val numbered = lines.mapIndexed { i, line -> "${i + 1}. $line" }
        val summary = numbered.joinToString("\n")
        return "$summary\n\n(${lines.size} Schritte zur Bestätigung)"
    }

    private fun parseArgs(json: String): Map<String, Any?> {
        return try {
            val obj = JSONObject(json)
            val map = mutableMapOf<String, Any?>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = when {
                    obj.isNull(key) -> null
                    else -> obj.optString(key, "")
                }
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun nativeHumanLabel(call: ModelDelta.ToolCall): String? {
        if (!call.name.startsWith("android.")) return null
        val args = runCatching { JSONObject(call.argumentsJson) }.getOrNull()
            ?: return "Android-Aktion ausführen"
        args.optString("why").trim().takeIf(String::isNotEmpty)?.let { return it }
        val target = args.optJSONObject("target")
        val targetLabel = target?.let(::targetLabel)
        return when (call.name) {
            "android.observe" -> "Bildschirm prüfen"
            "android.back" -> "Zurückgehen"
            "android.open_app" ->
                "${appLabel(args.optString("package_name"))} öffnen"
            "android.open_url" -> "Link öffnen"
            "android.click" -> targetLabel?.let { "Auf „$it“ tippen" } ?: "Element antippen"
            "android.long_click" ->
                targetLabel?.let { "„$it“ gedrückt halten" } ?: "Element gedrückt halten"
            "android.set_text" -> targetLabel?.let { "Text in „$it“ eingeben" } ?: "Text eingeben"
            "android.set_checked" -> {
                val verb = if (args.optBoolean("checked")) "aktivieren" else "deaktivieren"
                targetLabel?.let { "„$it“ $verb" } ?: "Option $verb"
            }
            "android.scroll" ->
                if (args.optBoolean("forward", true)) "Nach unten scrollen" else "Nach oben scrollen"
            else -> "Android-Aktion ausführen"
        }
    }

    private fun targetLabel(target: JSONObject): String? =
        sequenceOf("text", "content_description", "resource_id")
            .map { target.optString(it).trim() }
            .firstOrNull(String::isNotEmpty)

    private fun appLabel(packageName: String): String =
        APP_LABELS[packageName] ?: packageName.substringAfterLast('.').replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
        }.ifBlank { "App" }

    private val APP_LABELS = mapOf(
        "com.google.android.apps.maps" to "Google Maps",
        "com.caddie.studytelegram" to "Telegram",
        "com.caddie.studymail" to "E-Mail",
        "com.caddie.studycalendar" to "Kalender",
        "com.caddie.studybank" to "Sparkasse",
        "com.caddie.studymusic" to "Spotify",
        "com.caddie.studygallery" to "Galerie",
        "com.caddie.studynotes" to "Notizen",
    )
}
