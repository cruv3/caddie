package com.caddie.studycalendar

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

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

    private fun preferences() =
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences(StudyCalendarActivity.PREFS, 0)
}
