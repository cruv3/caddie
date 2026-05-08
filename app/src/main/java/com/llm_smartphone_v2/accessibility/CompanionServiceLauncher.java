package com.llm_smartphone_v2.accessibility;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.llm_smartphone_v2.MainActivity;
import com.llm_smartphone_v2.overlay.OverlayService;
import com.llm_smartphone_v2.wakeword.WakeWordService;

/**
 * Starts the WakeWord + Overlay foreground services if their respective
 * runtime permission is in place. Idempotent; missing permissions just skip
 * their service (logged once). Called from
 * {@link CompanionAccessibilityService#onServiceConnected()} when the user
 * activates the accessibility service, and as a safety net from
 * {@link com.llm_smartphone_v2.MainActivity#onResume()} once all three
 * permissions are present.
 */
public final class CompanionServiceLauncher {

    private static final String TAG = "ServiceLauncher";

    private CompanionServiceLauncher() {
    }

    public static void startAll(Context context) {
        Context app = context.getApplicationContext();
        startWakeWord(app);
        startOverlay(app);
    }

    public static void bringMainActivityForward(Context context) {
        Context app = context.getApplicationContext();
        Intent intent = new Intent(app, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            app.startActivity(intent);
        } catch (Throwable t) {
            Log.w(TAG, "could not bring MainActivity forward", t);
        }
    }

    private static void startWakeWord(Context app) {
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "skipping WakeWordService — RECORD_AUDIO not granted");
            return;
        }
        try {
            app.startForegroundService(new Intent(app, WakeWordService.class));
        } catch (Throwable t) {
            Log.e(TAG, "failed to start WakeWordService", t);
        }
    }

    private static void startOverlay(Context app) {
        if (!Settings.canDrawOverlays(app)) {
            Log.w(TAG, "skipping OverlayService — SYSTEM_ALERT_WINDOW not granted");
            return;
        }
        try {
            app.startForegroundService(new Intent(app, OverlayService.class));
        } catch (Throwable t) {
            Log.e(TAG, "failed to start OverlayService", t);
        }
    }
}
