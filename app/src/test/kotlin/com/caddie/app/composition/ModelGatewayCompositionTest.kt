package com.caddie.app.composition

import com.caddie.agent.core.AgentMessage
import com.caddie.agent.core.ModelClient
import com.caddie.agent.core.ModelDelta
import com.caddie.agent.core.ModelRequest
import com.caddie.agent.core.ModelUnavailableException
import com.caddie.agent.core.RunId
import com.caddie.app.gateway.GatewayRuntimeSettings
import com.caddie.app.gateway.GatewayRuntimeSettingsSource
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelGatewayCompositionTest {
    @Test
    fun `disabled does not create a client`() {
        var factoryCalls = 0
        val composition = ModelGatewayComposition.disabled {
            factoryCalls += 1
            error("Model client must remain disabled")
        }

        assertFalse(composition.productionEnabled)
        assertEquals(0, factoryCalls)
    }

    @Test
    fun `configured resolves the current owner supplied settings when a request starts`() = runTest {
        val usedModels = mutableListOf<String>()
        val composition = ModelGatewayComposition.configured(
            settingsSource = source("https://gateway.example", "first-model"),
            clientFactory = { configuration ->
                usedModels += configuration.profile.modelId
                FakeModelClient()
            },
        )

        composition.createNormalClient().stream(
            ModelRequest(RunId("test"), emptyList(), emptyList()),
        ).collect { }

        assertEquals(listOf("first-model"), usedModels)
    }

    @Test
    fun `unconfigured settings fail before a model client is created`() = runTest {
        var factoryCalls = 0
        val composition = ModelGatewayComposition.configured(
            settingsSource = source("", ""),
            clientFactory = {
                factoryCalls += 1
                FakeModelClient()
            },
        )

        val failure = runCatching {
            composition.createNormalClient().stream(
                ModelRequest(RunId("test"), emptyList(), emptyList()),
            ).collect { }
        }.exceptionOrNull()

        assertTrue(failure is ModelUnavailableException)
        assertEquals(0, factoryCalls)
    }

    @Test
    fun `warm up sends only a synthetic tool-free request when configured`() = runTest {
        var captured: ModelRequest? = null
        val composition = ModelGatewayComposition.configured(
            settingsSource = source("https://gateway.example", "test-model"),
            clientFactory = {
                object : ModelClient {
                    override fun stream(request: ModelRequest) = flow {
                        captured = request
                        emit(ModelDelta.Completed)
                    }
                }
            },
        )

        composition.warmUpNormalModel()

        val request = checkNotNull(captured)
        assertEquals("startup-warmup", request.runId.value)
        assertEquals(emptyList<Any>(), request.tools)
        assertEquals(listOf(AgentMessage.Role.SYSTEM, AgentMessage.Role.USER), request.messages.map(AgentMessage::role))
    }

    private fun source(url: String, model: String) = object : GatewayRuntimeSettingsSource {
        override fun snapshot() = GatewayRuntimeSettings(baseUrl = url, modelId = model)
        override fun authorizationHeader(): String? = null
    }

    private class FakeModelClient : ModelClient {
        override fun stream(request: ModelRequest) = emptyFlow<ModelDelta>()
    }
}
