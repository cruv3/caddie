package com.caddie.studycalendar

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyModesActivityTest {
    @get:Rule
    val rule = ActivityScenarioRule(StudyModesActivity::class.java)

    @Before
    fun reset() {
        clearPreferences()
        rule.scenario.recreate()
    }

    @After
    fun cleanUp() {
        clearPreferences()
    }

    @Test
    fun defaultScreenShowsNoRule() {
        onView(withId(R.id.modes_rule_summary))
            .check(matches(withText("Keine Regel eingerichtet")))
    }

    @Test
    fun newRuleRequiresAnExplicitTimeSelection() {
        onView(withId(R.id.create_dnd_rule)).perform(click())

        onView(withId(R.id.dnd_start_time)).check(matches(withText("")))
        onView(withId(R.id.dnd_end_time)).check(matches(withText("")))
        onView(withId(R.id.save_dnd_rule)).check(matches(not(isEnabled())))
    }

    @Test
    fun savesCorrectExamRange() {
        onView(withId(R.id.create_dnd_rule)).perform(click())
        onView(withId(R.id.dnd_start_time)).perform(replaceText("10:00"), closeSoftKeyboard())
        onView(withId(R.id.dnd_end_time)).check(matches(withText("11:00")))
        onView(withId(R.id.save_dnd_rule)).perform(click())

        onView(withId(R.id.modes_rule_summary)).check(
            matches(withText(containsString("Morgen · 10:00–11:00 Uhr"))),
        )
    }

    @Test
    fun savesControlledErrorRange() {
        onView(withId(R.id.create_dnd_rule)).perform(click())
        onView(withId(R.id.dnd_start_time)).perform(replaceText("12:00"), closeSoftKeyboard())
        onView(withId(R.id.dnd_end_time)).check(matches(withText("13:00")))
        onView(withId(R.id.save_dnd_rule)).perform(click())

        onView(withId(R.id.modes_rule_summary)).check(
            matches(withText(containsString("Morgen · 12:00–13:00 Uhr"))),
        )
    }

    private fun clearPreferences() {
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences(StudyCalendarActivity.PREFS, 0)
            .edit()
            .clear()
            .commit()
    }
}
