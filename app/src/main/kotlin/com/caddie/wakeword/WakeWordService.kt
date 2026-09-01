package com.caddie.wakeword

import android.Manifest
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
import com.caddie.agent.core.RunId
import com.caddie.app.AgentLanguage
import com.caddie.app.AgentLanguageSettings
import com.caddie.app.CaddieApplication
import com.caddie.app.androidRuntimeProcess
import com.caddie.app.overlay.OverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Listens for Hey Jarvis on-device, captures speech, and submits it to the native runtime. */
class WakeWordService : Service() {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val captureRunning = AtomicBoolean(false)
    private val pendingSpeech = AtomicReference<SpeechRequest?>(null)
    private var captureJob: Job? = null
    private var detector: OpenWakeWordDetector? = null
    private val speech by lazy { SpeechCapture(this) }

    override fun onCreate() {
        super.onCreate()
        if (!hasRecordAudioPermission(this)) {
            Log.w(TAG, "WakeWordService started without RECORD_AUDIO; stopping")
            stopSelf()
            return
        }
        startForegroundWithNotification()
        scope.launch {
            (application as CaddieApplication).awaitAgentReady()
            withContext(Dispatchers.Main.immediate) {
                if (scope.isActive) startListening()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!hasRecordAudioPermission(this)) {
            Log.w(TAG, "WakeWordService command ignored because RECORD_AUDIO is missing")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_LISTEN_ANSWER) {
            requestSpeechCapture(
                SpeechRequest(
                    purpose = SpeechPurpose.ANSWER,
                    questionId = intent.getStringExtra(EXTRA_QUESTION_ID).orEmpty(),
                ),
            )
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        captureRunning.set(false)
        pendingSpeech.getAndSet(null)?.let(::releaseVoiceHold)
        captureJob?.cancel()
        speech.cleanup()
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundWithNotification() {
        val notifications = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notifications.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Wake-Word Listener",
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Listening for Hey Jarvis")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "RECORD_AUDIO permission missing")
            return
        }
        val activeDetector = OpenWakeWordDetector(this)
        if (!activeDetector.initialize()) return
        detector = activeDetector
        captureRunning.set(true)
        captureJob = scope.launch { captureLoop(activeDetector) }
        Log.i(TAG, "wake-word detector ready (threshold=${activeDetector.detectionThreshold})")
    }

    private suspend fun captureLoop(activeDetector: OpenWakeWordDetector) {
        val frameSize = OpenWakeWordDetector.FRAME_SAMPLES
        val accumulator = PcmFrameAccumulator(frameSize)
        val minimumBuffer = AudioRecord.getMinBufferSize(
            OpenWakeWordDetector.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(frameSize * 4)

        try {
            while (scope.isActive && captureRunning.get()) {
                val recorder = createAudioRecord(minimumBuffer)
                if (recorder == null) {
                    delay(RECORDER_RETRY_MS)
                    continue
                }
                var failed = false
                var zeroReads = 0
                val readBuffer = ShortArray(frameSize)
                try {
                    recorder.startRecording()
                    failed = recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING
                    if (!failed) Log.i(TAG, "wake-word recorder started")
                    while (!failed && scope.isActive && captureRunning.get() && pendingSpeech.get() == null) {
                        when (val read = recorder.read(readBuffer, 0, readBuffer.size, AudioRecord.READ_BLOCKING)) {
                            0 -> {
                                zeroReads += 1
                                if (zeroReads == 1 || zeroReads % 100 == 0) {
                                    Log.w(TAG, "AudioRecord returned zero samples ($zeroReads consecutive)")
                                }
                            }
                            in Int.MIN_VALUE until 0 -> failed = true
                            else -> {
                                zeroReads = 0
                                for (frame in accumulator.append(readBuffer, read)) {
                                    val score = activeDetector.process(frame)
                                    if (score != null && score >= DIAGNOSTIC_SCORE_FLOOR) {
                                        Log.i(TAG, "wake-word score=$score")
                                    }
                                    if (activeDetector.shouldFire(score)) {
                                        Log.i(
                                            TAG,
                                            "wake-word fired (score=$score, threshold=${activeDetector.detectionThreshold})",
                                        )
                                        val request = SpeechRequest(
                                            purpose = SpeechPurpose.TASK,
                                            interruptedRunId = androidRuntimeProcess().pauseForVoiceCapture(),
                                        )
                                        if (!requestSpeechCapture(request)) releaseVoiceHold(request)
                                        break
                                    }
                                }
                            }
                        }
                    }
                } catch (error: Throwable) {
                    Log.w(TAG, "wake-word recorder failed", error)
                    failed = true
                } finally {
                    try {
                        recorder.stop()
                    } catch (_: Throwable) {
                    }
                    recorder.release()
                    Log.i(TAG, "wake-word recorder released")
                }

                val request = pendingSpeech.get()
                if (scope.isActive && captureRunning.get() && request != null) {
                    captureOneUtterance(request)
                }
                while (scope.isActive && captureRunning.get() && pendingSpeech.get() == request) {
                    delay(SPEECH_POLL_MS)
                }
                accumulator.reset()
                activeDetector.reset()
                if (failed) delay(RECORDER_RETRY_MS)
            }
        } finally {
            activeDetector.close()
            if (detector === activeDetector) detector = null
        }
    }

    private fun createAudioRecord(bufferSize: Int): AudioRecord? {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "RECORD_AUDIO permission was revoked")
            return null
        }
        return try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                OpenWakeWordDetector.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
            ).also { recorder ->
                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    recorder.release()
                    return null
                }
            }
        } catch (error: Throwable) {
            Log.w(TAG, "AudioRecord creation failed", error)
            null
        }
    }

    private fun requestSpeechCapture(request: SpeechRequest): Boolean {
        while (true) {
            val current = pendingSpeech.get()
            if (current == null) return pendingSpeech.compareAndSet(null, request)
            if (current.purpose != SpeechPurpose.ANSWER ||
                request.purpose != SpeechPurpose.ANSWER ||
                current.questionId == request.questionId
            ) return false
            if (pendingSpeech.compareAndSet(current, request)) return true
        }
    }

    private fun captureOneUtterance(request: SpeechRequest) {
        OverlayService.notifyListening(this)
        speech.start(
            locale = speechRecognizerLocale(),
            onResult = { transcript ->
                Log.i(TAG, "speech captured (${transcript.length} chars)")
                if (transcript.isBlank()) {
                    releaseVoiceHold(request)
                    OverlayService.notifyDismiss(this)
                } else when (request.purpose) {
                    SpeechPurpose.TASK -> runCatching {
                        OverlayService.dispatchTask(
                            this,
                            transcript,
                            runtimeControlsEnabled = true,
                            interruptedRunId = request.interruptedRunId,
                        )
                    }.onFailure {
                        releaseVoiceHold(request)
                        OverlayService.notifyDismiss(this)
                        Log.w(TAG, "failed to dispatch captured speech", it)
                    }
                    SpeechPurpose.ANSWER -> {
                        val command = RuntimeVoiceCommandParser.parse(transcript)
                        if (RuntimeVoiceCommandParser.isExplicitInvocation(transcript) &&
                            command !is RuntimeVoiceCommand.Task
                        ) {
                            OverlayService.dispatchTask(
                                this,
                                transcript,
                                runtimeControlsEnabled = true,
                            )
                        } else {
                            val answer = (command as? RuntimeVoiceCommand.Task)?.text ?: transcript
                            OverlayService.submitQuestionAnswer(this, request.questionId, answer)
                        }
                    }
                }
                pendingSpeech.compareAndSet(request, null)
            },
            onError = { error ->
                Log.w(TAG, "speech capture failed: ${error.message}")
                releaseVoiceHold(request)
                OverlayService.notifyDismiss(this)
                pendingSpeech.compareAndSet(request, null)
            },
        )
    }

    private fun releaseVoiceHold(request: SpeechRequest) {
        request.interruptedRunId?.let { androidRuntimeProcess().resumeAfterVoiceCapture(it) }
    }

    /** Keeps the controlled study's established German speech path deterministic. */
    private fun speechRecognizerLocale(): String =
        if (androidRuntimeProcess().studyCoordinator.isStudyModeActive()) {
            AgentLanguage.German.speechRecognizerLocale
        } else {
            AgentLanguageSettings.selected(this).speechRecognizerLocale
        }

    companion object {
        private const val TAG = "WakeWordService"
        private const val CHANNEL_ID = "wakeword_channel"
        private const val NOTIFICATION_ID = 4242
        private const val DIAGNOSTIC_SCORE_FLOOR = 0.08f
        private const val RECORDER_RETRY_MS = 500L
        private const val SPEECH_POLL_MS = 25L
        const val ACTION_LISTEN_ANSWER = "com.caddie.wakeword.LISTEN_ANSWER"
        const val EXTRA_QUESTION_ID = "question_id"

        fun hasRecordAudioPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

        /**
         * Starts one answer capture only when microphone access is available. Callers use the
         * return value to offer the normal-build text fallback instead.
         */
        fun listenForAnswer(context: Context, questionId: String): Boolean {
            if (questionId.isBlank() || !hasRecordAudioPermission(context)) return false
            return try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, WakeWordService::class.java).apply {
                        action = ACTION_LISTEN_ANSWER
                        putExtra(EXTRA_QUESTION_ID, questionId)
                    },
                )
                true
            } catch (error: SecurityException) {
                Log.w(TAG, "answer capture could not start", error)
                false
            } catch (error: IllegalStateException) {
                Log.w(TAG, "answer capture could not start", error)
                false
            }
        }
    }

    private enum class SpeechPurpose { TASK, ANSWER }
    private data class SpeechRequest(
        val purpose: SpeechPurpose,
        val questionId: String = "",
        val interruptedRunId: RunId? = null,
    )
}
