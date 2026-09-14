package com.caddie.app.runtime

import com.caddie.agent.core.AttemptId
import com.caddie.agent.core.RunId
import com.caddie.agent.core.RunRecord
import com.caddie.agent.core.RunState
import com.caddie.agent.core.SessionId
import com.caddie.agent.core.StepOutcome
import com.caddie.context.InMemorySessionStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeAgentRunnerTest {
    @Test
    fun `stop after durable completion preserves the completed journal and event`() = runTest {
        val store = InMemorySessionStore()
        val completed = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val runId = RunId("r1")
        val events = mutableListOf<NativeAgentEvent>()
        val runner = NativeAgentRunner(
            store = store,
            step = { currentRunId, _ ->
                val outcome = complete(store, currentRunId, "Done")
                completed.complete(Unit)
                release.await()
                outcome
            },
            sessionIds = { SessionId("s1") },
            runIds = { runId },
        )
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            runner.events.collect { events += it }
        }
        val execution = async { runner.run("task") }
        completed.await()

        assertTrue(runner.stopActiveRun(runId))
        release.complete(Unit)

        assertEquals(NativeTaskResult.Completed(runId, "Done"), execution.await())
        assertEquals(RunState.COMPLETED, store.snapshot(runId).state)
        assertEquals(1, events.count { it is NativeAgentEvent.Completed })
        assertFalse(events.any { it is NativeAgentEvent.Aborted })
        assertNull(runner.activeRunState.value)
        collector.cancelAndJoin()
    }

    @Test
    fun `active run state covers only the owned execution`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val store = InMemorySessionStore()
        val runner = NativeAgentRunner(
            store = store,
            step = { runId, _ ->
                entered.complete(Unit)
                release.await()
                complete(store, runId, "Done")
            },
            sessionIds = { SessionId("s1") },
            runIds = { RunId("r1") },
        )

        val execution = async { runner.run("task") }
        entered.await()
        assertEquals(RunId("r1"), runner.activeRunState.value)

        release.complete(Unit)
        execution.await()
        assertNull(runner.activeRunState.value)
    }

    @Test
    fun `runner advances tool turns until final assistant text`() = runTest {
        val store = InMemorySessionStore()
        val runId = RunId("r1")
        var steps = 0
        val runner = NativeAgentRunner(
            store = store,
            step = { currentRunId, _ ->
                steps += 1
                if (steps == 1) {
                    StepOutcome.TOOL_FINISHED
                } else {
                    store.append(RunRecord.AssistantCompleted(currentRunId, "Done"))
                    store.append(RunRecord.RunCompleted(currentRunId))
                    StepOutcome.RUN_COMPLETED
                }
            },
            sessionIds = { SessionId("s1") },
            runIds = { runId },
        )

        val result = runner.run("Open settings")

        assertEquals(NativeTaskResult.Completed(runId, "Done"), result)
        assertEquals(2, steps)
        assertEquals(RunState.COMPLETED, store.snapshot(runId).state)
    }

    @Test
    fun `explicit agent failure preserves its human reason`() = runTest {
        val store = InMemorySessionStore()
        val runId = RunId("r1")
        val runner = NativeAgentRunner(
            store = store,
            step = { activeRunId, _ ->
                store.append(RunRecord.AssistantCompleted(activeRunId, "Die Ziel-App fehlt."))
                store.append(RunRecord.RunAborted(activeRunId))
                StepOutcome.RUN_ABORTED
            },
            sessionIds = { SessionId("s1") },
            runIds = { runId },
        )

        assertEquals(
            NativeTaskResult.Aborted(runId, "Die Ziel-App fehlt."),
            runner.run("task"),
        )
    }

    @Test
    fun `second simultaneous task is rejected without starting another run`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var runIdsCreated = 0
        val runner = NativeAgentRunner(
            store = InMemorySessionStore(),
            step = { _, _ ->
                entered.complete(Unit)
                release.await()
                StepOutcome.PAUSED_NETWORK
            },
            sessionIds = { SessionId("s${++runIdsCreated}") },
            runIds = { RunId("r$runIdsCreated") },
        )

        val first = async { runner.run("first") }
        entered.await()
        val second = runner.run("second")
        release.complete(Unit)
        first.await()

        assertEquals(NativeTaskResult.Busy, second)
        assertEquals(1, runIdsCreated)
    }

    @Test
    fun `cancellation pauses a running turn before releasing ownership`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val never = CompletableDeferred<Unit>()
        val store = InMemorySessionStore()
        val runId = RunId("r1")
        var calls = 0
        var ids = 0
        val runner = NativeAgentRunner(
            store = store,
            step = { _, _ ->
                calls += 1
                if (calls == 1) {
                    entered.complete(Unit)
                    never.await()
                    StepOutcome.TOOL_FINISHED
                } else {
                    StepOutcome.PAUSED_NETWORK
                }
            },
            sessionIds = { SessionId("s${++ids}") },
            runIds = { RunId("r$ids") },
        )

        val job = async { runner.run("task") }
        entered.await()
        job.cancelAndJoin()

        assertEquals(RunState.PAUSED_RECOVERABLE, store.snapshot(runId).state)
        assertTrue(runner.run("replacement") !is NativeTaskResult.Busy)
    }

    @Test
    fun `one run can override its step without changing the default runner`() = runTest {
        val store = InMemorySessionStore()
        var defaultCalls = 0
        var overrideCalls = 0
        var nextId = 0
        val runner = NativeAgentRunner(
            store = store,
            step = { runId, _ ->
                defaultCalls += 1
                complete(store, runId, "default")
            },
            sessionIds = { SessionId("s${++nextId}") },
            runIds = { RunId("r$nextId") },
        )

        val special = runner.run("study") { runId, _ ->
            overrideCalls += 1
            complete(store, runId, "study")
        }
        val normal = runner.run("normal")

        assertEquals("study", (special as NativeTaskResult.Completed).answer)
        assertEquals("default", (normal as NativeTaskResult.Completed).answer)
        assertEquals(1, overrideCalls)
        assertEquals(1, defaultCalls)
    }

    @Test
    fun `pre-dispatch pause is retryable only before any real action was claimed`() = runTest {
        val untouchedStore = InMemorySessionStore()
        val untouched = NativeAgentRunner(
            store = untouchedStore,
            step = { _, _ -> StepOutcome.PAUSED_PRE_DISPATCH },
            sessionIds = { SessionId("untouched-session") },
            runIds = { RunId("untouched-run") },
        ).run("task") as NativeTaskResult.Paused

        val actionStore = InMemorySessionStore()
        var step = 0
        val actionRunId = RunId("action-run")
        val afterAction = NativeAgentRunner(
            store = actionStore,
            step = { runId, _ ->
                step += 1
                if (step == 1) {
                    actionStore.append(
                        RunRecord.ActionDispatched(
                            runId = runId,
                            attemptId = AttemptId("attempt-1"),
                            actionKind = "OPEN_URL",
                            selectorFingerprint = null,
                            postconditionFingerprint = null,
                            recoverySpecId = null,
                        ),
                    )
                    StepOutcome.TOOL_FINISHED
                } else {
                    StepOutcome.PAUSED_PRE_DISPATCH
                }
            },
            sessionIds = { SessionId("action-session") },
            runIds = { actionRunId },
        ).run("task") as NativeTaskResult.Paused

        assertTrue(untouched.safeToRetry)
        assertEquals(false, afterAction.safeToRetry)
    }

    @Test
    fun `participant intervention blocks the next action until resumed`() = runTest {
        val store = InMemorySessionStore()
        val enteredFirstStep = CompletableDeferred<Unit>()
        val releaseFirstStep = CompletableDeferred<Unit>()
        var steps = 0
        val runner = NativeAgentRunner(
            store = store,
            step = { runId, _ ->
                steps += 1
                if (steps == 1) {
                    enteredFirstStep.complete(Unit)
                    releaseFirstStep.await()
                    StepOutcome.TOOL_FINISHED
                } else {
                    complete(store, runId, "Done")
                }
            },
            sessionIds = { SessionId("s1") },
            runIds = { RunId("r1") },
        )

        val result = async { runner.run("task") }
        enteredFirstStep.await()
        assertEquals(RunId("r1"), runner.activeRunId())
        assertFalse(runner.pauseForIntervention(RunId("stale-run")))
        assertTrue(runner.pauseForIntervention(RunId("r1")))
        releaseFirstStep.complete(Unit)
        runCurrent()

        assertEquals(1, steps)
        assertTrue(runner.resumeAfterIntervention())
        assertEquals(NativeTaskResult.Completed(RunId("r1"), "Done"), result.await())
        assertEquals(2, steps)
    }

    @Test
    fun `intervention revision changes only when a new hold starts`() = runTest {
        val gate = NativeInterventionGate()

        assertEquals(0L, gate.revision())
        assertTrue(gate.pause())
        assertEquals(1L, gate.revision())
        assertFalse(gate.pause())
        assertEquals(1L, gate.revision())
        assertTrue(gate.resume())
        assertTrue(gate.pause())
        assertEquals(2L, gate.revision())
    }

    @Test
    fun `touch and voice holds cannot release each other`() = runTest {
        val gate = NativeInterventionGate()
        assertTrue(gate.pause())
        assertTrue(gate.pauseForVoice())
        val released = async { gate.awaitRelease() }

        assertTrue(gate.resume())
        runCurrent()
        assertFalse(released.isCompleted)

        assertTrue(gate.resumeAfterVoice())
        released.await()
    }

    @Test
    fun `turn revision is returned only after every hold is released`() = runTest {
        val gate = NativeInterventionGate()
        assertTrue(gate.pauseForVoice())
        val revision = async { gate.awaitReleasedRevision() }

        runCurrent()
        assertFalse(revision.isCompleted)
        assertTrue(gate.resumeAfterVoice())

        assertEquals(1L, revision.await())
    }

    @Test
    fun `potential swipe blocks dispatch without publishing intervention events`() = runTest {
        val store = InMemorySessionStore()
        val enteredFirstStep = CompletableDeferred<Unit>()
        val releaseFirstStep = CompletableDeferred<Unit>()
        val events = mutableListOf<NativeAgentEvent>()
        var steps = 0
        val runner = NativeAgentRunner(
            store = store,
            step = { runId, _ ->
                steps += 1
                if (steps == 1) {
                    enteredFirstStep.complete(Unit)
                    releaseFirstStep.await()
                    StepOutcome.TOOL_FINISHED
                } else {
                    complete(store, runId, "Done")
                }
            },
            sessionIds = { SessionId("s1") },
            runIds = { RunId("r1") },
        )
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            runner.events.collect(events::add)
        }

        val result = async { runner.run("task") }
        enteredFirstStep.await()
        assertTrue(runner.holdForPotentialIntervention(RunId("r1")))
        releaseFirstStep.complete(Unit)
        runCurrent()
        assertEquals(1, steps)
        assertFalse(events.any { it is NativeAgentEvent.InterventionPaused })

        assertTrue(runner.resumeAfterIntervention(RunId("r1")))
        assertEquals(NativeTaskResult.Completed(RunId("r1"), "Done"), result.await())
        assertFalse(events.any { it is NativeAgentEvent.InterventionResumed })
        collector.cancelAndJoin()
    }

    @Test
    fun `spoken stop aborts the active run at its next safe boundary`() = runTest {
        val store = InMemorySessionStore()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val runId = RunId("r1")
        val runner = NativeAgentRunner(
            store = store,
            step = { _, _ ->
                entered.complete(Unit)
                release.await()
                StepOutcome.TOOL_FINISHED
            },
            sessionIds = { SessionId("s1") },
            runIds = { runId },
        )

        val result = async { runner.run("task") }
        entered.await()
        assertTrue(runner.stopActiveRun())
        release.complete(Unit)

        assertEquals(NativeTaskResult.Aborted(runId), result.await())
        assertEquals(RunState.ABORTED, store.snapshot(runId).state)
    }

    @Test
    fun `agent question waits for and returns an answer in the same run`() = runTest {
        val store = InMemorySessionStore()
        val runId = RunId("r1")
        val questionPublished = CompletableDeferred<Unit>()
        var questionId = ""
        var answer: String? = null
        lateinit var runner: NativeAgentRunner
        runner = NativeAgentRunner(
            store = store,
            step = { activeRunId, _ ->
                answer = runner.askUser(activeRunId, "Welcher Bahnhof?")
                complete(store, activeRunId, "Danke")
            },
            sessionIds = { SessionId("s1") },
            runIds = { runId },
        )
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            runner.events.collect { event ->
                if (event is NativeAgentEvent.QuestionAsked) {
                    questionId = event.questionId
                    questionPublished.complete(Unit)
                }
            }
        }

        val result = async(UnconfinedTestDispatcher(testScheduler)) {
            runner.run("Plane die Abholung")
        }
        questionPublished.await()
        assertFalse(runner.answerActiveQuestion("stale-question", "Köln Messe/Deutz"))
        assertTrue(runner.answerActiveQuestion(questionId, "Köln Messe/Deutz"))

        assertEquals("Köln Messe/Deutz", answer)
        assertEquals(NativeTaskResult.Completed(runId, "Danke"), result.await())
        collector.cancelAndJoin()
    }

    @Test
    fun `correction queued during intervention supersedes deterministic dispatch`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val approachBoundary = CompletableDeferred<Unit>()
        var actionAllowed = true
        val runId = RunId("r1")
        val runner = NativeAgentRunner(
            store = InMemorySessionStore(),
            step = { _, _ -> StepOutcome.PAUSED_NETWORK },
            sessionIds = { SessionId("s1") },
            runIds = { runId },
        )

        val result = async {
            runner.runDeterministic("study", { activeRun ->
                entered.complete(Unit)
                approachBoundary.await()
                actionAllowed = runner.awaitActionPermission(activeRun)
                false
            }, "done")
        }
        entered.await()
        assertTrue(runner.holdForPotentialIntervention(runId))
        approachBoundary.complete(Unit)
        runCurrent()
        assertFalse(runner.correctActiveRun(RunId("stale-run"), "nicht das"))
        assertTrue(runner.correctActiveRun(runId, "nicht das"))
        assertTrue(runner.resumeAfterIntervention(runId))

        assertTrue(result.await() is NativeTaskResult.Paused)
        assertFalse(actionAllowed)
    }

    private suspend fun complete(
        store: InMemorySessionStore,
        runId: RunId,
        answer: String,
    ): StepOutcome {
        store.append(RunRecord.AssistantCompleted(runId, answer))
        store.append(RunRecord.RunCompleted(runId))
        return StepOutcome.RUN_COMPLETED
    }
}
