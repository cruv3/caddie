package com.caddie.study.runtime.mode

import com.caddie.study.runtime.coordinator.ArmedTrialCoordinator

/** Keeps the app UI and study portal on one fail-closed operating mode. */
class StudyModeArbiter(
    private val coordinator: ArmedTrialCoordinator,
) {
    enum class Mode(val wireValue: String) {
        NORMAL("normal"),
        TEST("test"),
        LIVE("live");

        companion object {
            fun fromWire(value: String): Mode = entries.firstOrNull { it.wireValue == value }
                ?: error("unsupported study mode: $value")
        }
    }

    enum class Priority(val rank: Int) {
        LOCAL(0),
        PORTAL(1),
    }

    data class Snapshot(val mode: Mode, val owner: Any?, val priority: Priority?)

    private var snapshot = Snapshot(Mode.NORMAL, null, null)

    @Synchronized
    fun current(): Mode = snapshot.mode

    @Synchronized
    fun set(owner: Any, mode: Mode, priority: Priority = Priority.LOCAL): Mode {
        val currentOwner = snapshot.owner
        val currentPriority = snapshot.priority
        check(currentOwner == null || currentOwner === owner || !coordinator.hasActiveTrial()) {
            "an active trial blocks cross-controller mode changes"
        }
        check(
            currentOwner == null || currentOwner === owner ||
                priority.rank > requireNotNull(currentPriority).rank
        ) { "study mode is owned by a higher-priority controller" }
        snapshot = if (mode == Mode.NORMAL) {
            Snapshot(Mode.NORMAL, null, null)
        } else {
            Snapshot(mode, owner, priority)
        }
        coordinator.setStudyMode(active = mode != Mode.NORMAL)
        return mode
    }

    @Synchronized
    fun owns(owner: Any): Boolean = snapshot.owner === owner

    /** Returns to normal only when the closing component still owns the mode. */
    @Synchronized
    fun release(owner: Any): Boolean {
        if (snapshot.owner !== owner) return false
        snapshot = Snapshot(Mode.NORMAL, null, null)
        coordinator.setStudyMode(active = false)
        return true
    }
}
