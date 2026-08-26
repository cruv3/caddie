package com.caddie.app.runtime

import com.caddie.executor.accessibility.ActionOutcome
import com.caddie.executor.accessibility.ExecutionGateway
import com.caddie.executor.accessibility.RequestedAction
import com.caddie.executor.accessibility.SemanticTarget
import com.caddie.executor.accessibility.SnapshotCompleteness
import com.caddie.executor.accessibility.UiObservation
import com.caddie.executor.accessibility.VerificationDecision
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccessibilityGatewaySlotTest {
    @Test
    fun `navigation capabilities are delegated to the connected gateway`() = runTest {
        val slot = AccessibilityGatewaySlot()
        val action = RequestedAction.OpenUrl("https://example.test")
        val observation = observation()
        slot.connect(
            Any(),
            object : ExecutionGateway {
                override suspend fun observe() = observation

                override fun resolveDestinationPackages(action: RequestedAction) =
                    setOf("com.android.chrome")

                override fun verifyNavigation(
                    action: RequestedAction,
                    observation: UiObservation,
                ) = VerificationDecision.Satisfied

                override suspend fun performSemantic(
                    target: SemanticTarget,
                    action: RequestedAction,
                ) = ActionOutcome.Accepted
            },
        )

        assertEquals(setOf("com.android.chrome"), slot.resolveDestinationPackages(action))
        assertEquals(
            VerificationDecision.Satisfied,
            slot.verifyNavigation(action, observation),
        )
    }

    @Test
    fun `disconnected slot exposes no navigation evidence`() {
        val slot = AccessibilityGatewaySlot()
        val action = RequestedAction.OpenUrl("https://example.test")

        assertEquals(emptySet<String>(), slot.resolveDestinationPackages(action))
        assertNull(slot.verifyNavigation(action, observation()))
    }

    private fun observation() = UiObservation(
        id = "snapshot",
        capturedAtElapsedRealtimeMillis = 1,
        completeness = SnapshotCompleteness.ALL_INTERACTIVE_WINDOWS,
        inputWindows = emptyList(),
    )
}
