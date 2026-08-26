package com.caddie.status

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import com.caddie.app.gateway.GatewayRuntimeSettings
import com.caddie.app.gateway.GatewayRuntimeSettingsSource
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayHealthCheckerTest {
    @Test
    fun checkClosesTheHttpResponse() {
        val closed = AtomicBoolean(false)
        val receivedUrl = AtomicReference<String>()
        val body = object : ResponseBody() {
            private val responseSource = object : ForwardingSource(Buffer().writeUtf8("{}")) {
                override fun close() {
                    closed.set(true)
                    super.close()
                }
            }.buffer()

            override fun contentType() = "application/json".toMediaType()
            override fun contentLength() = 2L
            override fun source(): BufferedSource = responseSource
        }
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                receivedUrl.set(chain.request().url.toString())
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body)
                    .build()
            }
            .build()
        val checker = GatewayHealthChecker(
            settingsSource = source("http://localhost/openai", "test-model"),
            client = client,
        )

        checker.check()

        assertTrue(closed.get())
        assertEquals("http://localhost/openai/v1/models", receivedUrl.get())
    }

    @Test
    fun `unconfigured endpoint is reported as disconnected without an http call`() {
        val checker = GatewayHealthChecker(source("", ""))

        org.junit.Assert.assertEquals(GatewayConnectionStatus.Disconnected, checker.check())
    }

    @Test
    fun `unexpected request failure is reported as disconnected`() {
        val client = OkHttpClient.Builder()
            .addInterceptor { throw IllegalArgumentException("unexpected test failure") }
            .build()
        val checker = GatewayHealthChecker(
            settingsSource = source("https://gateway.example", "test-model"),
            client = client,
        )

        org.junit.Assert.assertEquals(GatewayConnectionStatus.Disconnected, checker.check())
    }

    @Test
    fun `configuration failure is reported as disconnected`() {
        val source = object : GatewayRuntimeSettingsSource {
            override fun snapshot() = GatewayRuntimeSettings()
            override fun authorizationHeader(): String? = null
            override fun gatewayConfiguration() =
                throw IllegalStateException("unexpected settings failure")
        }

        org.junit.Assert.assertEquals(
            GatewayConnectionStatus.Disconnected,
            GatewayHealthChecker(source).check(),
        )
    }

    private fun source(url: String, model: String) = object : GatewayRuntimeSettingsSource {
        override fun snapshot() = GatewayRuntimeSettings(baseUrl = url, modelId = model)
        override fun authorizationHeader(): String? = null
    }
}
