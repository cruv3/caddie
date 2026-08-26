package com.caddie.study

import com.caddie.agent.core.ModelDelta
import com.caddie.agent.core.OversightDecision
import com.caddie.agent.core.ToolCallId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NativeStudyGateTest {

    private var lastPending: PendingConfirmation? = null
        @Synchronized get
        @Synchronized set

    private fun enqueueCallback(pending: PendingConfirmation) {
        synchronized(this) { lastPending = pending }
    }

    private val gate = NativeStudyGate(
        onConfirmationRequested = ::enqueueCallback,
        timeoutMs = 10_000L,
    )

    @Before
    fun resetState() {
        synchronized(this) { lastPending = null }
    }

    private fun tap(): ModelDelta.ToolCall = ModelDelta.ToolCall(
        ToolCallId("call-1"),
        "smartphone_tap_coordinates",
        """{"x": 100, "y": 200}""",
    )

    private fun tapButton(): ModelDelta.ToolCall = ModelDelta.ToolCall(
        ToolCallId("call-2"),
        "smartphone_press_button",
        """{"button": "Home", "why": "Going home"}""",
    )

    // --- confirmStep ---

    @Test
    fun `confirmStep creates pending confirmation`() = runTest {
        val job = async { gate.confirmStep(tap()) }
        runCurrent()

        val pending = synchronized(this@NativeStudyGateTest) { lastPending }!!
        assertEquals("call-1", pending.callId.value)
        assertEquals("smartphone_tap_coordinates", pending.toolName)
        assertFalse(pending.isBatch)

        // Complete to avoid UncompletedCoroutinesError
        pending.approve()
        job.await()
    }

    @Test
    fun `confirmStep resolves with approved on approve`() = runTest {
        val deferred = async { gate.confirmStep(tap()) }
        runCurrent()

        val pending = synchronized(this@NativeStudyGateTest) { lastPending }!!
        pending.approve()

        val decision = deferred.await()
        assertTrue(decision.approved)
    }

    @Test
    fun `approved confirmation waits for overlay dismissal before resuming`() = runTest {
        val settlingGate = NativeStudyGate(
            onConfirmationRequested = ::enqueueCallback,
            timeoutMs = 10_000L,
            approvalSettleMs = 200L,
        )
        val result = async { settlingGate.confirmStep(tap()) }
        runCurrent()

        val pending = synchronized(this@NativeStudyGateTest) { lastPending }!!
        pending.approve()
        runCurrent()

        assertFalse(result.isCompleted)
        advanceTimeBy(199L)
        runCurrent()
        assertFalse(result.isCompleted)
        advanceTimeBy(1L)
        runCurrent()
        assertTrue(result.isCompleted)
        assertTrue(result.await().approved)
    }

    @Test
    fun `confirmStep resolves with declined on decline`() = runTest {
        val deferred = async { gate.confirmStep(tap()) }
        runCurrent()

        val pending = synchronized(this@NativeStudyGateTest) { lastPending }!!
        assertTrue(gate.hasPendingConfirmation)
        pending.decline(reason = "user said no")

        val decision = deferred.await()
        assertFalse(decision.approved)
        assertEquals("user said no", decision.reason)
        assertFalse(gate.hasPendingConfirmation)
    }

    @Test
    fun `confirmStep auto declines when participant does not answer`() = runTest {
        val timeoutGate = NativeStudyGate(
            onConfirmationRequested = ::enqueueCallback,
            timeoutMs = 1_000L,
        )
        val result = async { timeoutGate.confirmStep(tap()) }
        runCurrent()

        advanceTimeBy(1_000L)
        runCurrent()

        assertTrue(result.isCompleted)
        assertEquals(
            OversightDecision(false, "step confirmation timeout"),
            result.await(),
        )
    }

    @Test
    fun `cancelPending declines an active confirmation immediately`() = runTest {
        val result = async { gate.confirmStep(tap()) }
        runCurrent()

        assertTrue(gate.cancelPending("investigator aborted trial"))
        val decision = result.await()
        assertFalse(decision.approved)
        assertEquals("investigator aborted trial", decision.reason)
        assertEquals(null, decision.responseLatencyMs)
        assertFalse(gate.cancelPending("already cleared"))
    }

    @Test
    fun `confirmStep human label uses why arg when present`() = runTest {
        val job = async { gate.confirmStep(tapButton()) }
        runCurrent()

        val pending = synchronized(this@NativeStudyGateTest) { lastPending }!!
        // The "why" arg should take priority in ToolNarration.humanLabel
        assertTrue(pending.description.contains("Going home", ignoreCase = true))

        pending.approve()
        job.await()
    }

    @Test
    fun `confirmStep displays the frozen study confirmation text`() = runTest {
        val job = async {
            gate.confirmStep(tap(), "Neue Startzeit übernehmen")
        }
        runCurrent()

        val pending = synchronized(this@NativeStudyGateTest) { lastPending }!!
        assertEquals("Neue Startzeit übernehmen", pending.description)

        pending.approve()
        job.await()
    }

    // --- confirmFinal ---

    @Test
    fun `confirmFinal creates batch pending confirmation`() = runTest {
        val calls = listOf(tap(), tapButton())
        val job = async { gate.confirmFinal(calls) }
        runCurrent()

        val pending = synchronized(this@NativeStudyGateTest) { lastPending }!!
        assertEquals("call-1", pending.callId.value) // primary = first
        assertTrue(pending.isBatch)
        assertTrue(pending.description.contains("1."))
        assertTrue(pending.description.contains("2."))
        assertTrue(pending.description.contains("2 Schritte"))

        pending.approve()
        job.await()
    }

    @Test
    fun `confirmFinal displays the frozen C2 summary`() = runTest {
        val job = async {
            gate.confirmFinal(
                listOf(tap()),
                listOf("Projektsitzung auf 15:00–16:00 Uhr aktualisieren"),
            )
        }
        runCurrent()

        val pending = synchronized(this@NativeStudyGateTest) { lastPending }!!
        assertEquals(
            "1. Projektsitzung auf 15:00–16:00 Uhr aktualisieren\n\n(1 Schritte zur Bestätigung)",
            pending.description,
        )

        pending.approve()
        job.await()
    }

    @Test
    fun `confirmFinal with empty list returns approved`() = runTest {
        val decision = gate.confirmFinal(emptyList())
        assertTrue(decision.approved)
        val pending = synchronized(this@NativeStudyGateTest) { lastPending }
        assertEquals(null, pending)
    }

    @Test
    fun `confirmFinal resolves on approve`() = runTest {
        val calls = listOf(tap())
        val deferred = async { gate.confirmFinal(calls) }
        runCurrent()

        val pending = synchronized(this@NativeStudyGateTest) { lastPending }!!
        pending.approve()

        val decision = deferred.await()
        assertTrue(decision.approved)
    }

    @Test
    fun `confirmFinal auto declines when participant does not answer`() = runTest {
        val timeoutGate = NativeStudyGate(
            onConfirmationRequested = ::enqueueCallback,
            timeoutMs = 1_000L,
        )
        val result = async { timeoutGate.confirmFinal(listOf(tap())) }
        runCurrent()

        advanceTimeBy(1_000L)
        runCurrent()

        assertTrue(result.isCompleted)
        assertEquals(
            OversightDecision(false, "final checkpoint timeout"),
            result.await(),
        )
    }

    // --- PendingConfirmation ---

    @Test
    fun `PendingConfirmation approve creates approved decision`() = runTest {
        val deferred = CompletableDeferred<OversightDecision>()
        val pending = PendingConfirmation(
            callId = ToolCallId("x"),
            toolName = "test",
            description = "test action",
            isBatch = false,
            decision = deferred,
        )
        pending.approve()
        assertTrue(deferred.isCompleted)
        assertEquals(true, deferred.await().approved)
    }

    @Test
    fun `PendingConfirmation decline creates declined decision`() = runTest {
        val deferred = CompletableDeferred<OversightDecision>()
        val pending = PendingConfirmation(
            callId = ToolCallId("x"),
            toolName = "test",
            description = "test action",
            isBatch = true,
            decision = deferred,
        )
        pending.decline(reason = "not safe")
        assertTrue(deferred.isCompleted)
        val decision = deferred.await()
        assertFalse(decision.approved)
        assertEquals("not safe", decision.reason)
    }

    @Test
    fun `PendingConfirmation measures response at decision time`() = runTest {
        val deferred = CompletableDeferred<OversightDecision>()
        var nowNs = 1_000_000_000L
        val pending = PendingConfirmation(
            callId = ToolCallId("x"),
            toolName = "test",
            description = "test action",
            isBatch = false,
            decision = deferred,
            nanoTime = { nowNs },
        )
        pending.markPresented()
        nowNs = 2_250_000_000L

        pending.approve()

        assertEquals(1_250.0, deferred.await().responseLatencyMs!!, 0.001)
    }

    @Test
    fun `PendingConfirmation excludes resolutions before presentation`() = runTest {
        val deferred = CompletableDeferred<OversightDecision>()
        val pending = PendingConfirmation(
            callId = ToolCallId("x"),
            toolName = "test",
            description = "test action",
            isBatch = false,
            decision = deferred,
        )

        pending.cancel("overlay unavailable")

        assertEquals(null, deferred.await().responseLatencyMs)
    }

    @Test
    fun `approval survives cancellation during overlay settle`() = runTest {
        var nowNs = 1_000_000_000L
        var captured: ResolvedConfirmationCancellation? = null
        val settleGate = NativeStudyGate(
            onConfirmationRequested = ::enqueueCallback,
            timeoutMs = 10_000L,
            approvalSettleMs = 200L,
            confirmationNanoTime = { nowNs },
        )
        val job = launch {
            try {
                settleGate.confirmStep(tap(), "Send message")
            } catch (cancelled: ResolvedConfirmationCancellation) {
                captured = cancelled
            }
        }
        runCurrent()
        val pending = lastPending!!
        pending.markPresented()
        nowNs = 2_250_000_000L
        pending.approve()
        runCurrent()

        job.cancel(CancellationException("trial aborted during settle"))
        runCurrent()

        assertEquals(true, captured?.resolvedDecision?.approved)
        assertEquals(1_250.0, captured?.resolvedDecision?.responseLatencyMs!!, 0.001)
    }

    // --- ToolNarrationBridge ---

    @Test
    fun `ToolNarrationBridge parses arguments`() {
        val call = ModelDelta.ToolCall(
            ToolCallId("c"),
            "smartphone_press_button",
            """{"button": "Back"}""",
        )
        val label = ToolNarrationBridge.humanLabel(call)
        assertEquals("Pressing Back", label)
    }

    @Test
    fun `ToolNarrationBridge describes native app opening without exposing tool name`() {
        val call = ModelDelta.ToolCall(
            ToolCallId("c"),
            "android.open_app",
            """{"package_name":"com.google.android.apps.maps","postcondition":{"target":{"package_name":"com.google.android.apps.maps"}}}""",
        )

        assertEquals("Google Maps öffnen", ToolNarrationBridge.humanLabel(call))
    }

    @Test
    fun `ToolNarrationBridge describes native semantic click target`() {
        val call = ModelDelta.ToolCall(
            ToolCallId("c"),
            "android.click",
            """{"target":{"text":"Anna"},"postcondition":{"target":{"text":"Chat"}}}""",
        )

        assertEquals("Auf „Anna“ tippen", ToolNarrationBridge.humanLabel(call))
    }

    @Test
    fun `ToolNarrationBridge handles invalid json`() {
        val call = ModelDelta.ToolCall(
            ToolCallId("c"),
            "smartphone_tap_coordinates",
            """{invalid json""",
        )
        val label = ToolNarrationBridge.humanLabel(call)
        assertEquals("Tapping", label)
    }

    @Test
    fun `ToolNarrationBridge batch label includes count`() {
        val calls = listOf(tap(), tapButton())
        val label = ToolNarrationBridge.batchLabel(calls)
        assertTrue(label.contains("2 Schritte"))
        assertTrue(label.contains("1."))
        assertTrue(label.contains("2."))
    }
}
