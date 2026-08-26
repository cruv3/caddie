package com.caddie.app.composition

import com.caddie.agent.core.ModelClient
import com.caddie.agent.core.AgentMessage
import com.caddie.agent.core.ModelRequest
import com.caddie.agent.core.RunId
import com.caddie.agent.core.ModelUnavailableException
import com.caddie.app.gateway.GatewayRuntimeSettingsSource
import com.caddie.model.gateway.GatewayConfiguration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

/** Holds the model-gateway configurations used for study and normal operation. */
data class ModelGatewayComposition(
    val variant: String,
    val productionEnabled: Boolean,
    private val studyConfiguration: () -> GatewayConfiguration?,
    private val normalConfiguration: () -> GatewayConfiguration?,
    private val clientFactory: (GatewayConfiguration) -> ModelClient,
) {
    private val studyClient = ConfiguredModelClient(studyConfiguration, clientFactory)
    private val normalClient = ConfiguredModelClient(normalConfiguration, clientFactory)

    fun createStudyClient(): ModelClient {
        check(productionEnabled) { "Android-native model gateway is disabled" }
        return studyClient
    }

    fun createNormalClient(): ModelClient {
        check(productionEnabled) { "Android-native model gateway is disabled" }
        return normalClient
    }

    /** Loads the configured model before the first participant task is accepted. */
    suspend fun warmUpNormalModel() {
        check(productionEnabled) { "Android-native model gateway is disabled" }
        if (normalConfiguration() == null) return
        withTimeout(WARM_UP_TIMEOUT_MS) {
            normalClient.stream(
                ModelRequest(
                    runId = RunId("startup-warmup"),
                    messages = listOf(
                        AgentMessage(
                            AgentMessage.Role.SYSTEM,
                            "This is a startup warm-up. Do not call tools.",
                        ),
                        AgentMessage(AgentMessage.Role.USER, "Reply exactly: OK."),
                    ),
                    tools = emptyList(),
                ),
            ).collect()
        }
    }

    companion object {
        private const val WARM_UP_TIMEOUT_MS = 45_000L

        fun disabled(
            normalConfiguration: () -> GatewayConfiguration? = { null },
            clientFactory: (GatewayConfiguration) -> ModelClient = {
                error("Android-native model gateway is disabled")
            },
        ): ModelGatewayComposition =
            ModelGatewayComposition(
                variant = "android-model-gateway-shadow",
                productionEnabled = false,
                studyConfiguration = normalConfiguration,
                normalConfiguration = normalConfiguration,
                clientFactory = clientFactory,
            )

        fun configured(
            settingsSource: GatewayRuntimeSettingsSource,
            clientFactory: (GatewayConfiguration) -> ModelClient,
        ): ModelGatewayComposition =
            ModelGatewayComposition(
                variant = "android-model-gateway-enabled",
                productionEnabled = true,
                studyConfiguration = settingsSource::gatewayConfiguration,
                normalConfiguration = settingsSource::gatewayConfiguration,
                clientFactory = clientFactory,
            )
    }
}

/** Resolves the owner-controlled endpoint directly before each model request. */
private class ConfiguredModelClient(
    private val configurationProvider: () -> GatewayConfiguration?,
    private val clientFactory: (GatewayConfiguration) -> ModelClient,
) : ModelClient {
    override fun stream(request: ModelRequest): Flow<com.caddie.agent.core.ModelDelta> = flow {
        val configuration = configurationProvider()
            ?: throw ModelUnavailableException(
                "gateway_unconfigured: configure a model endpoint in Runtime settings",
            )
        clientFactory(configuration).stream(request).collect { emit(it) }
    }
}
