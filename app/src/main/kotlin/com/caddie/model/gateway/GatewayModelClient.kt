package com.caddie.model.gateway

import android.util.Log
import com.caddie.agent.core.ModelClient
import com.caddie.agent.core.ModelDelta
import com.caddie.agent.core.ModelRequest
import com.caddie.agent.core.ModelUnavailableException
import com.caddie.model.openai.ChatCompletionCodec
import com.caddie.model.openai.ChatCompletionStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Streams model responses from an OpenAI-compatible homeserver gateway. */
class GatewayModelClient(
    private val configuration: GatewayConfiguration,
    client: OkHttpClient = OkHttpClient(),
    private val ioContext: CoroutineContext = Dispatchers.IO,
) : ModelClient {
    private val codec = ChatCompletionCodec()
    private val client =
        client
            .newBuilder()
            .connectTimeout(
                configuration.connectTimeoutMillis,
                TimeUnit.MILLISECONDS,
            ).writeTimeout(
                configuration.writeTimeoutMillis,
                TimeUnit.MILLISECONDS,
            ).readTimeout(
                configuration.streamIdleTimeoutMillis,
                TimeUnit.MILLISECONDS,
            ).build()

    override fun stream(
        request: ModelRequest,
    ): Flow<ModelDelta> =
        flow {
            val body =
                try {
                    codec.encodeRequest(
                        request,
                        configuration.profile.modelId,
                        configuration.thinkingEnabled,
                    )
                } catch (
                    failure: GatewayFailureException,
                ) {
                    throw failure.asModelUnavailable()
                }
            var failedAttempt = 0
            while (true) {
                var acceptedData = false
                try {
                    executeAttempt(
                        body = body,
                        onDataAccepted = {
                            acceptedData = true
                        },
                        emitDelta = { delta ->
                            emit(delta)
                        },
                    )
                    return@flow
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (
                    failure: GatewayFailureException,
                ) {
                    failedAttempt += 1
                    val canRetry =
                        failure.failure.retryable &&
                            !acceptedData &&
                            failedAttempt <
                            configuration
                                .retryPolicy
                                .maxAttempts
                    if (!canRetry) {
                        throw failure.asModelUnavailable()
                    }
                    delay(
                        configuration
                            .retryPolicy
                            .backoffMillisAfter(
                                failedAttempt,
                            ),
                    )
                }
            }
        }

    private suspend fun executeAttempt(
        body: String,
        onDataAccepted: () -> Unit,
        emitDelta: suspend (ModelDelta) -> Unit,
    ): Unit = coroutineScope {
        val startedAt = monotonicMillis()
        logTiming("request started model=${configuration.profile.modelId}")
        val requestBuilder =
            Request
                .Builder()
                .url(configuration.chatCompletionsUrl)
                .header("Accept", "text/event-stream")
                .post(
                    body.toRequestBody(
                        "application/json; charset=utf-8"
                            .toMediaType(),
                    ),
                )
        configuration.credentialProvider
            .authorizationHeader()
            ?.takeIf(String::isNotBlank)
            ?.let {
                requestBuilder.header(
                    "Authorization",
                    it,
                )
            }
        val call = client.newCall(requestBuilder.build())
        val response = call.awaitResponse()
        // Socket reads need the call closed; interrupting the IO thread is insufficient.
        val cancellationWatcher = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                call.cancel()
            }
        }
        logTiming(
            "response headers model=${configuration.profile.modelId} " +
                "afterMs=${monotonicMillis() - startedAt}",
        )
        try {
            if (!response.isSuccessful) {
                throw httpFailure(response.code)
            }
            val mediaType = response.body?.contentType()
            if (
                mediaType == null ||
                mediaType.type != "text" ||
                mediaType.subtype != "event-stream"
            ) {
                throw GatewayFailureException(
                    GatewayFailure(
                        GatewayFailureKind.MEDIA_TYPE,
                        "gateway response must be " +
                            "text/event-stream",
                        retryable = false,
                    ),
                )
            }
            val source =
                response.body?.source()
                    ?: throw GatewayFailureException(
                        GatewayFailure(
                            GatewayFailureKind.PROTOCOL,
                            "gateway response body is missing",
                            retryable = false,
                        ),
                    )
            val assembler =
                ChatCompletionStream(codec)
            var firstDataSeen = false
            while (true) {
                val line =
                    runInterruptible(ioContext) {
                        source.readUtf8Line()
                    } ?: break
                if (line.startsWith("data:")) {
                    if (!firstDataSeen) {
                        firstDataSeen = true
                        logTiming(
                            "first stream data model=${configuration.profile.modelId} " +
                                "afterMs=${monotonicMillis() - startedAt}",
                        )
                    }
                    onDataAccepted()
                }
                assembler
                    .acceptLine(line)
                    .forEach { emitDelta(it) }
            }
            assembler
                .endOfInput()
                .forEach { emitDelta(it) }
            logTiming(
                "stream completed model=${configuration.profile.modelId} " +
                    "afterMs=${monotonicMillis() - startedAt}",
            )
        } catch (cancellation: CancellationException) {
            call.cancel()
            throw cancellation
        } catch (failure: GatewayFailureException) {
            throw failure
        } catch (error: IOException) {
            currentCoroutineContext().ensureActive()
            throw GatewayFailureException(
                GatewayFailure(
                    GatewayFailureKind.STREAM,
                    "model stream read failed",
                    retryable = true,
                ),
                error,
            )
        } finally {
            cancellationWatcher.cancel()
            response.close()
            call.cancel()
        }
    }

    private suspend fun Call.awaitResponse(): Response =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation {
                cancel()
            }
            enqueue(
                object : Callback {
                    override fun onFailure(
                        call: Call,
                        e: IOException,
                    ) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                GatewayFailureException(
                                    GatewayFailure(
                                        GatewayFailureKind.NETWORK,
                                        "model gateway is unavailable",
                                        retryable = true,
                                    ),
                                    e,
                                ),
                            )
                        }
                    }

                    override fun onResponse(
                        call: Call,
                        response: Response,
                    ) {
                        if (continuation.isActive) {
                            continuation.resume(response)
                        } else {
                            response.close()
                        }
                    }
                },
            )
        }

    private fun httpFailure(
        code: Int,
    ): GatewayFailureException {
        val failure =
            when (code) {
                401, 403 ->
                    GatewayFailure(
                        GatewayFailureKind.AUTHENTICATION,
                        "model gateway rejected authorization",
                        retryable = false,
                    )

                429 ->
                    GatewayFailure(
                        GatewayFailureKind.RATE_LIMITED,
                        "model gateway rate limit reached",
                        retryable = true,
                    )

                in 500..599 ->
                    GatewayFailure(
                        GatewayFailureKind.SERVER,
                        "model gateway server failure",
                        retryable = true,
                    )

                else ->
                    GatewayFailure(
                        GatewayFailureKind.HTTP,
                        "model gateway returned HTTP $code",
                        retryable = false,
                    )
            }
        return GatewayFailureException(failure)
    }

    private fun GatewayFailureException
        .asModelUnavailable(): ModelUnavailableException =
        ModelUnavailableException(
            "${failure.kind.code}: " +
                failure.safeMessage,
            this,
        )

    private fun monotonicMillis(): Long = System.nanoTime() / 1_000_000L

    private fun logTiming(message: String) {
        runCatching { Log.i(TAG, message) }
    }

    private companion object {
        const val TAG = "ModelGateway"
    }
}
