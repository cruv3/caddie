package com.caddie.app.runtime

import com.caddie.agent.core.StepOutcome
import com.caddie.app.overlay.event.ThoughtEvent
import org.json.JSONObject

/** Converts native runtime lifecycle events into the overlay's presentation model. */
object NativeOverlayEventMapper {
    fun map(event: NativeAgentEvent): ThoughtEvent? =
        when (event) {
            is NativeAgentEvent.Started -> ThoughtEvent.TaskStarted("")
            is NativeAgentEvent.CurrentAction -> ThoughtEvent.CurrentAction(event.narration)
            is NativeAgentEvent.StepFinished ->
                if (event.outcome == StepOutcome.TOOL_FINISHED) {
                    ThoughtEvent.ToolCallFinished("native", null)
                } else {
                    null
                }
            is NativeAgentEvent.Completed -> ThoughtEvent.TaskFinished(
                ok = true,
                payload = JSONObject().put("message", event.answer),
            )
            is NativeAgentEvent.Aborted -> ThoughtEvent.TaskFinished(
                ok = false,
                payload = JSONObject()
                    .put("outcome", if (event.reason == null) "stopped" else "failed")
                    .put("message", event.reason ?: "Agent gestoppt"),
            )
            is NativeAgentEvent.Paused -> ThoughtEvent.TaskFinished(
                ok = false,
                payload = JSONObject().put("message", "Agent angehalten"),
            )
            is NativeAgentEvent.InterventionPaused -> ThoughtEvent.TaskPaused
            is NativeAgentEvent.InterventionResumed -> ThoughtEvent.TaskResumed
            is NativeAgentEvent.QuestionAsked ->
                ThoughtEvent.QuestionAsked(event.question, event.questionId)
            is NativeAgentEvent.QuestionResolved -> ThoughtEvent.QuestionResolved
        }

    fun map(result: NativeTaskResult): ThoughtEvent? =
        when (result) {
            is NativeTaskResult.Completed,
            is NativeTaskResult.Aborted,
            is NativeTaskResult.Paused,
            -> null
            NativeTaskResult.Busy,
            NativeTaskResult.AccessibilityUnavailable,
            NativeTaskResult.RuntimeClosed,
            -> ThoughtEvent.TaskFinished(ok = false, payload = null)
        }
}
