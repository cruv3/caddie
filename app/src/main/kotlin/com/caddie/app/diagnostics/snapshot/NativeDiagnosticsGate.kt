package com.caddie.app.diagnostics.snapshot

import android.content.Context
import java.io.File

/** Enables native snapshot diagnostics for a short, marker-controlled period. */
class NativeDiagnosticsGate internal constructor(
    private val marker: File,
    private val clock: () -> Long,
) {
    fun isEnabled(): Boolean {
        if (!marker.isFile) {
            return false
        }
        val modifiedAt = marker.lastModified()
        val age = clock() - modifiedAt
        return modifiedAt > 0L && age >= 0L && age <= TTL_MILLIS
    }

    companion object {
        const val MARKER_FILE_NAME = "native_diagnostics_enabled"
        const val TTL_MILLIS = 5 * 60 * 1000L

        @Volatile
        private var installed: NativeDiagnosticsGate? = null

        @JvmStatic
        fun initialize(context: Context) {
            installed =
                NativeDiagnosticsGate(
                    File(context.noBackupFilesDir, MARKER_FILE_NAME),
                    System::currentTimeMillis,
                )
        }

        @JvmStatic
        fun isGloballyEnabled(): Boolean = installed?.isEnabled() == true
    }
}
