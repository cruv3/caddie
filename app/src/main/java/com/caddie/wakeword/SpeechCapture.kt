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

class SpeechCapture(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null

    fun start(
        locale: String = "de-DE",
        onResult: (String) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError(IllegalStateException("speech_recognition_unavailable"))
            return
        }
        // SpeechRecognizer must be created on main thread
        Handler(Looper.getMainLooper()).post {
            createAndStart(locale, onResult, onError)
        }
    }

    private fun createAndStart(
        locale: String,
        onResult: (String) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        recognizer?.destroy()
        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = sr

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onResults(results: Bundle?) {
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = list?.firstOrNull()?.trim().orEmpty()
                if (text.isNotEmpty()) {
                    onResult(text)
                } else {
                    onError(RuntimeException("empty_transcript"))
                }
                cleanup()
            }

            override fun onError(error: Int) {
                Log.w("SpeechCapture", "recognizer error: $error")
                onError(RuntimeException("recognizer_error_$error"))
                cleanup()
            }
        })
        sr.startListening(intent)
    }

    fun cleanup() {
        recognizer?.let {
            try { it.cancel() } catch (_: Throwable) {}
            try { it.destroy() } catch (_: Throwable) {}
        }
        recognizer = null
    }
}
