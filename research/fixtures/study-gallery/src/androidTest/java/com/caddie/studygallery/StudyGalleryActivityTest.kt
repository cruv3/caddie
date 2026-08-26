package com.caddie.studygallery

import android.content.Intent
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.hamcrest.Matchers.containsString

@RunWith(AndroidJUnit4::class)
class StudyGalleryActivityTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(StudyGalleryActivity::class.java)

    @Before
    fun resetState() {
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences(StudyGalleryActivity.PREFS, 0).edit().clear().commit()
        activityRule.scenario.recreate()
    }

    @Test
    fun gridShowsStableWhiteboardSelector() {
        onView(withId(R.id.gallery_grid)).check(matches(isDisplayed()))
        onView(withId(R.id.whiteboard_photo)).check(matches(withContentDescription("Caddie Whiteboard Photo Project Meeting")))
        onView(withText("Heute")).check(matches(isDisplayed()))
        onView(withId(R.id.library_photo)).check(matches(isDisplayed()))
        onView(withId(R.id.tram_photo)).check(matches(isDisplayed()))
        onView(withId(R.id.study_group_photo)).check(matches(isDisplayed()))
    }

    @Test
    fun whiteboardPhotoOpensCanonicalTaskText() {
        onView(withId(R.id.whiteboard_photo)).perform(click())
        onView(withId(R.id.whiteboard_image)).check(
            matches(
                org.hamcrest.Matchers.allOf(
                    isDisplayed(),
                    withContentDescription(containsString("Tuesday = Submit Report")),
                    withContentDescription(containsString("Thursday = Review Draft")),
                ),
            ),
        )
    }

    @Test
    fun resetBroadcastReturnsToGrid() {
        onView(withId(R.id.whiteboard_photo)).perform(click())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.sendBroadcast(Intent(StudyGalleryActivity.ACTION_RESET).setPackage(context.packageName))
        activityRule.scenario.recreate()
        onView(withId(R.id.gallery_grid)).check(matches(isDisplayed()))
    }
}
