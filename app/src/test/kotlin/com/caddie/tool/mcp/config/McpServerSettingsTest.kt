package com.caddie.tool.mcp.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class McpServerSettingsTest {
    @Test
    fun `codec round trips stable non-secret server settings`() {
        val encoded = McpServerSettingsCodec.encode(
            listOf(
                McpServerSettings("zeta", "https://example.test/mcp/", enabled = false),
                McpServerSettings(
                    "alpha",
                    "http://100.64.0.10:9000/mcp",
                    requiredForStudy = true,
                ),
            ),
        )

        assertEquals(
            listOf(
                McpServerSettings(
                    "alpha",
                    "http://100.64.0.10:9000/mcp",
                    requiredForStudy = true,
                ),
                McpServerSettings("zeta", "https://example.test/mcp", enabled = false),
            ),
            McpServerSettingsCodec.decode(encoded),
        )
        assertFalse(encoded.contains("authorization", ignoreCase = true))
        assertFalse(encoded.contains("token", ignoreCase = true))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `codec rejects duplicate server IDs`() {
        McpServerSettingsCodec.encode(
            listOf(
                McpServerSettings("shared", "https://one.test/mcp"),
                McpServerSettings("shared", "https://two.test/mcp"),
            ),
        )
    }

    @Test
    fun `settings reject credentials and query parameters in endpoint`() {
        assertThrows(IllegalArgumentException::class.java) {
            McpServerSettings("secret", "https://user:pass@example.test/mcp")
        }
        assertThrows(IllegalArgumentException::class.java) {
            McpServerSettings("secret", "https://example.test/mcp?token=secret")
        }
    }
}
