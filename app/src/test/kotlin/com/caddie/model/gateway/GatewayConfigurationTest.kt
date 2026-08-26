package com.caddie.model.gateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class GatewayConfigurationTest {
    @Test
    fun `configuration derives the chat completions URL`() {
        val configuration =
            GatewayConfiguration(
                baseUrl = "https://gateway.example.test/",
                profile = ModelProfile("normal", "test-model"),
            )

        assertEquals(
            "https://gateway.example.test/v1/chat/completions",
            configuration.chatCompletionsUrl,
        )
    }

    @Test
    fun `configuration rejects invalid URLs profiles and retry limits`() {
        listOf(
            "file:///tmp/model",
            "https://user:secret@gateway.example",
            "https://gateway.example?debug=true",
            "https://gateway.example#fragment",
            "https://gateway.example:99999",
        ).forEach { invalidUrl ->
            assertThrows(IllegalArgumentException::class.java) {
                GatewayConfiguration(
                    baseUrl = invalidUrl,
                    profile = ModelProfile("normal", "test-model"),
                )
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            ModelProfile("", "test-model")
        }
        assertThrows(IllegalArgumentException::class.java) {
            RetryPolicy(maxAttempts = 0, initialBackoffMillis = 100)
        }
    }

    @Test
    fun `configuration string never evaluates or prints credentials`() {
        var reads = 0
        val configuration =
            GatewayConfiguration(
                baseUrl = "https://gateway.example",
                profile = ModelProfile("normal", "example-model"),
                credentialProvider = GatewayCredentialProvider {
                    reads += 1
                    "Bearer secret"
                },
            )

        assertFalse(configuration.toString().contains("secret"))
        assertEquals(0, reads)
    }
}
