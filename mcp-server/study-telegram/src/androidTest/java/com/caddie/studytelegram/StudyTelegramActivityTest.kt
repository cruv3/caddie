package com.caddie.studytelegram

import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.allOf
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.greaterThan
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyTelegramActivityTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(StudyTelegramActivity::class.java)

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
            .check(matches(withText("Hast du später kurz Zeit?")))
            .check(matches(not(withText("Song-Tipp: As It Was von Harry Styles."))))
    }

    @Test
    fun annaLikePreviewsDoNotRepeatContactNames() {
        onView(withId(R.id.chat_anne_preview))
            .check(matches(withText("16 Uhr passt")))
        onView(withId(R.id.chat_anni_preview))
            .check(matches(withText("Folien kommen gleich")))
    }

    @Test
    fun anneAndAnniChatsCanBeOpened() {
        onView(withId(R.id.chat_anne)).perform(click())
        onView(withId(R.id.chat_header_name)).check(matches(withText("Anne")))
        onView(withId(R.id.anne_message))
            .check(matches(isDisplayed()))
            .check(matches(withText("Kaffee um 16 Uhr passt")))

        onView(withId(R.id.btn_back_to_chats)).perform(click())
        onView(withId(R.id.chat_anni)).perform(click())
        onView(withId(R.id.chat_header_name)).check(matches(withText("Anni")))
        onView(withId(R.id.anni_message))
            .check(matches(isDisplayed()))
            .check(matches(withText("Ich schicke dir gleich die Folien")))
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

        onView(withText("Warst du schon in der neuen Cafeteria?"))
            .check(matches(isDisplayed()))
        onView(withText("Ja, gestern kurz nach dem Seminar."))
            .check(matches(isDisplayed()))
        onView(withId(R.id.lena_song_message))
            .check(matches(isDisplayed()))
            .check(matches(withText("Song-Tipp: As It Was von Harry Styles.")))
    }
}
