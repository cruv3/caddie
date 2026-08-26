package com.caddie.tool.mcp.client

import com.caddie.tool.mcp.config.McpCredentialProvider
import com.caddie.tool.mcp.config.McpServerConfiguration
import java.io.ByteArrayOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer

class KotlinSdkMcpConnectionTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `initialize session authorization and cursor are preserved`() = runBlocking {
        val requests = CopyOnWriteArrayList<RecordedRequest>()
        var credentialReads = 0
        server.dispatcher =
            rpcDispatcher(requests) { method, request ->
                when (method) {
                    "tools/list" -> {
                        val cursor =
                            request.optJSONObject("params")
                                ?.optString("cursor")
                                ?.takeIf { it.isNotEmpty() }
                        if (cursor == null) {
                            ok(
                                request,
                                """{"tools":[{"name":"alpha","description":"Alpha","inputSchema":{"type":"object"}}],"nextCursor":"page-2"}""",
                            )
                        } else {
                            ok(request, """{"tools":[]}""")
                        }
                    }
                    else -> error("Unexpected method: $method")
                }
            }
        val connection =
            KotlinSdkMcpConnectionFactory().connect(
                configuration(
                    credentialProvider =
                        McpCredentialProvider {
                            credentialReads += 1
                            "Bearer test-token"
                        },
                ),
            )

        val first = connection.listTools(null)
        val second = connection.listTools(first.nextCursor)

        assertEquals("alpha", first.tools.single().name)
        assertEquals("page-2", first.nextCursor)
        assertTrue(second.tools.isEmpty())
        assertEquals(1, credentialReads)
        assertTrue(requests.any { bodyMethod(it) == "initialize" })
        assertTrue(requests.any { bodyMethod(it) == "notifications/initialized" })
        assertTrue(
            requests
                .filter { bodyMethod(it) == "tools/list" }
                .all { it.getHeader("mcp-session-id") == SESSION_ID },
        )
        assertTrue(
            requests
                .filter { bodyMethod(it) == "tools/list" }
                .all { it.getHeader("mcp-protocol-version") == "2025-06-18" },
        )
        assertTrue(requests.all { it.getHeader("Authorization") == "Bearer test-token" })
        val listBodies = requests.filter { bodyMethod(it) == "tools/list" }.map(::bodyJson)
        assertEquals("page-2", listBodies[1].getJSONObject("params").getString("cursor"))
        connection.close()
    }

    @Test
    fun `tool call keeps exact name and arguments while omitting binary payload`() = runBlocking {
        val requests = CopyOnWriteArrayList<RecordedRequest>()
        server.dispatcher =
            rpcDispatcher(requests) { method, request ->
                check(method == "tools/call")
                ok(
                    request,
                    """
                    {
                      "content":[
                        {"type":"text","text":"done"},
                        {"type":"image","mimeType":"image/png","data":"SECRETBASE64"}
                      ],
                      "structuredContent":{"ok":true},
                      "isError":true
                    }
                    """.trimIndent(),
                )
            }
        val connection = KotlinSdkMcpConnectionFactory().connect(configuration())

        val result =
            connection.callTool(
                name = "create_event",
                argumentsJson = """{"title":"Review"}""",
            )

        val call = requests.single { bodyMethod(it) == "tools/call" }.let(::bodyJson)
        assertEquals("create_event", call.getJSONObject("params").getString("name"))
        assertEquals("Review", call.getJSONObject("params").getJSONObject("arguments").getString("title"))
        assertTrue(result.isError)
        assertTrue(result.contentJson.contains("\"done\""))
        assertTrue(result.contentJson.contains("\"image/png\""))
        assertTrue(result.contentJson.contains("\"structuredContent\""))
        assertFalse(result.contentJson.contains("SECRETBASE64"))
        connection.close()
    }

    @Test
    fun `authentication failure maps to a safe category`() = runBlocking {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) =
                    MockResponse().setResponseCode(401).setBody("private upstream message")
            }

        val failure =
            captureFailure {
                KotlinSdkMcpConnectionFactory().connect(configuration())
            }

        assertTrue(failure is McpClientException)
        assertEquals(McpFailureKind.AUTHENTICATION, (failure as McpClientException).kind)
        assertFalse(failure.message.orEmpty().contains("private upstream message"))
    }

    @Test
    fun `invalid session makes later discovery unavailable`() = runBlocking {
        val requests = CopyOnWriteArrayList<RecordedRequest>()
        server.dispatcher =
            rpcDispatcher(requests) { method, _ ->
                check(method == "tools/list")
                MockResponse().setResponseCode(404).setBody("expired session details")
            }
        val connection = KotlinSdkMcpConnectionFactory().connect(configuration())

        val failure = captureFailure { connection.listTools(null) }

        assertTrue(failure is McpClientException)
        assertEquals(McpFailureKind.SESSION, (failure as McpClientException).kind)
        assertFalse(failure.message.orEmpty().contains("expired session details"))
        connection.close()
    }

    @Test
    fun `close is idempotent`() = runBlocking {
        server.dispatcher =
            rpcDispatcher(CopyOnWriteArrayList()) { method, _ ->
                error("Unexpected method after initialization: $method")
            }
        val connection = KotlinSdkMcpConnectionFactory().connect(configuration())

        connection.close()
        connection.close()
    }

    @Test
    fun `compressed initialization still preserves negotiated protocol version`() = runBlocking {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val json = bodyJson(request)
                    return when (json.getString("method")) {
                        "initialize" ->
                            compressedOk(
                                json,
                                """
                                {
                                  "protocolVersion":"2025-06-18",
                                  "capabilities":{"tools":{}},
                                  "serverInfo":{"name":"test-server","version":"1.0"}
                                }
                                """.trimIndent(),
                            ).setHeader("mcp-session-id", SESSION_ID)
                        "notifications/initialized" -> MockResponse().setResponseCode(200)
                        else -> error("Unexpected request")
                    }
                }
            }

        val connection = KotlinSdkMcpConnectionFactory().connect(configuration())

        connection.close()
    }

    @Test
    fun `method not found maps to unavailable without retry`() = runBlocking {
        val requests = CopyOnWriteArrayList<RecordedRequest>()
        server.dispatcher =
            rpcDispatcher(requests) { method, request ->
                check(method == "tools/call")
                rpcError(request, -32601, "private missing tool details")
            }
        val connection = KotlinSdkMcpConnectionFactory().connect(configuration())

        val failure = captureFailure { connection.callTool("missing", "{}") }

        assertTrue(failure is McpClientException)
        assertEquals(McpFailureKind.TOOL_UNAVAILABLE, (failure as McpClientException).kind)
        assertFalse(failure.message.orEmpty().contains("private missing tool details"))
        assertEquals(1, requests.count { bodyMethod(it) == "tools/call" })
        connection.close()
    }

    @Test
    fun `server error leaves tool outcome unknown without retry`() = runBlocking {
        val requests = CopyOnWriteArrayList<RecordedRequest>()
        server.dispatcher =
            rpcDispatcher(requests) { method, request ->
                check(method == "tools/call")
                rpcError(request, -32603, "private server details")
            }
        val connection = KotlinSdkMcpConnectionFactory().connect(configuration())

        val failure = captureFailure { connection.callTool("change_state", "{}") }

        assertTrue(failure is McpClientException)
        assertEquals(McpFailureKind.TOOL_OUTCOME_UNKNOWN, (failure as McpClientException).kind)
        assertEquals(1, requests.count { bodyMethod(it) == "tools/call" })
        connection.close()
    }

    @Test
    fun `cancelling a tool call cancels the in-flight request`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        server.dispatcher =
            rpcDispatcher(CopyOnWriteArrayList()) { method, _ ->
                check(method == "tools/call")
                started.complete(Unit)
                MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
            }
        val connection = KotlinSdkMcpConnectionFactory().connect(configuration())
        val call =
            launch {
                connection.callTool("slow", "{}")
            }
        started.await()

        call.cancelAndJoin()

        assertTrue(call.isCancelled)
        connection.close()
    }

    private fun configuration(
        credentialProvider: McpCredentialProvider = McpCredentialProvider.None,
    ) = McpServerConfiguration(
        serverId = "server",
        endpoint = server.url("/mcp").toString(),
        credentialProvider = credentialProvider,
    )

    private fun rpcDispatcher(
        requests: CopyOnWriteArrayList<RecordedRequest>,
        handle: (String, JSONObject) -> MockResponse,
    ) = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            requests += request
            val json = bodyJson(request)
            return when (val method = json.getString("method")) {
                "initialize" ->
                    ok(
                        json,
                        """
                        {
                          "protocolVersion":"2025-06-18",
                          "capabilities":{"tools":{}},
                          "serverInfo":{"name":"test-server","version":"1.0"}
                        }
                        """.trimIndent(),
                    ).setHeader("mcp-session-id", SESSION_ID)
                "notifications/initialized" -> MockResponse().setResponseCode(200)
                else -> handle(method, json)
            }
        }
    }

    private fun ok(
        request: JSONObject,
        resultJson: String,
    ) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(
            JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", request.get("id"))
                .put("result", JSONObject(resultJson))
                .toString(),
        )

    private fun rpcError(
        request: JSONObject,
        code: Int,
        message: String,
    ) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(
            JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", request.get("id"))
                .put(
                    "error",
                    JSONObject()
                        .put("code", code)
                        .put("message", message),
                ).toString(),
        )

    private fun compressedOk(
        request: JSONObject,
        resultJson: String,
    ): MockResponse {
        val bytes =
            ByteArrayOutputStream().use { output ->
                GZIPOutputStream(output).use { gzip ->
                    gzip.write(rpcBody(request, resultJson).toByteArray())
                }
                output.toByteArray()
            }
        return MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setHeader("Content-Encoding", "gzip")
            .setBody(Buffer().write(bytes))
    }

    private fun rpcBody(
        request: JSONObject,
        resultJson: String,
    ) = JSONObject()
        .put("jsonrpc", "2.0")
        .put("id", request.get("id"))
        .put("result", JSONObject(resultJson))
        .toString()

    private fun bodyJson(request: RecordedRequest) =
        JSONObject(request.body.clone().readUtf8())

    private fun bodyMethod(request: RecordedRequest) =
        runCatching { bodyJson(request).getString("method") }.getOrNull()

    private suspend fun captureFailure(block: suspend () -> Unit): Throwable? =
        runCatching { block() }.exceptionOrNull()

    private companion object {
        const val SESSION_ID = "session-123"
    }
}
