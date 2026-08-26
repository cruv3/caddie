package com.caddie.executor.accessibility

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class NoCoordinateFallbackTest {
    @Test
    fun `every semantic rejection stops without gesture`() = runTest {
        val rejections =
            listOf(
                ActionOutcome.InvalidTarget,
                ActionOutcome.TargetMissing,
                ActionOutcome.TargetAmbiguous,
                ActionOutcome.TargetNotVisible,
                ActionOutcome.TargetDisabled,
                ActionOutcome.BlockedByWindow,
                ActionOutcome.ActionUnavailable,
                ActionOutcome.ActionRejected,
                ActionOutcome.StaleObservation,
                ActionOutcome.AccessibilityUnavailable,
            )

        rejections.forEach { rejection ->
            val gateway = FakeGateway(rejection)

            assertEquals(
                rejection,
                SemanticActionExecutor(gateway)
                    .execute(
                        SemanticTarget(text = "Send"),
                        RequestedAction.Click,
                    ),
            )
            assertEquals(1, gateway.semanticCalls)
        }
    }

    private class FakeGateway(
        private val result: ActionOutcome,
    ) : ExecutionGateway {
        var semanticCalls = 0

        override suspend fun observe(): UiObservation =
            error("semantic dispatch must not pre-observe through this executor")

        override suspend fun performSemantic(
            target: SemanticTarget,
            action: RequestedAction,
        ): ActionOutcome {
            semanticCalls++
            return result
        }
    }
}
