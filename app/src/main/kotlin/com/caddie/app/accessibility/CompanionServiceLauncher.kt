package com.caddie.app.accessibility

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.caddie.app.MainActivity
import com.caddie.app.overlay.OverlayService
import com.caddie.wakeword.WakeWordService

/**
 * Starts the WakeWord + Overlay foreground services if their respective
 * runtime permission is in place. Idempotent; missing permissions just skip
 * their service. Called from CompanionAccessibilityService.onServiceConnected()
 * and as a safety net from MainActivity.onResume().
 */
object CompanionServiceLauncher {

    private const val TAG = "ServiceLauncher"

    fun startAll(context: Context) {
        val app = context.applicationContext
        startWakeWord(app)
        startOverlay(app)
    }

    fun bringMainActivityForward(context: Context) {
        val app = context.applicationContext
        val intent = Intent(app, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        }
        try {
            app.startActivity(intent)
        } catch (t: Throwable) {
            Log.w(TAG, "could not bring MainActivity forward", t)
        }
    }

    private fun startWakeWord(app: Context) {
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "skipping WakeWordService — RECORD_AUDIO not granted")
            return
        }
        try {
            app.startForegroundService(Intent(app, WakeWordService::class.java))
        } catch (t: Throwable) {
            Log.e(TAG, "failed to start WakeWordService", t)
        }
    }

    private fun startOverlay(app: Context) {
        if (!Settings.canDrawOverlays(app)) {
            Log.w(TAG, "skipping OverlayService — SYSTEM_ALERT_WINDOW not granted")
            return
        }
        try {
            app.startForegroundService(Intent(app, OverlayService::class.java))
        } catch (t: Throwable) {
            Log.e(TAG, "failed to start OverlayService", t)
        }
    }
}
