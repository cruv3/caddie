package com.caddie.app.accessibility

import android.content.Context
import android.content.SharedPreferences

/**
 * One-shot flag set by MainActivity before deep-linking into system Accessibility Settings,
 * and consumed by CompanionAccessibilityService.onServiceConnected().
 *
 * Without this, every reconnect of the accessibility service would bring MainActivity
 * to the foreground, interrupting the user's workflow.
 */
object AccessibilityReturnFlag {

    private const val PREFS = "companion_setup"
    private const val KEY = "expecting_accessibility_return"

    fun arm(context: Context) {
        prefs(context).edit().putBoolean(KEY, true).apply()
    }

    fun consume(context: Context): Boolean {
        val prefs = prefs(context)
        val armed = prefs.getBoolean(KEY, false)
        if (armed) prefs.edit().remove(KEY).apply()
        return armed
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
