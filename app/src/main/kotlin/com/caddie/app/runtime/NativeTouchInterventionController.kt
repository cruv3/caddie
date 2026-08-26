package com.caddie.app.runtime

import com.caddie.agent.core.RunId

/** Converts participant touches into a native pause followed by quiet-time resume. */
class NativeTouchInterventionController(
    private val pauseNativeRun: (RunId) -> Boolean,
    private val resumeNativeRun: (RunId) -> Boolean,
    private val schedule: (delayMs: Long, action: () -> Unit) -> Unit,
    private val holdNativeRun: (RunId) -> Boolean = pauseNativeRun,
    private val confirmHeldPause: (RunId) -> Boolean = { true },
) {
    private var resumeGeneration = 0L
    private var pausedRunId: RunId? = null
    private var pauseConfirmed = false

    @Synchronized
    fun onHumanTouch(runId: RunId): Boolean {
        if (pausedRunId == runId) {
            if (pauseConfirmed) scheduleResume()
            return true
        }
        if (!pauseNativeRun(runId)) return false
        pausedRunId = runId
        pauseConfirmed = true
        scheduleResume()
        return true
    }

    /** Acquires a silent action gate until the contact is classified. */
    @Synchronized
    fun beginPotentialTouch(runId: RunId): Boolean {
        if (pausedRunId == runId) return true
        if (!holdNativeRun(runId)) return false
        pausedRunId = runId
        pauseConfirmed = false
        return true
    }

    /** Publishes a pause and starts quiet-time resume after a confirmed tap. */
    @Synchronized
    fun confirmPotentialTouch(runId: RunId): Boolean {
        if (pausedRunId != runId) return false
        if (!pauseConfirmed && !confirmHeldPause(runId)) {
            cancelHumanTouch(runId)
            return false
        }
        pauseConfirmed = true
        scheduleResume()
        return true
    }

    /** Keeps an intervention pause active while the participant is still touching the UI. */
    @Synchronized
    fun onHumanActivity(runId: RunId): Boolean {
        if (pausedRunId != runId || !pauseConfirmed) return false
        scheduleResume()
        return true
    }

    /** Releases a pause immediately when a held touch proves to be a gesture. */
    fun cancelHumanTouch(runId: RunId): Boolean {
        val shouldResume = synchronized(this) {
            if (pausedRunId != runId) {
                false
            } else {
                resumeGeneration += 1
                pausedRunId = null
                pauseConfirmed = false
                true
            }
        }
        return shouldResume && resumeNativeRun(runId)
    }

    private fun scheduleResume() {
        val generation = ++resumeGeneration
        schedule(RESUME_QUIET_MS) {
            val shouldResume = synchronized(this) {
                if (generation != resumeGeneration || pausedRunId == null) {
                    null
                } else {
                    val runId = pausedRunId
                    pausedRunId = null
                    pauseConfirmed = false
                    runId
                }
            }
            if (shouldResume != null) resumeNativeRun(shouldResume)
        }
    }

    @Synchronized
    fun close() {
        resumeGeneration += 1
        val releaseRun = pausedRunId
        pausedRunId = null
        pauseConfirmed = false
        if (releaseRun != null) resumeNativeRun(releaseRun)
    }

    companion object {
        const val RESUME_QUIET_MS = 2_150L
    }
}
