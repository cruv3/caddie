package com.caddie.tool.mcp.client

import com.caddie.tool.mcp.config.McpServerConfiguration
import com.caddie.tool.mcp.mapping.McpToolBinding
import com.caddie.tool.mcp.mapping.McpToolMapper
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Reports whether one configured MCP server can currently provide tools. */
enum class McpServerStatus {
    AVAILABLE,
    UNAVAILABLE,
    DISCONNECTED,
}

/** Exposes one immutable generation of a server's discovered tools. */
data class McpServerSnapshot(
    val serverId: String,
    val generation: Long,
    val status: McpServerStatus,
    val tools: List<McpToolBinding>,
)

/** Signals that bounded MCP tool discovery could not produce a valid snapshot. */
class McpDiscoveryException(
    message: String,
) : IllegalStateException(message)

/** Provides the dynamic MCP operations consumed by app orchestration and tools. */
interface McpManager {
    fun snapshots(): List<McpServerSnapshot>

    fun snapshot(serverId: String): McpServerSnapshot?

    suspend fun callTool(
        binding: McpToolBinding,
        generation: Long,
        argumentsJson: String,
    ): McpCallResult

    suspend fun connect(configuration: McpServerConfiguration)

    suspend fun refresh(serverId: String)

    suspend fun disconnect(serverId: String)

    suspend fun close()
}

/** Owns MCP connections and publishes complete per-server tool snapshots. */
class McpClientManager(
    private val connectionFactory: McpConnectionFactory,
    private val scope: CoroutineScope,
) : McpManager {
    private val mutex = Mutex()
    private val generations = mutableMapOf<String, Long>()
    private val active = mutableMapOf<String, ActiveConnection>()
    private var closed = false

    @Volatile
    private var published = emptyMap<String, McpServerSnapshot>()

    override fun snapshots(): List<McpServerSnapshot> =
        published.values.sortedBy { it.serverId }

    override fun snapshot(serverId: String): McpServerSnapshot? = published[serverId]

    override suspend fun callTool(
        binding: McpToolBinding,
        generation: Long,
        argumentsJson: String,
    ): McpCallResult {
        val connection =
            mutex.withLock {
                check(!closed) { "MCP client manager is closed" }
                val snapshot =
                    published[binding.serverId]
                        ?: error("MCP server is unavailable: ${binding.serverId}")
                check(snapshot.status == McpServerStatus.AVAILABLE) {
                    "MCP server is unavailable: ${binding.serverId}"
                }
                check(snapshot.generation == generation) {
                    "MCP tool binding is stale: ${binding.definition.name}"
                }
                check(snapshot.tools.contains(binding)) {
                    "MCP tool binding is stale: ${binding.definition.name}"
                }
                val current =
                    active[binding.serverId]
                        ?: error("MCP server is unavailable: ${binding.serverId}")
                check(current.generation == generation) {
                    "MCP tool binding is stale: ${binding.definition.name}"
                }
                current.connection.delegate
            }
        return connection.callTool(binding.remoteName, argumentsJson)
    }

    override suspend fun connect(configuration: McpServerConfiguration) {
        val allocation =
            mutex.withLock {
                check(!closed) { "MCP client manager is closed" }
                val generation = generations.getOrDefault(configuration.serverId, 0) + 1
                generations[configuration.serverId] = generation
                val previous = active.remove(configuration.serverId)
                published = published - configuration.serverId
                Allocation(generation, previous)
            }
        allocation.previous?.close()

        val connection =
            try {
                ManagedConnection(connectionFactory.connect(configuration))
            } catch (error: Throwable) {
                publishUnavailable(configuration.serverId, allocation.generation)
                throw error
            }

        val signals = Channel<Unit>(Channel.CONFLATED)
        val refreshJob =
            scope.launch(start = CoroutineStart.LAZY) {
                try {
                    for (signal in signals) {
                        try {
                            refreshGeneration(
                                serverId = configuration.serverId,
                                generation = allocation.generation,
                            )
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (_: Exception) {
                            break
                        }
                    }
                } finally {
                    signals.close()
                }
            }
        val candidate =
            ActiveConnection(
                generation = allocation.generation,
                configuration = configuration,
                connection = connection,
                signals = signals,
                refreshJob = refreshJob,
            )
        val accepted =
            mutex.withLock {
                if (
                    !closed &&
                    generations[configuration.serverId] == allocation.generation
                ) {
                    active[configuration.serverId] = candidate
                    true
                } else {
                    false
                }
            }
        if (!accepted) {
            candidate.close()
            return
        }

        connection.delegate.setToolsListChangedHandler {
            signals.trySend(Unit)
        }
        refreshActive(candidate)
        refreshJob.start()
    }

    override suspend fun refresh(serverId: String) {
        val current =
            mutex.withLock {
                check(!closed) { "MCP client manager is closed" }
                checkNotNull(active[serverId]) {
                    "MCP server is not connected: $serverId"
                }
            }
        refreshActive(current)
    }

    private suspend fun refreshGeneration(
        serverId: String,
        generation: Long,
    ) {
        val current =
            mutex.withLock {
                active[serverId]?.takeIf { it.generation == generation }
            } ?: return
        refreshActive(current)
    }

    private suspend fun refreshActive(current: ActiveConnection) {
        current.refreshMutex.withLock {
            val isCurrent =
                mutex.withLock {
                    !closed &&
                        generations[current.configuration.serverId] == current.generation &&
                        active[current.configuration.serverId] === current
                }
            if (!isCurrent) {
                return@withLock
            }
            discoverAndPublish(
                serverId = current.configuration.serverId,
                generation = current.generation,
                configuration = current.configuration,
                connection = current.connection,
            )
        }
    }

    override suspend fun disconnect(serverId: String) {
        val connection =
            mutex.withLock {
                generations[serverId] = generations.getOrDefault(serverId, 0) + 1
                published = published - serverId
                active.remove(serverId)
            }
        connection?.close()
    }

    override suspend fun close() {
        val connections =
            mutex.withLock {
                if (closed) {
                    return
                }
                closed = true
                generations.replaceAll { _, generation -> generation + 1 }
                published = emptyMap()
                active.values.toList().also { active.clear() }
            }
        connections.forEach { it.close() }
    }

    private suspend fun discoverAndPublish(
        serverId: String,
        generation: Long,
        configuration: McpServerConfiguration,
        connection: ManagedConnection,
    ) {
        try {
            val tools = discoverAll(configuration, connection.delegate)
            val bindings = McpToolMapper.bindings(serverId, tools)
            val installed =
                mutex.withLock {
                    if (
                        !closed &&
                        generations[serverId] == generation &&
                        active[serverId]?.connection === connection
                    ) {
                        published =
                            published +
                                (
                                    serverId to
                                        McpServerSnapshot(
                                            serverId = serverId,
                                            generation = generation,
                                            status = McpServerStatus.AVAILABLE,
                                            tools = bindings.toList(),
                                        )
                                )
                        true
                    } else {
                        false
                    }
                }
            if (!installed) {
                connection.closeOnce()
            }
        } catch (error: Throwable) {
            val removed =
                mutex.withLock {
                    if (
                        !closed &&
                        generations[serverId] == generation &&
                        active[serverId]?.connection === connection
                    ) {
                        val current = active.remove(serverId)
                        published =
                            published +
                                (
                                    serverId to
                                        McpServerSnapshot(
                                            serverId = serverId,
                                            generation = generation,
                                            status = McpServerStatus.UNAVAILABLE,
                                            tools = emptyList(),
                                        )
                                )
                        current
                    } else {
                        null
                    }
                }
            removed?.signals?.close()
            if (removed?.refreshJob?.isActive == false) {
                removed.refreshJob.cancel()
            }
            connection.closeOnce()
            throw error
        }
    }

    private suspend fun discoverAll(
        configuration: McpServerConfiguration,
        connection: McpConnection,
    ): List<McpRemoteTool> {
        val tools = mutableListOf<McpRemoteTool>()
        val seenCursors = mutableSetOf<String>()
        var cursor: String? = null
        var pageCount = 0

        while (true) {
            if (pageCount >= configuration.maxDiscoveryPages) {
                throw McpDiscoveryException(
                    "MCP discovery exceeded ${configuration.maxDiscoveryPages} pages",
                )
            }
            val page = connection.listTools(cursor)
            pageCount += 1
            tools += page.tools
            val nextCursor = page.nextCursor ?: break
            if (!seenCursors.add(nextCursor)) {
                throw McpDiscoveryException("MCP discovery repeated a cursor")
            }
            cursor = nextCursor
        }
        return tools
    }

    private suspend fun publishUnavailable(
        serverId: String,
        generation: Long,
    ) {
        mutex.withLock {
            if (!closed && generations[serverId] == generation) {
                published =
                    published +
                        (
                            serverId to
                                McpServerSnapshot(
                                    serverId = serverId,
                                    generation = generation,
                                    status = McpServerStatus.UNAVAILABLE,
                                    tools = emptyList(),
                                )
                        )
            }
        }
    }

    private data class Allocation(
        val generation: Long,
        val previous: ActiveConnection?,
    )

    private data class ActiveConnection(
        val generation: Long,
        val configuration: McpServerConfiguration,
        val connection: ManagedConnection,
        val signals: Channel<Unit>,
        val refreshJob: Job,
        val refreshMutex: Mutex = Mutex(),
    ) {
        suspend fun close() {
            signals.close()
            refreshJob.cancel()
            connection.closeOnce()
        }
    }

    private class ManagedConnection(
        val delegate: McpConnection,
    ) {
        private val closed = AtomicBoolean(false)

        suspend fun closeOnce() {
            if (closed.compareAndSet(false, true)) {
                delegate.setToolsListChangedHandler {}
                delegate.close()
            }
        }
    }
}
