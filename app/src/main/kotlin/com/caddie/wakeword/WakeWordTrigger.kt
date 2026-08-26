package com.caddie.wakeword

/** Applies the wake score threshold and a sample-based refractory period. */
internal class WakeWordTrigger(
    val threshold: Float,
    private val refractorySamples: Int,
) {
    private var samplesSinceHit = Int.MAX_VALUE

    init {
        require(threshold in 0f..1f)
        require(refractorySamples >= 0)
    }

    fun evaluate(score: Float?, newSamples: Int): Boolean {
        require(newSamples >= 0)
        if (samplesSinceHit < Int.MAX_VALUE) {
            samplesSinceHit = (samplesSinceHit.toLong() + newSamples)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
        }
        if (score == null || score < threshold || samplesSinceHit < refractorySamples) {
            return false
        }
        samplesSinceHit = 0
        return true
    }

    fun reset() {
        samplesSinceHit = Int.MAX_VALUE
    }
}
