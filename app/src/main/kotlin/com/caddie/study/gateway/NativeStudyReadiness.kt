package com.caddie.study.gateway

/** Reports native study requirements plus non-blocking remote diagnostics. */
data class NativeStudyReadiness(
    val accessibilityConnected: Boolean,
    val modelGatewayConnected: Boolean,
    val mcpConnected: Boolean,
    val studySpecsLoaded: Boolean,
) {
    val isReady: Boolean
        get() = accessibilityConnected &&
            studySpecsLoaded

    fun toSnapshot(): Map<String, Any> = mapOf(
        "accessibility_connected" to accessibilityConnected,
        "model_gateway_connected" to modelGatewayConnected,
        "mcp_connected" to mcpConnected,
        "study_specs_loaded" to studySpecsLoaded,
        "failed_checks" to buildList {
            if (!accessibilityConnected) add("accessibility")
            if (!studySpecsLoaded) add("study_specs")
        },
    )

    companion object {
        val Unavailable = NativeStudyReadiness(
            accessibilityConnected = false,
            modelGatewayConnected = false,
            mcpConnected = false,
            studySpecsLoaded = false,
        )
    }
}
