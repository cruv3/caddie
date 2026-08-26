package com.caddie.executor.capability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutorCapabilitiesTest {
    @Test
    fun `accessibility absence blocks only gui capabilities`() {
        val capabilities = availableCapabilities(
            accessibilityConnected = false,
            shizukuAvailable = false,
        )

        assertEquals(
            setOf(ExecutorCapability.ANDROID_PUBLIC_API),
            capabilities,
        )
    }

    @Test
    fun `accessibility enables only semantic gui capability`() {
        val capabilities = availableCapabilities(
            accessibilityConnected = true,
            shizukuAvailable = false,
        )

        assertTrue(
            ExecutorCapability.ACCESSIBILITY_SEMANTIC in capabilities,
        )
        assertTrue(
            ExecutorCapability.ANDROID_PUBLIC_API in capabilities,
        )
        assertFalse(
            ExecutorCapability.SHIZUKU_PRIVILEGED in capabilities,
        )
    }

    @Test
    fun `shizuku absence removes only privileged capability`() {
        val withoutShizuku = availableCapabilities(
            accessibilityConnected = true,
            shizukuAvailable = false,
        )
        val withShizuku = availableCapabilities(
            accessibilityConnected = true,
            shizukuAvailable = true,
        )

        assertEquals(
            setOf(ExecutorCapability.SHIZUKU_PRIVILEGED),
            withShizuku - withoutShizuku,
        )
        assertEquals(
            withShizuku - ExecutorCapability.SHIZUKU_PRIVILEGED,
            withoutShizuku,
        )
    }
}
