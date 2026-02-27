package com.llmcompanion.logic

import android.content.Context
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.llmcompanion.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalLlmHandler(private val context: Context){
    private var generativeModel: GenerativeModel? = null
    var isInit: Boolean = false

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        Logger.d(this@LocalLlmHandler, "Lade lokales Gemini Nano Modell (via ML Kit)...")

        return@withContext try {
            generativeModel = Generation.getClient()

            if (generativeModel == null) {
                Logger.e(this@LocalLlmHandler, "Modell-Client ist null. Initialisierung abgebrochen.")
                false
            } else {
                generativeModel?.warmup()
                isInit = true

                Logger.d(this@LocalLlmHandler, "Gemini Nano erfolgreich geladen und aufgewärmt!")
                true
            }
        } catch (e: Exception) {
            Logger.e(this@LocalLlmHandler, "Fehler beim Laden des lokalen LLMs", e)
            false
        }
    }

    suspend fun askModel(uiDescription: String, task: String): String = withContext(Dispatchers.IO) {
        val model = generativeModel ?: return@withContext "Modell nicht initialisiert."

        val prompt = """
            Du bist ein Smartphone-Agent. 
            Hier ist der Inhalt des aktuellen Bildschirms:
            $uiDescription
            
            Aufgabe: $task
            
            Welchen Button würdest du klicken? Antworte nur mit dem exakten Namen des Buttons.
        """.trimIndent()

        return@withContext try {
            Logger.d(this@LocalLlmHandler, "Sende Anfrage an lokale NPU...")

            val response = model.generateContent(prompt)
            val answer = response.candidates.firstOrNull()?.text ?: "Keine Antwort erhalten."

            Logger.i(this@LocalLlmHandler, "Antwort vom LLM: $answer")
            answer
        } catch (e: Exception) {
            Logger.e(this@LocalLlmHandler, "Fehler bei der Inference. Evtl. ist das Modell noch nicht fertig heruntergeladen.", e)
            "Fehler: ${e.message}"
        }
    }
}