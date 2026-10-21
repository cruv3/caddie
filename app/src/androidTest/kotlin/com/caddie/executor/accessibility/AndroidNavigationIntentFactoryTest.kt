package com.caddie.executor.accessibility

import android.content.Intent
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies the Android intents used by native navigation tools. */
@RunWith(AndroidJUnit4::class)
class AndroidNavigationIntentFactoryTest {
    @Test
    fun explicitBrowserKeepsHttpsViewIntent() {
        val intent = AndroidNavigationIntentFactory.openUrl("https://example.test/path", "com.android.chrome")
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https://example.test/path", intent.dataString)
        assertEquals("com.android.chrome", intent.`package`)
        assertTrue(intent.categories.contains(Intent.CATEGORY_BROWSABLE))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    /** Opt-in device probe only; ordinary test runs never launch an external website. */
    @Test
    fun explicitBrowserDeviceProbe() {
        val url = InstrumentationRegistry.getArguments().getString("native_url_probe")
        assumeNotNull(url)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        val intent = AndroidNavigationIntentFactory.openUrl(checkNotNull(url), "com.android.chrome")
        assertEquals("https", intent.data?.scheme)
        instrumentation.targetContext.startActivity(intent)
        val deadline = SystemClock.elapsedRealtime() + 15_000
        var verified = false
        while (SystemClock.elapsedRealtime() < deadline) {
            val root = automation.rootInActiveWindow
            val expectedHost = checkNotNull(intent.data?.host)
            fun addressMatches(node: AccessibilityNodeInfo): Boolean {
                if (node.packageName?.toString() == "com.android.chrome" &&
                    node.viewIdResourceName == "com.android.chrome:id/url_bar" &&
                    node.text?.toString()?.contains(expectedHost) == true) return true
                return (0 until node.childCount).any { index ->
                    node.getChild(index)?.let(::addressMatches) == true
                }
            }
            if (root != null && addressMatches(root)) {
                verified = true
                break
            }
            SystemClock.sleep(100)
        }
        assertTrue("Explicit native ACTION_VIEW must display the requested host in Chrome", verified)
    }

    @Test
    fun packageAndComponentOpenAsNewTasks() {
        val packageIntent = AndroidNavigationIntentFactory.openApp("com.caddie.studybank")
        val componentIntent = AndroidNavigationIntentFactory.openApp(
            "com.caddie.studycalendar/.StudyCalendarActivity",
        )

        assertEquals(Intent.ACTION_MAIN, packageIntent.action)
        assertEquals("com.caddie.studybank", packageIntent.`package`)
        assertTrue(packageIntent.categories.contains(Intent.CATEGORY_LAUNCHER))
        assertEquals(
            "com.caddie.studycalendar/com.caddie.studycalendar.StudyCalendarActivity",
            componentIntent.component?.flattenToString(),
        )
        assertTrue(packageIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(componentIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun installedPackageUsesResolvedLauncherComponent() {
        val resolved = Intent(Intent.ACTION_MAIN).setClassName(
            "com.caddie.studytelegram",
            "com.caddie.studytelegram.StudyTelegramActivity",
        )

        val intent = AndroidNavigationIntentFactory.openApp("com.caddie.studytelegram") {
            resolved
        }

        assertEquals(resolved.component, intent.component)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun httpsUrlUsesAndroidViewResolutionAsANewTask() {
        val intent = AndroidNavigationIntentFactory.openUrl("https://example.test/path")

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https://example.test/path", intent.dataString)
        assertTrue(intent.categories.contains(Intent.CATEGORY_BROWSABLE))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }
}
