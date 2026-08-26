package com.caddie.app.runtime

import com.caddie.agent.core.ModelDelta
import org.json.JSONObject

/** Creates the short English action text shown in the normal-mode pill. */
internal object NormalToolNarration {
    private const val MAX_LENGTH = 80

    fun humanLabel(call: ModelDelta.ToolCall): String? {
        if (call.name in SILENT_TOOLS) return null
        val args = runCatching { JSONObject(call.argumentsJson) }.getOrNull()
        args?.optString("why")?.trim()?.takeIf(String::isNotEmpty)?.let {
            return it.take(MAX_LENGTH)
        }
        val target = args?.optJSONObject("target")?.let(::targetLabel)
        return when (call.name) {
            "android.back" -> "Going back"
            "android.open_app" -> "Opening ${appLabel(args?.optString("package_name").orEmpty())}"
            "android.open_url" -> "Opening the requested page"
            "android.click" -> target?.let { "Tapping “$it”" } ?: "Tapping the next option"
            "android.long_click" -> target?.let { "Holding “$it”" } ?: "Holding the selected option"
            "android.set_text" -> target?.let { "Entering text in “$it”" } ?: "Entering the requested text"
            "android.set_checked" -> {
                val verb = if (args?.optBoolean("checked") == true) "Turning on" else "Turning off"
                target?.let { "$verb “$it”" } ?: "$verb the selected option"
            }
            "android.scroll" ->
                if (args?.optBoolean("forward", true) != false) "Scrolling down" else "Scrolling up"
            else -> "Working on the next step"
        }.take(MAX_LENGTH)
    }

    private fun targetLabel(target: JSONObject): String? =
        sequenceOf("text", "content_description", "resource_id")
            .map { target.optString(it).trim() }
            .firstOrNull(String::isNotEmpty)

    private fun appLabel(packageName: String): String =
        APP_LABELS[packageName]
            ?: packageName.substringAfterLast('.').replaceFirstChar {
                if (it.isLowerCase()) it.titlecase() else it.toString()
            }.ifBlank { "app" }

    private val SILENT_TOOLS = setOf(
        "android.observe",
        "caddie.ask_user",
        "caddie.complete",
        "caddie.fail",
    )

    private val APP_LABELS = mapOf(
        "com.google.android.apps.maps" to "Google Maps",
        "com.android.chrome" to "Chrome",
        "com.caddie.studytelegram" to "Telegram",
        "com.caddie.studymail" to "Email",
        "com.caddie.studycalendar" to "Calendar",
        "com.caddie.studybank" to "Sparkasse",
        "com.caddie.studymusic" to "Spotify",
    )
}
