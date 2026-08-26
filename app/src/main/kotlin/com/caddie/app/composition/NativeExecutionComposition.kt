package com.caddie.app.composition

/** Describes how native Android action execution is configured. */
data class NativeExecutionComposition(
    val variant: String,
    val productionEnabled: Boolean,
    val shizukuRequired: Boolean,
) {
    companion object {
        fun shadow() =
            NativeExecutionComposition(
                variant = "android-native-execution-enabled",
                productionEnabled = true,
                shizukuRequired = false,
            )
    }
}
