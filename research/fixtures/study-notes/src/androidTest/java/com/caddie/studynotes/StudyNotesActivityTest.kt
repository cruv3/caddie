package com.caddie.studynotes

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.not
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class StudyNotesActivityTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(StudyNotesActivity::class.java)

    @Before
    fun reset() {
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences(StudyNotesActivity.PREFS, 0).edit().clear().commit()
        activityRule.scenario.recreate()
    }

    @Test
    fun realisticListShowsSeedNotesAndCreateAction() {
        onView(withText("Klausur lernen")).check(matches(isDisplayed()))
        onView(withText("Einkaufsliste")).check(matches(isDisplayed()))
        onView(withText("Literatur für die Hausarbeit")).check(matches(isDisplayed()))
        onView(withId(R.id.create_note)).check(matches(withText("Notiz erstellen")))
    }

    @Test
    fun noteIsSavedAndShownAfterBack() {
        val note = "Projekt: Bericht Dienstag abgeben; Entwurf Donnerstag prüfen"
        onView(withId(R.id.create_note)).perform(click())
        onView(withId(R.id.note_text)).perform(replaceText(note))
        activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        onView(withId(R.id.saved_note)).check(matches(withText(note)))
    }

    @Test
    fun savedNoteCanBeOpenedAndRewrittenWithoutCreatingAnotherNote() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val wrongNote = "Projekt: Bericht Dienstag abgeben; Entwurf Dienstag prüfen"
        val correctedNote = "Projekt: Bericht Dienstag abgeben; Entwurf Donnerstag prüfen"
        context.getSharedPreferences(StudyNotesActivity.PREFS, 0).edit()
            .putString(StudyNotesActivity.KEY_NOTE, wrongNote).commit()
        activityRule.scenario.recreate()

        onView(withId(R.id.saved_note)).perform(click())
        onView(withId(R.id.note_text)).check(matches(withText(wrongNote)))
        onView(withId(R.id.note_text)).perform(replaceText(correctedNote))
        activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        onView(withId(R.id.saved_note)).check(matches(withText(correctedNote)))
    }

    @Test
    fun resetBroadcastDeletesSavedNote() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences(StudyNotesActivity.PREFS, 0).edit()
            .putString(StudyNotesActivity.KEY_NOTE, "Study note").commit()
        val resetFinished = CountDownLatch(1)
        context.sendOrderedBroadcast(
            Intent(StudyNotesActivity.ACTION_RESET).setPackage(context.packageName),
            null,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = resetFinished.countDown()
            },
            null,
            0,
            null,
            null,
        )
        assertTrue(resetFinished.await(2, TimeUnit.SECONDS))
        activityRule.scenario.recreate()
        onView(withText("Klausur lernen")).check(matches(isDisplayed()))
        onView(withText("Einkaufsliste")).check(matches(isDisplayed()))
        onView(withId(R.id.saved_note)).check(matches(not(isDisplayed())))
    }
}
