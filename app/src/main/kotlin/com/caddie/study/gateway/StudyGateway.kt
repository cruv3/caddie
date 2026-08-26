package com.caddie.study.gateway

/**
 * Boundary between the portal and the native study runtime.
 * Mirrors Python `caddie.study.portal.service.StudyGateway` Protocol.
 *
 * All methods return a snapshot of the new state. Callers must validate
 * snapshots before proceeding (same pattern as Python service).
 */
interface StudyGateway {

    /** Current snapshot of runtime state (mode, participant, trial, phone_ready). */
    fun snapshot(): Map<String, Any>

    /** Check if the study runtime is ready for live operation. */
    fun preflight(): Boolean

    /** Set the study runtime mode (normal, live). Returns new snapshot. */
    fun setMode(mode: String): Map<String, Any>

    /** Activate a participant. Returns new snapshot. */
    fun activateParticipant(participantId: String): Map<String, Any>

    /** Get the next assignment for a participant. Returns null if exhausted. */
    fun nextAssignment(participantId: String): Map<String, Any>?

    /** Tasks available for an isolated deterministic test run. */
    fun testTasks(): List<Map<String, String>> = emptyList()

    /** Participant-facing context for a task, including app roles and goal. */
    fun participantTaskBriefing(taskId: String): Map<String, Any>? = null

    /** Resets and arms one isolated test trial without advancing live progress. */
    fun armTestTrial(
        participantId: String,
        taskId: String,
        condition: String,
        injectError: Boolean,
        studyRunId: String,
    ): Map<String, Any> = error("test trials unavailable")

    /** Arm the next trial with the given assignment. Returns new snapshot. */
    fun armNextTrial(assignment: Map<String, Any>): Map<String, Any>

    /** Abort the current trial. Returns new snapshot. */
    fun abortTrial(reason: String): Map<String, Any>

    /** Reset the device for the next trial. Returns true on success. */
    fun resetDevice(): Boolean

    /** Prepares the phone for free practice while keeping the normal agent mode active. */
    fun startTraining(): Boolean = false

    /** Exclusive lock for mutations. Use tryLock/unlock pattern. */
    fun tryExclusive(): Boolean

    fun releaseExclusive()
}
