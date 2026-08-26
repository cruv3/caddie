package com.caddie.executor.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class SnapshotCompletenessPolicyTest {
    @Test
    fun `rootless active or barrier window makes snapshot partial`() {
        listOf(
            uiWindow(active = true),
            uiWindow(focused = true),
            uiWindow(barrier = true),
        ).forEach { rootlessWindow ->
            assertEquals(
                SnapshotCompleteness.PARTIAL_INTERACTIVE_WINDOWS,
                interactiveWindowCompleteness(listOf(rootlessWindow)),
            )
        }
    }

    @Test
    fun `rootless inactive non-system window makes snapshot partial`() {
        listOf(
            UiWindowType.APPLICATION,
            UiWindowType.INPUT_METHOD,
            UiWindowType.ACCESSIBILITY_OVERLAY,
            UiWindowType.UNKNOWN,
        ).forEach { type ->
            assertEquals(
                type.name,
                SnapshotCompleteness.PARTIAL_INTERACTIVE_WINDOWS,
                interactiveWindowCompleteness(
                    listOf(uiWindow(type = type)),
                ),
            )
        }
    }

    @Test
    fun `rootless inactive nonblocking decoration stays complete`() {
        assertEquals(
            SnapshotCompleteness.ALL_INTERACTIVE_WINDOWS,
            interactiveWindowCompleteness(
                listOf(
                    uiWindow(type = UiWindowType.SYSTEM),
                ),
            ),
        )
    }

    @Test
    fun `readable interactive windows stay complete`() {
        assertEquals(
            SnapshotCompleteness.ALL_INTERACTIVE_WINDOWS,
            interactiveWindowCompleteness(
                listOf(
                    uiWindow(
                        active = true,
                        focused = true,
                        barrier = true,
                        nodes = listOf(node()),
                    ),
                ),
            ),
        )
    }

    private fun uiWindow(
        type: UiWindowType = UiWindowType.APPLICATION,
        active: Boolean = false,
        focused: Boolean = false,
        barrier: Boolean = false,
        nodes: List<UiNode> = emptyList(),
    ) = UiWindow(
        id = 1,
        type = type,
        layer = 1,
        active = active,
        focused = focused,
        interactionBarrier = barrier,
        nodes = nodes,
    )
}
