package com.llmcompanion.logic

import com.llmcompanion.config.AppConfig
import com.llmcompanion.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class RemoteNetworkLLMHandler : BaseLLMHandler() {
    private val baseUrl = "http://${AppConfig.PC_IP_ADDRESS}:${AppConfig.REMOTE_PORT}"
    private val chatUrl = "$baseUrl/api/chat"


    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    override suspend fun initialize(): Boolean {
        isInit = true
        return true
    }

    override suspend fun askModel(uiDescription: String, task: String, lastActionResult: String): String = withContext(Dispatchers.IO) {
        if (!isInit) return@withContext "Fehler: Nicht initialisiert."

        if (chatHistory.isEmpty()) {
            val systemPrompt = buildPrompt(task, lastActionResult)
            chatHistory.add(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
        }

        val userTurn = JSONObject().apply {
            put("role", "user")
            put("content", "AKTUELLER BILDSCHIRM:\n$uiDescription")
        }
        chatHistory.add(userTurn)

        val options = JSONObject().apply {
            put("num_ctx", AppConfig.NUM_CTX)
            put("temperature", AppConfig.TEMPERATURE)
            put("num_predict", AppConfig.MAX_TOKENS)
        }

        val jsonBody = JSONObject().apply {
            put("model", AppConfig.REMOTE_MODEL)
            put("messages", JSONArray(chatHistory))
            put("stream", false)
            put("format", "json")
            put("keep_alive", -1)
            put("options", options)
        }.toString()

        val request = Request.Builder()
            .url(chatUrl)
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        return@withContext try {
            val response = client.newCall(request).execute()
            val responseBody = response.body.string() ?: ""
            val jsonResponse = JSONObject(responseBody)

            val assistantMessage = jsonResponse.getJSONObject("message")
            val answer = assistantMessage.getString("content")

            chatHistory.add(JSONObject().apply {
                put("role", "assistant")
                put("content", answer)
            })

            if (chatHistory.size > 10) {
                for (i in 1 until chatHistory.size - 2) {
                    val msg = chatHistory[i]
                    if (msg.getString("role") == "user") {
                        msg.put("content", "[Vorheriger Screen-Inhalt entfernt, um Platz zu sparen]")
                    }
                }
                chatHistory.removeAt(1) // Alter User-Platzhalter
                chatHistory.removeAt(1) // Alte Assistant-Antwort
            }

            answer
        } catch (e: Exception) {
            Logger.e(this@RemoteNetworkLLMHandler, "Fehler im LLM-Chat", e)
            "{\"action\": \"ERROR\", \"reason\": \"${e.message}\"}"
        }
    }
}