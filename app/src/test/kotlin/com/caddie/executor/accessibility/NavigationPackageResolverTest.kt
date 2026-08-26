package com.caddie.executor.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationPackageResolverTest {
    @Test
    fun `concrete resolved package wins`() {
        assertEquals(
            "com.android.chrome",
            navigationPackageCandidates("com.android.chrome", listOf("com.other.browser")).single(),
        )
    }

    @Test
    fun `single visible candidate is safe fallback`() {
        assertEquals(
            "com.android.chrome",
            navigationPackageCandidates(null, listOf("com.android.chrome")).single(),
        )
    }

    @Test
    fun `all ambiguous candidates remain eligible for observed active package verification`() {
        assertEquals(
            setOf("com.android.chrome", "com.other.browser"),
            navigationPackageCandidates(
                resolvedPackage = null,
                candidatePackages = listOf("com.android.chrome", "com.other.browser"),
            ),
        )
    }

    @Test
    fun `url handoff accepts an observed active external app when handlers are hidden`() {
        assertEquals(
            VerificationDecision.Satisfied,
            verifyObservedNavigation(
                action = RequestedAction.OpenUrl("https://example.test"),
                callerPackage = "com.caddie.debug",
                observation = observation("com.android.chrome"),
            ),
        )
    }

    @Test
    fun `url handoff never treats the caller window as its destination`() {
        assertEquals(
            VerificationDecision.Contradicted,
            verifyObservedNavigation(
                action = RequestedAction.OpenUrl("https://example.test"),
                callerPackage = "com.caddie.debug",
                observation = observation("com.caddie.debug"),
            ),
        )
    }

    @Test
    fun `top visible application is used when an overlay clears Android active flags`() {
        assertEquals(
            VerificationDecision.Satisfied,
            verifyObservedNavigation(
                action = RequestedAction.OpenUrl("https://example.test"),
                callerPackage = "com.caddie.debug",
                observation = observation(
                    packageName = "com.android.chrome",
                    active = false,
                    focused = false,
                ),
            ),
        )
    }

    @Test
    fun `marked active caller still wins over a background external application`() {
        val caller = window(
            id = 1,
            layer = 2,
            packageName = "com.caddie.debug",
            active = true,
            focused = true,
        )
        val background = window(
            id = 2,
            layer = 1,
            packageName = "com.android.chrome",
            active = false,
            focused = false,
        )

        assertEquals(
            VerificationDecision.Contradicted,
            verifyObservedNavigation(
                action = RequestedAction.OpenUrl("https://example.test"),
                callerPackage = "com.caddie.debug",
                observation = UiObservation(
                    id = "navigation",
                    capturedAtElapsedRealtimeMillis = 1,
                    completeness = SnapshotCompleteness.PARTIAL_INTERACTIVE_WINDOWS,
                    inputWindows = listOf(caller, background),
                ),
            ),
        )
    }

    private fun observation(
        packageName: String,
        active: Boolean = true,
        focused: Boolean = true,
    ) = UiObservation(
        id = "navigation",
        capturedAtElapsedRealtimeMillis = 1,
        completeness = SnapshotCompleteness.PARTIAL_INTERACTIVE_WINDOWS,
        inputWindows = listOf(
            window(1, 1, packageName, active, focused),
        ),
    )

    private fun window(
        id: Int,
        layer: Int,
        packageName: String,
        active: Boolean,
        focused: Boolean,
    ) = UiWindow(
        id = id,
        type = UiWindowType.APPLICATION,
        layer = layer,
        active = active,
        focused = focused,
        interactionBarrier = false,
        nodes = listOf(
            UiNode(
                observationNodeId = "root-$id",
                windowId = id,
                packageName = packageName,
                enabled = true,
                visibleToUser = true,
            ),
        ),
    )
}
