package com.caddie.app.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeTouchGestureRouterTest {
    private val router = NativeTouchGestureRouter<String>(
        touchSlopPx = 12f,
        systemEdgeInsetPx = 24f,
    )

    @Test
    fun `blank-area tap is held then classified when the finger lifts`() {
        assertEquals(
            NativeTouchGestureRouter.Decision.Hold("run-1"),
            router.onDown(
                "run-1",
                alreadyPaused = false,
                x = 200f,
                y = 300f,
                displayWidth = 1080,
                displayHeight = 2400,
            ),
        )

        assertEquals(
            NativeTouchGestureRouter.Decision.Tap("run-1"),
            router.onUp(),
        )
    }

    @Test
    fun `movement beyond touch slop delegates instead of becoming a tap`() {
        router.onDown("run-1", false, 200f, 300f, 1080, 2400)

        assertEquals(
            NativeTouchGestureRouter.Decision.Delegate,
            router.onMove(x = 213f, y = 300f),
        )
        assertEquals(NativeTouchGestureRouter.Decision.None, router.onUp())
    }

    @Test
    fun `small finger jitter remains a tap`() {
        router.onDown("run-1", false, 200f, 300f, 1080, 2400)

        assertEquals(
            NativeTouchGestureRouter.Decision.None,
            router.onMove(x = 206f, y = 306f),
        )
        assertEquals(
            NativeTouchGestureRouter.Decision.Tap("run-1"),
            router.onUp(),
        )
    }

    @Test
    fun `touches during an owned pause delegate immediately`() {
        assertEquals(
            NativeTouchGestureRouter.Decision.Delegate,
            router.onDown("run-1", true, 200f, 300f, 1080, 2400),
        )
    }

    @Test
    fun `confirmation overlay input delegates even while a run is active`() {
        assertEquals(
            NativeTouchGestureRouter.Decision.Delegate,
            router.onDown(
                activeTarget = "run-1",
                alreadyPaused = false,
                x = 200f,
                y = 300f,
                displayWidth = 1080,
                displayHeight = 2400,
                delegateImmediately = true,
            ),
        )
    }

    @Test
    fun `touches without an active run delegate immediately`() {
        assertEquals(
            NativeTouchGestureRouter.Decision.Delegate,
            router.onDown(null, false, 200f, 300f, 1080, 2400),
        )
    }

    @Test
    fun `system edge gestures always delegate immediately`() {
        val points = listOf(
            10f to 400f,
            1070f to 400f,
            400f to 10f,
            400f to 2390f,
        )

        points.forEach { (x, y) ->
            assertEquals(
                NativeTouchGestureRouter.Decision.Delegate,
                router.onDown("run-1", false, x, y, 1080, 2400),
            )
        }
    }

    @Test
    fun `second pointer delegates the complete interaction`() {
        router.onDown("run-1", false, 200f, 300f, 1080, 2400)

        assertEquals(NativeTouchGestureRouter.Decision.Delegate, router.onAdditionalPointer())
        assertEquals(NativeTouchGestureRouter.Decision.None, router.onUp())
    }

    @Test
    fun `cancel clears a held tap`() {
        router.onDown("run-1", false, 200f, 300f, 1080, 2400)

        router.onCancel()

        assertTrue(router.onUp() is NativeTouchGestureRouter.Decision.None)
    }
}
