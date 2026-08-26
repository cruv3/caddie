package com.caddie.app.accessibility

/** Distinguishes passive display-touch candidates from accessibility scroll gestures. */
class PassiveTouchInterventionArbiter<T : Any>(
    private val captureTarget: () -> T?,
    private val observeTouch: (target: T) -> Unit = {},
    private val confirmTouch: (target: T, source: String) -> Boolean,
    private val schedule: (delayMs: Long, action: () -> Unit) -> Unit,
) {
    private var generation = 0L
    private var candidatePending = false
    private var candidateTarget: T? = null

    @Synchronized
    fun onTouchCandidate(source: String): Boolean {
        val target = captureTarget()
        if (target == null) {
            candidatePending = false
            candidateTarget = null
            generation += 1
            return false
        }
        observeTouch(target)
        val candidateGeneration = ++generation
        candidatePending = true
        candidateTarget = target
        schedule(CLASSIFICATION_WINDOW_MS) {
            val confirmation = synchronized(this) {
                if (!candidatePending || generation != candidateGeneration) {
                    null
                } else {
                    candidatePending = false
                    val capturedTarget = candidateTarget
                    candidateTarget = null
                    capturedTarget?.let { it to source }
                }
            }
            confirmation?.let { (capturedTarget, capturedSource) ->
                confirmTouch(capturedTarget, capturedSource)
            }
        }
        return true
    }

    @Synchronized
    fun onScrollObserved(): Boolean {
        if (!candidatePending) return false
        candidatePending = false
        candidateTarget = null
        generation += 1
        return true
    }

    fun onConfirmedTouch(source: String): Boolean {
        val target = synchronized(this) {
            val capturedTarget = if (candidatePending) candidateTarget else captureTarget()
            candidatePending = false
            candidateTarget = null
            generation += 1
            capturedTarget
        } ?: return false
        return confirmTouch(target, source)
    }

    @Synchronized
    fun close() {
        candidatePending = false
        candidateTarget = null
        generation += 1
    }

    companion object {
        const val CLASSIFICATION_WINDOW_MS = 120L
    }
}
