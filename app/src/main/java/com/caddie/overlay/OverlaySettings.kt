package com.caddie.overlay

import android.content.Context

/**
 * Persisted overlay configuration.
 *
 * Backs the study's "Baseline" condition: with the pill disabled the agent
 * runs identically (tasks are still dispatched and executed) but no companion
 * UI is shown. This is the no-abstraction control condition.
 *
 * Kept a plain boolean for now. When the Selective-Spotlight / Solid-Canvas
 * conditions land this should become an OverlayCondition enum.
 */
object OverlaySettings {

    private const val PREFS = "overlay_settings"
    private const val KEY_PILL_ENABLED = "pill_enabled"

    /** Whether the companion pill/overlay is shown. Default: true. */
    fun isPillEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PILL_ENABLED, true)

    fun setPillEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_PILL_ENABLED, enabled).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
