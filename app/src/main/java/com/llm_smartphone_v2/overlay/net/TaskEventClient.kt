package com.llm_smartphone_v2.overlay.net

import com.llm_smartphone_v2.lmstudio.LmStudioConfig
import com.llm_smartphone_v2.overlay.event.ThoughtEvent
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class TaskEventClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    /**
     * Fire-and-forget POST to /task. The server runs the task and publishes
     * lifecycle + tool events on the EventBus; the phone observes those via
     * [observe]. This call returns once the task is fully done (or fails).
     */
    fun fireTask(task: String, followUp: Boolean = false, onError: (Throwable) -> Unit = {}) {
        val payload = JSONObject().put("task", task).put("follow_up", followUp).toString()
        val body = payload.toRequestBody(JSON_MEDIA)
        val request = Request.Builder()
            .url(taskEndpoint())
            .apply { authHeader()?.let { header("Authorization", it) } }
            .post(body)
            .build()
        try {
            client.newCall(request).execute().use { /* drain + close */ }
        } catch (t: Throwable) {
            onError(t)
        }
    }

    /**
     * Long-lived observer subscription. Mirrors every event published on the
     * server's EventBus to [onEvent], regardless of which client triggered the
     * underlying task. Used by the overlay to react to LM-Studio-driven runs
     * where the phone never POSTed a /task/stream itself.
     */
    fun observe(
        onEvent: (ThoughtEvent) -> Unit,
        onClosed: () -> Unit,
        onFailure: (Throwable) -> Unit,
    ): EventSource {
        val request = Request.Builder()
            .url(observeEndpoint())
            .header("Accept", "text/event-stream")
            .apply { authHeader()?.let { header("Authorization", it) } }
            .get()
            .build()
        val factory = EventSources.createFactory(client)
        return factory.newEventSource(request, object : EventSourceListener() {
            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String,
            ) {
                ThoughtEvent.parse(data)?.let(onEvent)
            }

            override fun onClosed(eventSource: EventSource) {
                onClosed()
            }

            override fun onFailure(
                eventSource: EventSource,
                t: Throwable?,
                response: Response?,
            ) {
                val err = t ?: RuntimeException("HTTP ${response?.code}")
                onFailure(err)
            }
        })
    }

    private fun taskEndpoint(): String = LmStudioConfig.ENDPOINT
    private fun observeEndpoint(): String = LmStudioConfig.BASE_URL + "/events"

    private fun authHeader(): String? {
        val token = LmStudioConfig.API_TOKEN?.trim().orEmpty()
        return if (token.isEmpty()) null else "Bearer $token"
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
