package com.caddie.app.gateway

import com.caddie.model.gateway.GatewayCredentialProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayRuntimeSettingsTest {
    @Test
    fun `empty settings are deliberately unconfigured`() {
        val settings = GatewayRuntimeSettings()

        assertFalse(settings.isConfigured)
        assertNull(settings.gatewayConfiguration(GatewayCredentialProvider.None))
        assertEquals("Enter the model server URL.", settings.validationMessage())
    }

    @Test
    fun `valid owner supplied settings produce one normal gateway configuration`() {
        val settings = GatewayRuntimeSettings(
            baseUrl = "https://gateway.example/",
            modelId = "tool-model",
            thinkingEnabled = true,
        )

        val configuration = checkNotNull(settings.gatewayConfiguration(GatewayCredentialProvider.None))

        assertTrue(settings.isConfigured)
        assertEquals("https://gateway.example", configuration.baseUrl)
        assertEquals("tool-model", configuration.profile.modelId)
        assertEquals(true, configuration.thinkingEnabled)
    }

    @Test
    fun `non http endpoints are rejected before use`() {
        val settings = GatewayRuntimeSettings(baseUrl = "file:///model", modelId = "local")

        assertFalse(settings.isConfigured)
        assertEquals(
            "Use an absolute http:// or https:// URL without credentials, query parameters, or fragments.",
            settings.validationMessage(),
        )
    }

    @Test
    fun `gateway URL is canonicalized before it reaches the model client`() {
        val settings = GatewayRuntimeSettings(
            baseUrl = " HTTPS://GATEWAY.EXAMPLE:443/openai/ ",
            modelId = "local",
        )

        val configuration = checkNotNull(settings.gatewayConfiguration(GatewayCredentialProvider.None))

        assertEquals("https://gateway.example/openai", configuration.baseUrl)
    }

    @Test
    fun `gateway URL rejects credentials query fragments and invalid ports`() {
        val invalidUrls = listOf(
            "https://owner:secret@gateway.example",
            "https://gateway.example?debug=true",
            "https://gateway.example#fragment",
            "https://gateway.example:99999",
            "https://gateway.example:invalid",
        )

        invalidUrls.forEach { url ->
            val settings = GatewayRuntimeSettings(baseUrl = url, modelId = "local")

            assertFalse("Expected $url to be rejected", settings.isConfigured)
            assertNull(settings.gatewayConfiguration(GatewayCredentialProvider.None))
        }
    }
}
