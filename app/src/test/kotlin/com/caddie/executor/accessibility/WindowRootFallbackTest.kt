package com.caddie.executor.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WindowRootFallbackTest {
    @Test
    fun nullWindowRootUsesMatchingActiveRoot() {
        val root = selectWindowRoot(
            windowId = 2175,
            windowRoot = null,
            activeRoot = WindowRootCandidate(2175, "permission-dialog"),
        )

        assertEquals("permission-dialog", root)
    }

    @Test
    fun nullWindowRootRejectsActiveRootFromDifferentWindow() {
        val root = selectWindowRoot(
            windowId = 2175,
            windowRoot = null,
            activeRoot = WindowRootCandidate(99, "different-window"),
        )

        assertNull(root)
    }
}
