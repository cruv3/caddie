package com.caddie.model.gateway

/** Supplies an optional authorization header for model-gateway requests. */
fun interface GatewayCredentialProvider {
    suspend fun authorizationHeader(): String?

    companion object {
        val None = GatewayCredentialProvider { null }
    }
}
