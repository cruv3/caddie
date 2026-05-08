package com.llm_smartphone_v2.accessibility;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * One-shot expectation flag set by MainActivity right before it deep-links
 * the user into system Accessibility-Settings, and consumed by
 * {@link CompanionAccessibilityService#onServiceConnected()}.
 *
 * Without this, every reconnect of the accessibility service (which can
 * happen during normal usage as Android recycles the binding) would yank
 * MainActivity to the foreground, interrupting the user's workflow.
 */
public final class AccessibilityReturnFlag {

    private static final String PREFS = "companion_setup";
    private static final String KEY = "expecting_accessibility_return";

    private AccessibilityReturnFlag() {
    }

    public static void arm(Context context) {
        prefs(context).edit().putBoolean(KEY, true).apply();
    }

    public static boolean consume(Context context) {
        SharedPreferences prefs = prefs(context);
        boolean armed = prefs.getBoolean(KEY, false);
        if (armed) {
            prefs.edit().remove(KEY).apply();
        }
        return armed;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
