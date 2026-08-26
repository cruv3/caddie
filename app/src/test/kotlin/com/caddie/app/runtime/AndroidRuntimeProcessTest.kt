package com.caddie.app.runtime

import com.caddie.agent.core.RunId
import com.caddie.agent.core.RunRecord
import com.caddie.agent.core.RunSnapshot
import com.caddie.agent.core.SessionStore
import com.caddie.agent.core.StepOutcome
import com.caddie.executor.accessibility.ActionOutcome
import com.caddie.executor.accessibility.ExecutionGateway
import com.caddie.executor.accessibility.RequestedAction
import com.caddie.executor.accessibility.SemanticTarget
import com.caddie.executor.accessibility.SnapshotCompleteness
import com.caddie.executor.accessibility.UiObservation
import com.caddie.study.runtime.coordinator.ArmedTrialCoordinator
import com.caddie.study.runtime.model.CriticalityClass
import com.caddie.study.runtime.model.RuntimeStudyCondition
import com.caddie.study.runtime.model.StepType
import com.caddie.study.runtime.model.StudyStep
import com.caddie.study.runtime.model.TriggerContract
import com.caddie.study.runtime.model.TrialSpec
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidRuntimeProcessTest {
    @Test
    fun `tasks require the current accessibility connection`() = runTest {
        var runnerCreations = 0
        val process = AndroidRuntimeProcess(
            nativeRuntimeFactory = {
                runnerCreations += 1
                NativeRuntimeHost(pausedRunner())
            },
        )

        assertEquals(NativeTaskResult.AccessibilityUnavailable, process.submitTask("open settings"))

        val firstConnection = Any()
        process.connectAccessibility(firstConnection, FakeGateway())
        assertTrue(process.submitTask("open settings") is NativeTaskResult.Paused)
        assertEquals(1, runnerCreations)

        process.disconnectAccessibility(Any())
        assertTrue(process.submitTask("open settings") is NativeTaskResult.Paused)

        process.disconnectAccessibility(firstConnection)
        assertEquals(NativeTaskResult.AccessibilityUnavailable, process.submitTask("open settings"))

        process.connectAccessibility(Any(), FakeGateway())
        assertTrue(process.submitTask("open settings") is NativeTaskResult.Paused)
        assertEquals(1, runnerCreations)
    }

    @Test
    fun `process closes one initialized native runtime exactly once`() = runTest {
        var closeCalls = 0
        val process = AndroidRuntimeProcess(
            nativeRuntimeFactory = {
                NativeRuntimeHost(pausedRunner()) { closeCalls += 1 }
            },
        )
        process.connectAccessibility(Any(), FakeGateway())
        process.submitTask("open settings")

        process.closeForTests()
        process.closeForTests()

        assertEquals(1, closeCalls)
        assertEquals(NativeTaskResult.RuntimeClosed, process.submitTask("open settings"))
    }

    @Test
    fun `unarmed utterance uses normal run while matching armed utterance uses study run`() = runTest {
        var normalCalls = 0
        var studyCalls = 0
        val process = AndroidRuntimeProcess(
            nativeRuntimeFactory = {
                NativeRuntimeHost(
                    NativeAgentRunner(
                        store = InMemoryStore(),
                        step = { _, _ ->
                            normalCalls += 1
                            StepOutcome.PAUSED_NETWORK
                        },
                    ),
                )
            },
            studyRunnerFactory = { _, _ ->
                ClaimedStudyRunner {
                    studyCalls += 1
                    NativeTaskResult.Paused(RunId("study"), com.caddie.agent.core.RunState.PAUSED_NETWORK)
                }
            },
        )
        process.connectAccessibility(Any(), FakeGateway())

        val normal = process.submitUtterance("hello")
        process.studyCoordinator.arm(studyConfig(), studySpec())
        val study = process.submitUtterance("Jarvis öffne die Einstellungen")

        assertTrue(normal is RuntimeUtteranceResult.Normal)
        assertTrue(study is RuntimeUtteranceResult.Study)
        assertEquals(1, normalCalls)
        assertEquals(1, studyCalls)
    }

    @Test
    fun `delayed normal dispatch cannot start after study mode activates`() = runTest {
        var normalCalls = 0
        val process = AndroidRuntimeProcess(
            nativeRuntimeFactory = {
                NativeRuntimeHost(
                    NativeAgentRunner(
                        store = InMemoryStore(),
                        step = { _, _ -> normalCalls += 1; StepOutcome.PAUSED_NETWORK },
                    ),
                )
            },
        )
        process.connectAccessibility(Any(), FakeGateway())
        process.studyCoordinator.setStudyMode(active = true)

        val result = process.submitTask("already routed normal task")

        assertEquals(NativeTaskResult.Busy, result)
        assertEquals(0, normalCalls)
    }

    @Test
    fun `study preparation reservation excludes an active normal submission`() = runTest {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val process = AndroidRuntimeProcess(
            nativeRuntimeFactory = {
                NativeRuntimeHost(
                    NativeAgentRunner(
                        store = InMemoryStore(),
                        step = { _, _ ->
                            started.complete(Unit)
                            finish.await()
                            StepOutcome.PAUSED_NETWORK
                        },
                    ),
                )
            },
        )
        process.connectAccessibility(Any(), FakeGateway())
        val running = async { process.submitTask("normal task") }
        started.await()

        assertEquals(null, process.tryReserveStudyPreparation())
        finish.complete(Unit)
        running.await()

        val reservation = process.tryReserveStudyPreparation()
        assertTrue(reservation != null)
        reservation?.close()
    }

    @Test
    fun `study-only routing never starts a normal run for pass through input`() = runTest {
        var normalCalls = 0
        val process = AndroidRuntimeProcess(
            nativeRuntimeFactory = {
                NativeRuntimeHost(
                    NativeAgentRunner(
                        store = InMemoryStore(),
                        step = { _, _ ->
                            normalCalls += 1
                            StepOutcome.PAUSED_NETWORK
                        },
                    ),
                )
            },
        )
        process.connectAccessibility(Any(), FakeGateway())

        val outcome = process.submitStudyUtterance("hello")

        assertEquals(StudyRouteOutcome.PassThrough, outcome)
        assertEquals(0, normalCalls)
    }

    @Test
    fun `matching armed utterance cannot fall back when study runner is missing`() = runTest {
        var normalCalls = 0
        val process = AndroidRuntimeProcess(
            nativeRuntimeFactory = {
                NativeRuntimeHost(
                    NativeAgentRunner(
                        store = InMemoryStore(),
                        step = { _, _ ->
                            normalCalls += 1
                            StepOutcome.PAUSED_NETWORK
                        },
                    ),
                )
            },
        )
        process.connectAccessibility(Any(), FakeGateway())
        process.studyCoordinator.arm(studyConfig(), studySpec())

        val outcome = process.submitUtterance("Jarvis öffne die Einstellungen")

        assertTrue(outcome is RuntimeUtteranceResult.Study)
        assertEquals(0, normalCalls)
        assertEquals(ArmedTrialCoordinator.ArmedState.ARMED, process.studyCoordinator.status().state)
    }

    @Test
    fun `process exposes events from the same native runner used for submission`() = runTest {
        val process = AndroidRuntimeProcess(
            nativeRuntimeFactory = { NativeRuntimeHost(pausedRunner()) },
        )
        process.connectAccessibility(Any(), FakeGateway())
        val event = async { process.nativeEvents().first() }
        yield()

        process.submitTask("open settings")

        assertTrue(event.await() is NativeAgentEvent.Started)
    }

    @Test
    fun `process clears normal context through its initialized host`() = runTest {
        var clearCalls = 0
        val process = AndroidRuntimeProcess(
            nativeRuntimeFactory = {
                NativeRuntimeHost(
                    runner = pausedRunner(),
                    clearNormalContextAction = { clearCalls += 1 },
                )
            },
        )
        process.connectAccessibility(Any(), FakeGateway())
        process.submitTask("initialize")

        assertTrue(process.clearNormalContext())
        assertEquals(1, clearCalls)

        process.closeForTests()
        assertFalse(process.clearNormalContext())
        assertEquals(1, clearCalls)
    }

    private fun pausedRunner() = NativeAgentRunner(
        store = InMemoryStore(),
        step = { _, _ -> StepOutcome.PAUSED_NETWORK },
    )

    private class FakeGateway : ExecutionGateway {
        override suspend fun observe() = UiObservation(
            id = "snapshot",
            capturedAtElapsedRealtimeMillis = 1,
            completeness = SnapshotCompleteness.ALL_INTERACTIVE_WINDOWS,
            inputWindows = emptyList(),
        )

        override suspend fun performSemantic(
            target: SemanticTarget,
            action: RequestedAction,
        ): ActionOutcome = ActionOutcome.Accepted
    }

    private class InMemoryStore : SessionStore {
        private val records = linkedMapOf<RunId, MutableList<RunRecord>>()

        override suspend fun snapshot(runId: RunId): RunSnapshot =
            com.caddie.agent.core.reduce(records[runId].orEmpty(), recoveredProcess = false)

        override suspend fun append(event: RunRecord) {
            records.getOrPut(event.runId) { mutableListOf() } += event
        }
    }

    private fun studyConfig() =
        com.caddie.study.runtime.coordinator.ArmedTrialCoordinator.ArmedTrialConfig(
            participantId = "P01",
            trialIndex = 0,
            taskId = "task_settings",
            condition = RuntimeStudyCondition.STEPWISE,
            injectError = false,
        )

    private fun studySpec() = TrialSpec(
        version = "v1",
        id = "task_settings",
        instructionDe = "Öffne die Einstellungen",
        criticality = CriticalityClass.LOW,
        steps = listOf(StudyStep("s1", "click", "open", StepType.NORMAL)),
        trigger = TriggerContract(
            referencePhrases = listOf("Öffne die Einstellungen"),
            requiredConcepts = listOf(listOf("einstellungen")),
            forbiddenConcepts = emptyList(),
            wakeWords = listOf("jarvis"),
        ),
    )
}
