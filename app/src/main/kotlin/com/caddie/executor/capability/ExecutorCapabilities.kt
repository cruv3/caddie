package com.caddie.executor.capability

/** Lists optional capabilities supported by an action executor. */
enum class ExecutorCapability {
    ACCESSIBILITY_SEMANTIC,
    ANDROID_PUBLIC_API,
    SHIZUKU_PRIVILEGED,
}

fun availableCapabilities(
    accessibilityConnected: Boolean,
    shizukuAvailable: Boolean,
): Set<ExecutorCapability> =
    buildSet {
        add(ExecutorCapability.ANDROID_PUBLIC_API)
        if (accessibilityConnected) {
            add(ExecutorCapability.ACCESSIBILITY_SEMANTIC)
        }
        if (shizukuAvailable) {
            add(ExecutorCapability.SHIZUKU_PRIVILEGED)
        }
    }
