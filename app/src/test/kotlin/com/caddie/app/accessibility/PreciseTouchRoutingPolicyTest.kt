package com.caddie.app.accessibility

import com.caddie.app.lockscreen.LockScreenSafetyPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreciseTouchRoutingPolicyTest {
    @Test
    fun `normal phone use never enables system touch routing`() {
        assertFalse(
            PreciseTouchRoutingPolicy.allows(
                nativeRunActive = false,
                signal = LockScreenSafetyPolicy.Signal.USER_PRESENT,
                keyguardLocked = false,
            ),
        )
    }

    @Test
    fun `active agent enables routing only on an unlocked display`() {
        assertTrue(
            PreciseTouchRoutingPolicy.allows(
                nativeRunActive = true,
                signal = LockScreenSafetyPolicy.Signal.USER_PRESENT,
                keyguardLocked = false,
            ),
        )
        assertFalse(
            PreciseTouchRoutingPolicy.allows(
                nativeRunActive = true,
                signal = LockScreenSafetyPolicy.Signal.SCREEN_OFF,
                keyguardLocked = false,
            ),
        )
        assertFalse(
            PreciseTouchRoutingPolicy.allows(
                nativeRunActive = true,
                signal = LockScreenSafetyPolicy.Signal.USER_PRESENT,
                keyguardLocked = true,
            ),
        )
    }
}
