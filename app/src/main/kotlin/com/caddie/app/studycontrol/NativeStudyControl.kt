package com.caddie.app.studycontrol

import com.caddie.study.gateway.StudyDeviceResetter
import com.caddie.study.runtime.coordinator.ArmedTrialCoordinator
import com.caddie.study.runtime.model.RuntimeStudyCondition
import com.caddie.study.runtime.model.TrialSpec
import com.caddie.study.runtime.mode.StudyModeArbiter
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Owns the explicit Normal/Test study mode and arms fixed test trials on-device. */
class NativeStudyControl(
    private val coordinator: ArmedTrialCoordinator,
    private val specs: Map<String, TrialSpec>,
    private val deviceResetter: StudyDeviceResetter = StudyDeviceResetter.NoOp,
    private val modeArbiter: StudyModeArbiter = StudyModeArbiter(coordinator),
    private val runtimeIdle: () -> Boolean = { true },
    private val reserveRuntime: () -> AutoCloseable? = { AutoCloseable {} },
    private val stopActiveRun: () -> Boolean = { false },
    private val cancelPendingConfirmation: (String) -> Unit = {},
) {
    enum class Mode(val wireValue: String, val label: String) {
        NORMAL("normal", "Normal"),
        TEST("test", "Test - Study"),
        LIVE("live", "Live Study"),
    }

    data class Snapshot(
        val mode: Mode,
        val trial: ArmedTrialCoordinator.CoordinatorStatus,
    )

    private val lock = ReentrantLock()
    private val modeOwner = Any()

    fun snapshot(): Snapshot = lock.withLock {
        Snapshot(modeFromShared(modeArbiter.current()), coordinator.status())
    }

    fun setMode(next: Mode): Snapshot = lock.withLock {
        check(!trialActive()) { "an active trial blocks mode changes" }
        modeArbiter.set(modeOwner, next.toShared())
        Snapshot(next, coordinator.status())
    }

    fun armTest(
        taskId: String,
        condition: RuntimeStudyCondition,
        injectError: Boolean,
    ): Snapshot = lock.withLock {
        check(modeArbiter.current() == StudyModeArbiter.Mode.TEST) { "test mode required" }
        check(modeArbiter.owns(modeOwner)) { "test mode is controlled by the study portal" }
        check(!trialActive()) { "a study trial is already active" }
        check(runtimeIdle()) { "stop the active agent before arming a study trial" }
        val spec = specs[taskId] ?: error("unknown study task: $taskId")
        val reservation = reserveRuntime()
            ?: error("stop the active agent before arming a study trial")
        reservation.use {
            check(deviceResetter.reset()) { "study device reset failed" }
            if (coordinator.status().state != null) coordinator.clear()
            coordinator.arm(
                ArmedTrialCoordinator.ArmedTrialConfig(
                    participantId = "TEST",
                    trialIndex = 0,
                    taskId = taskId,
                    condition = condition,
                    injectError = injectError,
                    runScope = ArmedTrialCoordinator.StudyRunScope.TEST,
                ),
                spec,
            )
        }
        Snapshot(Mode.TEST, coordinator.status())
    }

    fun reset(): Snapshot = lock.withLock {
        check(!trialActive()) { "an active trial blocks reset" }
        check(modeArbiter.current() != StudyModeArbiter.Mode.NORMAL) { "study mode required" }
        check(modeArbiter.owns(modeOwner)) { "study mode is controlled by the study portal" }
        check(runtimeIdle()) { "stop the active agent before resetting study apps" }
        val reservation = reserveRuntime()
            ?: error("stop the active agent before resetting study apps")
        reservation.use {
            check(deviceResetter.reset()) { "study device reset failed" }
            if (coordinator.status().state != null) coordinator.clear()
        }
        Snapshot(modeFromShared(modeArbiter.current()), coordinator.status())
    }

    fun abort(reason: String = "experimenter_abort"): Snapshot = lock.withLock {
        if (trialActive()) {
            stopActiveRun()
            cancelPendingConfirmation(reason)
        }
        coordinator.abortIfActive(reason)
        Snapshot(modeFromShared(modeArbiter.current()), coordinator.status())
    }

    private fun trialActive(): Boolean = coordinator.status().state in setOf(
        ArmedTrialCoordinator.ArmedState.ARMED,
        ArmedTrialCoordinator.ArmedState.RUNNING,
    )

    private fun Mode.toShared(): StudyModeArbiter.Mode =
        StudyModeArbiter.Mode.fromWire(wireValue)

    private fun modeFromShared(mode: StudyModeArbiter.Mode): Mode =
        Mode.entries.first { it.wireValue == mode.wireValue }
}
