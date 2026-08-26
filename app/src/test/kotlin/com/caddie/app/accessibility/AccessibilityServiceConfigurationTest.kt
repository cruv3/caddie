package com.caddie.app.accessibility

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityServiceConfigurationTest {
    @Test
    fun `touch exploration is available but never enabled before an agent run`() {
        val candidates = listOf(
            File("src/main/res/xml/accessibility_service.xml"),
            File("app/src/main/res/xml/accessibility_service.xml"),
        )
        val configuration = candidates.first { it.isFile }.readText()

        assertTrue(configuration.contains("android:canRequestTouchExplorationMode=\"true\""))
        assertFalse(configuration.contains("flagRequestTouchExplorationMode"))
    }
}
