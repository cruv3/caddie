package com.caddie.studybank

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.TextView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewAssertion
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class BankingActivityTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(BankingActivity::class.java)

    @Test
    fun initialHomeShowsStudyAccountAndTransferAction() {
        onView(withText("Girokonto")).check(matches(isDisplayed()))
        onView(withText("40,00 €")).check(matches(isDisplayed()))
        onView(withText("Überweisung")).check(matches(isDisplayed()))
    }

    @Test
    fun transferFieldsKeepReadableTextAndHintsInDarkMode() {
        onView(withText("Überweisung")).perform(click())

        listOf(
            R.id.transfer_recipient,
            R.id.transfer_iban,
            R.id.transfer_amount,
            R.id.purpose_text,
        ).forEach { fieldId ->
            onView(withId(fieldId))
                .check(hasCurrentTextColor(Color.rgb(32, 33, 36)))
                .check(hasHintTextColor(Color.rgb(107, 111, 118)))
        }
    }

    @Test
    fun polishedTransferAreaShowsDetailsCardAndSecurityNote() {
        onView(withText("Überweisung")).perform(click())

        onView(withText("Zahlungsdetails")).check(matches(isDisplayed()))
        onView(withText("Sicher über die Sparkasse Demo")).check(matches(isDisplayed()))
    }

    @Test
    fun transferScreenReservesSystemBarSpace() {
        onView(withText("Überweisung")).perform(click())

        onView(withId(R.id.transfer_screen)).check { view, error ->
            if (view == null) throw error
            org.junit.Assert.assertTrue("Expected top inset padding", view.paddingTop > 0)
            org.junit.Assert.assertTrue("Expected bottom inset padding", view.paddingBottom > 0)
        }
    }

    @Test
    fun correctTransferReturnsHomeWithUpdatedBalanceAndTransaction() {
        completeTransfer(amount = "30,00")

        onView(withText("10,00 €")).check(matches(isDisplayed()))
        onView(withText("Study Vendor GmbH")).check(matches(isDisplayed()))
        onView(withText("−30,00 €")).check(matches(isDisplayed()))
    }

    @Test
    fun injectedErrorAllowsOverdraftAndShowsNegativeValuesInRed() {
        completeTransfer(amount = "80,00")

        onView(withText("−40,00 €"))
            .check(matches(isDisplayed()))
            .check(hasCurrentTextColor(Color.rgb(200, 16, 30)))
        onView(withText("−80,00 €"))
            .check(matches(isDisplayed()))
            .check(hasCurrentTextColor(Color.rgb(200, 16, 30)))
    }

    @Test
    fun resetClearsAllParticipantDataAndRestoresInitialState() {
        completeTransfer(amount = "30,00")

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resetFinished = CountDownLatch(1)
        context.sendOrderedBroadcast(
            Intent(BankingActivity.ACTION_RESET).setPackage(context.packageName),
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
        org.junit.Assert.assertTrue(resetFinished.await(2, TimeUnit.SECONDS))

        onView(withText("40,00 €")).check(matches(isDisplayed()))
        onView(withText("Noch keine Überweisung in dieser Sitzung"))
            .check(matches(isDisplayed()))
        onView(withText("Überweisung")).check(matches(isDisplayed()))
    }

    @Test
    fun homeDoesNotExposeParticipantResetControl() {
        onView(withText("Zurücksetzen")).check(doesNotExist())
    }

    private fun completeTransfer(amount: String) {
        onView(withText("Überweisung")).perform(click())
        onView(withId(R.id.transfer_recipient)).perform(replaceText("Study Vendor GmbH"))
        onView(withId(R.id.transfer_iban)).perform(replaceText("DE02120300000000202051"))
        onView(withId(R.id.transfer_amount)).perform(replaceText(amount))
        onView(withId(R.id.purpose_text)).perform(replaceText("Rechnung INV-2026-001"))
        onView(withText("Weiter")).perform(click())
        onView(withText("Überweisung senden")).perform(click())
    }

    private fun hasCurrentTextColor(expectedColor: Int): ViewAssertion = ViewAssertion { view, error ->
        if (view == null) throw error
        val textView = view as? TextView
            ?: throw AssertionError("Expected TextView but was ${view.javaClass.simpleName}")
        if (textView.currentTextColor != expectedColor) {
            throw AssertionError(
                "Expected text color ${colorHex(expectedColor)} but was ${colorHex(textView.currentTextColor)}"
            )
        }
    }

    private fun hasHintTextColor(expectedColor: Int): ViewAssertion = ViewAssertion { view, error ->
        if (view == null) throw error
        val textView = view as? TextView
            ?: throw AssertionError("Expected TextView but was ${view.javaClass.simpleName}")
        val actualColor = textView.hintTextColors.defaultColor
        if (actualColor != expectedColor) {
            throw AssertionError(
                "Expected hint color ${colorHex(expectedColor)} but was ${colorHex(actualColor)}"
            )
        }
    }

    private fun colorHex(color: Int): String = String.format("#%08X", color)
}
