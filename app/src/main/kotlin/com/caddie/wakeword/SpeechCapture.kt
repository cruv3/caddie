package com.caddie.wakeword

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Captures one utterance with Android's installed speech recognizer in the requested locale. */
class SpeechCapture(private val context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val generation = AtomicLong(0)
    private var recognizer: SpeechRecognizer? = null
    private var timeoutRunnable: Runnable? = null

    fun start(
        locale: String = "de-DE",
        onResult: (String) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        val requestGeneration = generation.incrementAndGet()
        mainHandler.post {
            if (generation.get() == requestGeneration) {
                createAndStart(requestGeneration, locale, onResult, onError)
            }
        }
    }

    fun cleanup() {
        generation.incrementAndGet()
        mainHandler.removeCallbacksAndMessages(null)
        timeoutRunnable?.let(mainHandler::removeCallbacks)
        timeoutRunnable = null
        destroyRecognizer(recognizer)
    }

    private fun destroyRecognizer(active: SpeechRecognizer?) {
        active?.let {
            try {
                it.cancel()
            } catch (_: Throwable) {
            }
            try {
                it.destroy()
            } catch (_: Throwable) {
            }
        }
        if (recognizer === active) recognizer = null
    }

    private fun createAndStart(
        requestGeneration: Long,
        locale: String,
        onResult: (String) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        destroyRecognizer(recognizer)
        if (generation.get() != requestGeneration) return
        val active = try {
            require(SpeechRecognizer.isRecognitionAvailable(context)) {
                "speech_recognition_unavailable"
            }
            SpeechRecognizer.createSpeechRecognizer(context).also {
                recognizer = it
            }
        } catch (error: Throwable) {
            cleanup()
            onError(error)
            return
        }
        val completed = AtomicBoolean(false)
        lateinit var timeout: Runnable

        fun finish(text: String? = null, error: Throwable? = null) {
            if (!completed.compareAndSet(false, true)) return
            mainHandler.removeCallbacks(timeout)
            timeoutRunnable = null
            destroyRecognizer(active)
            if (generation.get() != requestGeneration) return
            if (error != null) onError(error) else onResult(text.orEmpty())
        }

        timeout = Runnable { finish(error = RuntimeException("recognizer_timeout")) }
        timeoutRunnable = timeout
        active.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                if (text.isEmpty()) finish(error = RuntimeException("empty_transcript"))
                else finish(text = text)
            }

            override fun onError(error: Int) {
                Log.w(TAG, "recognizer error: $error")
                finish(error = RuntimeException("recognizer_error_$error"))
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        mainHandler.postDelayed(timeout, RECOGNIZER_TIMEOUT_MS)
        try {
            active.startListening(intent)
        } catch (error: Throwable) {
            finish(error = error)
        }
    }

    companion object {
        private const val TAG = "SpeechCapture"
        private const val RECOGNIZER_TIMEOUT_MS = 12_000L
    }
}
