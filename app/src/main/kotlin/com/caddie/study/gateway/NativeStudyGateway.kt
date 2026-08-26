package com.caddie.study.gateway

import com.caddie.study.runtime.coordinator.ArmedTrialCoordinator
import com.caddie.study.runtime.matrix.StudyMatrix
import com.caddie.study.runtime.model.ParticipantConfig
import com.caddie.study.runtime.model.RuntimeStudyCondition
import com.caddie.study.runtime.model.TrialSpec
import com.caddie.study.runtime.mode.StudyModeArbiter
import java.io.Closeable
import java.util.concurrent.locks.ReentrantLock

/**
 * Native StudyGateway implementation that bridges the ArmedTrialCoordinator,
 * StudyMatrix, and spec data into the gateway protocol expected by PortalService.
 */
class NativeStudyGateway(
    private val coordinator: ArmedTrialCoordinator,
    private val configs: Map<String, ParticipantConfig>,
    private val specs: Map<String, TrialSpec>,
    private val readinessProvider: () -> NativeStudyReadiness = {
        NativeStudyReadiness.Unavailable
    },
    private val cancelPendingConfirmation: (String) -> Unit = {},
    private val deviceResetter: StudyDeviceResetter = StudyDeviceResetter.NoOp,
    private val normalPracticePreparer: () -> Boolean = { false },
    private val modeArbiter: StudyModeArbiter = StudyModeArbiter(coordinator),
    private val stopActiveRun: () -> Boolean = { false },
    private val runtimeIdle: () -> Boolean = { true },
    private val reserveRuntime: () -> AutoCloseable? = { AutoCloseable {} },
    private val awaitRuntimeIdle: () -> Boolean = runtimeIdle,
) : StudyGateway, Closeable {

    private val lock = ReentrantLock()
    private val modeOwner = Any()
    @Volatile private var activeParticipant: String? = null
    @Volatile private var trialState: String = "idle"
    @Volatile private var trialInfo: Map<String, Any> = emptyMap()

    override fun close() {
        lock.lock()
        try {
            if (modeArbiter.owns(modeOwner)) {
                coordinator.abortIfActive("study portal stopped")
                stopActiveRun()
                cancelPendingConfirmation("study portal stopped")
            }
            modeArbiter.release(modeOwner)
        } finally {
            lock.unlock()
        }
    }

    override fun snapshot(): Map<String, Any> {
        lock.lock()
        try {
            releaseFinishedTestModeIfIdle()
            val readiness = readinessProvider()
            val coordinatorStatus = coordinator.status()
            val effectiveTrialState = coordinatorStatus
                .takeIf { it.participantId == activeParticipant }
                ?.state
                ?.wireValue
                ?: trialState
            return mapOf(
                "mode" to modeArbiter.current().wireValue,
                "active_participant" to (activeParticipant ?: ""),
                "phone_ready" to (
                    modeArbiter.current() == StudyModeArbiter.Mode.LIVE &&
                        effectiveTrialState !in listOf("armed", "running") && readiness.isReady
                ),
                "trial" to mapOf(
                    "state" to effectiveTrialState,
                    "reason" to (coordinatorStatus.reason ?: ""),
                ) + trialInfo,
                "readiness" to readiness.toSnapshot(),
            )
        } finally {
            lock.unlock()
        }
    }

    override fun preflight(): Boolean {
        lock.lock()
        try {
            releaseFinishedTestModeIfIdle()
            return modeArbiter.current() == StudyModeArbiter.Mode.LIVE &&
                trialState in listOf("idle", "aborted", "completed", "failed") &&
                !coordinator.hasActiveTrial() &&
                runtimeIdle() &&
                readinessProvider().isReady
        } finally {
            lock.unlock()
        }
    }

    override fun setMode(mode: String): Map<String, Any> {
        lock.lock()
        try {
            releaseFinishedTestModeIfIdle()
            check(!coordinator.hasActiveTrial()) { "a study trial is already active" }
            modeArbiter.set(
                modeOwner,
                StudyModeArbiter.Mode.fromWire(mode),
                StudyModeArbiter.Priority.PORTAL,
            )
            return snapshot()
        } finally {
            lock.unlock()
        }
    }

    override fun activateParticipant(participantId: String): Map<String, Any> {
        lock.lock()
        try {
            activeParticipant = participantId
            return snapshot()
        } finally {
            lock.unlock()
        }
    }

    override fun nextAssignment(participantId: String): Map<String, Any>? {
        val config = configs[participantId]
            ?: StudyMatrix.generateParticipant(participantId, specs)
        return buildAssignment(config)
    }

    override fun testTasks(): List<Map<String, String>> = specs.values
        .sortedBy { it.id }
        .map { spec ->
            mapOf(
                "id" to spec.id,
                "instruction" to spec.instructionDe,
                "criticality" to spec.criticality.wireValue,
            )
        }

    override fun participantTaskBriefing(taskId: String): Map<String, Any>? =
        specs[taskId]?.participantBriefingMap()

    override fun armTestTrial(
        participantId: String,
        taskId: String,
        condition: String,
        injectError: Boolean,
        studyRunId: String,
    ): Map<String, Any> {
        lock.lock()
        try {
            releaseFinishedTestModeIfIdle()
            check(!coordinator.hasActiveTrial()) { "a study trial is already active" }
            check(readinessProvider().isReady) { "phone not ready" }
            check(runtimeIdle()) { "stop the active agent before arming a study trial" }
            val spec = specs[taskId] ?: error("unknown study task: $taskId")
            val parsedCondition = RuntimeStudyCondition.fromWire(condition)
            val reservation = reserveRuntime()
                ?: error("stop the active agent before arming a study trial")
            try {
                reservation.use {
                    modeArbiter.set(
                        modeOwner,
                        StudyModeArbiter.Mode.TEST,
                        StudyModeArbiter.Priority.PORTAL,
                    )
                    check(modeArbiter.owns(modeOwner)) { "study portal does not own test mode" }
                    check(deviceResetter.reset()) { "study device reset failed" }
                    if (coordinator.status().state != null) coordinator.clear()
                    coordinator.arm(
                        ArmedTrialCoordinator.ArmedTrialConfig(
                            participantId = participantId,
                            trialIndex = 0,
                            taskId = taskId,
                            condition = parsedCondition,
                            injectError = injectError,
                            runScope = ArmedTrialCoordinator.StudyRunScope.TEST,
                            studyRunId = studyRunId,
                        ),
                        spec,
                    )
                    activeParticipant = participantId
                    trialState = "armed"
                    trialInfo = mapOf(
                        "task_id" to taskId,
                        "trial_index" to 0,
                        "condition" to condition,
                        "inject_error" to injectError,
                        "run_scope" to "test",
                    )
                }
            } catch (error: Throwable) {
                rollbackFailedTestArm()
                throw error
            }
            return snapshot()
        } finally {
            lock.unlock()
        }
    }

    override fun armNextTrial(assignment: Map<String, Any>): Map<String, Any> {
        lock.lock()
        try {
            check(modeArbiter.owns(modeOwner)) { "study portal does not own active mode" }
            check(runtimeIdle()) { "stop the active agent before arming a study trial" }
            val reservation = reserveRuntime()
                ?: error("stop the active agent before arming a study trial")
            reservation.use {
                val taskId = assignment["task_id"] as String
                val trialIndex = assignment["trial_index"] as Int
                val conditionStr = assignment["condition"] as String
                val injectError = assignment["inject_error"] as Boolean

                val spec = specs[taskId]
                    ?: throw IllegalStateException("no spec for $taskId")
                val condition = RuntimeStudyCondition.fromWire(conditionStr)
                val config = ArmedTrialCoordinator.ArmedTrialConfig(
                    participantId = activeParticipant ?: "unknown",
                    trialIndex = trialIndex,
                    taskId = taskId,
                    condition = condition,
                    injectError = injectError,
                    studyRunId = assignment["study_run_id"] as? String,
                    trialAttemptId = assignment["trial_attempt_id"] as String,
                )
                coordinator.arm(config, spec)

                trialState = "armed"
                trialInfo = mapOf(
                    "task_id" to taskId,
                    "trial_index" to trialIndex,
                    "condition" to conditionStr,
                    "inject_error" to injectError,
                    "trial_attempt_id" to config.trialAttemptId,
                )
            }
            return snapshot()
        } finally {
            lock.unlock()
        }
    }

    override fun abortTrial(reason: String): Map<String, Any> {
        lock.lock()
        try {
            if (modeArbiter.owns(modeOwner)) {
                coordinator.abortIfActive(reason)
                stopActiveRun()
                cancelPendingConfirmation(reason)
                check(awaitRuntimeIdle()) { "native runner did not stop after study abort" }
            }
            trialState = "aborted"
            trialInfo = emptyMap()
            activeParticipant = null
            modeArbiter.release(modeOwner)
            return snapshot()
        } finally {
            lock.unlock()
        }
    }

    override fun resetDevice(): Boolean {
        lock.lock()
        try {
            if (
                modeArbiter.current() != StudyModeArbiter.Mode.NORMAL &&
                !modeArbiter.owns(modeOwner)
            ) return false
            if (!runtimeIdle()) return false
            val reservation = reserveRuntime() ?: return false
            reservation.use {
                cancelPendingConfirmation("study device reset")
                if (!deviceResetter.reset()) return false
                coordinator.clear()
                trialState = "idle"
                trialInfo = emptyMap()
            }
            return true
        } finally {
            lock.unlock()
        }
    }

    override fun startTraining(): Boolean {
        lock.lock()
        try {
            if (modeArbiter.current() != StudyModeArbiter.Mode.NORMAL) return false
            if (!runtimeIdle()) return false
            val reservation = reserveRuntime() ?: return false
            reservation.use {
                cancelPendingConfirmation("normal practice start")
                coordinator.clear()
                trialState = "idle"
                trialInfo = emptyMap()
                activeParticipant = null
                return normalPracticePreparer()
            }
        } finally {
            lock.unlock()
        }
    }

    override fun tryExclusive(): Boolean = lock.tryLock()
    override fun releaseExclusive() = lock.unlock()

    private fun releaseFinishedTestModeIfIdle() {
        if (
            modeArbiter.current() != StudyModeArbiter.Mode.TEST ||
            !modeArbiter.owns(modeOwner) ||
            !runtimeIdle()
        ) return
        val terminal = coordinator.status().state?.takeIf {
            it in setOf(
                ArmedTrialCoordinator.ArmedState.COMPLETED,
                ArmedTrialCoordinator.ArmedState.FAILED,
                ArmedTrialCoordinator.ArmedState.ABORTED,
            )
        } ?: return
        trialState = terminal.wireValue
        activeParticipant = null
        modeArbiter.release(modeOwner)
    }

    private fun rollbackFailedTestArm() {
        if (!modeArbiter.owns(modeOwner)) return
        if (coordinator.hasActiveTrial()) coordinator.abortIfActive("test arm failed")
        if (coordinator.status().state != null) coordinator.clear()
        activeParticipant = null
        trialState = "idle"
        trialInfo = emptyMap()
        modeArbiter.release(modeOwner)
    }

    private fun buildAssignment(config: ParticipantConfig): Map<String, Any> {
        val tasks = config.taskOrder.mapIndexed { index, taskId ->
            val spec = specs[taskId]
                ?: throw IllegalStateException("missing spec: $taskId")
            val errorVariants = spec.steps
                .filter { it.id in spec.errorSteps }
                .mapNotNull { step ->
                    step.errorVariant?.let {
                        mapOf("id" to it.id, "description" to it.description)
                    }
                }
            val task = mutableMapOf<String, Any>(
                "id" to taskId,
                "instruction" to spec.instructionDe,
                "criticality" to spec.criticality.wireValue,
                "error_step_ids" to spec.errorSteps,
                "assigned_error_variant_ids" to errorVariants.map { it["id"] as String },
                "assigned_error_variants" to errorVariants,
            )
            spec.participantBriefingMap()?.let { task["briefing"] = it }
            task
        }
        return mapOf(
            "participant_id" to config.participantId,
            "task_order" to config.taskOrder,
            "condition_order" to config.conditionOrder.map { it.wireValue },
            "error_tasks" to config.errorTasks,
            "screen_off_order" to config.screenOffOrder.map { it.wireValue },
            "screen_off_tasks" to config.screenOffTasks,
            "pair_assignments" to config.pairAssignments.map { pair ->
                mapOf("task_id" to pair.first, "criticality" to pair.second.wireValue)
            },
            "tasks" to tasks,
        )
    }

    private fun TrialSpec.participantBriefingMap(): Map<String, Any>? =
        participantBriefing?.let { briefing ->
            mapOf(
                "situation" to briefing.situationDe,
                "apps" to briefing.apps.map { app ->
                    mapOf("name" to app.name, "purpose" to app.purposeDe)
                },
                "goal" to briefing.goalDe,
                "preparation" to briefing.preparationDe,
                "reference_values" to briefing.referenceValuesDe,
            )
        }
}
