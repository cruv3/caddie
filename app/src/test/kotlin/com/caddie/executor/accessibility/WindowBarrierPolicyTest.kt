package com.caddie.executor.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowBarrierPolicyTest {
    private val interactiveNodes =
        listOf(
            node(
                actions = setOf(UiAction.CLICK),
            ),
        )

    @Test
    fun `interactive non IME windows are barriers regardless of reported type`() {
        listOf(
            UiWindowType.APPLICATION,
            UiWindowType.SYSTEM,
            UiWindowType.ACCESSIBILITY_OVERLAY,
            UiWindowType.UNKNOWN,
        ).forEach { type ->
            assertTrue(
                type.name,
                WindowBarrierPolicy.isInteractionBarrier(
                    type = type,
                    active = false,
                    focused = false,
                    nodes = interactiveNodes,
                ),
            )
        }
    }

    @Test
    fun `noninteractive status overlay is not a barrier`() {
        assertFalse(
            WindowBarrierPolicy.isInteractionBarrier(
                type = UiWindowType.UNKNOWN,
                active = false,
                focused = false,
                nodes = listOf(node(text = "Working", actions = emptySet())),
            ),
        )
    }

    @Test
    fun `IME is not a generic barrier solely because it has semantic actions`() {
        assertFalse(
            WindowBarrierPolicy.isInteractionBarrier(
                type = UiWindowType.INPUT_METHOD,
                active = false,
                focused = false,
                nodes = interactiveNodes,
            ),
        )
    }

    @Test
    fun `active or focused window remains a barrier`() {
        assertTrue(
            WindowBarrierPolicy.isInteractionBarrier(
                type = UiWindowType.INPUT_METHOD,
                active = true,
                focused = false,
                nodes = emptyList(),
            ),
        )
        assertTrue(
            WindowBarrierPolicy.isInteractionBarrier(
                type = UiWindowType.UNKNOWN,
                active = false,
                focused = true,
                nodes = emptyList(),
            ),
        )
    }
}
