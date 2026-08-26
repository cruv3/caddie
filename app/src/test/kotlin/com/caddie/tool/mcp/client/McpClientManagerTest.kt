package com.caddie.tool.mcp.client

import com.caddie.tool.mcp.config.McpServerConfiguration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class McpClientManagerTest {
    @Test
    fun `all pages publish atomically in stable name order`() = runTest {
        val secondPageRequested = CompletableDeferred<Unit>()
        val releaseSecondPage = CompletableDeferred<Unit>()
        val connection =
            FakeConnection { cursor ->
                when (cursor) {
                    null ->
                        McpToolPage(
                            tools = listOf(tool("zebra")),
                            nextCursor = "page-2",
                        )
                    "page-2" -> {
                        secondPageRequested.complete(Unit)
                        releaseSecondPage.await()
                        McpToolPage(
                            tools = listOf(tool("alpha")),
                            nextCursor = null,
                        )
                    }
                    else -> error("Unexpected cursor: $cursor")
                }
            }
        val manager = McpClientManager(queueFactory(connection), backgroundScope)

        val connecting = async { manager.connect(config("calendar")) }
        secondPageRequested.await()
        assertTrue(manager.snapshots().isEmpty())

        releaseSecondPage.complete(Unit)
        connecting.await()

        val snapshot = manager.snapshots().single()
        assertEquals(McpServerStatus.AVAILABLE, snapshot.status)
        assertEquals(
            listOf("mcp__calendar__alpha", "mcp__calendar__zebra"),
            snapshot.tools.map { it.definition.name },
        )
    }

    @Test
    fun `repeated cursor marks only that server unavailable`() = runTest {
        val healthy = FakeConnection.singlePage(tool("healthy"))
        val repeating =
            FakeConnection {
                McpToolPage(
                    tools = listOf(tool("loop")),
                    nextCursor = "same",
                )
            }
        val manager = McpClientManager(queueFactory(healthy, repeating), backgroundScope)
        manager.connect(config("healthy"))

        assertFails<McpDiscoveryException> {
            manager.connect(config("broken"))
        }

        assertEquals(McpServerStatus.AVAILABLE, manager.snapshot("healthy")?.status)
        assertEquals(McpServerStatus.UNAVAILABLE, manager.snapshot("broken")?.status)
        assertTrue(manager.snapshot("broken")?.tools.orEmpty().isEmpty())
    }

    @Test
    fun `page limit prevents an infinite server`() = runTest {
        val connection =
            FakeConnection { cursor ->
                McpToolPage(
                    tools = listOf(tool(cursor ?: "first")),
                    nextCursor = if (cursor == null) "second" else "third",
                )
            }
        val manager = McpClientManager(queueFactory(connection), backgroundScope)

        assertFails<McpDiscoveryException> {
            manager.connect(config("limited", maxDiscoveryPages = 2))
        }

        assertEquals(2, connection.listCursors.size)
        assertEquals(McpServerStatus.UNAVAILABLE, manager.snapshot("limited")?.status)
    }

    @Test
    fun `failed refresh removes only the failed server tools`() = runTest {
        var failRefresh = false
        val first =
            FakeConnection {
                if (failRefresh) error("offline")
                McpToolPage(listOf(tool("first")), null)
            }
        val second = FakeConnection.singlePage(tool("second"))
        val manager = McpClientManager(queueFactory(first, second), backgroundScope)
        manager.connect(config("first"))
        manager.connect(config("second"))

        failRefresh = true
        assertFails<IllegalStateException> {
            manager.refresh("first")
        }

        assertEquals(McpServerStatus.UNAVAILABLE, manager.snapshot("first")?.status)
        assertTrue(manager.snapshot("first")?.tools.orEmpty().isEmpty())
        assertEquals(McpServerStatus.AVAILABLE, manager.snapshot("second")?.status)
    }

    @Test
    fun `late old generation cannot overwrite a reconnect`() = runTest {
        val oldStarted = CompletableDeferred<Unit>()
        val releaseOld = CompletableDeferred<Unit>()
        val old =
            FakeConnection {
                oldStarted.complete(Unit)
                releaseOld.await()
                McpToolPage(listOf(tool("old")), null)
            }
        val replacement = FakeConnection.singlePage(tool("new"))
        val manager = McpClientManager(queueFactory(old, replacement), backgroundScope)

        val oldConnect = async { manager.connect(config("server")) }
        oldStarted.await()
        manager.connect(config("server"))
        releaseOld.complete(Unit)
        oldConnect.await()

        assertEquals(
            listOf("mcp__server__new"),
            manager.snapshot("server")?.tools?.map { it.definition.name },
        )
        assertEquals(1, old.closeCount)
    }

    @Test
    fun `disconnect removes one server and closes it once`() = runTest {
        val first = FakeConnection.singlePage(tool("first"))
        val second = FakeConnection.singlePage(tool("second"))
        val manager = McpClientManager(queueFactory(first, second), backgroundScope)
        manager.connect(config("first"))
        manager.connect(config("second"))

        manager.disconnect("first")
        manager.disconnect("first")

        assertEquals(null, manager.snapshot("first"))
        assertEquals(McpServerStatus.AVAILABLE, manager.snapshot("second")?.status)
        assertEquals(1, first.closeCount)
        assertEquals(0, second.closeCount)
    }

    @Test
    fun `close is idempotent and closes every connection`() = runTest {
        val first = FakeConnection.singlePage(tool("first"))
        val second = FakeConnection.singlePage(tool("second"))
        val manager = McpClientManager(queueFactory(first, second), backgroundScope)
        manager.connect(config("first"))
        manager.connect(config("second"))

        manager.close()
        manager.close()

        assertTrue(manager.snapshots().isEmpty())
        assertEquals(1, first.closeCount)
        assertEquals(1, second.closeCount)
        assertFails<IllegalStateException> {
            manager.connect(config("later"))
        }
    }

    @Test
    fun `list change signals coalesce into serialized refreshes`() = runTest {
        val refreshStarted = CompletableDeferred<Unit>()
        val releaseRefresh = CompletableDeferred<Unit>()
        var listCalls = 0
        var activeRefreshes = 0
        var maxActiveRefreshes = 0
        val connection =
            FakeConnection {
                listCalls += 1
                if (listCalls == 2) {
                    activeRefreshes += 1
                    maxActiveRefreshes = maxOf(maxActiveRefreshes, activeRefreshes)
                    refreshStarted.complete(Unit)
                    releaseRefresh.await()
                    activeRefreshes -= 1
                }
                McpToolPage(listOf(tool("tool-$listCalls")), null)
            }
        val manager = McpClientManager(queueFactory(connection), backgroundScope)
        manager.connect(config("server"))

        repeat(3) { connection.emitToolsListChanged() }
        runCurrent()
        refreshStarted.await()
        repeat(3) { connection.emitToolsListChanged() }
        releaseRefresh.complete(Unit)
        runCurrent()

        assertEquals(3, listCalls)
        assertEquals(1, maxActiveRefreshes)
        assertEquals(
            listOf("mcp__server__tool-3"),
            manager.snapshot("server")?.tools?.map { it.definition.name },
        )
    }

    @Test
    fun `stale connection list change signal is ignored`() = runTest {
        val old = FakeConnection.singlePage(tool("old"))
        val replacement = FakeConnection.singlePage(tool("new"))
        val manager = McpClientManager(queueFactory(old, replacement), backgroundScope)
        manager.connect(config("server"))
        manager.connect(config("server"))

        old.emitToolsListChanged()
        advanceUntilIdle()

        assertEquals(1, old.listCursors.size)
        assertEquals(1, replacement.listCursors.size)
        assertEquals(
            listOf("mcp__server__new"),
            manager.snapshot("server")?.tools?.map { it.definition.name },
        )
    }

    @Test
    fun `explicit and notified refreshes are serialized`() = runTest {
        val explicitStarted = CompletableDeferred<Unit>()
        val releaseExplicit = CompletableDeferred<Unit>()
        var listCalls = 0
        var activeRefreshes = 0
        var maxActiveRefreshes = 0
        val connection =
            FakeConnection {
                listCalls += 1
                if (listCalls > 1) {
                    activeRefreshes += 1
                    maxActiveRefreshes = maxOf(maxActiveRefreshes, activeRefreshes)
                }
                if (listCalls == 2) {
                    explicitStarted.complete(Unit)
                    releaseExplicit.await()
                }
                if (listCalls > 1) {
                    activeRefreshes -= 1
                }
                McpToolPage(listOf(tool("tool-$listCalls")), null)
            }
        val manager = McpClientManager(queueFactory(connection), backgroundScope)
        manager.connect(config("server"))

        val explicit = async { manager.refresh("server") }
        explicitStarted.await()
        connection.emitToolsListChanged()
        runCurrent()

        assertEquals(2, listCalls)
        releaseExplicit.complete(Unit)
        explicit.await()
        runCurrent()

        assertEquals(3, listCalls)
        assertEquals(1, maxActiveRefreshes)
    }

    private fun config(
        serverId: String,
        maxDiscoveryPages: Int = 100,
    ) = McpServerConfiguration(
        serverId = serverId,
        endpoint = "https://example.test/mcp",
        maxDiscoveryPages = maxDiscoveryPages,
    )

    private fun tool(name: String) =
        McpRemoteTool(
            name = name,
            description = "$name tool",
            inputSchemaJson = """{"type":"object"}""",
        )

    private fun queueFactory(vararg connections: McpConnection): McpConnectionFactory {
        val queue = ArrayDeque(connections.toList())
        return McpConnectionFactory { queue.removeFirst() }
    }

    private suspend inline fun <reified T : Throwable> assertFails(
        crossinline block: suspend () -> Unit,
    ) {
        val failure = runCatching { block() }.exceptionOrNull()
        assertTrue(
            "Expected ${T::class.java.simpleName}, got $failure",
            failure is T,
        )
    }

    private class FakeConnection(
        private val page: suspend (String?) -> McpToolPage,
    ) : McpConnection {
        val listCursors = mutableListOf<String?>()
        var closeCount = 0
        private var toolsListChangedHandler: () -> Unit = {}

        override suspend fun listTools(cursor: String?): McpToolPage {
            listCursors += cursor
            return page(cursor)
        }

        override suspend fun callTool(
            name: String,
            argumentsJson: String,
        ) = error("Not used")

        override fun setToolsListChangedHandler(handler: () -> Unit) {
            toolsListChangedHandler = handler
        }

        fun emitToolsListChanged() {
            toolsListChangedHandler()
        }

        override suspend fun close() {
            closeCount += 1
        }

        companion object {
            fun singlePage(vararg tools: McpRemoteTool) =
                FakeConnection { McpToolPage(tools.toList(), null) }
        }
    }
}
