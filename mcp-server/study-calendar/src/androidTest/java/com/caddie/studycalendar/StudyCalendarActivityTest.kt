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
}
