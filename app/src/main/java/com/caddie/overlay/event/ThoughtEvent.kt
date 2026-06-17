package com.caddie.overlay.event

import org.json.JSONObject

sealed class ThoughtEvent {
    data class TaskStarted(val task: String) : ThoughtEvent()
    data class ToolCallStarted(val tool: String, val argsSummary: String) : ThoughtEvent()
    data class ToolCallFinished(val tool: String, val error: String?) : ThoughtEvent()
    data class TaskFinished(val ok: Boolean, val payload: JSONObject?) : ThoughtEvent()
    object TaskPaused : ThoughtEvent()
    object TaskResumed : ThoughtEvent()
    data class ConfirmationRequired(val tool: String, val description: String) : ThoughtEvent()
    object ConfirmationResolved : ThoughtEvent()
    object SessionReady : ThoughtEvent()
    data class QuestionAsked(val question: String) : ThoughtEvent()
    object QuestionResolved : ThoughtEvent()

    companion object {
        fun parse(line: String): ThoughtEvent? {
            return try {
                val json = JSONObject(line)
                when (json.optString("type")) {
                    "task_started" -> TaskStarted(json.optString("task", ""))
                    "tool_call_started" -> ToolCallStarted(
                        tool = json.optString("tool", "unknown"),
                        argsSummary = formatArgs(json.optJSONObject("args"))
                    )
                    "tool_call_finished" -> ToolCallFinished(
                        tool = json.optString("tool", "unknown"),
                        error = json.takeIf { it.has("error") }?.optString("error")?.takeIf { it.isNotBlank() }
                    )
                    "task_finished" -> TaskFinished(
                        ok = json.optBoolean("ok", false),
                        payload = json.optJSONObject("payload")
                    )
                    "task_paused" -> TaskPaused
                    "task_resumed" -> TaskResumed
                    "confirmation_required" -> ConfirmationRequired(
                        tool = json.optString("tool", ""),
                        description = json.optJSONObject("payload")
                            ?.optString("description", "").orEmpty()
                            .ifBlank { "Confirm critical action?" }
                    )
                    "confirmation_resolved" -> ConfirmationResolved
                    "session_ready" -> SessionReady
                    "question_asked" -> QuestionAsked(
                        json.optJSONObject("payload")?.optString("question", "").orEmpty()
                            .ifBlank { "I need a decision." }
                    )
                    "question_resolved" -> QuestionResolved
                    else -> null
                }
            } catch (_: Throwable) {
                null
            }
        }

        private fun formatArgs(obj: JSONObject?): String {
            if (obj == null || obj.length() == 0) return ""
            val parts = mutableListOf<String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = obj.opt(k)
                parts += "$k=$v"
            }
            return parts.joinToString(", ")
        }
    }
}
