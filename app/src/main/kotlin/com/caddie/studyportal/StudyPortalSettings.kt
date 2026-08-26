package com.caddie.studyportal

import android.content.Context

/**
 * Persisted configuration for the on-device study portal.
 *
 * The portal is an embedded Ktor CIO HTTP server hosted in a foreground
 * service. It serves the existing study-portal frontend (reused unchanged
 * from the Python implementation) and exposes JSON endpoints that drive the
 * native V2 runtime.
 */
object StudyPortalSettings {

    private const val PREFS = "study_portal_settings"
    private const val KEY_ENABLED = "portal_enabled"
    private const val KEY_PORT = "portal_port"

    /** Default port — matches the Python agent HTTP server default. */
    const val DEFAULT_PORT = 8787

    /** Whether the study portal foreground service should be running. */
    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** Port the embedded server binds to. */
    fun getPort(context: Context): Int =
        prefs(context).getInt(KEY_PORT, DEFAULT_PORT)

    fun setPort(context: Context, port: Int) {
        prefs(context).edit().putInt(KEY_PORT, port.coerceIn(1024, 65535)).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
