package com.caddie.executor.accessibility

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SemanticActionExecutorTest {
    private val target = SemanticTarget(text = "Send")

    @Test
    fun `executor delegates every requested action exactly once`() = runTest {
        val actions =
            listOf(
                RequestedAction.Click,
                RequestedAction.LongClick,
                RequestedAction.SetText("hello"),
                RequestedAction.SetText("hello", submit = true),
                RequestedAction.SetChecked(true),
                RequestedAction.Scroll(forward = true),
                RequestedAction.Scroll(forward = false),
            )
        val gateway = FakeGateway(ActionOutcome.Accepted)
        val executor = SemanticActionExecutor(gateway)

        actions.forEach { action ->
            assertEquals(
                ActionOutcome.Accepted,
                executor.execute(target, action),
            )
        }

        assertEquals(actions, gateway.actions)
    }

    @Test
    fun `requested actions map to their required semantic actions`() {
        assertEquals(UiAction.CLICK, RequestedAction.Click.requiredUiAction())
        assertEquals(
            UiAction.LONG_CLICK,
            RequestedAction.LongClick.requiredUiAction(),
        )
        assertEquals(
            UiAction.SET_TEXT,
            RequestedAction.SetText("hello").requiredUiAction(),
        )
        assertEquals(
            UiAction.CLICK,
            RequestedAction.SetChecked(true).requiredUiAction(),
        )
        assertEquals(
            UiAction.SCROLL_FORWARD,
            RequestedAction.Scroll(forward = true).requiredUiAction(),
        )
        assertEquals(
            UiAction.SCROLL_BACKWARD,
            RequestedAction.Scroll(forward = false).requiredUiAction(),
        )
    }

    private class FakeGateway(
        private val result: ActionOutcome,
    ) : ExecutionGateway {
        val actions = mutableListOf<RequestedAction>()

        override suspend fun observe(): UiObservation =
            error("semantic dispatch must own its fresh observation")

        override suspend fun performSemantic(
            target: SemanticTarget,
            action: RequestedAction,
        ): ActionOutcome {
            actions += action
            return result
        }
    }
}
