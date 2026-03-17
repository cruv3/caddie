package com.llmcompanion.logic

import com.llmcompanion.config.AppConfig
import com.llmcompanion.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class OpenAILLMHandler: BaseLLMHandler() {
    val openaiUrl = "https://api.openai.com/v1/chat/completions"
    val openAiKey = AppConfig.OPENAI_KEY

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        Logger.d(this@OpenAILLMHandler, "Prüfe OpenAI API Key...")
        if (openAiKey.isBlank()) {
            Logger.e(this@OpenAILLMHandler, "API Key fehlt!")
            return@withContext false
        }
        isInit = true
        return@withContext true
    }

    override suspend fun askModel(uiDescription: String, task: String, lastActionResult: String): String = withContext(Dispatchers.IO) {
        if (!isInit) return@withContext "Fehler: Nicht initialisiert."

        val promptText = buildPrompt(task, lastActionResult)

        val jsonBody = JSONObject().apply {
            put("model", "gpt-4o-mini")

            val messagesArray = org.json.JSONArray()
            val messageObj = JSONObject().apply {
                put("role", "user")
                put("content", promptText)
            }
            messagesArray.put(messageObj)
            put("messages", messagesArray)

            put("temperature", 0.2)
        }.toString()

        val request = Request.Builder()
            .url(openaiUrl)
            .addHeader("Authorization", "Bearer ${openAiKey}")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        return@withContext try {
            Logger.d(this@OpenAILLMHandler, "Sende Request an OpenAI...")
            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body.string()

                val jsonResponse = JSONObject(responseBody ?: "")
                val choices = jsonResponse.getJSONArray("choices")
                val answer = choices.getJSONObject(0).getJSONObject("message").getString("content")

                Logger.i(this@OpenAILLMHandler, "Antwort von ChatGPT: $answer")
                answer
            } else {
                Logger.e(this@OpenAILLMHandler, "HTTP Fehler: ${response.code}")
                "HTTP Fehler: ${response.message}"
            }
        } catch (e: Exception) {
            Logger.e(this@OpenAILLMHandler, "Netzwerkfehler", e)
            "Fehler: ${e.message}"
        }
    }
}