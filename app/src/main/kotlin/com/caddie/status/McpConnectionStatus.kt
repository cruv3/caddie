package com.caddie.status

import com.caddie.app.gateway.GatewayRuntimeSettingsSource
import com.caddie.model.gateway.GatewayEndpoint
import okhttp3.OkHttpClient
import okhttp3.Request

/** Connection status to the configured model gateway. */
enum class GatewayConnectionStatus {
    Connected,
    Disconnected,
    Connecting,
}

/**
 * Checks the same configured endpoint that normal-mode model requests use.
 */
class GatewayHealthChecker(
    private val settingsSource: GatewayRuntimeSettingsSource,
    private val client: OkHttpClient = OkHttpClient(),
) {
    fun check(): GatewayConnectionStatus {
        return try {
            val configuration = settingsSource.gatewayConfiguration()
                ?: return GatewayConnectionStatus.Disconnected
            val modelsUrl = GatewayEndpoint.modelsUrl(configuration.baseUrl)
                ?: return GatewayConnectionStatus.Disconnected
            val requestBuilder = Request.Builder()
                .url(modelsUrl)
                .get()
            settingsSource.authorizationHeader()?.let { authorization ->
                requestBuilder.header("Authorization", authorization)
            }
            val request = requestBuilder.build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    GatewayConnectionStatus.Connected
                } else {
                    GatewayConnectionStatus.Disconnected
                }
            }
        } catch (failure: Exception) {
            GatewayConnectionStatus.Disconnected
        }
    }
}
