package com.caddie.model.gateway

import com.caddie.agent.core.AgentMessage
import com.caddie.agent.core.ModelDelta
import com.caddie.agent.core.ModelRequest
import com.caddie.agent.core.ModelUnavailableException
import com.caddie.agent.core.RunId
import com.caddie.agent.core.ToolCallId
import com.caddie.agent.core.ToolDefinition
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GatewayModelClientTest {
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
    fun `flow is cold and streams text tool call and completion`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setHeader(
                        "Content-Type",
                        "text/event-stream",
                    ).setBody(
                        """
                        data: {"choices":[{"delta":{"content":"Working"},"finish_reason":null}]}

                        data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call-1","function":{"name":"phone.open","arguments":"{}"}}]},"finish_reason":"tool_calls"}]}

                        data: [DONE]

                        """.trimIndent(),
                    ),
            )
            val client = client()
            val flow = client.stream(request())

            assertEquals(0, server.requestCount)

            val deltas = flow.toList()

            assertEquals(
                listOf(
                    ModelDelta.Text("Working"),
                    ModelDelta.ToolCall(
                        ToolCallId("call-1"),
                        "phone.open",
                        "{}",
                    ),
                    ModelDelta.Completed,
                ),
                deltas,
            )
            val recorded = server.takeRequest()
            assertEquals("POST", recorded.method)
            assertEquals(
                "/v1/chat/completions",
                recorded.path,
            )
            assertEquals(
                "text/event-stream",
                recorded.getHeader("Accept"),
            )
            assertNull(recorded.getHeader("Authorization"))
            val body =
                Json
                    .parseToJsonElement(
                        recorded.body.readUtf8(),
                    ).jsonObject
            assertEquals(
                "test-tool-model",
                body.getValue("model").jsonPrimitive.content,
            )
        }

    @Test
    fun `retryable failure before stream data retries with fresh credential`() =
        runTest {
            server.enqueue(
                MockResponse().setResponseCode(503),
            )
            server.enqueue(
                MockResponse()
                    .setHeader(
                        "Content-Type",
                        "text/event-stream",
                    ).setBody("data: [DONE]\n\n"),
            )
            var credentialReads = 0
            val client =
                client(
                    credentialProvider =
                        GatewayCredentialProvider {
                            credentialReads += 1
                            "Bearer token-$credentialReads"
                        },
                    retryPolicy =
                        RetryPolicy(
                            maxAttempts = 2,
                            initialBackoffMillis = 0,
                        ),
                )

            assertEquals(
                listOf(ModelDelta.Completed),
                client.stream(request()).toList(),
            )
            assertEquals(2, server.requestCount)
            assertEquals(2, credentialReads)
            assertEquals(
                "Bearer token-1",
                server
                    .takeRequest()
                    .getHeader("Authorization"),
            )
            assertEquals(
                "Bearer token-2",
                server
                    .takeRequest()
                    .getHeader("Authorization"),
            )
        }

    @Test
    fun `failure after accepted data is never retried`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setHeader(
                        "Content-Type",
                        "text/event-stream",
                    ).setBody(
                        """data: {"choices":[{"delta":{"content":"partial"},"finish_reason":null}]}""" +
                            "\n\n",
                    ),
            )
            val error =
                runCatching {
                    client(
                        retryPolicy =
                            RetryPolicy(
                                maxAttempts = 2,
                                initialBackoffMillis = 0,
                            ),
                    ).stream(request()).toList()
                }.exceptionOrNull()

            assertTrue(error is ModelUnavailableException)
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `cancellation closes active call and remains cancellation`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setSocketPolicy(
                        SocketPolicy.NO_RESPONSE,
                    ),
            )
            val job =
                launch {
                    client().stream(request()).toList()
                }
            while (server.requestCount == 0) {
                yield()
            }

            job.cancel()
            job.join()

            assertTrue(job.isCancelled)
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `authentication error exposes no response or credential`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(401)
                    .setBody(
                        "participant text and Bearer secret",
                    ),
            )
            val error =
                runCatching {
                    client(
                        credentialProvider =
                            GatewayCredentialProvider {
                                "Bearer secret"
                            },
                    ).stream(request()).toList()
                }.exceptionOrNull()

            val message = error?.message.orEmpty()
            assertTrue(
                message.contains("authentication_failed"),
            )
            assertFalse(message.contains("participant text"))
            assertFalse(message.contains("secret"))
        }

    @Test
    fun `invalid local tool schema maps to minimized model failure`() =
        runTest {
            val invalidRequest =
                request().copy(
                    tools =
                        listOf(
                            ToolDefinition(
                                "broken",
                                "",
                                "[]",
                            ),
                        ),
                )

            val error =
                runCatching {
                    client()
                        .stream(invalidRequest)
                        .toList()
                }.exceptionOrNull()

            assertTrue(error is ModelUnavailableException)
            assertTrue(
                error
                    ?.message
                    .orEmpty()
                    .contains("invalid_gateway_protocol"),
            )
            assertEquals(0, server.requestCount)
        }

    private fun client(
        credentialProvider: GatewayCredentialProvider =
            GatewayCredentialProvider.None,
        retryPolicy: RetryPolicy =
            RetryPolicy(maxAttempts = 1),
    ) = GatewayModelClient(
        GatewayConfiguration(
            baseUrl = server.url("/").toString(),
            profile =
                ModelProfile(
                    "normal",
                    "test-tool-model",
                ),
            retryPolicy = retryPolicy,
            credentialProvider = credentialProvider,
        ),
    )

    private fun request() =
        ModelRequest(
            RunId("run-1"),
            listOf(
                AgentMessage(
                    AgentMessage.Role.USER,
                    "Synthetic request",
                ),
            ),
            listOf(
                ToolDefinition(
                    "phone.open",
                    "Open",
                    """{"type":"object"}""",
                ),
            ),
        )
}
