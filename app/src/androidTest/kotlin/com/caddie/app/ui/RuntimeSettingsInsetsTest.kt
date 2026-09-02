package com.caddie.app.ui

import android.graphics.Rect
import android.os.SystemClock
import android.view.WindowInsets
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.caddie.app.RuntimeSettingsActivity
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RuntimeSettingsInsetsTest {
    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation

    @Test
    fun headerAndCloseButtonStayBelowSystemBars() {
        ActivityScenario.launch(RuntimeSettingsActivity::class.java).use { scenario ->
            val heading = awaitText("Runtime settings")
            var safeTop = 0
            scenario.onActivity { activity ->
                safeTop = activity.window.decorView.rootWindowInsets
                    .getInsets(WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout()).top
            }
            assertTrue("Test device must expose a top system inset", safeTop > 0)
            for (node in listOf(heading, awaitText("Done"))) {
                val bounds = Rect().also(node::getBoundsInScreen)
                assertTrue("Header must be below top inset: $bounds, inset=$safeTop", bounds.top >= safeTop)
            }
        }
    }

    @Test
    fun lastControlCanBeScrolledAboveNavigationBar() {
        ActivityScenario.launch(RuntimeSettingsActivity::class.java).use { scenario ->
            awaitText("Runtime settings")
            var safeBottom = 0
            scenario.onActivity { activity ->
                val decor = activity.window.decorView
                val navigation = decor.rootWindowInsets
                    .getInsets(WindowInsets.Type.navigationBars() or WindowInsets.Type.displayCutout())
                safeBottom = decor.height - navigation.bottom
            }
            repeat(8) {
                val root = automation.rootInActiveWindow ?: error("Settings window unavailable")
                findScrollable(root)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                automation.waitForIdle(200, 5_000)
            }
            val bounds = Rect().also(awaitText("Add MCP server")::getBoundsInScreen)
            assertTrue("Last control must be visible", bounds.height() > 0)
            assertTrue("Last control must be above navigation inset: $bounds, bottom=$safeBottom", bounds.bottom <= safeBottom)
        }
    }

    private fun awaitText(text: String): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (SystemClock.uptimeMillis() < deadline) {
            automation.rootInActiveWindow?.let { findText(it, text) }?.let { return it }
            SystemClock.sleep(100)
        }
        error("Visible settings text not found: $text")
    }

    private fun findScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (index in 0 until node.childCount) {
            node.getChild(index)?.let { findScrollable(it)?.let { found -> return found } }
        }
        return null
    }

    private fun findText(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        if (node.text?.toString() == text && node.isVisibleToUser) return node
        for (index in 0 until node.childCount) {
            node.getChild(index)?.let { findText(it, text)?.let { found -> return found } }
        }
        return null
    }
}
