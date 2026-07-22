package com.caddie.studycalendar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isSelected
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
import org.hamcrest.Matchers.anyOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
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
        val latestToday = LocalDate.now()
        val earliestToday = latestToday.minusDays(1)
        val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN)
        val weekdayFormatter = DateTimeFormatter.ofPattern("EEE", Locale.GERMAN)

        onView(withId(R.id.month_title)).check(
            matches(anyOf(
                withText(earliestToday.format(monthFormatter)),
                withText(latestToday.format(monthFormatter)),
            )),
        )
        onView(withId(R.id.today_weekday)).check(
            matches(anyOf(
                withText(earliestToday.format(weekdayFormatter)),
                withText(latestToday.format(weekdayFormatter)),
            )),
        )
        onView(withId(R.id.today_number)).check(matches(anyOf(
            withText(earliestToday.dayOfMonth.toString()),
            withText(latestToday.dayOfMonth.toString()),
        )))
        onView(withId(R.id.tomorrow_weekday)).check(
            matches(anyOf(
                withText(earliestToday.plusDays(1).format(weekdayFormatter)),
                withText(latestToday.plusDays(1).format(weekdayFormatter)),
            )),
        )
        onView(withId(R.id.tomorrow_number)).check(matches(anyOf(
            withText(earliestToday.plusDays(1).dayOfMonth.toString()),
            withText(latestToday.plusDays(1).dayOfMonth.toString()),
        )))
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
    fun unsupportedMeetingHourFallsBackAfterRecreation() {
        preferences().edit()
            .putInt(StudyCalendarActivity.KEY_MEETING_START_HOUR, 13)
            .commit()

        rule.scenario.recreate()

        onView(withId(R.id.meeting_time)).check(matches(withText("14:00–15:00 Uhr")))
    }

    @Test
    fun meetingCanBeSavedAtDefaultWithoutOpeningPicker() {
        onView(withId(R.id.meeting_event)).perform(click())
        onView(withId(R.id.edit_event)).perform(click())
        onView(withId(R.id.save_event)).perform(click())

        onView(allOf(withText("14:00–15:00 Uhr"), isDescendantOfA(withId(R.id.detail_screen))))
            .check(matches(isDisplayed()))
        assertEquals(14, storedMeetingHour())
    }

    @Test
    fun backFromPickerDiscardsPendingHour() {
        onView(withId(R.id.meeting_event)).perform(click())
        onView(withId(R.id.edit_event)).perform(click())
        onView(withId(R.id.start_time)).perform(click())
        onView(withId(R.id.hour_16)).perform(click())
        onView(withId(R.id.hour_16)).check(matches(isSelected()))
        pressBack()
        onView(withId(R.id.save_event)).perform(click())

        onView(allOf(withText("14:00–15:00 Uhr"), isDescendantOfA(withId(R.id.detail_screen))))
            .check(matches(isDisplayed()))
        assertEquals(14, storedMeetingHour())
    }

    @Test
    fun confirmedEditorHourSurvivesRecreationWithoutSaving() {
        onView(withId(R.id.meeting_event)).perform(click())
        onView(withId(R.id.edit_event)).perform(click())
        onView(withId(R.id.start_time)).perform(click())
        onView(withId(R.id.hour_15)).perform(click())
        onView(withId(R.id.confirm_time)).perform(click())

        rule.scenario.recreate()

        onView(withId(R.id.editor_screen)).check(matches(isDisplayed()))
        onView(withId(R.id.start_time)).check(matches(withText("Beginnt um: 15:00")))
        assertEquals(14, storedMeetingHour())
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

    @Test
    fun examDetailSurvivesRecreationReadOnly() {
        onView(withId(R.id.exam_event)).perform(click())

        rule.scenario.recreate()

        onView(withId(R.id.detail_screen)).check(matches(isDisplayed()))
        onView(withId(R.id.detail_title)).check(matches(withText("Prüfung")))
        onView(withId(R.id.detail_time)).check(matches(withText("10:00–11:00 Uhr")))
        onView(withId(R.id.edit_event)).check(matches(withEffectiveVisibility(GONE)))
    }

    @Test
    fun wrongPackageScopedActionDoesNotResetMeetingHour() {
        preferences().edit()
            .putInt(StudyCalendarActivity.KEY_MEETING_START_HOUR, 16)
            .commit()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val broadcastFinished = CountDownLatch(1)
        val intent = Intent(
            context,
            StudyCalendarResetReceiver::class.java,
        ).setAction("com.caddie.studycalendar.WRONG_ACTION")
        context.sendOrderedBroadcast(
            intent,
            null,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    broadcastFinished.countDown()
                }
            },
            null,
            0,
            null,
            null,
        )
        assertTrue(broadcastFinished.await(5, TimeUnit.SECONDS))

        assertEquals(16, storedMeetingHour())
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

    private fun storedMeetingHour() = preferences().getInt(
        StudyCalendarActivity.KEY_MEETING_START_HOUR,
        StudyCalendarActivity.DEFAULT_MEETING_START_HOUR,
    )
}
