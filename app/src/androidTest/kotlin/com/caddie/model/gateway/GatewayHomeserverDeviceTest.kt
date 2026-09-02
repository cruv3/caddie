package com.caddie.model.gateway

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.caddie.agent.core.AgentMessage
import com.caddie.agent.core.ConversationRequestFactory
import com.caddie.agent.core.ModelDelta
import com.caddie.agent.core.ModelRequest
import com.caddie.agent.core.RunId
import com.caddie.agent.core.RunSnapshot
import com.caddie.agent.core.RunState
import com.caddie.app.composition.AndroidContextRequestFactoryProvider
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in device probe for a user-supplied streaming gateway. */
@RunWith(AndroidJUnit4::class)
class GatewayHomeserverDeviceTest {
    @Test
    fun configuredGatewayStreamsFromPhone() = runBlocking {
        assumeTrue(
            InstrumentationRegistry.getArguments()
                .getString("caddie_gateway_smoke") == "1",
        )
        val baseUrl = InstrumentationRegistry.getArguments().getString("caddie_gateway_base_url")
        val modelId = InstrumentationRegistry.getArguments().getString("caddie_gateway_model_id")
        assumeTrue(!baseUrl.isNullOrBlank() && !modelId.isNullOrBlank())
        val deltas = GatewayModelClient(
            GatewayConfiguration(
                baseUrl = requireNotNull(baseUrl),
                profile = ModelProfile("device-smoke", requireNotNull(modelId)),
                retryPolicy = RetryPolicy(maxAttempts = 1),
                thinkingEnabled = false,
            ),
        ).stream(
            ModelRequest(
                runId = RunId("device-gateway-smoke"),
                messages = listOf(
                    AgentMessage(AgentMessage.Role.USER, "Reply exactly: CADDIE_OK"),
                ),
                tools = emptyList(),
            ),
        ).toList()

        assertTrue(deltas.any { it is ModelDelta.Text })
        assertTrue(deltas.last() is ModelDelta.Completed)
    }

    @Test
    fun configuredGatewayAcceptsProductionRagContextFromPhone() = runBlocking {
        assumeTrue(
            InstrumentationRegistry.getArguments()
                .getString("caddie_gateway_smoke") == "1",
        )
        val baseUrl = InstrumentationRegistry.getArguments().getString("caddie_gateway_base_url")
        val modelId = InstrumentationRegistry.getArguments().getString("caddie_gateway_model_id")
        assumeTrue(!baseUrl.isNullOrBlank() && !modelId.isNullOrBlank())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val provider = AndroidContextRequestFactoryProvider.production(context)
        try {
            val task = "Antworte nur mit dem Wort Hallo"
            val factory = provider.forTask(
                task,
                ConversationRequestFactory("You are Caddie, an Android assistant."),
            )
            val request = factory.create(
                RunSnapshot(
                    runId = RunId("device-rag-gateway-smoke"),
                    state = RunState.RUNNING,
                    messages = listOf(AgentMessage(AgentMessage.Role.USER, task)),
                ),
                emptyList(),
            )
            val deltas = GatewayModelClient(
                GatewayConfiguration(
                    baseUrl = requireNotNull(baseUrl),
                    profile = ModelProfile("device-rag-smoke", requireNotNull(modelId)),
                    retryPolicy = RetryPolicy(maxAttempts = 1),
                    thinkingEnabled = false,
                ),
            ).stream(request).toList()

            assertTrue(deltas.any { it is ModelDelta.Text })
            assertTrue(deltas.last() is ModelDelta.Completed)
        } finally {
            provider.close()
        }
    }
}
