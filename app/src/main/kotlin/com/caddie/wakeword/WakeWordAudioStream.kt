package com.caddie.wakeword

/** Reassembles arbitrary microphone reads into fixed-size inference frames. */
internal class PcmFrameAccumulator(private val frameSize: Int) {
    private val pending = ShortArray(frameSize)
    private var pendingCount = 0

    init {
        require(frameSize > 0)
    }

    fun append(samples: ShortArray, count: Int): List<ShortArray> {
        require(count in 0..samples.size)
        val frames = mutableListOf<ShortArray>()
        var sourceOffset = 0
        while (sourceOffset < count) {
            val copied = minOf(frameSize - pendingCount, count - sourceOffset)
            samples.copyInto(pending, pendingCount, sourceOffset, sourceOffset + copied)
            pendingCount += copied
            sourceOffset += copied
            if (pendingCount == frameSize) {
                frames += pending.copyOf()
                pendingCount = 0
            }
        }
        return frames
    }

    fun reset() {
        pendingCount = 0
    }
}

/** Adds the previous PCM tail required by openWakeWord's mel model. */
internal class MelOverlapWindow(
    private val frameSize: Int,
    private val overlapSize: Int,
) {
    private var previousTail: ShortArray? = null

    init {
        require(frameSize > 0)
        require(overlapSize in 0..frameSize)
    }

    fun next(frame: ShortArray): ShortArray {
        require(frame.size == frameSize)
        val tail = previousTail
        val window = if (tail == null) frame.copyOf() else tail + frame
        previousTail = frame.copyOfRange(frameSize - overlapSize, frameSize)
        return window
    }

    fun reset() {
        previousTail = null
    }
}
