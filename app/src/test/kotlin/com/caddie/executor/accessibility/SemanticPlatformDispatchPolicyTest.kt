package com.caddie.executor.accessibility

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class SemanticPlatformDispatchPolicyTest {
    @Test
    fun `each requested action invokes exactly its matching semantic operation`() {
        val cases =
            listOf(
                RequestedAction.Click to listOf(PlatformOperation(UiAction.CLICK)),
                RequestedAction.LongClick to
                    listOf(PlatformOperation(UiAction.LONG_CLICK)),
                RequestedAction.SetText("hello") to
                    listOf(
                        PlatformOperation(
                            UiAction.SET_TEXT,
                            text = "hello",
                        ),
                    ),
                RequestedAction.SetChecked(true) to
                    listOf(PlatformOperation(UiAction.CLICK)),
                RequestedAction.Scroll(forward = true) to
                    listOf(PlatformOperation(UiAction.SCROLL_FORWARD)),
                RequestedAction.Scroll(forward = false) to
                    listOf(PlatformOperation(UiAction.SCROLL_BACKWARD)),
            )

        cases.forEach { (requested, expected) ->
            val operations = mutableListOf<PlatformOperation>()

            val accepted =
                SemanticPlatformDispatchPolicy.perform(requested) { operation ->
                    operations += operation
                    true
                }

            assertEquals(true, accepted)
            assertEquals(expected, operations)
        }
    }

    @Test
    fun `submit rejection remains accepted after set text acceptance`() {
        val operations = mutableListOf<PlatformOperation>()

        val accepted =
            SemanticPlatformDispatchPolicy.perform(
                RequestedAction.SetText("hello", submit = true),
            ) { operation ->
                operations += operation
                operation.action == UiAction.SET_TEXT
            }

        assertEquals(true, accepted)
        assertEquals(
            listOf(
                PlatformOperation(UiAction.SET_TEXT, text = "hello"),
                PlatformOperation(UiAction.IME_ENTER),
            ),
            operations,
        )
    }

    @Test
    fun `submit exception remains accepted after set text acceptance`() {
        var dispatchCount = 0

        val accepted =
            SemanticPlatformDispatchPolicy.perform(
                RequestedAction.SetText("hello", submit = true),
            ) {
                dispatchCount++
                if (dispatchCount == 1) {
                    true
                } else {
                    error("IME disappeared")
                }
            }

        assertEquals(true, accepted)
        assertEquals(2, dispatchCount)
    }

    @Test
    fun `submit cancellation propagates after set text acceptance`() {
        var dispatchCount = 0

        try {
            SemanticPlatformDispatchPolicy.perform(
                RequestedAction.SetText("hello", submit = true),
            ) {
                dispatchCount++
                if (dispatchCount == 1) {
                    true
                } else {
                    throw CancellationException("cancelled")
                }
            }
            fail("cancellation must propagate")
        } catch (_: CancellationException) {
            // Structured cancellation must not be converted to Accepted.
        }

        assertEquals(2, dispatchCount)
    }

    @Test
    fun `set text exception propagates before any action is accepted`() {
        try {
            SemanticPlatformDispatchPolicy.perform(
                RequestedAction.SetText("hello", submit = true),
            ) {
                error("set text failed")
            }
            fail("initial dispatch exception must propagate")
        } catch (failure: IllegalStateException) {
            assertEquals("set text failed", failure.message)
        }
    }

    @Test
    fun `rejected set text does not submit`() {
        val operations = mutableListOf<PlatformOperation>()

        val accepted =
            SemanticPlatformDispatchPolicy.perform(
                RequestedAction.SetText("hello", submit = true),
            ) { operation ->
                operations += operation
                false
            }

        assertEquals(false, accepted)
        assertEquals(
            listOf(PlatformOperation(UiAction.SET_TEXT, text = "hello")),
            operations,
        )
    }
}
