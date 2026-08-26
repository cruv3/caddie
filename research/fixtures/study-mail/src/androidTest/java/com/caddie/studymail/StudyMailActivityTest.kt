package com.caddie.studymail

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isSelected
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.containsString
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class StudyMailActivityTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(StudyMailActivity::class.java)

    @Before
    fun resetState() {
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("study_mail_state", 0)
            .edit()
            .clear()
            .commit()
        activityRule.scenario.recreate()
    }

    @Test
    fun inboxShowsTwoUnreadStudyMessagesAndReadBackgroundMessages() {
        onView(withId(R.id.mail_invoice)).check(matches(isDisplayed()))
        onView(withText("Offene Rechnung 30,00 EUR")).check(matches(isDisplayed()))
        onView(withId(R.id.mail_meeting_change)).check(matches(isDisplayed()))
        onView(withText("Terminänderung Projektsitzung")).check(matches(isDisplayed()))
        onView(withId(R.id.mail_invoice)).check(matches(isSelected()))
        onView(withId(R.id.mail_meeting_change)).check(matches(isSelected()))

        onView(withId(R.id.mail_mensa)).check(matches(not(isSelected())))
        onView(withText("Speiseplan für diese Woche")).check(matches(isDisplayed()))
        onView(withText("Wartungsarbeiten am WLAN")).check(matches(isDisplayed()))
    }

    @Test
    fun invoiceOpensCanonicalPaymentDetailsAndMarksOnlyInvoiceRead() {
        onView(withId(R.id.mail_invoice)).perform(click())
        onView(withId(R.id.mail_subject)).check(matches(withText("Offene Rechnung 30,00 EUR")))
        onView(withId(R.id.mail_body)).check(matches(withText(containsString("DE02 1203 0000 0000 2020 51"))))
        onView(withId(R.id.mail_body)).check(matches(withText(containsString("Rechnung INV-2026-001"))))
        onView(withId(R.id.btn_back_to_inbox)).perform(click())
        activityRule.scenario.onActivity { activity ->
            val preferences = activity.getSharedPreferences(StudyMailActivity.PREFS, 0)
            assertTrue(preferences.getBoolean(StudyMailActivity.KEY_INVOICE_READ, false))
            assertFalse(activity.findViewById<android.view.View>(R.id.mail_invoice).isSelected)
        }
        onView(withId(R.id.mail_invoice)).check(matches(not(isSelected())))
        onView(withId(R.id.mail_meeting_change)).check(matches(isSelected()))
    }

    @Test
    fun meetingChangeShowsOldAndNewTime() {
        onView(withId(R.id.mail_meeting_change)).perform(click())
        onView(withId(R.id.mail_subject)).check(matches(withText("Terminänderung Projektsitzung")))
        onView(withId(R.id.mail_body)).check(matches(withText(containsString("heute"))))
        onView(withId(R.id.mail_body)).check(matches(not(withText(containsString("Donnerstag")))))
        onView(withId(R.id.mail_body)).check(matches(withText(containsString("14:00 Uhr"))))
        onView(withId(R.id.mail_body)).check(matches(withText(containsString("15:00 Uhr"))))
    }

    @Test
    fun resetBroadcastRestoresUnreadInboxState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences(StudyMailActivity.PREFS, 0)
            .edit()
            .putBoolean(StudyMailActivity.KEY_INVOICE_READ, true)
            .putBoolean(StudyMailActivity.KEY_MEETING_READ, true)
            .commit()
        val resetFinished = CountDownLatch(1)
        context.sendOrderedBroadcast(
            Intent(StudyMailActivity.ACTION_RESET).setPackage(context.packageName),
            null,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    resetFinished.countDown()
                }
            },
            null,
            0,
            null,
            null,
        )
        assertTrue(resetFinished.await(2, TimeUnit.SECONDS))
        activityRule.scenario.recreate()

        onView(withId(R.id.mail_inbox)).check(matches(isDisplayed()))
        onView(withId(R.id.mail_invoice)).check(matches(isSelected()))
        onView(withId(R.id.mail_meeting_change)).check(matches(isSelected()))
    }

    @Test
    fun inboxAndInvoiceDetailBeginBelowTheStatusBar() {
        onView(withId(R.id.mail_inbox)).check(matches(isDisplayed()))
        assertBelowStatusBar(R.id.mail_inbox)

        onView(withId(R.id.mail_invoice)).perform(click())
        onView(withId(R.id.mail_detail)).check(matches(isDisplayed()))
        assertBelowStatusBar(R.id.mail_detail)
    }

    private fun assertBelowStatusBar(viewId: Int) {
        activityRule.scenario.onActivity { activity ->
            val insets = ViewCompat.getRootWindowInsets(activity.window.decorView)
                ?: throw AssertionError("System-bar insets must be available for the mail root")
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val container = activity.findViewById<View>(viewId)
            val location = IntArray(2)
            container.getLocationOnScreen(location)
            val contentOrigin = location[1] + container.paddingTop
            assertTrue("Status-bar top inset must be positive on the test device", statusBar.top > 0)
            assertTrue(
                "Mail content must begin at or below the status-bar bottom",
                contentOrigin >= statusBar.top,
            )
        }
    }
}
