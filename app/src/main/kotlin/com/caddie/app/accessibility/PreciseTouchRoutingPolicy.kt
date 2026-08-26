package com.caddie.app.accessibility

import com.caddie.app.lockscreen.LockScreenSafetyPolicy

/** Enables system-level touch routing only for an active agent on an unlocked display. */
internal object PreciseTouchRoutingPolicy {
    fun allows(
        nativeRunActive: Boolean,
        signal: LockScreenSafetyPolicy.Signal,
        keyguardLocked: Boolean,
    ): Boolean =
        nativeRunActive &&
            LockScreenSafetyPolicy.allowsInteractiveSurface(signal, keyguardLocked)
}
