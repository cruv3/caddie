package com.caddie.app.lockscreen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockScreenSafetyPolicyTest {
    @Test
    fun `runtime activity restores a missing enabled overlay only while unlocked`() {
        assertTrue(
            LockScreenSafetyPolicy.shouldRestoreOverlay(
                pillEnabled = true,
                screenInteractive = true,
                keyguardLocked = false,
                overlayAttached = false,
            ),
        )
        assertFalse(
            LockScreenSafetyPolicy.shouldRestoreOverlay(
                pillEnabled = true,
                screenInteractive = true,
                keyguardLocked = true,
                overlayAttached = false,
            ),
        )
        assertFalse(
            LockScreenSafetyPolicy.shouldRestoreOverlay(
                pillEnabled = false,
                screenInteractive = true,
                keyguardLocked = false,
                overlayAttached = false,
            ),
        )
        assertFalse(
            LockScreenSafetyPolicy.shouldRestoreOverlay(
                pillEnabled = true,
                screenInteractive = true,
                keyguardLocked = false,
                overlayAttached = true,
            ),
        )
        assertFalse(
            LockScreenSafetyPolicy.shouldRestoreOverlay(
                pillEnabled = true,
                screenInteractive = false,
                keyguardLocked = false,
                overlayAttached = false,
            ),
        )
    }

    @Test
    fun `visible keyguard never exposes Caddie touch surfaces`() {
        LockScreenSafetyPolicy.Signal.entries.forEach { signal ->
            assertFalse(LockScreenSafetyPolicy.allowsInteractiveSurface(signal, keyguardLocked = true))
        }
    }

    @Test
    fun `screen off suspends surfaces even before keyguard reports locked`() {
        assertFalse(
            LockScreenSafetyPolicy.allowsInteractiveSurface(
                LockScreenSafetyPolicy.Signal.SCREEN_OFF,
                keyguardLocked = false,
            ),
        )
    }

    @Test
    fun `unlocked start screen on and user present restore surfaces`() {
        listOf(
            LockScreenSafetyPolicy.Signal.STARTED,
            LockScreenSafetyPolicy.Signal.SCREEN_ON,
            LockScreenSafetyPolicy.Signal.USER_PRESENT,
        ).forEach { signal ->
            assertTrue(LockScreenSafetyPolicy.allowsInteractiveSurface(signal, keyguardLocked = false))
        }
    }
}
