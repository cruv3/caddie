package com.caddie.executor.accessibility

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies the Android intents used by native navigation tools. */
@RunWith(AndroidJUnit4::class)
class AndroidNavigationIntentFactoryTest {

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
