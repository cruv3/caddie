package com.caddie.app.lockscreen

/** Decides whether Caddie's interactive surfaces may exist for the current lock state. */
internal object LockScreenSafetyPolicy {
    enum class Signal { STARTED, SCREEN_OFF, SCREEN_ON, USER_PRESENT }

    fun allowsInteractiveSurface(signal: Signal, keyguardLocked: Boolean): Boolean =
        signal != Signal.SCREEN_OFF && !keyguardLocked

    /** Recovers a lost overlay when new runtime activity proves it is needed. */
    fun shouldRestoreOverlay(
        pillEnabled: Boolean,
        screenInteractive: Boolean,
        keyguardLocked: Boolean,
        overlayAttached: Boolean,
    ): Boolean = pillEnabled && screenInteractive && !keyguardLocked && !overlayAttached
}
