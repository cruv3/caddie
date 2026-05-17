package com.llm_smartphone_v2.overlay

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
        // verb ("Öffne URL"). Fall back to tool-derived labels.
        val why = (args["why"] as? String).orEmpty().trim()
        if (why.isNotEmpty()) return why.take(MAX_LABEL_CHARS)

        return when {
            tool == "smartphone_tap_coordinates" -> "Tippe"
            tool == "smartphone_double_tap_coordinates" -> "Doppel-Tippe"
            tool == "smartphone_long_press_coordinates" -> "Halte gedrückt"
            tool == "smartphone_swipe" -> "Wische"
            tool == "smartphone_type_text" -> "Tippe Text"
            tool == "smartphone_press_button" -> {
                val b = args["button"]?.toString() ?: ""
                if (b.isNotEmpty()) "Drücke $b" else "Drücke Taste"
            }
            tool == "smartphone_open_app" -> {
                val pkg = args["package_name"]?.toString().orEmpty()
                if (pkg.isNotEmpty()) "Öffne $pkg" else "Öffne App"
            }
            tool == "smartphone_terminate_app" -> "Schließe App"
            tool == "smartphone_install_app" -> "Installiere App"
            tool == "smartphone_uninstall_app" -> "Deinstalliere App"
            tool == "smartphone_open_url" -> "Öffne URL"
            tool == "smartphone_take_screenshot" -> "Schaue auf den Screen"
            tool == "smartphone_list_elements" -> "Lese Screen"
            tool == "smartphone_list_apps" -> "Liste Apps"
            tool == "smartphone_list_devices" -> "Suche Geräte"
            tool == "smartphone_get_screen_size" -> "Lese Bildschirmgröße"
            tool == "smartphone_get_orientation" -> "Lese Ausrichtung"
            tool == "smartphone_set_orientation" -> "Ändere Ausrichtung"
            tool == "smartphone_save_skill" -> "Speichere Anleitung"
            tool == "smartphone_done" -> "fertig"
            tool == "smartphone_failed" -> "abgebrochen"
            tool.startsWith("smartphone_get_skill_") -> "Lade Anleitung"
            else -> tool.removePrefix("smartphone_").replace('_', ' ')
        }
    }
}
