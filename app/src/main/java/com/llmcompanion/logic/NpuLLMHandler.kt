package com.llmcompanion.logic

import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.llmcompanion.MainActivity
import com.llmcompanion.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NpuLLMHandler : BaseLLMHandler() {
    private var generativeModel: GenerativeModel? = null

    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        Logger.d(this@NpuLLMHandler, "Lade lokales Gemini Nano Modell (via ML Kit)...")

        return@withContext try {
            generativeModel = Generation.getClient()

            if (generativeModel == null) {
                Logger.e(this@NpuLLMHandler, "Modell-Client ist null. Initialisierung abgebrochen.")
                false
            } else {
                generativeModel?.warmup()
                isInit = true

                Logger.d(this@NpuLLMHandler, "Gemini Nano erfolgreich geladen und aufgewärmt!")
                true
            }
        } catch (e: Exception) {
            Logger.e(this@NpuLLMHandler, "Fehler beim Laden des lokalen LLMs", e)
            false
        }
    }

    override suspend fun askModel(uiDescription: String, task: String, lastActionResult: String): String = withContext(Dispatchers.IO) {
        val model = generativeModel ?: return@withContext "Modell nicht initialisiert."

        val prompt = buildPrompt(task, lastActionResult)

        return@withContext try {
            Logger.d(this@NpuLLMHandler, "Sende Anfrage an lokale NPU...")

            val response = model.generateContent(prompt)
            val answer = response.candidates.firstOrNull()?.text ?: "Keine Antwort erhalten."

            Logger.i(this@NpuLLMHandler, "Antwort vom LLM: $answer")
            answer
        } catch (e: Exception) {
            Logger.e(this@NpuLLMHandler, "Fehler bei der Inference. Evtl. ist das Modell noch nicht fertig heruntergeladen.", e)
            "Fehler: ${e.message}"
        }
    }
}