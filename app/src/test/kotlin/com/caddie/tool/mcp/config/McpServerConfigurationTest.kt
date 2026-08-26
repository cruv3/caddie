package com.caddie.tool.mcp.config

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class McpServerConfigurationTest {
    @Test
    fun `accepts a valid streamable http server`() {
        val configuration =
            McpServerConfiguration(
                serverId = "calendar",
                endpoint = "https://example.test/mcp/",
            )

        assertEquals("calendar", configuration.serverId)
        assertEquals("https://example.test/mcp", configuration.endpoint)
        assertEquals(100, configuration.maxDiscoveryPages)
    }

    @Test
    fun `rejects identifiers that require normalization`() {
        assertThrows(IllegalArgumentException::class.java) {
            McpServerConfiguration(
                serverId = "my server",
                endpoint = "https://example.test/mcp",
            )
        }
    }

    @Test
    fun `rejects non-http endpoints`() {
        assertThrows(IllegalArgumentException::class.java) {
            McpServerConfiguration(
                serverId = "calendar",
                endpoint = "file:///tmp/mcp",
            )
        }
    }

    @Test
    fun `rejects non-positive limits and timeouts`() {
        assertThrows(IllegalArgumentException::class.java) {
            McpServerConfiguration(
                serverId = "calendar",
                endpoint = "https://example.test/mcp",
                maxDiscoveryPages = 0,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            McpServerConfiguration(
                serverId = "calendar",
                endpoint = "https://example.test/mcp",
                connectTimeoutMillis = 0,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            McpServerConfiguration(
                serverId = "calendar",
                endpoint = "https://example.test/mcp",
                requestTimeoutMillis = 0,
            )
        }
    }

    @Test
    fun `toString never reveals authorization`() =
        runTest {
            val configuration =
                McpServerConfiguration(
                    serverId = "calendar",
                    endpoint = "https://example.test/mcp",
                    credentialProvider =
                        McpCredentialProvider {
                            "Bearer secret"
                        },
                )

            assertFalse(configuration.toString().contains("secret"))
            assertEquals(
                "Bearer secret",
                configuration.credentialProvider.authorizationHeader(),
            )
        }
}
