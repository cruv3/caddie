package com.caddie.study.portal.http

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Verifies stable session-scoped participant browser capabilities. */
class StudyPortalHandoffsTest {
    @Test
    fun issuingANewHandoffRevokesThePreviousBrowser() {
        val handoffs = StudyPortalHandoffs()
        val first = handoffs.issue(7, "consent")

        val second = handoffs.issue(8, "task_card")

        assertNull(handoffs.resolve(first.token))
        assertEquals(8L, handoffs.resolve(second.token)?.sessionId)
    }

    @Test
    fun rotationUpdatesStateWithoutInvalidatingTheBrowserToken() {
        val handoffs = StudyPortalHandoffs()
        val first = handoffs.issue(7, "task_card")

        val rotated = handoffs.rotate(first.token, "trial_running")

        assertEquals(first.token, rotated.token)
        assertEquals(7L, handoffs.resolve(rotated.token)?.sessionId)
        assertEquals("trial_running", handoffs.resolve(rotated.token)?.workflowState)
    }

    @Test
    fun rotationRenewsTheActiveParticipantDeadline() {
        var now = 1_000L
        val handoffs = StudyPortalHandoffs(nowMillis = { now }, lifetimeMillis = 100L)
        val issued = handoffs.issue(7, "consent")

        now = 1_090L
        val rotated = handoffs.rotate(issued.token, "training")
        now = 1_150L

        assertEquals(1_190L, rotated.expiresAtMillis)
        assertEquals("training", handoffs.resolve(issued.token)?.workflowState)
    }

    @Test
    fun recoveringAnActiveHandoffReplacesTheLostBrowserToken() {
        val handoffs = StudyPortalHandoffs()
        val issued = handoffs.issue(7, "task_card")

        val recovered = handoffs.recoverActive(issued.token)

        assertNotEquals(issued.token, recovered?.token)
        assertNull(handoffs.resolve(issued.token))
        assertEquals(7L, handoffs.resolve(recovered?.token)?.sessionId)
        assertEquals("task_card", recovered?.workflowState)
    }
}
