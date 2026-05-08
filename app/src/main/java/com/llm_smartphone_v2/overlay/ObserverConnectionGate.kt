package com.llm_smartphone_v2.overlay

internal class ObserverConnectionGate(
    private val baseRetryMs: Long = 1000L,
    private val maxRetryMs: Long = 30_000L,
) {
    private var currentGeneration: Int = 0
    var retryCount: Int = 0
        private set
    private var retryPending: Boolean = false

    fun beginNewConnection(): Int {
        currentGeneration += 1
        retryPending = false
        return currentGeneration
    }

    fun isCurrent(generation: Int): Boolean = generation == currentGeneration

    fun markEvent(generation: Int): Boolean {
        if (!isCurrent(generation)) return false
        retryCount = 0
        return true
    }

    fun scheduleRetry(generation: Int): Long? {
        if (!isCurrent(generation) || retryPending) return null
        retryPending = true
        retryCount += 1
        return baseRetryMs.shl(retryCount.coerceAtMost(6))
            .coerceAtMost(maxRetryMs)
    }

    fun resetBackoff() {
        retryCount = 0
        retryPending = false
    }

    fun invalidate() {
        currentGeneration += 1
        retryPending = false
    }
}
