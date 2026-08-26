package com.caddie.app.runtime.mcp

import com.caddie.tool.mcp.client.McpCallResult
import com.caddie.tool.mcp.client.McpManager
import com.caddie.tool.mcp.client.McpServerSnapshot
import com.caddie.tool.mcp.client.McpServerStatus
import com.caddie.tool.mcp.config.McpServerConfiguration
import com.caddie.tool.mcp.config.McpServerSettings
import com.caddie.tool.mcp.config.McpServerSettingsStore
import com.caddie.tool.mcp.mapping.McpToolBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpRuntimeControllerTest {
    @Test
    fun `study readiness fails closed before persisted settings are loaded`() {
        val controller = McpRuntimeController(
            FakeSettingsStore(emptyList()),
            FakeMcpManager(),
        )

        assertFalse(controller.requiredServersReady())
    }

    @Test
    fun `invalid persisted settings fail closed until replaced`() = runTest {
        val store = FakeSettingsStore(emptyList(), loadFailure = IllegalArgumentException("bad"))
        val controller = McpRuntimeController(store, FakeMcpManager())

        controller.start()

        assertFalse(controller.requiredServersReady())
        assertTrue(controller.settingsProblem() != null)
        controller.replace(emptyList())
        assertTrue(controller.requiredServersReady())
        assertEquals(null, controller.settingsProblem())
    }

    @Test
    fun `start connects enabled settings and leaves disabled settings inert`() = runTest {
        val manager = FakeMcpManager()
        val controller = McpRuntimeController(
            store = FakeSettingsStore(
                listOf(
                    McpServerSettings("enabled", "https://enabled.test/mcp"),
                    McpServerSettings("disabled", "https://disabled.test/mcp", enabled = false),
                ),
            ),
            manager = manager,
        )

        controller.start()

        assertEquals(listOf("enabled"), manager.connected)
        assertEquals(2, controller.settings().size)
    }

    @Test
    fun `replace disconnects removed servers and reconnects changed endpoints`() = runTest {
        val manager = FakeMcpManager()
        val store = FakeSettingsStore(
            listOf(
                McpServerSettings("keep", "https://old.test/mcp"),
                McpServerSettings("remove", "https://remove.test/mcp"),
            ),
        )
        val controller = McpRuntimeController(store, manager)
        controller.start()
        manager.connected.clear()

        controller.replace(
            listOf(McpServerSettings("keep", "https://new.test/mcp")),
        )

        assertEquals(listOf("keep", "remove"), manager.disconnected.sorted())
        assertEquals(listOf("keep"), manager.connected)
        assertEquals("https://new.test/mcp", store.values.single().endpoint)
    }

    @Test
    fun `required readiness reflects only enabled required server snapshots`() = runTest {
        val manager = FakeMcpManager()
        val controller = McpRuntimeController(
            FakeSettingsStore(
                listOf(
                    McpServerSettings(
                        "required",
                        "https://required.test/mcp",
                        requiredForStudy = true,
                    ),
                    McpServerSettings(
                        "optional",
                        "https://optional.test/mcp",
                    ),
                ),
            ),
            manager,
        )
        controller.start()

        manager.statuses["required"] = McpServerStatus.UNAVAILABLE
        manager.statuses["optional"] = McpServerStatus.AVAILABLE
        assertFalse(controller.requiredServersReady())

        manager.statuses["required"] = McpServerStatus.AVAILABLE
        assertTrue(controller.requiredServersReady())
    }

    @Test
    fun `retry reconnects an enabled unavailable server`() = runTest {
        val manager = FakeMcpManager()
        val controller = McpRuntimeController(
            FakeSettingsStore(listOf(McpServerSettings("retry", "https://retry.test/mcp"))),
            manager,
        )
        controller.start()
        manager.connected.clear()
        manager.statuses["retry"] = McpServerStatus.UNAVAILABLE

        controller.retry("retry")

        assertEquals(listOf("retry"), manager.disconnected)
        assertEquals(listOf("retry"), manager.connected)
    }

    @Test
    fun `required health refresh fails closed when discovery refresh fails`() = runTest {
        val manager = FakeMcpManager()
        val controller = McpRuntimeController(
            FakeSettingsStore(
                listOf(
                    McpServerSettings(
                        "required",
                        "https://required.test/mcp",
                        requiredForStudy = true,
                    ),
                ),
            ),
            manager,
        )
        controller.start()
        manager.failRefresh += "required"

        assertFalse(controller.refreshRequiredServers())
        assertEquals(McpServerStatus.UNAVAILABLE, manager.statuses["required"])
    }

    @Test
    fun `cancelled reconciliation still exposes the persisted desired settings`() = runTest {
        val manager = FakeMcpManager()
        val store = FakeSettingsStore(emptyList())
        val controller = McpRuntimeController(store, manager)
        controller.start()
        manager.cancelConnect += "next"
        val next = McpServerSettings("next", "https://next.test/mcp")

        try {
            controller.replace(listOf(next))
        } catch (_: CancellationException) {
            // Expected: caller cancellation still propagates.
        }

        assertEquals(listOf(next), store.values)
        assertEquals(listOf(next), controller.settings())
    }
}

private class FakeSettingsStore(
    initial: List<McpServerSettings>,
    private var loadFailure: Exception? = null,
) : McpServerSettingsStore {
    var values = initial

    override fun load(): List<McpServerSettings> = loadFailure?.let { throw it } ?: values

    override fun replace(settings: List<McpServerSettings>) {
        values = settings
        loadFailure = null
    }
}

private class FakeMcpManager : McpManager {
    val connected = mutableListOf<String>()
    val disconnected = mutableListOf<String>()
    val statuses = mutableMapOf<String, McpServerStatus>()
    val failRefresh = mutableSetOf<String>()
    val cancelConnect = mutableSetOf<String>()

    override fun snapshots(): List<McpServerSnapshot> =
        statuses.map { (serverId, status) ->
            McpServerSnapshot(serverId, 1, status, emptyList())
        }

    override fun snapshot(serverId: String): McpServerSnapshot? =
        snapshots().singleOrNull { it.serverId == serverId }

    override suspend fun connect(configuration: McpServerConfiguration) {
        if (configuration.serverId in cancelConnect) throw CancellationException("cancel")
        connected += configuration.serverId
        statuses[configuration.serverId] = McpServerStatus.AVAILABLE
    }

    override suspend fun disconnect(serverId: String) {
        disconnected += serverId
        statuses.remove(serverId)
    }

    override suspend fun refresh(serverId: String) {
        if (serverId in failRefresh) {
            statuses[serverId] = McpServerStatus.UNAVAILABLE
            error("refresh failed")
        }
    }

    override suspend fun callTool(
        binding: McpToolBinding,
        generation: Long,
        argumentsJson: String,
    ): McpCallResult = error("not used")

    override suspend fun close() = Unit
}
