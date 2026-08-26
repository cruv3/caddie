package com.caddie.app.overlay

import com.caddie.app.overlay.event.ThoughtEvent
import com.caddie.study.PendingConfirmation

enum class RunState { Hidden, Listening, Thinking, Acting, Paused, Done, Error }

data class OverlayUiState(
    val state: RunState = RunState.Hidden,
    val currentStepLabel: String = "",
    val topMessage: String? = null,
    val topMessageIsUser: Boolean = false,
    /** The question currently being answered through the normal-build text fallback. */
    val textQuestionId: String? = null,
    /** When set: the agent waits for swipe-to-confirm for this critical action. */
    val confirmationText: String? = null,
)

internal fun OverlayUiState.showQuestion(question: String): OverlayUiState = copy(
    topMessage = question,
    topMessageIsUser = false,
    textQuestionId = null,
    state = RunState.Listening,
    currentStepLabel = "listening...",
)

internal fun OverlayUiState.showTextQuestion(question: String, questionId: String): OverlayUiState =
    showQuestion(question).copy(textQuestionId = questionId)

internal fun shouldOfferTextQuestionAnswer(
    studyFeaturesEnabled: Boolean,
    voiceCaptureStarted: Boolean,
): Boolean = !studyFeaturesEnabled && !voiceCaptureStarted

internal fun OverlayUiState.resolveQuestion(): OverlayUiState = copy(
    topMessage = null,
    textQuestionId = null,
    state = RunState.Thinking,
    currentStepLabel = "...",
)

internal fun shouldPresentConfirmation(
    serviceDestroyed: Boolean,
    pendingStillCurrent: Boolean,
): Boolean = !serviceDestroyed && pendingStillCurrent

internal fun rejectConfirmationAfterDestroy(pending: PendingConfirmation) {
    pending.decline("overlay service stopped")
}

internal fun OverlayUiState.expireListening(): OverlayUiState =
    if (state == RunState.Listening) {
        copy(state = RunState.Hidden, currentStepLabel = "", textQuestionId = null)
    } else {
        this
    }

internal fun OverlayUiState.applyStudyRetry(): OverlayUiState = copy(
    state = RunState.Error,
    currentStepLabel = "Aufgabe nicht erkannt",
)

internal fun OverlayUiState.expireStudyRetry(): OverlayUiState =
    if (state == RunState.Error && currentStepLabel == "Aufgabe nicht erkannt") {
        copy(state = RunState.Hidden, currentStepLabel = "")
    } else {
        this
    }

internal fun OverlayUiState.applyTaskFinished(
    event: ThoughtEvent.TaskFinished,
): OverlayUiState {
    if (event.payload?.optString("outcome") == "aborted") {
        return copy(
            state = RunState.Hidden,
            currentStepLabel = "",
            topMessage = null,
            textQuestionId = null,
            confirmationText = null,
        )
    }
    return copy(
        state = if (event.ok) RunState.Done else RunState.Error,
        currentStepLabel = if (event.ok) "done" else "Error",
        textQuestionId = null,
    )
}

object ToolNarration {
    private const val MAX_LABEL_CHARS = 80

    fun humanLabel(tool: String, args: Map<String, Any?> = emptyMap()): String {
        val why = (args["why"] as? String).orEmpty().trim()
        if (why.isNotEmpty()) return why.take(MAX_LABEL_CHARS)

        return when {
            tool == "smartphone_tap_coordinates" -> "Tapping"
            tool == "smartphone_double_tap_coordinates" -> "Double-tapping"
            tool == "smartphone_long_press_coordinates" -> "Long-pressing"
            tool == "smartphone_swipe" -> "Swiping"
            tool == "smartphone_type_text" -> "Typing text"
            tool == "smartphone_press_button" -> {
                val b = args["button"]?.toString() ?: ""
                if (b.isNotEmpty()) "Pressing $b" else "Pressing key"
            }
            tool == "smartphone_open_app" -> {
                val pkg = args["package_name"]?.toString().orEmpty()
                if (pkg.isNotEmpty()) "Opening $pkg" else "Opening app"
            }
            tool == "smartphone_terminate_app" -> "Closing app"
            tool == "smartphone_install_app" -> "Installing app"
            tool == "smartphone_uninstall_app" -> "Uninstalling app"
            tool == "smartphone_open_url" -> "Opening URL"
            tool == "smartphone_take_screenshot" -> "Looking at the screen"
            tool == "smartphone_list_elements" -> "Reading screen"
            tool == "smartphone_list_apps" -> "Listing apps"
            tool == "smartphone_list_devices" -> "Finding devices"
            tool == "smartphone_get_screen_size" -> "Reading screen size"
            tool == "smartphone_get_orientation" -> "Reading orientation"
            tool == "smartphone_set_orientation" -> "Changing orientation"
            tool == "smartphone_save_skill" -> "Saving skill"
            tool == "smartphone_done" -> "done"
            tool == "smartphone_failed" -> "cancelled"
            tool.startsWith("smartphone_get_skill_") -> "Loading skill"
            else -> tool.removePrefix("smartphone_").replace('_', ' ')
        }
    }
}
