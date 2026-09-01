package com.caddie.app

import android.content.Context

/** The user-selected language for normal voice input and agent-facing text. */
enum class AgentLanguage(
    val storedValue: String,
    val label: String,
    val speechRecognizerLocale: String,
    val modelOutputLanguage: String,
) {
    German(
        storedValue = "de",
        label = "Deutsch",
        speechRecognizerLocale = "de-DE",
        modelOutputLanguage = "German",
    ),
    English(
        storedValue = "en",
        label = "English",
        speechRecognizerLocale = "en-US",
        modelOutputLanguage = "English",
    ),
    ;

    companion object {
        fun fromStoredValue(value: String?): AgentLanguage =
            entries.firstOrNull { it.storedValue == value } ?: German
    }
}

/** Persists the normal-mode language independently from the fixed study configuration. */
object AgentLanguageSettings {
    private const val PREFS = "agent_language_settings"
    private const val KEY_LANGUAGE = "language"

    fun selected(context: Context): AgentLanguage =
        AgentLanguage.fromStoredValue(prefs(context).getString(KEY_LANGUAGE, null))

    fun setSelected(context: Context, language: AgentLanguage) {
        prefs(context).edit().putString(KEY_LANGUAGE, language.storedValue).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
