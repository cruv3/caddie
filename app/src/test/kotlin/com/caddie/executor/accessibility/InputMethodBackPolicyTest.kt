package com.caddie.executor.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies that keyboard-only Back cannot navigate away from an application. */
class InputMethodBackPolicyTest {
    @Test
    fun `dismisses only while an input-method window is present`() {
        val app = window(type = UiWindowType.APPLICATION, nodes = arrayOf(node()))
        val keyboard = window(
            id = 2,
            layer = 2,
            type = UiWindowType.INPUT_METHOD,
            nodes = arrayOf(node(id = "2:0", windowId = 2)),
        )

        assertFalse(InputMethodBackPolicy.shouldPerformGlobalBack(snapshot(app)))
        assertTrue(InputMethodBackPolicy.shouldPerformGlobalBack(snapshot(keyboard, app)))
    }
}
