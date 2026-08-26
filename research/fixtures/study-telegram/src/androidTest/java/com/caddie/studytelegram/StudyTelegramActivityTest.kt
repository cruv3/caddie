package com.caddie.studytelegram

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.allOf
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.greaterThan
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull

@RunWith(AndroidJUnit4::class)
class StudyTelegramActivityTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(StudyTelegramActivity::class.java)

    @Before
    fun resetState() {
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences(StudyTelegramActivity.PREFS, 0)
            .edit()
            .clear()
            .commit()
        activityRule.scenario.recreate()
    }

    @Test
    fun chatListShowsAmbiguousAnnaLikeContacts() {
        onView(allOf(withText("Anna"), isDisplayed())).check(matches(isDisplayed()))
        onView(allOf(withText("Anne"), isDisplayed())).check(matches(isDisplayed()))
        onView(allOf(withText("Anni"), isDisplayed())).check(matches(isDisplayed()))
        onView(allOf(withText("Lena"), isDisplayed())).check(matches(isDisplayed()))
    }

    @Test
    fun chatListDoesNotExposeFullSongRecommendation() {
        onView(withId(R.id.chat_lena_preview))
            .check(matches(withText("nice, hör ich später rein")))
            .check(matches(not(withText("Song-Tipp: As It Was von Harry Styles."))))
    }

    @Test
    fun annaLikePreviewsDoNotRepeatContactNames() {
        onView(withId(R.id.chat_anne_preview))
            .check(matches(withText("bis dann")))
        onView(withId(R.id.chat_anni_preview))
            .check(matches(withText("perfekt")))
    }

    @Test
    fun anneAndAnniChatsCanBeOpened() {
        onView(withId(R.id.chat_anne)).perform(click())
        onView(withId(R.id.chat_header_name)).check(matches(withText("Anne")))
        onView(withText("Kaffee um 16 Uhr passt"))
            .check(matches(isDisplayed()))

        onView(withId(R.id.btn_back_to_chats)).perform(click())
        onView(withId(R.id.chat_anni)).perform(click())
        onView(withId(R.id.chat_header_name)).check(matches(withText("Anni")))
        onView(withText("Ich schicke dir gleich die Folien"))
            .check(matches(isDisplayed()))
    }

    @Test
    fun topAndBottomControlsReserveSystemBarSpace() {
        activityRule.scenario.onActivity { activity ->
            val chatList = activity.findViewById<View>(R.id.screen_chats)
            assertThat(chatList.paddingTop, greaterThan(0))

            activity.findViewById<View>(R.id.chat_anna).performClick()

            val input = activity.findViewById<EditText>(R.id.message_input)
            val inputBar = input.parent as ViewGroup
            assertThat(inputBar.paddingBottom, greaterThan(0))
        }
    }

    @Test
    fun lenaChatContainsConversationBeforeSongRecommendation() {
        onView(withId(R.id.chat_lena)).perform(click())

        onView(withText("Song-Tipp: As It Was von Harry Styles."))
            .check(matches(isDisplayed()))
    }

    @Test
    fun lenaOpensAtLatestRecommendationWithNaturalContext() {
        onView(withId(R.id.chat_lena)).perform(click())

        onView(allOf(withText("Song-Tipp: As It Was von Harry Styles."), isDisplayed()))
            .check(matches(isDisplayed()))
        onView(allOf(withText("nice, hör ich später rein"), isDisplayed()))
            .check(matches(isDisplayed()))
    }

    @Test
    fun annaCanSendAfterSeedHistoryAndResetRemovesOnlySentMessage() {
        onView(withId(R.id.chat_anna)).perform(click())
        onView(withId(R.id.message_input)).perform(androidx.test.espresso.action.ViewActions.typeText("Bin in 45 Min. da"))
        onView(withId(R.id.btn_send)).perform(click())
        onView(withText("Bin in 45 Min. da")).check(matches(isDisplayed()))

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resetFinished = CountDownLatch(1)
        context.sendOrderedBroadcast(
            Intent(StudyTelegramActivity.ACTION_RESET).setPackage(context.packageName),
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
        activityRule.scenario.onActivity { activity ->
            activity.findViewById<View>(R.id.chat_anna).performClick()
        }

        onView(withText("Bin in 45 Min. da")).check(doesNotExist())
        onView(allOf(withText("Sag mir kurz, wann du ungefähr in Deutz ankommst."), isDisplayed()))
            .check(matches(isDisplayed()))
    }

    @Test
    fun annaSentTimestampSurvivesNavigationAndRecreation() {
        onView(withId(R.id.chat_anna)).perform(click())
        onView(withId(R.id.message_input)).perform(androidx.test.espresso.action.ViewActions.typeText("Bin in 45 Min. da"))
        onView(withId(R.id.btn_send)).perform(click())
        val originalTime = savedAnnaTimestamp()
        assertNotNull(originalTime)

        onView(withId(R.id.btn_back_to_chats)).perform(click())
        activityRule.scenario.onActivity { activity ->
            activity.findViewById<View>(R.id.chat_anna).performClick()
        }
        assertEquals(originalTime, savedAnnaTimestamp())

        activityRule.scenario.recreate()
        assertEquals(originalTime, savedAnnaTimestamp())
    }

    @Test
    fun bottomChatsCanBeScrolledToAndOpened() {
        revealChat(R.id.chat_project)
        onView(withId(R.id.chat_project)).perform(click())
        onView(withId(R.id.chat_header_name)).check(matches(withText("Projektgruppe")))

        onView(withId(R.id.btn_back_to_chats)).perform(click())
        revealChat(R.id.chat_mila)
        onView(withId(R.id.chat_mila)).perform(click())
        onView(withId(R.id.chat_header_name)).check(matches(withText("Mila")))

        onView(withId(R.id.btn_back_to_chats)).perform(click())
        revealChat(R.id.chat_jonas)
        onView(withId(R.id.chat_jonas)).perform(click())
        onView(withId(R.id.chat_header_name)).check(matches(withText("Jonas")))
    }

    @Test
    fun recreationRestoresTheOpenChatWithoutShowingAnnaInput() {
        onView(withId(R.id.chat_lena)).perform(click())
        activityRule.scenario.recreate()

        onView(withId(R.id.chat_header_name)).check(matches(withText("Lena")))
        onView(allOf(withText("nice, hör ich später rein"), isDisplayed()))
            .check(matches(isDisplayed()))
        onView(withId(R.id.chat_input_bar)).check(matches(not(isDisplayed())))
    }

    private fun savedAnnaTimestamp(): String? =
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences(StudyTelegramActivity.PREFS, 0)
            .getString("last_message_time", null)

    private fun revealChat(chatId: Int) {
        activityRule.scenario.onActivity { activity ->
            activity.findViewById<View>(chatId).requestRectangleOnScreen(
                Rect(0, 0, activity.findViewById<View>(chatId).width, activity.findViewById<View>(chatId).height),
                true,
            )
        }
    }
}
