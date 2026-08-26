package com.caddie.app.runtime

import com.caddie.agent.core.RunId
import com.caddie.study.runtime.coordinator.ArmedTrialCoordinator
import com.caddie.executor.accessibility.ExecutionGateway
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/** Identifies whether an utterance used the normal or armed study route. */
sealed interface RuntimeUtteranceResult {
    data class Normal(val result: NativeTaskResult) : RuntimeUtteranceResult
    data class Study(val outcome: StudyRouteOutcome) : RuntimeUtteranceResult
}

/** Owns native runtime state shared by Android services in this app process. */
class AndroidRuntimeProcess(
    val studyCoordinator: ArmedTrialCoordinator = ArmedTrialCoordinator(),
    private val nativeRuntimeFactory: ((ExecutionGateway) -> NativeRuntimeHost)? = null,
    private val studyRunnerFactory: ((NativeRuntimeHost, ExecutionGateway) -> ClaimedStudyRunner)? = null,
    private val routeLogger: (String) -> Unit = {},
) {
    internal val studyExecutionConfigured: Boolean = studyRunnerFactory != null
    private val accessibility = AccessibilityGatewaySlot()
    private val closed = AtomicBoolean()
    private val studyPreparation = AtomicBoolean()
    private val normalSubmissions = AtomicInteger()
    private val nativeRuntime = lazy {
        nativeRuntimeFactory?.invoke(accessibility)
    }
    private val studyRouter = lazy {
        StudyAgentRouter(studyCoordinator) { claim ->
            if (closed.get()) {
                NativeTaskResult.RuntimeClosed
            } else if (!accessibility.isConnected) {
                NativeTaskResult.AccessibilityUnavailable
            } else {
                val createRunner = studyRunnerFactory
                    ?: return@StudyAgentRouter NativeTaskResult.RuntimeClosed
                val host = nativeRuntime.value
                    ?: return@StudyAgentRouter NativeTaskResult.AccessibilityUnavailable
                createRunner(host, accessibility).run(claim)
            }
        }
    }

    fun connectAccessibility(
        connection: Any,
        gateway: ExecutionGateway,
    ) {
        accessibility.connect(connection, gateway)
    }

    fun disconnectAccessibility(connection: Any) {
        accessibility.disconnect(connection)
    }

    /** Completes process recovery before the first spoken task arrives. */
    suspend fun warmUp(): Boolean =
        !closed.get() && nativeRuntime.value?.warmUp() == true

    suspend fun submitTask(task: String): NativeTaskResult {
        if (closed.get()) return NativeTaskResult.RuntimeClosed
        if (!accessibility.isConnected) return NativeTaskResult.AccessibilityUnavailable
        if (studyPreparation.get() || studyCoordinator.isStudyModeActive()) {
            return NativeTaskResult.Busy
        }
        normalSubmissions.incrementAndGet()
        try {
            if (studyPreparation.get() || studyCoordinator.isStudyModeActive()) {
                return NativeTaskResult.Busy
            }
            return nativeRuntime.value?.run(task)
                ?: NativeTaskResult.AccessibilityUnavailable
        } finally {
            normalSubmissions.decrementAndGet()
        }
    }

    suspend fun submitUtterance(utterance: String): RuntimeUtteranceResult {
        return when (val outcome = submitStudyUtterance(utterance)) {
            StudyRouteOutcome.PassThrough ->
                RuntimeUtteranceResult.Normal(submitTask(utterance))
            else -> RuntimeUtteranceResult.Study(outcome)
        }
    }

    /** Routes only an armed study utterance and never starts a normal task. */
    suspend fun submitStudyUtterance(utterance: String): StudyRouteOutcome {
        val outcome = studyRouter.value.route(utterance)
        val status = studyCoordinator.status()
        routeLogger(
            "study route=${outcome::class.simpleName} state=${status.state?.wireValue ?: "idle"} " +
                "task=${status.taskId ?: "none"} reason=${status.reason ?: "none"}",
        )
        return outcome
    }

    /** Emits lifecycle events for the single process-owned native runner. */
    fun nativeEvents(): Flow<NativeAgentEvent> =
        nativeRuntime.value?.runner?.events ?: emptyFlow()

    /** Returns the current run identity without starting the lazy runtime. */
    fun activeNativeRunId(): RunId? =
        if (!closed.get() && nativeRuntime.isInitialized()) {
            nativeRuntime.value?.runner?.activeRunId()
        } else {
            null
        }

    /** Emits the active run identity so touch interception exists only while an agent is running. */
    fun activeNativeRunChanges(): Flow<RunId?> =
        nativeRuntime.value?.runner?.activeRunState ?: flowOf(null)

    /** Pauses that exact native run at its next action boundary. */
    fun pauseForIntervention(expectedRunId: RunId): Boolean =
        !closed.get() && nativeRuntime.isInitialized() &&
            nativeRuntime.value?.runner?.pauseForIntervention(expectedRunId) == true

    /** Silently freezes the active run while the participant dictates a voice command. */
    fun pauseForVoiceCapture(): RunId? {
        if (closed.get() || !nativeRuntime.isInitialized()) return null
        val runner = nativeRuntime.value?.runner ?: return null
        val runId = runner.activeRunId() ?: return null
        return runId.takeIf { runner.holdForVoiceCapture(it) }
    }

    /** Releases only wake-word capture while leaving a simultaneous touch pause intact. */
    fun resumeAfterVoiceCapture(expectedRunId: RunId): Boolean =
        !closed.get() && nativeRuntime.isInitialized() &&
            nativeRuntime.value?.runner?.resumeAfterVoiceCapture(expectedRunId) == true

    /** Silently blocks the active run while a contact may still become a swipe. */
    fun holdForPotentialIntervention(expectedRunId: RunId): Boolean =
        !closed.get() && nativeRuntime.isInitialized() &&
            nativeRuntime.value?.runner?.holdForPotentialIntervention(expectedRunId) == true

    /** Publishes the intervention only after the held contact is confirmed as a tap. */
    fun confirmIntervention(expectedRunId: RunId): Boolean =
        !closed.get() && nativeRuntime.isInitialized() &&
            nativeRuntime.value?.runner?.confirmIntervention(expectedRunId) == true

    /** Resumes a native run previously paused by a participant touch. */
    fun resumeAfterIntervention(expectedRunId: RunId? = null): Boolean =
        !closed.get() && nativeRuntime.isInitialized() &&
            nativeRuntime.value?.runner?.resumeAfterIntervention(expectedRunId) == true

    /** Stops the active normal or study run before another real action dispatches. */
    fun stopActiveRun(): Boolean =
        !closed.get() && nativeRuntime.isInitialized() &&
            nativeRuntime.value?.runner?.stopActiveRun() == true

    fun stopActiveRun(expectedRunId: RunId): Boolean =
        !closed.get() && nativeRuntime.isInitialized() &&
            nativeRuntime.value?.runner?.stopActiveRun(expectedRunId) == true

    /** Atomically excludes normal submissions while study apps reset and a trial is armed. */
    fun tryReserveStudyPreparation(): AutoCloseable? {
        if (!studyPreparation.compareAndSet(false, true)) return null
        if (normalSubmissions.get() != 0 || activeNativeRunId() != null) {
            studyPreparation.set(false)
            return null
        }
        return AutoCloseable { studyPreparation.set(false) }
    }

    /** Waits for an asynchronous stop to remain idle long enough to be stable. */
    fun awaitNativeIdle(timeoutMs: Long = 3_000, quietPeriodMs: Long = 100): Boolean {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        var idleSince = 0L
        while (System.nanoTime() < deadline) {
            if (activeNativeRunId() == null && normalSubmissions.get() == 0) {
                if (idleSince == 0L) idleSince = System.nanoTime()
                if (System.nanoTime() - idleSince >= quietPeriodMs * 1_000_000) return true
            } else {
                idleSince = 0L
            }
            Thread.sleep(10)
        }
        return false
    }

    /** Adds a spoken correction to the active run's next model turn. */
    fun correctActiveRun(text: String): Boolean =
        !closed.get() && nativeRuntime.isInitialized() &&
            nativeRuntime.value?.runner?.correctActiveRun(text) == true

    fun correctActiveRun(expectedRunId: RunId, text: String): Boolean =
        !closed.get() && nativeRuntime.isInitialized() &&
            nativeRuntime.value?.runner?.correctActiveRun(expectedRunId, text) == true

    /** Delivers speech to a pending model clarification in the current run. */
    fun answerActiveQuestion(questionId: String, text: String): Boolean =
        !closed.get() && nativeRuntime.isInitialized() &&
            nativeRuntime.value?.runner?.answerActiveQuestion(questionId, text) == true

    /** Forgets recent completed normal tasks without affecting study state. */
    fun clearNormalContext(): Boolean {
        if (closed.get()) return false
        if (nativeRuntime.isInitialized()) nativeRuntime.value?.clearNormalContext()
        return true
    }

    suspend fun closeForTests() {
        if (!closed.compareAndSet(false, true)) return
        if (nativeRuntime.isInitialized()) nativeRuntime.value?.close()
    }
}
