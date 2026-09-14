package com.caddie.app.runtime

import com.caddie.agent.core.AgentMessage
import com.caddie.agent.core.RunId
import com.caddie.agent.core.RunRecord
import com.caddie.agent.core.RunState
import com.caddie.agent.core.SessionId
import com.caddie.agent.core.SessionStore
import com.caddie.agent.core.StepId
import com.caddie.agent.core.StepOutcome
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Describes the terminal result of submitting one task to the native runner. */
sealed interface NativeTaskResult {
    data class Completed(val runId: RunId, val answer: String) : NativeTaskResult
    data class Aborted(val runId: RunId, val reason: String? = null) : NativeTaskResult
    data class Paused(
        val runId: RunId,
        val state: RunState,
        val safeToRetry: Boolean = false,
    ) : NativeTaskResult
    data object Busy : NativeTaskResult
    data object AccessibilityUnavailable : NativeTaskResult
    data object RuntimeClosed : NativeTaskResult
}

/** Publishes process-local lifecycle changes used by the Android overlay. */
sealed interface NativeAgentEvent {
    data class Started(val runId: RunId) : NativeAgentEvent
    data class CurrentAction(val runId: RunId, val narration: String) : NativeAgentEvent
    data class StepFinished(val runId: RunId, val outcome: StepOutcome) : NativeAgentEvent
    data class Completed(val runId: RunId, val answer: String) : NativeAgentEvent
    data class Aborted(val runId: RunId, val reason: String? = null) : NativeAgentEvent
    data class Paused(val runId: RunId, val state: RunState) : NativeAgentEvent
    data class InterventionPaused(val runId: RunId) : NativeAgentEvent
    data class InterventionResumed(val runId: RunId) : NativeAgentEvent
    data class QuestionAsked(
        val runId: RunId,
        val questionId: String,
        val question: String,
    ) : NativeAgentEvent
    data class QuestionResolved(val runId: RunId) : NativeAgentEvent
}

/** Shares the participant pause state between the runner and imminent tool dispatches. */
internal class NativeInterventionGate {
    private val holds = MutableStateFlow(0)
    private val revision = AtomicLong(0L)

    fun pause(): Boolean = addHold(TOUCH_HOLD)

    fun pauseForVoice(): Boolean = addHold(VOICE_HOLD)

    fun resume(): Boolean = removeHold(TOUCH_HOLD)

    fun resumeAfterVoice(): Boolean = removeHold(VOICE_HOLD)

    @Synchronized
    fun reset() {
        holds.value = 0
    }

    suspend fun awaitRelease() {
        holds.first { value -> value == 0 }
    }

    /** Returns a revision that was observed while no touch or voice hold was active. */
    suspend fun awaitReleasedRevision(): Long {
        while (true) {
            holds.first { value -> value == 0 }
            val currentRevision = revision.get()
            if (holds.value == 0) return currentRevision
        }
    }

    fun revision(): Long = revision.get()

    @Synchronized
    private fun addHold(reason: Int): Boolean {
        val current = holds.value
        if (current and reason != 0) return false
        holds.value = current or reason
        revision.incrementAndGet()
        return true
    }

    @Synchronized
    private fun removeHold(reason: Int): Boolean {
        val current = holds.value
        if (current and reason == 0) return false
        holds.value = current and reason.inv()
        return true
    }

    private companion object {
        const val TOUCH_HOLD = 1
        const val VOICE_HOLD = 2
    }
}

/** Carries a user stop request to both dispatch gating and the active runner loop. */
internal class NativeStopSignal {
    private val requestedRun = AtomicReference<RunId?>(null)

    fun request(runId: RunId) {
        requestedRun.set(runId)
    }

    fun isRequested(runId: RunId): Boolean = requestedRun.get() == runId

    fun clear(runId: RunId) {
        requestedRun.updateAndGet { current -> if (current == runId) null else current }
    }
}

/** Owns one single-flight native agent run and advances it turn by turn. */
class NativeAgentRunner internal constructor(
    private val store: SessionStore,
    private val step: suspend (RunId, StepId) -> StepOutcome,
    private val sessionIds: () -> SessionId = {
        SessionId(UUID.randomUUID().toString())
    },
    private val runIds: () -> RunId = {
        RunId(UUID.randomUUID().toString())
    },
    private val maxSteps: Int = 64,
    internal val interventionGate: NativeInterventionGate = NativeInterventionGate(),
    private val corrections: NativeRunCorrections = NativeRunCorrections(),
    private val stopSignal: NativeStopSignal = NativeStopSignal(),
    private val questionTimeoutMs: Long = 25_000L,
    private val onRunFinished: (RunId) -> Unit = {},
) {
    private val ownership = Mutex()
    private val mutableEvents = MutableSharedFlow<NativeAgentEvent>(extraBufferCapacity = 32)
    private val activeRun = AtomicReference<RunId?>(null)
    private val mutableActiveRun = MutableStateFlow<RunId?>(null)
    private val interventionReported = AtomicBoolean(false)
    private val pendingQuestion = AtomicReference<PendingQuestion?>(null)
    private val questionsAsked = AtomicInteger()
    val events = mutableEvents.asSharedFlow()
    val activeRunState: StateFlow<RunId?> = mutableActiveRun.asStateFlow()

    init {
        require(maxSteps > 0) { "maxSteps must be positive" }
    }

    suspend fun run(
        task: String,
        stepOverride: suspend (RunId, StepId) -> StepOutcome = step,
    ): NativeTaskResult {
        require(task.isNotBlank()) { "task must not be blank" }
        if (!ownership.tryLock()) return NativeTaskResult.Busy

        val sessionId = sessionIds()
        val runId = runIds()
        var currentStep = StepId("step-1")
        try {
            interventionGate.reset()
            interventionReported.set(false)
            questionsAsked.set(0)
            activeRun.set(runId)
            mutableActiveRun.value = runId
            store.append(RunRecord.RunCreated(sessionId, runId, task))
            store.append(RunRecord.RunStarted(runId))
            mutableEvents.emit(NativeAgentEvent.Started(runId))

            for (stepNumber in 1..maxSteps) {
                if (stopSignal.isRequested(runId)) return abortedResult(runId)
                awaitInterventionRelease()
                currentStep = StepId("step-$stepNumber")
                val outcome = stepOverride(runId, currentStep)
                if (stopSignal.isRequested(runId)) return abortedResult(runId)
                mutableEvents.emit(NativeAgentEvent.StepFinished(runId, outcome))
                awaitInterventionRelease()
                when (outcome) {
                    StepOutcome.TOOL_FINISHED,
                    StepOutcome.TOOL_SUPERSEDED,
                    -> Unit
                    StepOutcome.RUN_COMPLETED -> return completedResult(runId)
                    StepOutcome.RUN_ABORTED -> return abortedResult(runId)
                    else -> return pausedResult(runId, currentStep, outcome)
                }
            }

            return pausedResult(
                runId,
                StepId("step-limit"),
                StepOutcome.PAUSED_RECOVERABLE,
                "native agent step limit reached",
            )
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                if (store.snapshot(runId).state == RunState.RUNNING) {
                    store.append(
                        RunRecord.RunPaused(
                            runId,
                            currentStep,
                            RunState.PAUSED_RECOVERABLE,
                            "native agent run cancelled",
                        ),
                    )
                }
            }
            throw cancelled
        } finally {
            stopSignal.clear(runId)
            corrections.clear(runId)
            pendingQuestion.getAndSet(null)?.answer?.complete(null)
            activeRun.set(null)
            mutableActiveRun.value = null
            interventionGate.reset()
            interventionReported.set(false)
            ownership.unlock()
            onRunFinished(runId)
        }
    }

    /** Runs one deterministic task inside the same single-flight and intervention lifecycle. */
    suspend fun runDeterministic(
        task: String,
        execute: suspend (RunId) -> Boolean,
        completionText: String,
    ): NativeTaskResult = run(task) { runId, _ ->
        if (execute(runId)) {
            store.append(RunRecord.AssistantCompleted(runId, completionText))
            store.append(RunRecord.RunCompleted(runId))
            StepOutcome.RUN_COMPLETED
        } else {
            StepOutcome.PAUSED_RECOVERABLE
        }
    }

    /** Pauses the active run between physical actions after a participant touch. */
    fun activeRunId(): RunId? = activeRun.get()

    /** Waits until participant touch intervention has ended for this exact run. */
    suspend fun awaitActionPermission(expectedRunId: RunId): Boolean {
        if (
            activeRun.get() != expectedRunId ||
            stopSignal.isRequested(expectedRunId) ||
            corrections.hasPending(expectedRunId)
        ) return false
        interventionGate.awaitRelease()
        return activeRun.get() == expectedRunId &&
            !stopSignal.isRequested(expectedRunId) &&
            !corrections.hasPending(expectedRunId)
    }

    /** Publishes the fixed participant-facing narration of a deterministic study step. */
    fun publishCurrentAction(expectedRunId: RunId, narration: String): Boolean {
        if (activeRun.get() != expectedRunId || narration.isBlank()) return false
        return mutableEvents.tryEmit(NativeAgentEvent.CurrentAction(expectedRunId, narration))
    }

    /** Returns and clears a pending correction for this exact deterministic run. */
    fun takeCorrection(expectedRunId: RunId): String? =
        if (activeRun.get() == expectedRunId) corrections.take(expectedRunId) else null

    fun hasPendingCorrection(expectedRunId: RunId): Boolean =
        activeRun.get() == expectedRunId && corrections.hasPending(expectedRunId)

    fun isStopRequested(expectedRunId: RunId): Boolean =
        activeRun.get() != expectedRunId || stopSignal.isRequested(expectedRunId)

    /** Blocks action dispatch without publishing a participant-visible pause yet. */
    fun holdForPotentialIntervention(expectedRunId: RunId): Boolean {
        val runId = activeRun.get() ?: return false
        if (runId != expectedRunId) return false
        interventionGate.pause()
        return true
    }

    /** Holds action dispatch for wake-word capture independently from touch gestures. */
    fun holdForVoiceCapture(expectedRunId: RunId): Boolean {
        val runId = activeRun.get() ?: return false
        if (runId != expectedRunId) return false
        return interventionGate.pauseForVoice()
    }

    /** Releases only the voice hold, preserving any simultaneous participant touch. */
    fun resumeAfterVoiceCapture(expectedRunId: RunId): Boolean {
        if (activeRun.get() != expectedRunId) return false
        return interventionGate.resumeAfterVoice()
    }

    /** Makes a held or direct intervention visible after a tap is confirmed. */
    fun confirmIntervention(expectedRunId: RunId): Boolean {
        val runId = activeRun.get() ?: return false
        if (runId != expectedRunId) return false
        interventionGate.pause()
        if (interventionReported.compareAndSet(false, true)) {
            mutableEvents.tryEmit(NativeAgentEvent.InterventionPaused(runId))
        }
        return true
    }

    fun pauseForIntervention(expectedRunId: RunId): Boolean =
        confirmIntervention(expectedRunId)

    /** Continues a run after the participant has stopped interacting. */
    fun resumeAfterIntervention(expectedRunId: RunId? = null): Boolean {
        val runId = activeRun.get() ?: return false
        if (expectedRunId != null && runId != expectedRunId) return false
        if (!interventionGate.resume()) return false
        if (interventionReported.getAndSet(false)) {
            mutableEvents.tryEmit(NativeAgentEvent.InterventionResumed(runId))
        }
        return true
    }

    /** Queues a spoken correction for the next safe model turn of the active run. */
    fun correctActiveRun(text: String): Boolean {
        val runId = activeRun.get() ?: return false
        return correctActiveRun(runId, text)
    }

    /** Queues a correction only when capture still refers to this exact run. */
    fun correctActiveRun(expectedRunId: RunId, text: String): Boolean {
        val runId = activeRun.get() ?: return false
        if (runId != expectedRunId) return false
        if (text.isBlank()) return false
        corrections.submit(runId, text)
        pendingQuestion.get()?.takeIf { it.runId == runId }?.answer?.complete(null)
        return true
    }

    /** Suspends a normal tool call until speech supplies an answer or the prompt times out. */
    internal suspend fun askUser(expectedRunId: RunId, question: String): String? {
        if (activeRun.get() != expectedRunId || question.isBlank()) return null
        if (questionsAsked.incrementAndGet() > MAX_QUESTIONS_PER_RUN) return null
        val pending = PendingQuestion(
            runId = expectedRunId,
            questionId = "${expectedRunId.value}:${questionsAsked.get()}",
            answer = CompletableDeferred(),
        )
        check(pendingQuestion.compareAndSet(null, pending)) { "another user question is pending" }
        return try {
            mutableEvents.emit(
                NativeAgentEvent.QuestionAsked(expectedRunId, pending.questionId, question),
            )
            withTimeoutOrNull(questionTimeoutMs) { pending.answer.await() }
        } finally {
            pendingQuestion.compareAndSet(pending, null)
            mutableEvents.emit(NativeAgentEvent.QuestionResolved(expectedRunId))
        }
    }

    /** Completes the question belonging to the active run without starting a second task. */
    fun answerActiveQuestion(questionId: String, answer: String): Boolean {
        if (answer.isBlank()) return false
        val pending = pendingQuestion.get() ?: return false
        if (activeRun.get() != pending.runId || pending.questionId != questionId) return false
        return pending.answer.complete(answer.trim())
    }

    /** Stops the active run at the next safe boundary and blocks imminent dispatch. */
    fun stopActiveRun(): Boolean {
        val runId = activeRun.get() ?: return false
        return stopActiveRun(runId)
    }

    /** Stops only the run that was active when the voice capture began. */
    fun stopActiveRun(expectedRunId: RunId): Boolean {
        val runId = activeRun.get() ?: return false
        if (runId != expectedRunId) return false
        stopSignal.request(runId)
        pendingQuestion.get()?.takeIf { it.runId == runId }?.answer?.complete(null)
        interventionGate.reset()
        interventionReported.set(false)
        return true
    }

    private suspend fun awaitInterventionRelease() {
        interventionGate.awaitRelease()
    }

    private suspend fun pausedResult(
        runId: RunId,
        stepId: StepId,
        outcome: StepOutcome,
        reason: String? = null,
    ): NativeTaskResult.Paused {
        val expectedState = outcome.pausedState()
        var snapshot = store.snapshot(runId)
        if (snapshot.state == RunState.RUNNING) {
            store.append(RunRecord.RunPaused(runId, stepId, expectedState, reason))
            snapshot = store.snapshot(runId)
        }
        mutableEvents.emit(NativeAgentEvent.Paused(runId, snapshot.state))
        return NativeTaskResult.Paused(
            runId,
            snapshot.state,
            safeToRetry =
                outcome == StepOutcome.PAUSED_PRE_DISPATCH &&
                    !snapshot.hasDispatchedAction,
        )
    }

    private suspend fun completedResult(runId: RunId): NativeTaskResult.Completed {
        val answer = store.snapshot(runId).messages
            .lastOrNull { it.role == AgentMessage.Role.ASSISTANT }
            ?.content
            .orEmpty()
        mutableEvents.emit(NativeAgentEvent.Completed(runId, answer))
        return NativeTaskResult.Completed(runId, answer)
    }

    private suspend fun abortedResult(runId: RunId): NativeTaskResult {
        val snapshot = store.snapshot(runId)
        // A late stop cannot replace a terminal record already committed by the step.
        if (snapshot.state == RunState.COMPLETED) return completedResult(runId)
        val reason = snapshot.messages.lastOrNull {
            it.role == AgentMessage.Role.ASSISTANT && it.toolCall == null
        }?.content
        if (snapshot.state != RunState.ABORTED) {
            store.append(RunRecord.RunAborted(runId))
        }
        mutableEvents.emit(NativeAgentEvent.Aborted(runId, reason))
        return NativeTaskResult.Aborted(runId, reason)
    }

    private fun StepOutcome.pausedState(): RunState =
        when (this) {
            StepOutcome.PAUSED_NETWORK -> RunState.PAUSED_NETWORK
            StepOutcome.PAUSED_OVERSIGHT -> RunState.PAUSED_OVERSIGHT
            StepOutcome.PAUSED_PRE_DISPATCH -> RunState.PAUSED_RECOVERABLE
            StepOutcome.PAUSED_RECOVERABLE -> RunState.PAUSED_RECOVERABLE
            else -> error("$this is not a paused outcome")
        }

    private data class PendingQuestion(
        val runId: RunId,
        val questionId: String,
        val answer: CompletableDeferred<String?>,
    )

    private companion object {
        const val MAX_QUESTIONS_PER_RUN = 3
    }
}
