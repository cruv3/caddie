package com.caddie.studyportal

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.caddie.app.MainActivity
import java.net.HttpURLConnection
import java.net.URL
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies the on-device portal lifecycle and its packaged frontend. */
@RunWith(AndroidJUnit4::class)
class StudyPortalServerDeviceTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @After
    fun cleanUp() {
        StudyPortalSettings.setEnabled(context, false)
        StudyPortalService.stop(context)
    }

    @Test
    fun portalIsExplicitlyDisabledByDefaultAndKeepsItsConfiguredPort() {
        context.getSharedPreferences("study_portal_settings", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        assertFalse(StudyPortalSettings.isEnabled(context))
        assertEquals(StudyPortalSettings.DEFAULT_PORT, StudyPortalSettings.getPort(context))

        StudyPortalSettings.setPort(context, TEST_PORT)

        assertEquals(TEST_PORT, StudyPortalSettings.getPort(context))
    }

    @Test
    fun packagedPortalContainsInvestigatorAndParticipantBootstrap() {
        val index = context.assets.open("study-portal/index.html").bufferedReader().use { it.readText() }
        val script = context.assets.open("study-portal/app.js").bufferedReader().use { it.readText() }

        assertTrue(index.contains("/study/app/static/app.js"))
        assertTrue(script.contains("investigator"))
        assertTrue(script.contains("participant"))
        assertTrue(script.contains("fetch(`/study/app${'$'}{path}`"))
    }

    @Test
    fun enabledPortalIsRehostedWhenTheAppIsOpened() {
        StudyPortalService.stop(context)
        StudyPortalSettings.setPort(context, TEST_PORT)
        StudyPortalSettings.setEnabled(context, true)

        val activity = InstrumentationRegistry.getInstrumentation().startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )

        try {
            assertEquals(200, awaitHealth())
        } finally {
            activity.finish()
        }
    }

    private fun awaitHealth(): Int {
        repeat(40) {
            try {
                val connection = URL("http://127.0.0.1:$TEST_PORT/study/app/api/health")
                    .openConnection() as HttpURLConnection
                connection.connectTimeout = 250
                connection.readTimeout = 250
                try {
                    return connection.responseCode
                } finally {
                    connection.disconnect()
                }
            } catch (_: Exception) {
                Thread.sleep(100)
            }
        }
        return -1
    }

    private companion object {
        const val TEST_PORT = 18787
    }
}
