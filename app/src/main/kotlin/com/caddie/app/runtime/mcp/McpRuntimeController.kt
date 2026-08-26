package com.caddie.app.runtime.mcp

import com.caddie.tool.mcp.client.McpManager
import com.caddie.tool.mcp.client.McpServerSnapshot
import com.caddie.tool.mcp.client.McpServerStatus
import com.caddie.tool.mcp.config.McpServerSettings
import com.caddie.tool.mcp.config.McpServerSettingsCodec
import com.caddie.tool.mcp.config.McpServerSettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Reconciles persisted MCP endpoints with the process-owned MCP client manager. */
class McpRuntimeController(
    private val store: McpServerSettingsStore,
    val manager: McpManager,
) {
    private val mutex = Mutex()

    @Volatile
    private var configured = emptyList<McpServerSettings>()
    @Volatile
    private var started = false
    @Volatile
    private var settingsProblem: String? = null

    suspend fun start() {
        mutex.withLock {
            if (started) return
            val loaded = try {
                McpServerSettingsCodec.normalize(store.load())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                configured = emptyList()
                settingsProblem = "Stored MCP settings could not be read. Save a valid configuration."
                started = true
                return
            }
            reconcile(previous = emptyList(), next = loaded)
            configured = loaded
            settingsProblem = null
            started = true
        }
    }

    suspend fun replace(settings: List<McpServerSettings>) {
        val normalized = McpServerSettingsCodec.normalize(settings)
        mutex.withLock {
            val previous = configured
            store.replace(normalized)
            configured = normalized
            settingsProblem = null
            reconcile(previous = previous, next = normalized)
        }
    }

    suspend fun retry(serverId: String) {
        mutex.withLock {
            val setting = configured.singleOrNull { it.serverId == serverId && it.enabled }
                ?: return
            manager.disconnect(serverId)
            connectWithoutBlockingOthers(setting)
        }
    }

    suspend fun refreshRequiredServers(): Boolean = mutex.withLock {
        if (!started) return@withLock false
        configured
            .filter { it.enabled && it.requiredForStudy }
            .forEach { setting ->
                try {
                    if (manager.snapshot(setting.serverId)?.status == McpServerStatus.AVAILABLE) {
                        manager.refresh(setting.serverId)
                    } else {
                        manager.connect(setting.toConfiguration())
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    return@withLock false
                }
            }
        requiredServersReady()
    }

    fun settings(): List<McpServerSettings> = configured

    fun snapshots(): List<McpServerSnapshot> = manager.snapshots()

    fun settingsProblem(): String? = settingsProblem

    fun requiredServersReady(): Boolean {
        if (!started || settingsProblem != null) return false
        val required = configured.filter { it.enabled && it.requiredForStudy }
        return required.all { setting ->
            manager.snapshot(setting.serverId)?.status == McpServerStatus.AVAILABLE
        }
    }

    private suspend fun reconcile(
        previous: List<McpServerSettings>,
        next: List<McpServerSettings>,
    ) {
        val oldEnabled = previous.filter(McpServerSettings::enabled).associateBy { it.serverId }
        val newEnabled = next.filter(McpServerSettings::enabled).associateBy { it.serverId }
        val disconnect = oldEnabled.filter { (id, old) -> newEnabled[id] != old }.keys.sorted()
        disconnect.forEach { manager.disconnect(it) }

        newEnabled
            .filter { (id, current) -> oldEnabled[id] != current }
            .toSortedMap()
            .values
            .forEach { connectWithoutBlockingOthers(it) }
    }

    private suspend fun connectWithoutBlockingOthers(setting: McpServerSettings) {
        try {
            manager.connect(setting.toConfiguration())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // The manager publishes UNAVAILABLE; other servers remain usable.
        }
    }
}
