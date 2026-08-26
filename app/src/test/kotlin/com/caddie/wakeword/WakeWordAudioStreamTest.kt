package com.caddie.wakeword

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordAudioStreamTest {
    @Test
    fun `partial reads are emitted without loss or duplication`() {
        val stream = PcmFrameAccumulator(frameSize = 4)

        assertTrue(stream.append(shortArrayOf(1, 2), 2).isEmpty())
        val frames = stream.append(shortArrayOf(3, 4, 5, 6, 99), 4)

        assertArrayEquals(shortArrayOf(1, 2, 3, 4), frames.single())
        assertArrayEquals(
            shortArrayOf(5, 6, 7, 8),
            stream.append(shortArrayOf(7, 8), 2).single(),
        )
    }

    @Test
    fun `reset drops only pending samples`() {
        val stream = PcmFrameAccumulator(frameSize = 4)
        stream.append(shortArrayOf(1, 2), 2)

        stream.reset()

        assertTrue(stream.append(shortArrayOf(3, 4), 2).isEmpty())
        assertArrayEquals(
            shortArrayOf(3, 4, 5, 6),
            stream.append(shortArrayOf(5, 6), 2).single(),
        )
    }

    @Test
    fun `mel windows prepend only the previous frame tail`() {
        val windows = MelOverlapWindow(frameSize = 4, overlapSize = 2)

        assertArrayEquals(shortArrayOf(1, 2, 3, 4), windows.next(shortArrayOf(1, 2, 3, 4)))
        assertArrayEquals(
            shortArrayOf(3, 4, 5, 6, 7, 8),
            windows.next(shortArrayOf(5, 6, 7, 8)),
        )
    }

    @Test
    fun `production timing matches openWakeWord`() {
        assertEquals(1280, OpenWakeWordDetector.FRAME_SAMPLES)
        assertEquals(480, OpenWakeWordDetector.MEL_OVERLAP_SAMPLES)
    }
}
