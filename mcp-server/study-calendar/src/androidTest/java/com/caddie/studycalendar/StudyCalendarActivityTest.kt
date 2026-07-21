package com.caddie.studycalendar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class StudyCalendarActivityTest {
    @get:Rule
    val rule = ActivityScenarioRule(StudyCalendarActivity::class.java)

    @Before
    fun reset() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences(StudyCalendarActivity.PREFS, 0).edit().clear().commit()
        rule.scenario.recreate()
    }

    @Test
    fun scheduleShowsBothStableStudyEvents() {
        onView(withId(R.id.schedule_screen)).check(matches(isDisplayed()))
        onView(withId(R.id.meeting_event)).check(
            matches(allOf(isDisplayed(), withContentDescription("Projektsitzung"))),
        )
        onView(withId(R.id.meeting_title)).check(matches(withText(containsString("Projektsitzung"))))
        onView(withId(R.id.meeting_time)).check(matches(withText("14:00–15:00 Uhr")))
        onView(withId(R.id.exam_event)).check(
            matches(allOf(isDisplayed(), withContentDescription("Prüfung"))),
        )
        onView(withId(R.id.exam_title)).check(matches(withText(containsString("Prüfung"))))
        onView(withId(R.id.exam_time)).check(matches(withText("10:00–11:00 Uhr")))
    }

    @Test
    fun scheduleShowsCurrentGermanDates() {
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)

        onView(withId(R.id.month_title)).check(
            matches(withText(today.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN)))),
        )
        onView(withId(R.id.today_weekday)).check(
            matches(withText(today.format(DateTimeFormatter.ofPattern("EEE", Locale.GERMAN)))),
        )
        onView(withId(R.id.today_number)).check(matches(withText(today.dayOfMonth.toString())))
        onView(withId(R.id.tomorrow_weekday)).check(
            matches(withText(tomorrow.format(DateTimeFormatter.ofPattern("EEE", Locale.GERMAN)))),
        )
        onView(withId(R.id.tomorrow_number)).check(matches(withText(tomorrow.dayOfMonth.toString())))
    }

    @Test
    fun outOfRangeMeetingHourFallsBackAfterRecreation() {
        preferences().edit()
            .putInt(StudyCalendarActivity.KEY_MEETING_START_HOUR, 23)
            .commit()

        rule.scenario.recreate()

        onView(withId(R.id.meeting_time)).check(matches(withText("14:00–15:00 Uhr")))
    }

    @Test
    fun wrongTypeMeetingHourFallsBackAfterRecreation() {
        preferences().edit()
            .putString(StudyCalendarActivity.KEY_MEETING_START_HOUR, "not-an-hour")
            .commit()

        rule.scenario.recreate()

        onView(withId(R.id.meeting_time)).check(matches(withText("14:00–15:00 Uhr")))
    }

    @Test
    fun meetingCanBeChangedToFifteenAndPersists() {
        changeMeetingHour(R.id.hour_15)

        onView(allOf(withText("15:00–16:00 Uhr"), isDescendantOfA(withId(R.id.detail_screen))))
            .check(matches(isDisplayed()))
        rule.scenario.recreate()
        onView(withId(R.id.meeting_time)).check(matches(withText("15:00–16:00 Uhr")))
    }

    @Test
    fun meetingCanBeChangedToSixteen() {
        changeMeetingHour(R.id.hour_16)

        onView(allOf(withText("16:00–17:00 Uhr"), isDescendantOfA(withId(R.id.detail_screen))))
            .check(matches(isDisplayed()))
    }

    @Test
    fun resetRestoresDefaultMeetingAndExamTimes() {
        preferences().edit()
            .putInt(StudyCalendarActivity.KEY_MEETING_START_HOUR, 16)
            .commit()
        rule.scenario.recreate()
        onView(withId(R.id.meeting_time)).check(matches(withText("16:00–17:00 Uhr")))

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resetReceived = CountDownLatch(1)
        context.sendOrderedBroadcast(
            Intent(StudyCalendarActivity.ACTION_RESET).setPackage(context.packageName),
            null,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    resetReceived.countDown()
                }
            },
            null,
            0,
            null,
            null,
        )
        assertTrue(resetReceived.await(5, TimeUnit.SECONDS))
        rule.scenario.recreate()

        onView(withId(R.id.meeting_time)).check(matches(withText("14:00–15:00 Uhr")))
        onView(withId(R.id.exam_time)).check(matches(withText("10:00–11:00 Uhr")))
    }

    @Test
    fun examOpensReadOnlyDetail() {
        onView(withId(R.id.exam_event)).perform(click())

        onView(withId(R.id.detail_screen)).check(matches(isDisplayed()))
        onView(withId(R.id.detail_title)).check(matches(withText("Prüfung")))
        onView(withId(R.id.detail_time)).check(matches(withText("10:00–11:00 Uhr")))
        onView(withId(R.id.edit_event)).check(matches(withEffectiveVisibility(GONE)))
    }

    private fun changeMeetingHour(hourId: Int) {
        onView(withId(R.id.meeting_event)).perform(click())
        onView(withId(R.id.edit_event)).perform(click())
        onView(withId(R.id.start_time)).perform(click())
        onView(withId(hourId)).perform(click())
        onView(withId(R.id.confirm_time)).perform(click())
        onView(withId(R.id.save_event)).perform(click())
    }

    private fun preferences() =
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences(StudyCalendarActivity.PREFS, 0)
}
