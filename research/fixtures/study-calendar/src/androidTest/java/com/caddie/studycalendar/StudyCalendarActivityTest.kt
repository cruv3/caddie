package com.caddie.studycalendar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.GridLayout
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.anyOf
import org.hamcrest.Matchers.not
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
    fun scheduleShowsACompleteSixWeekMonthGrid() {
        rule.scenario.onActivity { activity ->
            val grid = activity.findViewById<GridLayout>(R.id.month_grid)
            assertEquals(7, grid.columnCount)
            assertEquals(6, grid.rowCount)
            assertEquals(42, grid.childCount)
            assertEquals(
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
                grid.importantForAccessibility,
            )
        }
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
    fun unsupportedMeetingMinutesFallBackAfterRecreation() {
        preferences().edit()
            .putInt(StudyCalendarActivity.KEY_MEETING_START_MINUTES, 24 * 60)
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
        assertEquals(14 * 60, storedMeetingMinutes())
    }

    @Test
    fun invalidTimeCannotBeSaved() {
        onView(withId(R.id.meeting_event)).perform(click())
        onView(withId(R.id.edit_event)).perform(click())
        onView(withId(R.id.start_time)).perform(replaceText("25:00"), closeSoftKeyboard())

        onView(withId(R.id.save_event)).check(matches(not(isEnabled())))
        assertEquals(14 * 60, storedMeetingMinutes())
    }

    @Test
    fun confirmedEditorHourSurvivesRecreationWithoutSaving() {
        onView(withId(R.id.meeting_event)).perform(click())
        onView(withId(R.id.edit_event)).perform(click())
        onView(withId(R.id.start_time)).perform(replaceText("15:00"), closeSoftKeyboard())

        rule.scenario.recreate()

        onView(withId(R.id.editor_screen)).check(matches(isDisplayed()))
        onView(withId(R.id.start_time)).check(matches(withText("15:00")))
        onView(withId(R.id.end_time)).check(matches(withText("16:00")))
        assertEquals(14 * 60, storedMeetingMinutes())
    }

    @Test
    fun meetingCanBeChangedToFifteenAndPersists() {
        changeMeetingHour("15:00")

        onView(allOf(withText("15:00–16:00 Uhr"), isDescendantOfA(withId(R.id.detail_screen))))
            .check(matches(isDisplayed()))
        rule.scenario.recreate()
        onView(withId(R.id.meeting_time)).check(matches(withText("15:00–16:00 Uhr")))
    }

    @Test
    fun meetingCanBeChangedToSixteen() {
        changeMeetingHour("16:00")

        onView(allOf(withText("16:00–17:00 Uhr"), isDescendantOfA(withId(R.id.detail_screen))))
            .check(matches(isDisplayed()))
    }

    @Test
    fun resetRestoresDefaultMeetingAndExamTimes() {
        preferences().edit()
            .putInt(StudyCalendarActivity.KEY_MEETING_START_MINUTES, 16 * 60)
            .putInt(StudyCalendarActivity.KEY_EXAM_START_MINUTES, 12 * 60)
            .commit()
        rule.scenario.recreate()
        onView(withId(R.id.meeting_time)).check(matches(withText("16:00–17:00 Uhr")))
        onView(withId(R.id.exam_time)).check(matches(withText("12:00–13:00 Uhr")))

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resetReceived = CountDownLatch(1)
        var resetResultCode: Int? = null
        var resetResultData: String? = null
        context.sendOrderedBroadcast(
            Intent(StudyCalendarActivity.ACTION_RESET).setPackage(context.packageName),
            null,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    resetResultCode = resultCode
                    resetResultData = resultData
                    resetReceived.countDown()
                }
            },
            null,
            0,
            null,
            null,
        )
        assertTrue(resetReceived.await(5, TimeUnit.SECONDS))
        assertEquals(StudyCalendarResetReceiver.RESET_SUCCESS_RESULT_CODE, resetResultCode)
        assertEquals(StudyCalendarResetReceiver.RESET_SUCCESS_RESULT_DATA, resetResultData)
        rule.scenario.recreate()

        onView(withId(R.id.meeting_time)).check(matches(withText("14:00–15:00 Uhr")))
        onView(withId(R.id.exam_time)).check(matches(withText("10:00–11:00 Uhr")))
        assertTrue(!preferences().contains(StudyCalendarActivity.KEY_MEETING_START_MINUTES))
        assertTrue(!preferences().contains(StudyCalendarActivity.KEY_EXAM_START_MINUTES))
    }

    @Test
    fun examOpensEditableDetail() {
        onView(withId(R.id.exam_event)).perform(click())

        onView(withId(R.id.detail_screen)).check(matches(isDisplayed()))
        onView(withId(R.id.detail_title)).check(matches(withText("Prüfung")))
        onView(withId(R.id.detail_time)).check(matches(withText("10:00–11:00 Uhr")))
        onView(withId(R.id.edit_event)).check(matches(isDisplayed()))
    }

    @Test
    fun examCanBeChangedAndPersistsWithoutChangingMeeting() {
        onView(withId(R.id.exam_event)).perform(click())
        onView(withId(R.id.edit_event)).perform(click())
        onView(withId(R.id.start_time)).perform(replaceText("11:30"), closeSoftKeyboard())
        onView(withId(R.id.save_event)).perform(click())

        onView(withId(R.id.detail_title)).check(matches(withText("Prüfung")))
        onView(withId(R.id.detail_time)).check(matches(withText("11:30–12:30 Uhr")))

        rule.scenario.recreate()
        onView(withId(R.id.exam_time)).check(matches(withText("11:30–12:30 Uhr")))
        onView(withId(R.id.meeting_time)).check(matches(withText("14:00–15:00 Uhr")))
    }

    @Test
    fun unsavedExamEditorSurvivesRecreationWithoutPersisting() {
        onView(withId(R.id.exam_event)).perform(click())
        onView(withId(R.id.edit_event)).perform(click())
        onView(withId(R.id.start_time)).perform(replaceText("11:30"), closeSoftKeyboard())

        rule.scenario.recreate()

        onView(withId(R.id.editor_screen)).check(matches(isDisplayed()))
        onView(withId(R.id.editor_title)).check(
            matches(allOf(isDisplayed(), withText("Prüfung bearbeiten"))),
        )
        onView(withId(R.id.start_time)).check(matches(withText("11:30")))
        onView(withId(R.id.end_time)).check(matches(withText("12:30")))
        assertEquals(
            StudyCalendarActivity.DEFAULT_EXAM_START_MINUTES,
            storedExamMinutes(),
        )
    }

    @Test
    fun wrongPackageScopedActionDoesNotResetMeetingHour() {
        preferences().edit()
            .putInt(StudyCalendarActivity.KEY_MEETING_START_HOUR, 16)
            .commit()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val broadcastFinished = CountDownLatch(1)
        var wrongActionResultCode: Int? = null
        var wrongActionResultData: String? = null
        val intent = Intent(
            context,
            StudyCalendarResetReceiver::class.java,
        ).setAction("com.caddie.studycalendar.WRONG_ACTION")
        context.sendOrderedBroadcast(
            intent,
            null,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    wrongActionResultCode = resultCode
                    wrongActionResultData = resultData
                    broadcastFinished.countDown()
                }
            },
            null,
            0,
            null,
            null,
        )
        assertTrue(broadcastFinished.await(5, TimeUnit.SECONDS))

        assertEquals(0, wrongActionResultCode)
        assertEquals(null, wrongActionResultData)
        assertEquals(16 * 60, storedMeetingMinutes())
    }

    private fun changeMeetingHour(time: String) {
        onView(withId(R.id.meeting_event)).perform(click())
        onView(withId(R.id.edit_event)).perform(click())
        onView(withId(R.id.start_time)).perform(replaceText(time), closeSoftKeyboard())
        onView(withId(R.id.save_event)).perform(click())
    }

    private fun preferences() =
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences(StudyCalendarActivity.PREFS, 0)

    private fun storedMeetingMinutes(): Int = preferences().let { stored ->
        if (stored.contains(StudyCalendarActivity.KEY_MEETING_START_MINUTES)) {
            stored.getInt(
                StudyCalendarActivity.KEY_MEETING_START_MINUTES,
                StudyCalendarActivity.DEFAULT_MEETING_START_MINUTES,
            )
        } else {
            stored.getInt(
                StudyCalendarActivity.KEY_MEETING_START_HOUR,
                StudyCalendarActivity.DEFAULT_MEETING_START_HOUR,
            ) * 60
        }
    }

    private fun storedExamMinutes(): Int = preferences().getInt(
        StudyCalendarActivity.KEY_EXAM_START_MINUTES,
        StudyCalendarActivity.DEFAULT_EXAM_START_MINUTES,
    )
}
