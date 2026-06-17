package com.caddie.overlay

enum class RunState { Hidden, Listening, Thinking, Acting, Paused, Done, Error }

data class OverlayUiState(
    val state: RunState = RunState.Hidden,
    val currentStepLabel: String = "",
    val topMessage: String? = null,
    val topMessageIsUser: Boolean = false,
    /** Wenn gesetzt: der Agent wartet auf eine Swipe-to-Confirm-Bestaetigung
     *  fuer diese kritische Aktion. Loest die modale Confirm-Karte aus. */
    val confirmationText: String? = null,
)

object ToolNarration {
    private const val MAX_LABEL_CHARS = 80

    fun humanLabel(tool: String, args: Map<String, Any?> = emptyMap()): String {
        // Prefer the LLM's own brief explanation if it passed one via the
        // `why` parameter on the tool call. That gives the user a real
        // sentence ("Sucht nach Pizza bei Google") instead of a generic
        // verb ("Opening URL"). Fall back to tool-derived labels.
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
