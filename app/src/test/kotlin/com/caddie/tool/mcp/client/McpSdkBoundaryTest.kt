package com.caddie.tool.mcp.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import org.junit.Assert.assertNotNull
import org.junit.Test

class McpSdkBoundaryTest {
    @Test
    fun `official client and streamable http transport construct on the app classpath`() {
        val httpClient = HttpClient(OkHttp) { install(SSE) }
        val client =
            Client(
                clientInfo =
                    Implementation(
                        name = "caddie-android",
                        version = "1.0",
                    ),
            )
        val transport =
            StreamableHttpClientTransport(
                client = httpClient,
                url = "http://127.0.0.1/mcp",
            )

        assertNotNull(client)
        assertNotNull(transport)
        httpClient.close()
    }
}
