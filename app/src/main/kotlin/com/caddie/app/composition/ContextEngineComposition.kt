package com.caddie.app.composition

/** Controls whether normal agent requests may use local context. */
data class ContextEngineConfig(
    val enabled: Boolean = false,
)

/** Lazily owns the on-device context provider used outside study runs. */
class ContextEngineComposition private constructor(
    val config: ContextEngineConfig,
    val productionEnabled: Boolean,
    private val provider: Lazy<AndroidContextRequestFactoryProvider>,
) {
    val canStart: Boolean = productionEnabled && config.enabled

    fun providerOrNull(): AndroidContextRequestFactoryProvider? =
        if (canStart) provider.value else null

    companion object {
        fun disabled(
            config: ContextEngineConfig = ContextEngineConfig(),
            providerFactory: () -> AndroidContextRequestFactoryProvider = {
                error("Context engine is disabled")
            },
        ): ContextEngineComposition = ContextEngineComposition(
            config = config.copy(enabled = false),
            productionEnabled = false,
            provider = lazy(providerFactory),
        )

        fun enabled(
            config: ContextEngineConfig = ContextEngineConfig(enabled = true),
            providerFactory: () -> AndroidContextRequestFactoryProvider,
        ): ContextEngineComposition = ContextEngineComposition(
            config = config,
            productionEnabled = true,
            provider = lazy(providerFactory),
        )

        fun production(context: android.content.Context): ContextEngineComposition =
            enabled { AndroidContextRequestFactoryProvider.production(context) }
    }
}
