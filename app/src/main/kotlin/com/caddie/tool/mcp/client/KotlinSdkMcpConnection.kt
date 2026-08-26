package com.caddie.tool.mcp.client

import com.caddie.tool.mcp.config.McpServerConfiguration
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpError
import io.modelcontextprotocol.kotlin.sdk.types.AudioContent
import io.modelcontextprotocol.kotlin.sdk.types.BlobResourceContents
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.EmbeddedResource
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsRequest
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.PaginatedRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.ResourceLink
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import io.modelcontextprotocol.kotlin.sdk.types.ToolListChangedNotification
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Interceptor

/** Categorizes MCP failures without exposing transport or server details. */
enum class McpFailureKind {
    AUTHENTICATION,
    CONNECTION,
    SESSION,
    PROTOCOL,
    DISCOVERY,
    TOOL_UNAVAILABLE,
    TOOL_OUTCOME_UNKNOWN,
}

/** Exposes one safe MCP failure category to the Android runtime. */
class McpClientException(
    val kind: McpFailureKind,
    message: String,
) : IllegalStateException(message)

/** Opens official Kotlin SDK clients behind Caddie's narrow MCP connection port. */
class KotlinSdkMcpConnectionFactory : McpConnectionFactory {
    override suspend fun connect(
        configuration: McpServerConfiguration,
    ): McpConnection {
        val authorization =
            try {
                configuration.credentialProvider.authorizationHeader()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                throw McpClientException(
                    McpFailureKind.AUTHENTICATION,
                    "MCP credentials are unavailable",
                )
            }
        val negotiatedProtocolVersion = AtomicReference<String?>()
        val httpClient =
            HttpClient(OkHttp) {
                install(SSE)
                install(HttpTimeout) {
                    connectTimeoutMillis = configuration.connectTimeoutMillis
                    requestTimeoutMillis = configuration.requestTimeoutMillis
                    socketTimeoutMillis = configuration.requestTimeoutMillis
                }
                engine {
                    addInterceptor(
                        Interceptor { chain ->
                            val response = chain.proceed(chain.request())
                            response
                                .peekBody(MAX_INITIALIZE_RESPONSE_BYTES)
                                .string()
                                .negotiatedProtocolVersion()
                                ?.let { negotiatedProtocolVersion.compareAndSet(null, it) }
                            response
                        },
                    )
                }
            }
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
                url = configuration.endpoint,
                requestBuilder = {
                    authorization?.let {
                        header(HttpHeaders.Authorization, it)
                    }
                },
            )

        try {
            withTimeout(configuration.connectTimeoutMillis) {
                client.connect(transport)
            }
            transport.protocolVersion =
                negotiatedProtocolVersion.get()
                    ?: throw McpClientException(
                        McpFailureKind.PROTOCOL,
                        "MCP initialization omitted the protocol version",
                    )
            return KotlinSdkMcpConnection(
                client = client,
                httpClient = httpClient,
                requestTimeoutMillis = configuration.requestTimeoutMillis,
            )
        } catch (error: Throwable) {
            runCatching { client.close() }
            httpClient.close()
            throw mapFailure(error, Operation.CONNECT)
        }
    }

    private fun String.negotiatedProtocolVersion(): String? =
        runCatching {
            JSON
                .parseToJsonElement(this)
                .jsonObject["result"]
                ?.jsonObject
                ?.get("protocolVersion")
                ?.jsonPrimitive
                ?.contentOrNull
        }.getOrNull()

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
        const val MAX_INITIALIZE_RESPONSE_BYTES = 64L * 1024L
    }
}

/** Maps official Kotlin SDK discovery and calls to Caddie's SDK-free values. */
class KotlinSdkMcpConnection internal constructor(
    private val client: Client,
    private val httpClient: HttpClient,
    private val requestTimeoutMillis: Long,
) : McpConnection {
    private val closed = AtomicBoolean(false)
    private val toolsListChangedHandler = AtomicReference<() -> Unit>({})

    init {
        client.setNotificationHandler<ToolListChangedNotification>(
            Method.Defined.NotificationsToolsListChanged,
        ) {
            toolsListChangedHandler.get().invoke()
            CompletableDeferred(Unit)
        }
    }

    override suspend fun listTools(cursor: String?): McpToolPage =
        sdkRequest(Operation.DISCOVER) {
            val result =
                client.listTools(
                    ListToolsRequest(
                        params =
                            cursor?.let {
                                PaginatedRequestParams(cursor = it)
                            },
                    ),
                )
            McpToolPage(
                tools =
                    result.tools.map { tool ->
                        McpRemoteTool(
                            name = tool.name,
                            description = tool.description.orEmpty(),
                            inputSchemaJson = McpJson.encodeToString(tool.inputSchema),
                        )
                    },
                nextCursor = result.nextCursor,
            )
        }

    override suspend fun callTool(
        name: String,
        argumentsJson: String,
    ): McpCallResult {
        val arguments =
            try {
                JSON.parseToJsonElement(argumentsJson).jsonObject
            } catch (error: Exception) {
                throw IllegalArgumentException(
                    "MCP tool arguments must be one JSON object",
                    error,
                )
            }
        return sdkRequest(Operation.CALL) {
            val result =
                client.callTool(
                    CallToolRequest(
                        CallToolRequestParams(
                            name = name,
                            arguments = arguments,
                        ),
                    ),
                )
            McpCallResult(
                contentJson = contentJson(result),
                isError = result.isError == true,
            )
        }
    }

    override fun setToolsListChangedHandler(handler: () -> Unit) {
        toolsListChangedHandler.set(handler)
    }

    override suspend fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        toolsListChangedHandler.set {}
        try {
            client.close()
        } finally {
            httpClient.close()
        }
    }

    private suspend fun <T> sdkRequest(
        operation: Operation,
        block: suspend () -> T,
    ): T {
        check(!closed.get()) { "MCP connection is closed" }
        try {
            return withTimeout(requestTimeoutMillis) {
                block()
            }
        } catch (error: Throwable) {
            throw mapFailure(error, operation)
        }
    }

    private fun contentJson(
        result: io.modelcontextprotocol.kotlin.sdk.types.CallToolResult,
    ): String =
        buildJsonArray {
            result.content.take(MAX_CONTENT_BLOCKS).forEach { content ->
                when (content) {
                    is TextContent ->
                        add(
                            buildJsonObject {
                                put("type", "text")
                                put("text", content.text.take(MAX_TEXT_CHARS))
                            },
                        )
                    is ImageContent ->
                        add(
                            buildJsonObject {
                                put("type", "image")
                                put("mimeType", content.mimeType)
                                put("encodedChars", content.data.length)
                            },
                        )
                    is AudioContent ->
                        add(
                            buildJsonObject {
                                put("type", "audio")
                                put("mimeType", content.mimeType)
                                put("encodedChars", content.data.length)
                            },
                        )
                    is ResourceLink ->
                        add(
                            buildJsonObject {
                                put("type", "resource_link")
                                put("name", content.name)
                                put("uri", content.uri)
                                content.mimeType?.let { put("mimeType", it) }
                            },
                        )
                    is EmbeddedResource ->
                        add(
                            buildJsonObject {
                                put("type", "resource")
                                put("uri", content.resource.uri)
                                content.resource.mimeType?.let { put("mimeType", it) }
                                put(
                                    "contentKind",
                                    when (content.resource) {
                                        is TextResourceContents -> "text"
                                        is BlobResourceContents -> "binary"
                                        else -> "unknown"
                                    },
                                )
                            },
                        )
                }
            }
            result.structuredContent?.let {
                add(
                    buildJsonObject {
                        put("type", "structuredContent")
                        val encoded = it.toString()
                        if (encoded.length <= MAX_STRUCTURED_CHARS) {
                            put("value", it)
                        } else {
                            put("truncated", true)
                            put("encodedChars", encoded.length)
                        }
                    },
                )
            }
        }.toString()

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
        const val MAX_CONTENT_BLOCKS = 100
        const val MAX_TEXT_CHARS = 16_384
        const val MAX_STRUCTURED_CHARS = 65_536
    }
}

/** Identifies which MCP operation produced a transport failure. */
private enum class Operation {
    CONNECT,
    DISCOVER,
    CALL,
}

private fun mapFailure(
    error: Throwable,
    operation: Operation,
): Throwable {
    if (error is McpClientException) {
        return error
    }
    if (error is CancellationException && error !is TimeoutCancellationException) {
        return error
    }
    val causes = generateSequence(error) { it.cause }.take(8).toList()
    val httpError = causes.filterIsInstance<StreamableHttpError>().firstOrNull()
    val mcpError = causes.filterIsInstance<McpException>().firstOrNull()
    val kind =
        when {
            httpError?.code in setOf(401, 403) ->
                McpFailureKind.AUTHENTICATION
            httpError?.code in setOf(404, 410) ->
                McpFailureKind.SESSION
            operation == Operation.CALL &&
                mcpError?.code == RPCError.ErrorCode.METHOD_NOT_FOUND ->
                McpFailureKind.TOOL_UNAVAILABLE
            operation == Operation.CALL ->
                McpFailureKind.TOOL_OUTCOME_UNKNOWN
            causes.any { it is SerializationException } ->
                McpFailureKind.PROTOCOL
            mcpError?.code == RPCError.ErrorCode.CONNECTION_CLOSED ->
                McpFailureKind.CONNECTION
            causes.any { it is IOException || it is TimeoutCancellationException } ->
                McpFailureKind.CONNECTION
            operation == Operation.DISCOVER ->
                McpFailureKind.DISCOVERY
            else ->
                McpFailureKind.PROTOCOL
        }
    return McpClientException(kind, safeMessage(kind))
}

private fun safeMessage(kind: McpFailureKind): String =
    when (kind) {
        McpFailureKind.AUTHENTICATION -> "MCP authentication failed"
        McpFailureKind.CONNECTION -> "MCP connection failed"
        McpFailureKind.SESSION -> "MCP session is unavailable"
        McpFailureKind.PROTOCOL -> "MCP protocol failed"
        McpFailureKind.DISCOVERY -> "MCP tool discovery failed"
        McpFailureKind.TOOL_UNAVAILABLE -> "MCP tool is unavailable"
        McpFailureKind.TOOL_OUTCOME_UNKNOWN -> "MCP tool outcome is unknown"
    }
