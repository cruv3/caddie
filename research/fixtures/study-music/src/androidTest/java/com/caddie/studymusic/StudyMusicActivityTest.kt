package com.caddie.studymusic

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.pressImeActionButton
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class StudyMusicActivityTest {
    @get:Rule
    val rule = ActivityScenarioRule<Activity>(
        Intent().setComponent(ComponentName(PACKAGE_NAME, ACTIVITY_NAME)),
    )

    @Before
    fun resetLibrary() {
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        rule.scenario.recreate()
    }

    @Test
    fun searchFlowExposesOneSemanticTargetPerAction() {
        onView(withContentDescription("Suchen")).perform(click())
        assertSingleClickableTarget("Was möchtest du hören?")
        onView(withContentDescription("Was möchtest du hören?"))
            .perform(replaceText("As It Was"), pressImeActionButton())

        assertExposedClickableTargetCount("Was möchtest du hören?", 0)
        assertSingleClickableTarget("As It Was zu Meine Bibliothek hinzufügen")
        assertSingleClickableTarget("As It Was – Sped Up zu Meine Bibliothek hinzufügen")
        assertSingleClickableTarget("As ıt was slowed zu Meine Bibliothek hinzufügen")
        assertMinimumTouchTarget("As It Was zu Meine Bibliothek hinzufügen")
    }

    @Test
    fun addingAndResettingTrackChangesAccessibilityState() {
        searchForTracks()
        onView(withContentDescription("As It Was zu Meine Bibliothek hinzufügen")).perform(click())
        onView(withContentDescription("As It Was aus Meine Bibliothek entfernen"))
            .check(matches(isDisplayed()))
        assertSingleClickableTarget("As It Was aus Meine Bibliothek entfernen")

        rule.scenario.recreate()
        onView(withContentDescription("As It Was aus Meine Bibliothek entfernen"))
            .check(matches(isDisplayed()))
        assertSingleClickableTarget("As It Was aus Meine Bibliothek entfernen")

        sendResetBroadcast()

        onView(withContentDescription("As It Was zu Meine Bibliothek hinzufügen"))
            .check(matches(isDisplayed()))
        assertSingleClickableTarget("As It Was zu Meine Bibliothek hinzufügen")

        onView(withContentDescription("As It Was zu Meine Bibliothek hinzufügen")).perform(click())
        rule.scenario.moveToState(Lifecycle.State.CREATED)
        sendResetBroadcast()
        rule.scenario.moveToState(Lifecycle.State.RESUMED)

        onView(withContentDescription("As It Was zu Meine Bibliothek hinzufügen"))
            .check(matches(isDisplayed()))
        assertSingleClickableTarget("As It Was zu Meine Bibliothek hinzufügen")
    }

    @Test
    fun errorVariantResultIsAvailableAndIndependent() {
        searchForTracks()
        onView(withContentDescription("As It Was – Sped Up zu Meine Bibliothek hinzufügen"))
            .perform(click())

        onView(withContentDescription("As It Was – Sped Up aus Meine Bibliothek entfernen"))
            .check(matches(isDisplayed()))
        onView(withContentDescription("As It Was zu Meine Bibliothek hinzufügen"))
            .check(matches(isDisplayed()))
        assertSingleClickableTarget("As It Was – Sped Up aus Meine Bibliothek entfernen")
        assertSingleClickableTarget("As It Was zu Meine Bibliothek hinzufügen")
    }

    @Test
    fun polishedHomeShowsSixQuickItemsAndMiniPlayerInsideTheVisibleWindow() {
        rule.scenario.onActivity { activity ->
            val quickAccess = activity.findViewById<ViewGroup>(requiredViewId(activity, "quick_access"))
            assertTrue("Home must display six compact quick-access items", quickAccess.childCount >= 6)
            repeat(6) { index ->
                assertGloballyVisible(
                    quickAccess.getChildAt(index),
                    "Quick-access item ${index + 1}",
                )
            }
            assertGloballyVisible(
                activity.findViewById(requiredViewId(activity, "mini_player")),
                "Mini-player",
            )
        }
    }

    @Test
    fun polishedHomeCardsAndQuickItemsUseDrawableBackedImageViews() {
        rule.scenario.onActivity { activity ->
            val home = activity.findViewById<View>(R.id.home_screen)
            val homeImages = descendants(home).filterIsInstance<ImageView>().toList()
            assertTrue("Home needs local ImageView artwork and controls", homeImages.isNotEmpty())
            assertTrue("Every Home ImageView needs a drawable", homeImages.all { it.drawable != null })

            val quickAccess = activity.findViewById<ViewGroup>(requiredViewId(activity, "quick_access"))
            repeat(6) { index ->
                val quickItemImages = descendants(quickAccess.getChildAt(index))
                    .filterIsInstance<ImageView>()
                    .toList()
                assertTrue(
                    "Quick-access item ${index + 1} needs a drawable-backed cover ImageView",
                    quickItemImages.any { it.drawable != null },
                )
            }

            for (sectionId in listOf("recent_section", "made_for_you_section", "popular_section")) {
                val section = activity.findViewById<View>(requiredViewId(activity, sectionId))
                val images = descendants(section).filterIsInstance<ImageView>().toList()
                assertTrue("$sectionId needs cover ImageViews", images.isNotEmpty())
                assertTrue("$sectionId cover ImageViews need local drawables", images.all { it.drawable != null })
            }
        }
    }

    @Test
    fun bottomNavigationUsesDrawableIconsAndMovesTheGreenActiveTint() {
        rule.scenario.onActivity { activity ->
            assertDrawableNavigationIcons(activity)
            assertActiveNavigationTint(activity, "bottom_home_icon")
        }
        assertSingleClickableTarget("Startseite öffnen")
        assertSingleClickableTarget("Suchen")
        assertSingleClickableTarget("Meine Bibliothek öffnen")

        onView(withContentDescription("Suchen")).perform(click())
        rule.scenario.onActivity { activity ->
            assertActiveNavigationTint(activity, "bottom_search_icon")
        }
        onView(withContentDescription("Meine Bibliothek öffnen")).perform(click())
        rule.scenario.onActivity { activity ->
            assertActiveNavigationTint(activity, "bottom_library_icon")
        }
        onView(withContentDescription("Startseite öffnen")).perform(click())
        rule.scenario.onActivity { activity ->
            assertActiveNavigationTint(activity, "bottom_home_icon")
        }
    }

    @Test
    fun fullCatalogResultsUseDedicatedAndVisuallyDistinctCoverDrawables() {
        showAllTracks()
        rule.scenario.onActivity { activity ->
            val results = activity.findViewById<ViewGroup>(R.id.track_results)
            assertTrue("Search must expose at least twelve local catalog items", results.childCount >= 12)
            val trackCoverId = trackCoverId(activity)
            val covers = descendants(results).filter { it.id == trackCoverId }.toList()
            assertEquals("Every displayed track needs one dedicated cover view", results.childCount, covers.size)
            val coverImages = covers.map { cover ->
                assertTrue("Dedicated track covers must be ImageViews", cover is ImageView)
                cover as ImageView
            }
            assertTrue("Every result cover needs bundled artwork", coverImages.all { it.drawable != null })

            val visibleCovers = coverImages.filter(::isGloballyVisible)
            assertTrue("At least two result covers must be visible", visibleCovers.size >= 2)
            val distinctDrawables = visibleCovers.map { image ->
                image.drawable.constantState
                    ?: throw AssertionError("Visible result cover needs a comparable drawable state")
            }.distinct()
            assertTrue("Visible result covers must use multiple artworks", distinctDrawables.size >= 2)
        }
    }

    @Test
    fun unmatchedSearchShowsSearchEmptyStateWithoutTrackActions() {
        onView(withContentDescription("Suchen")).perform(click())
        onView(withContentDescription("Was möchtest du hören?"))
            .perform(replaceText("zzzz-no-track"), pressImeActionButton())

        rule.scenario.onActivity { activity ->
            val emptySearch = activity.findViewById<TextView>(
                requiredViewId(activity, "empty_search_results"),
            )
            assertGloballyVisible(emptySearch, "Search-specific empty state")
            assertEquals("Keine Ergebnisse gefunden", emptySearch.text.toString())

            val results = activity.findViewById<ViewGroup>(R.id.track_results)
            assertEquals("Unmatched search must not show track rows", 0, results.childCount)
            assertEquals(
                "Unmatched search must not expose add controls",
                0,
                descendants(results).count { view ->
                    view.isShown &&
                        view.isClickable &&
                        view.contentDescription?.endsWith("zu Meine Bibliothek hinzufügen") == true
                },
            )
        }
    }

    @Test
    fun fullCatalogTrackCanBeAddedOpenedInLibraryAndReturnedHome() {
        showAllTracks()
        onView(withContentDescription("Midnight Drive zu Meine Bibliothek hinzufügen")).perform(click())
        onView(withContentDescription("Meine Bibliothek öffnen")).perform(click())
        onView(withContentDescription("Midnight Drive aus Meine Bibliothek entfernen"))
            .check(matches(isDisplayed()))
        rule.scenario.onActivity { activity ->
            val results = activity.findViewById<ViewGroup>(R.id.track_results)
            assertEquals("Library must contain only the added track", 1, results.childCount)
            assertActiveNavigationTint(activity, "bottom_library_icon")
        }

        onView(withContentDescription("Startseite öffnen")).perform(click())
        rule.scenario.onActivity { activity ->
            assertGloballyVisible(activity.findViewById(R.id.home_screen), "Home screen")
            assertActiveNavigationTint(activity, "bottom_home_icon")
        }
    }

    private fun searchForTracks() {
        onView(withContentDescription("Suchen")).perform(click())
        onView(withContentDescription("Was möchtest du hören?"))
            .perform(replaceText("As It Was"), pressImeActionButton())
    }

    private fun showAllTracks() {
        onView(withContentDescription("Suchen")).perform(click())
        onView(withContentDescription("Was möchtest du hören?"))
            .perform(replaceText(""), pressImeActionButton())
    }

    private fun sendResetBroadcast() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val completed = CountDownLatch(1)
        var observedResultCode: Int? = null
        var observedResultData: String? = null
        context.sendOrderedBroadcast(
            Intent(ACTION_RESET).setPackage(context.packageName),
            null,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    observedResultCode = resultCode
                    observedResultData = resultData
                    completed.countDown()
                }
            },
            null,
            0,
            null,
            null,
        )
        assertTrue(completed.await(5, TimeUnit.SECONDS))
        assertEquals(1207, observedResultCode)
        assertEquals("music_reset_ok", observedResultData)
    }

    private fun assertSingleClickableTarget(description: String) {
        assertExposedClickableTargetCount(description, 1)
        rule.scenario.onActivity { activity ->
            val target = descendants(activity.window.decorView).single {
                it.isShown && it.isClickable && it.contentDescription == description
            }
            assertFalse(
                "Action target $description must not have a clickable ancestor",
                hasClickableAncestor(target),
            )
        }
    }

    private fun assertExposedClickableTargetCount(description: String, expectedCount: Int) {
        rule.scenario.onActivity { activity ->
            assertEquals(
                "Expected $expectedCount exposed actionable nodes for $description",
                expectedCount,
                descendants(activity.window.decorView)
                    .count { it.isShown && it.isClickable && it.contentDescription == description },
            )
        }
    }

    private fun hasClickableAncestor(view: View): Boolean {
        var parent = view.parent
        while (parent is View) {
            if (parent.isClickable) return true
            parent = parent.parent
        }
        return false
    }

    private fun assertMinimumTouchTarget(description: String) {
        rule.scenario.onActivity { activity ->
            val target = descendants(activity.window.decorView).single {
                it.isShown && it.isClickable && it.contentDescription == description
            }
            val minimumPixels = (48 * activity.resources.displayMetrics.density).toInt()
            assertTrue("$description width must be at least 48dp", target.width >= minimumPixels)
            assertTrue("$description height must be at least 48dp", target.height >= minimumPixels)
        }
    }

    private fun requiredViewId(activity: Activity, name: String): Int {
        val viewId = activity.resources.getIdentifier(name, "id", activity.packageName)
        assertTrue("Missing required view ID: $name", viewId != 0)
        return viewId
    }

    private fun trackCoverId(activity: Activity): Int = listOf("track_art", "track_cover")
        .map { name -> activity.resources.getIdentifier(name, "id", activity.packageName) }
        .firstOrNull { viewId -> viewId != 0 }
        ?: throw AssertionError("Missing dedicated track cover ID: track_art or track_cover")

    private fun assertDrawableNavigationIcons(activity: Activity) {
        for (iconId in navigationIconIds) {
            val icon = requiredImageView(activity, iconId)
            assertGloballyVisible(icon, iconId)
            assertTrue("$iconId must use a drawable", icon.drawable != null)
        }
    }

    private fun assertActiveNavigationTint(activity: Activity, activeIconId: String) {
        val green = activity.getColor(R.color.music_green)
        for (iconId in navigationIconIds) {
            val icon = requiredImageView(activity, iconId)
            val tint = icon.imageTintList
                ?: throw AssertionError("$iconId must expose an image tint")
            val currentTint = tint.getColorForState(icon.drawableState, tint.defaultColor)
            if (iconId == activeIconId) {
                assertEquals("$iconId must use Spotify green while active", green, currentTint)
            } else {
                assertTrue("$iconId must not use active green while inactive", currentTint != green)
            }
        }
    }

    private fun requiredImageView(activity: Activity, name: String): ImageView {
        val view = activity.findViewById<View>(requiredViewId(activity, name))
        assertTrue("$name must be an ImageView", view is ImageView)
        return view as ImageView
    }

    private fun assertGloballyVisible(view: View, label: String) {
        assertTrue("$label must occupy visible screen space", isGloballyVisible(view))
    }

    private fun isGloballyVisible(view: View): Boolean {
        val visibleBounds = Rect()
        return view.getGlobalVisibleRect(visibleBounds) && !visibleBounds.isEmpty
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is android.view.ViewGroup) {
            repeat(view.childCount) { index -> yieldAll(descendants(view.getChildAt(index))) }
        }
    }

    private companion object {
        const val PACKAGE_NAME = "com.caddie.studymusic"
        const val ACTIVITY_NAME = "$PACKAGE_NAME.StudyMusicActivity"
        const val ACTION_RESET = "$PACKAGE_NAME.ACTION_RESET"
        const val PREFS = "study_music_state"
        val navigationIconIds = listOf(
            "bottom_home_icon",
            "bottom_search_icon",
            "bottom_library_icon",
        )
    }
}
