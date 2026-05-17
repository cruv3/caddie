package com.llm_smartphone_v2.accessibility;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;

import com.llm_smartphone_v2.lmstudio.LmStudioConfig;

import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Meldet menschliche Eingriffe an den Host.
 *
 * Sobald {@link CompanionAccessibilityService} einen Touch erkennt, der nicht
 * vom Agenten stammt, ruft es {@link #onHumanInteraction()}. Der Reporter
 * sendet dann einmalig {@code POST /control intervene} (Agent pausiert) und
 * setzt nach {@code RESUME_QUIET_MS} ohne weiteren Touch automatisch
 * {@code POST /control resume} — knopflos, wie gewuenscht.
 */
public final class InterventionReporter {

    private static final String TAG = "Intervention";
    /** Touch-Ruhe, nach der der Agent automatisch fortsetzt. */
    private static final long RESUME_QUIET_MS = 4000L;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static final InterventionReporter INSTANCE = new InterventionReporter();

    public static InterventionReporter get() {
        return INSTANCE;
    }

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .build();
    private final Handler handler;
    private boolean paused = false;
    private long lastTouchMs = 0L;
    /** True, solange der Nutzer eine gesprochene Korrektur diktiert —
     *  unterdrueckt den Auto-Resume, damit der Agent nicht mitten im Satz
     *  weiterlaeuft. */
    private boolean voiceCaptureActive = false;

    private InterventionReporter() {
        HandlerThread thread = new HandlerThread("intervention-reporter");
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    /** Vom AccessibilityService bei jedem erkannten menschlichen Touch. */
    public synchronized void onHumanInteraction() {
        lastTouchMs = SystemClock.uptimeMillis();
        if (!paused) {
            paused = true;
            Log.i(TAG, "human intervention -> pausing agent");
            post("intervene");
            handler.postDelayed(this::checkResume, RESUME_QUIET_MS + 150);
        }
    }

    /** Vom WakeWordService, wenn der Nutzer waehrend eines Runs zu sprechen
     *  beginnt — setzt den Auto-Resume aus, bis die Korrektur da ist. */
    public synchronized void onVoiceCaptureStarted() {
        voiceCaptureActive = true;
    }

    /** Spracherfassung beendet (ohne verwertbares Ergebnis). */
    public synchronized void onVoiceCaptureEnded() {
        voiceCaptureActive = false;
        if (paused) {
            handler.post(this::checkResume);
        }
    }

    /** Gesprochene Mid-run-Korrektur an den Host schicken. Der Run wird
     *  host-seitig fortgesetzt, sobald die Korrektur eingespeist ist. */
    public synchronized void sendCorrection(String text) {
        paused = false;
        voiceCaptureActive = false;
        Log.i(TAG, "voice correction -> " + text);
        postCorrection(text);
    }

    /** Swipe-to-Confirm: kritische Aktion bestaetigt. */
    public void sendConfirm() {
        Log.i(TAG, "user confirmed critical action");
        post("confirm");
    }

    /** Swipe-to-Confirm: kritische Aktion abgelehnt. */
    public void sendDecline() {
        Log.i(TAG, "user declined critical action");
        post("decline");
    }

    /** Periodischer Check: nach genug Touch-Ruhe -> Agent fortsetzen. */
    private synchronized void checkResume() {
        if (!paused) {
            return;
        }
        if (voiceCaptureActive) {
            // Nutzer diktiert gerade eine Korrektur — Resume aufschieben.
            handler.postDelayed(this::checkResume, 500);
            return;
        }
        long quietMs = SystemClock.uptimeMillis() - lastTouchMs;
        if (quietMs >= RESUME_QUIET_MS) {
            paused = false;
            Log.i(TAG, "touch quiet for " + quietMs + "ms -> resuming agent");
            post("resume");
        } else {
            handler.postDelayed(this::checkResume, RESUME_QUIET_MS - quietMs + 100);
        }
    }

    private void post(String action) {
        handler.post(() -> {
            String body = "{\"action\":\"" + action + "\"}";
            Request request = new Request.Builder()
                    .url(LmStudioConfig.CONTROL_ENDPOINT)
                    .post(RequestBody.create(body, JSON))
                    .build();
            try (Response response = client.newCall(request).execute()) {
                Log.i(TAG, "POST /control " + action + " -> " + response.code());
            } catch (Exception exception) {
                Log.w(TAG, "POST /control " + action + " failed: " + exception.getMessage());
            }
        });
    }

    private void postCorrection(String text) {
        handler.post(() -> {
            String body;
            try {
                body = new JSONObject()
                        .put("action", "correct")
                        .put("text", text)
                        .toString();
            } catch (Exception exception) {
                Log.w(TAG, "correct payload build failed: " + exception.getMessage());
                return;
            }
            Request request = new Request.Builder()
                    .url(LmStudioConfig.CONTROL_ENDPOINT)
                    .post(RequestBody.create(body, JSON))
                    .build();
            try (Response response = client.newCall(request).execute()) {
                Log.i(TAG, "POST /control correct -> " + response.code());
            } catch (Exception exception) {
                Log.w(TAG, "POST /control correct failed: " + exception.getMessage());
            }
        });
    }
}
