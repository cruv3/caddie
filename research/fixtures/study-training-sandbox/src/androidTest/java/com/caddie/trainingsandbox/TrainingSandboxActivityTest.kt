package com.caddie.trainingsandbox

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrainingSandboxActivityTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(TrainingSandboxActivity::class.java)

    @Before
    fun reset() {
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences(TrainingSandboxActivity.PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        activityRule.scenario.recreate()
    }

    @Test
    fun startsEmptyAndOffersNewPackingList() {
        onView(withId(R.id.empty_state)).check(matches(isDisplayed()))
        onView(withText("Noch keine Packliste gespeichert")).check(matches(isDisplayed()))
        onView(withId(R.id.new_list)).check(matches(withText("Neue Packliste")))
    }

    @Test
    fun listCanBeEditedBeforeItIsSaved() {
        openDraft()
        onView(withId(R.id.list_title)).perform(replaceText("Tagesausflug"))
        onView(withId(R.id.list_items))
            .perform(replaceText("Wasser\nSnacks\nKopfhörer"), closeSoftKeyboard())
        onView(withId(R.id.list_items))
            .perform(replaceText("Wasser\nSnacks\nPowerbank"), closeSoftKeyboard())
        onView(withId(R.id.save_list)).perform(click())

        onView(withId(R.id.saved_title)).check(matches(withText("Tagesausflug")))
        onView(withId(R.id.saved_items)).check(matches(withText("Wasser\nSnacks\nPowerbank")))
        onView(withText(containsString("Kopfhörer"))).check(doesNotExist())
    }

    @Test
    fun savedPackingListPersistsAfterRecreation() {
        openDraft()
        onView(withId(R.id.list_title)).perform(replaceText("Tagesausflug"))
        onView(withId(R.id.list_items))
            .perform(replaceText("Wasser\nSnacks\nPowerbank"), closeSoftKeyboard())
        onView(withId(R.id.save_list)).perform(click())

        activityRule.scenario.recreate()

        onView(withId(R.id.saved_state)).check(matches(isDisplayed()))
        onView(withId(R.id.saved_title)).check(matches(withText("Tagesausflug")))
        onView(withId(R.id.saved_items)).check(matches(withText("Wasser\nSnacks\nPowerbank")))
    }

    @Test
    fun secondDraftCanBeDiscardedWithoutReplacingSavedList() {
        openDraft()
        onView(withId(R.id.list_title)).perform(replaceText("Tagesausflug"))
        onView(withId(R.id.list_items))
            .perform(replaceText("Wasser\nSnacks\nPowerbank"), closeSoftKeyboard())
        onView(withId(R.id.save_list)).perform(click())

        onView(withId(R.id.new_list)).perform(click())
        onView(withId(R.id.list_title)).perform(replaceText("Zweite Liste"))
        onView(withId(R.id.list_items))
            .perform(replaceText("Nicht speichern"), closeSoftKeyboard())
        onView(withId(R.id.discard_draft)).perform(click())

        onView(withId(R.id.saved_title)).check(matches(withText("Tagesausflug")))
        onView(withId(R.id.saved_items)).check(matches(withText("Wasser\nSnacks\nPowerbank")))
        onView(withText("Zweite Liste")).check(matches(not(isDisplayed())))
    }

    @Test
    fun resetBroadcastReturnsToEmptyState() {
        openDraft()
        onView(withId(R.id.list_title)).perform(replaceText("Tagesausflug"))
        onView(withId(R.id.list_items))
            .perform(replaceText("Wasser\nSnacks\nPowerbank"), closeSoftKeyboard())
        onView(withId(R.id.save_list)).perform(click())

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resetFinished = CountDownLatch(1)
        context.sendOrderedBroadcast(
            Intent(TrainingSandboxActivity.ACTION_RESET).setPackage(context.packageName),
            null,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) =
                    resetFinished.countDown()
            },
            null,
            0,
            null,
            null,
        )
        assertTrue(resetFinished.await(2, TimeUnit.SECONDS))
        activityRule.scenario.recreate()

        onView(withId(R.id.empty_state)).check(matches(isDisplayed()))
        onView(withText("Noch keine Packliste gespeichert")).check(matches(isDisplayed()))
    }

    private fun openDraft() {
        onView(withId(R.id.new_list)).perform(click())
        onView(withId(R.id.editor)).check(matches(isDisplayed()))
    }
}
