package com.llm_smartphone_v2.accessibility;

import android.os.SystemClock;

/**
 * Merkt sich Agenten-Aktivitaet, damit {@link CompanionAccessibilityService}
 * einen beobachteten Klick zuordnen kann:
 *
 *  - {@link #isAgentActive()}     — kurzes Fenster direkt nach einer Geste;
 *    ein Klick darin stammt vom Agenten, nicht vom Menschen.
 *  - {@link #isRunLikelyActive()} — langes Fenster; ueberhaupt nur waehrend
 *    eines laufenden Runs soll ein Touch als Eingriff zaehlen.
 *
 * "Run aktiv" haengt an JEDEM Bridge-Request (Screenshot, open_app, tap …),
 * nicht nur an Gesten — sonst wuerde ein Eingriff vor der ersten Geste
 * (z.B. waehrend der Agent per Intent eine App oeffnet) nicht erkannt.
 */
public final class AgentActivityTracker {

    /** Puffer nach Gesten-Ende, bis das ausgeloeste Klick-Event eintrifft. */
    private static final long GESTURE_MARGIN_MS = 1200L;

    /** Wie lange nach der letzten Agenten-Aktion ein Run als laufend gilt. */
    private static final long RUN_ACTIVE_WINDOW_MS = 90_000L;

    private static volatile long agentBusyUntilMs = 0L;
    private static volatile long lastRunActivityMs = 0L;

    private AgentActivityTracker() {
    }

    /** Vom PhoneController VOR jedem dispatchGesture aufzurufen. */
    public static void markDispatch(int durationMs) {
        long now = SystemClock.uptimeMillis();
        agentBusyUntilMs = now + Math.max(0, durationMs) + GESTURE_MARGIN_MS;
        lastRunActivityMs = now;
    }

    /** Bei jedem Agenten-Bridge-Request aufzurufen (haelt den Run "aktiv"). */
    public static void markRunActivity() {
        lastRunActivityMs = SystemClock.uptimeMillis();
    }

    /**
     * Beim {@code task_finished}-Event aufzurufen. Schliesst das
     * "Run aktiv"-Fenster sofort, statt es ~90 s auslaufen zu lassen — sonst
     * wuerde eine Sprach-Eingabe direkt nach "fertig" faelschlich als
     * Mid-run-Korrektur an einen nicht mehr existenten Run gehen und verpuffen.
     */
    public static void markRunFinished() {
        lastRunActivityMs = 0L;
        agentBusyUntilMs = 0L;
    }

    /** True, solange ein gerade beobachteter Klick vom Agenten stammen kann. */
    public static boolean isAgentActive() {
        return SystemClock.uptimeMillis() < agentBusyUntilMs;
    }

    /** True, wenn vermutlich gerade ein Agent-Run laeuft. */
    public static boolean isRunLikelyActive() {
        return lastRunActivityMs > 0L
                && SystemClock.uptimeMillis() - lastRunActivityMs < RUN_ACTIVE_WINDOW_MS;
    }
}
