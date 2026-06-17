package com.caddie.wakeword

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.caddie.R
import com.caddie.accessibility.AgentActivityTracker
import com.caddie.accessibility.InterventionReporter
import com.caddie.overlay.OverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class WakeWordService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val captureRunning = AtomicBoolean(false)
    private val wakeHandlingActive = AtomicBoolean(false)
    private var captureJob: Job? = null
    private var detector: OpenWakeWordDetector? = null
    private val speech by lazy { SpeechCapture(this) }

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification()
        startListening()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundWithNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Wake-Word Listener",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Listens for the wake word" }
            nm.createNotificationChannel(channel)
        }
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.wakeword_service_title))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "RECORD_AUDIO permission missing — cannot start listener")
            return
        }
        val det = OpenWakeWordDetector(this)
        if (!det.initialize()) {
            Log.w(TAG, "wake-word models not available — service idle. Drop them in assets/wakeword/.")
            return
        }
        detector = det
        captureJob = scope.launch { captureLoop(det) }
    }

    private suspend fun captureLoop(det: OpenWakeWordDetector) {
        val sampleRate = OpenWakeWordDetector.SAMPLE_RATE
        val frameSize = OpenWakeWordDetector.FRAME_SAMPLES
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(frameSize * 4)

        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuffer,
            )
        } catch (sec: SecurityException) {
            Log.w(TAG, "AudioRecord denied", sec)
            return
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.w(TAG, "AudioRecord not initialized")
            record.release()
            return
        }

        captureRunning.set(true)
        record.startRecording()
        val buf = ShortArray(frameSize)
        Log.i(TAG, "wake-word capture loop started")
        try {
            while (scope.isActive && captureRunning.get()) {
                if (wakeHandlingActive.get()) {
                    // Pause reading mic while STT is running
                    Thread.sleep(50)
                    continue
                }
                val read = record.read(buf, 0, frameSize)
                if (read <= 0) continue
                if (read != frameSize) continue
                val score = det.process(buf)
                if (score != null && score > 0.1f) Log.i(TAG, "ww score=$score")
                if (det.shouldFire(score)) {
                    Log.i(TAG, "wake-word fired (score=$score)")
                    onWakeWord()
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "capture loop crashed", t)
        } finally {
            try { record.stop() } catch (_: Throwable) {}
            try { record.release() } catch (_: Throwable) {}
        }
    }

    private fun onWakeWord() {
        if (!wakeHandlingActive.compareAndSet(false, true)) return

        // Laeuft gerade ein Agent-Run, ist das Gesprochene eine Mid-run-
        // Korrektur (Block 5) — kein neuer Task. Sonst wie bisher.
        val correctionMode = AgentActivityTracker.isRunLikelyActive()
        if (correctionMode) InterventionReporter.get().onVoiceCaptureStarted()

        OverlayService.notifyListening(this)
        speech.start(
            onResult = { transcript ->
                Log.i(TAG, "transcript: $transcript (correction=$correctionMode)")
                if (isStopCommand(transcript)) {
                    Log.i(TAG, "stop command recognized -> stopping run")
                    InterventionReporter.get().stopRun()
                    OverlayService.notifyDismiss(this)
                } else if (correctionMode) {
                    InterventionReporter.get().sendCorrection(transcript)
                } else {
                    OverlayService.dispatchTask(this, transcript)
                }
                onSpeechDone()
            },
            onError = { err ->
                Log.w(TAG, "speech error: ${err.message}")
                if (correctionMode) InterventionReporter.get().onVoiceCaptureEnded()
                OverlayService.notifyDismiss(this)
                onSpeechDone()
            },
        )
    }

    private fun onSpeechDone() {
        wakeHandlingActive.set(false)
        detector?.reset()
    }

    /** True if the spoken text is a stop command ("stop", "halt", "cancel", …). */
    private fun isStopCommand(text: String): Boolean {
        val norm = text.trim().lowercase()
        return STOP_WORDS.any { norm == it || norm.startsWith("$it ") || norm.startsWith("$it.") }
    }

    override fun onDestroy() {
        captureRunning.set(false)
        captureJob?.cancel()
        detector?.close()
        detector = null
        speech.cleanup()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "WakeWordService"
        private const val CHANNEL_ID = "wakeword_channel"
        private const val NOTIF_ID = 4242
        private val STOP_WORDS = listOf(
            "stop", "stopp", "stoppen", "stop it", "halt", "anhalten", "abbrechen", "cancel",
        )
    }
}
