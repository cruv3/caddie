package com.caddie.study.runtime.coordinator

import com.caddie.study.runtime.model.CriticalityClass
import com.caddie.study.runtime.model.RuntimeStudyCondition
import com.caddie.study.runtime.model.StepType
import com.caddie.study.runtime.model.StudyStep
import com.caddie.study.runtime.model.TriggerContract
import com.caddie.study.runtime.model.TrialSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ArmedTrialCoordinatorTest {

    private val trigger = TriggerContract(
        referencePhrases = listOf("Finde die Rechnung im Posteingang und bezahle in der Banking-App."),
        requiredConcepts = listOf(
            listOf("rechnung"),
            listOf("posteingang"),
            listOf("banking"),
        ),
        forbiddenConcepts = listOf("termin"),
        wakeWords = listOf("jarvis"),
    )

    private val spec = TrialSpec(
        version = "v1",
        id = "task_banking_payment",
        instructionDe = "instr",
        criticality = CriticalityClass.HIGH,
        steps = listOf(StudyStep("s1", "click", "narration", StepType.NORMAL)),
        errorSteps = listOf("s1"),
        trigger = trigger,
    )

    private fun config(condition: RuntimeStudyCondition = RuntimeStudyCondition.STEPWISE) =
        ArmedTrialCoordinator.ArmedTrialConfig(
            participantId = "P01",
            trialIndex = 0,
            taskId = "task_banking_payment",
            condition = condition,
            injectError = true,
        )

    @Test
    fun `route on idle coordinator passes through`() {
        val c = ArmedTrialCoordinator()
        val r = c.routeAndClaim("hello")
        assertEquals(ArmedTrialCoordinator.CoordinatorDecision.PASS_THROUGH, r.decision)
        assertEquals("idle", r.reason)
    }

    @Test
    fun `live study mode never lets an unarmed utterance reach normal agent`() {
        val c = ArmedTrialCoordinator()
        c.setStudyMode(active = true)

        val r = c.routeAndClaim("Jarvis erledige die Studienaufgabe")

        assertEquals(ArmedTrialCoordinator.CoordinatorDecision.RETRY, r.decision)
        assertEquals("study_not_armed", r.reason)
    }

    @Test
    fun `leaving live study mode restores normal pass through`() {
        val c = ArmedTrialCoordinator()
        c.setStudyMode(active = true)
        c.setStudyMode(active = false)

        assertEquals(
            ArmedTrialCoordinator.CoordinatorDecision.PASS_THROUGH,
            c.routeAndClaim("hello").decision,
        )
    }

    @Test
    fun `arm then claim on matching utterance moves to running`() {
        val c = ArmedTrialCoordinator()
        c.arm(config(), spec)
        val r = c.routeAndClaim("Jarvis, finde die Rechnung im Posteingang und bezahle in der Banking-App")
        assertEquals(ArmedTrialCoordinator.CoordinatorDecision.CLAIMED, r.decision)
        assertNotNull(r.claim)
        assertEquals(ArmedTrialCoordinator.ArmedState.RUNNING, c.status().state)
    }

    @Test
    fun `retry on non matching utterance keeps armed state`() {
        val c = ArmedTrialCoordinator()
        c.arm(config(), spec)
        val r = c.routeAndClaim("was gibt es heute")
        assertEquals(ArmedTrialCoordinator.CoordinatorDecision.RETRY, r.decision)
        assertNull(r.claim)
        assertEquals(ArmedTrialCoordinator.ArmedState.ARMED, c.status().state)
    }

    @Test
    fun `running input returns claim and does not re claim`() {
        val c = ArmedTrialCoordinator()
        c.arm(config(), spec)
        val first = c.routeAndClaim("Jarvis, finde die Rechnung im Posteingang und bezahle in der Banking-App")
        val second = c.routeAndClaim("und jetzt klicke auf senden")
        assertEquals(ArmedTrialCoordinator.CoordinatorDecision.RUNNING_INPUT, second.decision)
        assertEquals(first.claim!!.token, second.claim!!.token)
    }

    @Test
    fun `finish success with owning claim completes`() {
        val c = ArmedTrialCoordinator()
        c.arm(config(), spec)
        val claim = c.routeAndClaim("Jarvis, finde die Rechnung im Posteingang und bezahle in der Banking-App").claim!!
        c.finishSuccess(claim)
        assertEquals(ArmedTrialCoordinator.ArmedState.COMPLETED, c.status().state)
    }

    @Test
    fun `release owning claim returns running trial to armed`() {
        val c = ArmedTrialCoordinator()
        c.arm(config(), spec)
        val claim = c.routeAndClaim(
            "Jarvis, finde die Rechnung im Posteingang und bezahle in der Banking-App",
        ).claim!!

        c.releaseClaimForRetry(claim, "native runtime busy")

        assertEquals(ArmedTrialCoordinator.ArmedState.ARMED, c.status().state)
        assertEquals("native runtime busy", c.status().reason)
    }

    @Test
    fun `finish failure records reason`() {
        val c = ArmedTrialCoordinator()
        c.arm(config(), spec)
        val claim = c.routeAndClaim("Jarvis, finde die Rechnung im Posteingang und bezahle in der Banking-App").claim!!
        c.finishFailure(claim, "user aborted run")
        assertEquals(ArmedTrialCoordinator.ArmedState.FAILED, c.status().state)
        assertEquals("user aborted run", c.status().reason)
    }

    @Test
    fun `finish with stale claim token rejected`() {
        val c = ArmedTrialCoordinator()
        c.arm(config(), spec)
        c.routeAndClaim("Jarvis, finde die Rechnung im Posteingang und bezahle in der Banking-App")
        val stale = ArmedTrialCoordinator.ClaimedTrial(
            config(), spec, "x", ArmedTrialCoordinator.ClaimToken(999),
        )
        assertThrows(ArmedTrialCoordinator.InvalidTransitionError::class.java) {
            c.finishSuccess(stale)
        }
    }

    @Test
    fun `abort from armed returns true and sets aborted`() {
        val c = ArmedTrialCoordinator()
        c.arm(config(), spec)
        assertTrue(c.abort("investigator stopped"))
        assertEquals(ArmedTrialCoordinator.ArmedState.ABORTED, c.status().state)
    }

    @Test
    fun `abort twice returns false`() {
        val c = ArmedTrialCoordinator()
        c.arm(config(), spec)
        c.abort("stop")
        assertFalse(c.abort("again"))
    }

    @Test
    fun `abort if active atomically aborts an armed trial`() {
        val c = ArmedTrialCoordinator()
        c.arm(config(), spec)

        assertTrue(c.abortIfActive("portal stopped"))
        assertEquals(ArmedTrialCoordinator.ArmedState.ABORTED, c.status().state)
        assertEquals("portal stopped", c.status().reason)
    }

    @Test
    fun `abort if active ignores missing and terminal trials`() {
        val c = ArmedTrialCoordinator()
        assertFalse(c.abortIfActive("process restarted"))

        c.arm(config(), spec)
        val claim = c.routeAndClaim("Jarvis, finde die Rechnung im Posteingang und bezahle in der Banking-App").claim!!
        c.finishSuccess(claim)

        assertFalse(c.abortIfActive("portal caught up"))
        assertEquals(ArmedTrialCoordinator.ArmedState.COMPLETED, c.status().state)
    }

    @Test
    fun `clear after completion resets coordinator`() {
        val c = ArmedTrialCoordinator()
        c.arm(config(), spec)
        val claim = c.routeAndClaim("Jarvis, finde die Rechnung im Posteingang und bezahle in der Banking-App").claim!!
        c.finishSuccess(claim)
        c.clear()
        assertNull(c.status().state)
    }

    @Test
    fun `clear on active trial rejected`() {
        val c = ArmedTrialCoordinator()
        c.arm(config(), spec)
        assertThrows(ArmedTrialCoordinator.InvalidTransitionError::class.java) { c.clear() }
    }

    @Test
    fun `arm while active throws conflict`() {
        val c = ArmedTrialCoordinator()
        c.arm(config(), spec)
        assertThrows(ArmedTrialCoordinator.CoordinatorConflictError::class.java) {
            c.arm(config(), spec)
        }
    }

    @Test
    fun `arm rejects task id mismatch`() {
        val c = ArmedTrialCoordinator()
        val bad = config().copy(taskId = "wrong_task")
        assertThrows(IllegalArgumentException::class.java) { c.arm(bad, spec) }
    }

    @Test
    fun `classifier fallback claims when router retries`() {
        val c = ArmedTrialCoordinator(taskClassifier = { _ -> "task_banking_payment" })
        c.arm(config(), spec)
        // utterance that doesn't fully match trigger but classifier returns the spec id
        val r = c.routeAndClaim("bezahl das jetzt einfach")
        assertEquals(ArmedTrialCoordinator.CoordinatorDecision.CLAIMED, r.decision)
        assertEquals("classified", r.reason)
        assertNotNull(r.claim)
    }

    @Test
    fun `attempts are recorded for every route call`() {
        val c = ArmedTrialCoordinator()
        c.arm(config(), spec)
        c.routeAndClaim("hello")
        c.routeAndClaim("Jarvis, finde die Rechnung im Posteingang und bezahle in der Banking-App")
        c.routeAndClaim("und senden")
        assertEquals(3, c.status().attemptCount)
        assertEquals(3, c.attempts().size)
    }

    @Test
    fun `terminal state is fail closed while study mode remains live`() {
        val c = ArmedTrialCoordinator()
        c.setStudyMode(active = true)
        c.arm(config(), spec)
        c.abort("done")
        val r = c.routeAndClaim("any text")
        assertEquals(ArmedTrialCoordinator.CoordinatorDecision.RETRY, r.decision)
        assertEquals("trial_aborted", r.reason)
    }
}
