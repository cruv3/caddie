package com.caddie.study.runtime.android

import com.caddie.executor.accessibility.ActionOutcome
import com.caddie.executor.accessibility.ExecutionGateway
import com.caddie.executor.accessibility.RequestedAction
import com.caddie.executor.accessibility.SemanticTarget
import com.caddie.executor.accessibility.SnapshotCompleteness
import com.caddie.executor.accessibility.UiAction
import com.caddie.executor.accessibility.UiNode
import com.caddie.executor.accessibility.UiObservation
import com.caddie.executor.accessibility.UiWindow
import com.caddie.executor.accessibility.UiWindowType
import com.caddie.agent.core.OversightDecision
import com.caddie.agent.core.RunId
import com.caddie.agent.core.RunRecord
import com.caddie.agent.core.RunSnapshot
import com.caddie.agent.core.SessionStore
import com.caddie.agent.core.StepOutcome
import com.caddie.app.runtime.NativeAgentRunner
import com.caddie.app.runtime.NativeRuntimeHost
import com.caddie.app.runtime.NativeTaskResult
import com.caddie.study.StudyGate
import com.caddie.study.runtime.coordinator.ArmedTrialCoordinator
import com.caddie.study.runtime.audit.StudyRuntimeEvent
import com.caddie.study.runtime.audit.StudyRuntimeEventRecorder
import com.caddie.study.runtime.executor.CorrectionContext
import com.caddie.study.runtime.executor.UiStateQuery
import com.caddie.study.runtime.model.CriticalityClass
import com.caddie.study.runtime.model.ErrorVariant
import com.caddie.study.runtime.model.RuntimeStudyCondition
import com.caddie.study.runtime.model.StepType
import com.caddie.study.runtime.model.StudyStep
import com.caddie.study.runtime.model.TrialSpec
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidStudyBackendTest {
    @Test
    fun `step completion audit failure never escapes the executor logging boundary`() {
        recordRuntimeEventBestEffort(
            recorder = StudyRuntimeEventRecorder { error("database unavailable") },
            event = StudyRuntimeEvent(
                studyRunId = "1",
                participantId = "P01",
                trialIndex = 0,
                taskId = "task_test",
                eventType = "step_completed",
            ),
        )
    }

    @Test
    fun `lists study elements and taps the same semantic node`() {
        val node = UiNode(
            observationNodeId = "node-1",
            windowId = 1,
            packageName = "com.caddie.studytelegram",
            resourceId = "com.caddie.studytelegram:id/send",
            text = "Senden",
            className = "android.widget.Button",
            enabled = true,
            visibleToUser = true,
            clickable = true,
            actions = setOf(UiAction.CLICK),
        )
        val gateway = FakeGateway(observation(node))
        val backend = AndroidStudyBackend(gateway)

        val elements = backend.listElements()
        val result = backend.tapElement(elements.single()["index"] as Int)

        assertEquals("Senden", elements.single()["text"])
        assertEquals(true, result["success"])
        assertEquals(
            SemanticTarget(
                packageName = "com.caddie.studytelegram",
                resourceId = "com.caddie.studytelegram:id/send",
                text = "Senden",
                className = "android.widget.Button",
            ),
            gateway.target,
        )
        assertEquals(RequestedAction.Click, gateway.action)
    }

    @Test
    fun `top visible application remains foreground when overlay clears active flags`() {
        val node = UiNode(
            observationNodeId = "mail-subject",
            windowId = 1,
            packageName = "com.caddie.studymail",
            resourceId = "com.caddie.studymail:id/mail_subject",
            text = "Terminänderung Projektsitzung",
            enabled = true,
            visibleToUser = true,
        )
        val window = UiWindow(
            id = 1,
            type = UiWindowType.APPLICATION,
            layer = 1,
            active = false,
            focused = false,
            interactionBarrier = false,
            nodes = listOf(node),
        )
        val backend = AndroidStudyBackend(
            FakeGateway(
                UiObservation(
                    id = "overlay-active",
                    capturedAtElapsedRealtimeMillis = 1,
                    completeness = SnapshotCompleteness.ALL_INTERACTIVE_WINDOWS,
                    inputWindows = listOf(window),
                ),
            ),
        )

        assertEquals(true, backend.listElements().single()["window_foreground"])
    }

    @Test
    fun `refuses action when active run does not grant permission`() {
        val gateway = FakeGateway(observation())
        val backend = AndroidStudyBackend(gateway) { false }

        val result = backend.openApp("com.caddie.studytelegram")

        assertEquals(false, result["success"])
        assertEquals(null, gateway.action)
    }

    @Test
    fun `keyboard dismissal never becomes an unconditional back navigation`() {
        val gateway = FakeGateway(observation())
        val backend = AndroidStudyBackend(gateway)

        val result = backend.dismissInputMethod()

        assertEquals(true, result["success"])
        assertEquals(RequestedAction.DismissInputMethod, gateway.action)
    }

    @Test
    fun `explicit correction is accepted only for one active injected error`() {
        val variant = ErrorVariant("wrong-time", "time", "08:40", "08:00", "wrong time")
        val accepted = SingleInjectedErrorCorrectionClassifier.classify(
            correctionContext(listOf(variant), "die Uhrzeit stimmt nicht"),
        )
        val rejected = SingleInjectedErrorCorrectionClassifier.classify(
            correctionContext(listOf(variant, variant.copy(id = "other"))),
        )

        assertTrue(accepted.accepted)
        assertEquals("wrong-time", accepted.errorVariantId)
        assertFalse(rejected.accepted)
    }

    @Test
    fun `verification reads fresh accessibility text and descriptions`() {
        val gateway = FakeGateway(
            observation(
                UiNode(
                    observationNodeId = "result",
                    windowId = 1,
                    packageName = "com.caddie.studytelegram",
                    text = "Campus Deutz",
                    contentDescription = "Nachricht gesendet",
                    className = "android.widget.TextView",
                    enabled = true,
                    visibleToUser = true,
                ),
            ),
        )
        val verification = AccessibilityStudyVerification(AndroidStudyBackend(gateway))

        assertTrue(verification.checkTextPresent("Deutz"))
        assertTrue(verification.checkAccessibilityElement("gesendet"))
        assertTrue(verification.checkTextAbsent("Fehler"))
    }

    @Test
    fun `structured verification matches only the visible foreground committed node`() {
        val committed = UiNode(
            observationNodeId = "committed-transaction",
            windowId = 1,
            packageName = "com.caddie.studybank",
            resourceId = "com.caddie.studybank:id/new_transaction_amount",
            text = "−30,00 €",
            contentDescription = "Ausgeführte Überweisung",
            enabled = true,
            visibleToUser = true,
        )
        val verification = AccessibilityStudyVerification(
            backend = AndroidStudyBackend(FakeGateway(observation(committed))),
            maxAttempts = 1,
            retryDelayMs = 0,
        )

        assertTrue(
            verification.checkUiState(
                UiStateQuery(
                    packageName = "com.caddie.studybank",
                    resourceId = "com.caddie.studybank:id/new_transaction_amount",
                    text = "30,00 €",
                    contentDescription = "ausgeführte",
                ),
            ),
        )
        assertFalse(
            verification.checkUiState(
                UiStateQuery(
                    packageName = "com.caddie.studybank",
                    resourceId = "com.caddie.studybank:id/confirm_details",
                    text = "30,00 €",
                ),
            ),
        )
    }

    @Test
    fun `structured verification rejects hidden or background lookalikes`() {
        fun node(id: String, visible: Boolean) = UiNode(
            observationNodeId = id,
            windowId = 1,
            packageName = "com.caddie.studycalendar",
            resourceId = "com.caddie.studycalendar:id/detail_time",
            text = "15:00–16:00 Uhr",
            enabled = true,
            visibleToUser = visible,
        )
        val hiddenVerification = AccessibilityStudyVerification(
            backend = AndroidStudyBackend(FakeGateway(observation(node("hidden", false)))),
            maxAttempts = 1,
            retryDelayMs = 0,
        )
        val backgroundWindow = UiWindow(
            id = 1,
            type = UiWindowType.APPLICATION,
            layer = 0,
            active = false,
            focused = false,
            interactionBarrier = false,
            nodes = listOf(node("background", true)),
        )
        val foregroundWindow = UiWindow(
            id = 2,
            type = UiWindowType.APPLICATION,
            layer = 1,
            active = true,
            focused = true,
            interactionBarrier = false,
            nodes = listOf(
                UiNode(
                    observationNodeId = "foreground",
                    windowId = 2,
                    packageName = "com.caddie.studytelegram",
                    resourceId = "com.caddie.studytelegram:id/message_text",
                    text = "Aktive Unterhaltung",
                    enabled = true,
                    visibleToUser = true,
                ),
            ),
        )
        val backgroundVerification = AccessibilityStudyVerification(
            backend = AndroidStudyBackend(
                FakeGateway(
                    UiObservation(
                        id = "background-observation",
                        capturedAtElapsedRealtimeMillis = 1,
                        completeness = SnapshotCompleteness.ALL_INTERACTIVE_WINDOWS,
                        inputWindows = listOf(foregroundWindow, backgroundWindow),
                    ),
                ),
            ),
            maxAttempts = 1,
            retryDelayMs = 0,
        )
        val query = UiStateQuery(
            packageName = "com.caddie.studycalendar",
            resourceId = "com.caddie.studycalendar:id/detail_time",
            text = "15:00–16:00 Uhr",
        )

        assertFalse(hiddenVerification.checkUiState(query))
        assertFalse(backgroundVerification.checkUiState(query))
    }

    @Test
    fun `verification waits for accessibility tree to expose committed text`() {
        val gateway = SequencedGateway(
            listOf(
                observation(),
                observation(
                    UiNode(
                        observationNodeId = "sent-message",
                        windowId = 1,
                        packageName = "com.caddie.studytelegram",
                        text = "Campus Deutz",
                        className = "android.widget.TextView",
                        enabled = true,
                        visibleToUser = true,
                    ),
                ),
            ),
        )
        val delays = mutableListOf<Long>()
        val verification = AccessibilityStudyVerification(
            backend = AndroidStudyBackend(gateway),
            maxAttempts = 3,
            retryDelayMs = 25,
            pause = { delays += it },
        )

        assertTrue(verification.checkTextPresent("Campus Deutz"))
        assertEquals(2, gateway.observeCalls)
        assertEquals(listOf(25L), delays)
    }

    @Test
    fun `default verification window covers delayed app completion screens`() {
        val completed = observation(
            UiNode(
                observationNodeId = "committed-transaction",
                windowId = 1,
                packageName = "com.caddie.studybank",
                text = "Study Vendor GmbH",
                className = "android.widget.TextView",
                enabled = true,
                visibleToUser = true,
            ),
        )
        val gateway = SequencedGateway(List(12) { observation() } + completed)
        val verification = AccessibilityStudyVerification(
            backend = AndroidStudyBackend(gateway),
            pause = { },
        )

        assertTrue(verification.checkTextPresent("Study Vendor GmbH"))
        assertEquals(13, gateway.observeCalls)
    }

    @Test
    fun `verification fails only after bounded accessibility retries`() {
        val gateway = SequencedGateway(listOf(observation()))
        val delays = mutableListOf<Long>()
        val verification = AccessibilityStudyVerification(
            backend = AndroidStudyBackend(gateway),
            maxAttempts = 3,
            retryDelayMs = 25,
            pause = { delays += it },
        )

        assertFalse(verification.checkTextPresent("Campus Deutz"))
        assertEquals(3, gateway.observeCalls)
        assertEquals(listOf(25L, 25L), delays)
    }

    @Test
    fun `claimed study executes fixed spec without invoking normal agent step`() = runTest {
        var normalAgentCalls = 0
        val store = InMemoryStore()
        val host = NativeRuntimeHost(
            NativeAgentRunner(
                store = store,
                step = { _, _ ->
                    normalAgentCalls++
                    StepOutcome.PAUSED_NETWORK
                },
            ),
        )
        val gateway = FakeGateway(
            observation(
                UiNode(
                    observationNodeId = "root",
                    windowId = 1,
                    packageName = "com.caddie.studytelegram",
                    className = "android.view.View",
                    enabled = true,
                    visibleToUser = true,
                ),
            ),
        )
        val spec = TrialSpec(
            version = "1",
            id = "open-chat",
            instructionDe = "Öffne den Chat",
            criticality = CriticalityClass.LOW,
            steps = listOf(
                StudyStep(
                    id = "open",
                    action = "open com.caddie.studytelegram",
                    narration = "Chat öffnen",
                    stepType = StepType.NORMAL,
                    minNarrationMs = 0,
                ),
            ),
            completionMessageDe = "Chat geöffnet",
        )
        val claim = ArmedTrialCoordinator.ClaimedTrial(
            config = ArmedTrialCoordinator.ArmedTrialConfig(
                participantId = "P01",
                trialIndex = 0,
                taskId = spec.id,
                condition = RuntimeStudyCondition.VOLUNTARY_INTERVENTION,
                injectError = false,
                studyRunId = "1",
            ),
            spec = spec,
            participantUtterance = spec.instructionDe,
            token = ArmedTrialCoordinator.ClaimToken(1),
        )
        val runner = NativeDeterministicStudyRunner(
            host = host,
            gateway = gateway,
            gate = AutoApproveGate,
            errorRecorder = { error("no error expected") },
        )

        val result = runner.run(claim)

        assertTrue(result is NativeTaskResult.Completed)
        assertEquals("Chat geöffnet", (result as NativeTaskResult.Completed).answer)
        assertEquals(0, normalAgentCalls)
        assertTrue(gateway.action is RequestedAction.OpenApp)
    }

    @Test
    fun `aborted claim cannot dispatch before native run registration`() = runTest {
        val host = NativeRuntimeHost(
            NativeAgentRunner(InMemoryStore(), step = { _, _ -> StepOutcome.PAUSED_NETWORK }),
        )
        val gateway = FakeGateway(observation())
        val spec = TrialSpec(
            version = "1",
            id = "open-chat",
            instructionDe = "Öffne den Chat",
            criticality = CriticalityClass.LOW,
            steps = listOf(
                StudyStep(
                    id = "open",
                    action = "open com.caddie.studytelegram",
                    narration = "Chat öffnen",
                    stepType = StepType.NORMAL,
                    minNarrationMs = 0,
                ),
            ),
        )
        val claim = ArmedTrialCoordinator.ClaimedTrial(
            config = ArmedTrialCoordinator.ArmedTrialConfig(
                participantId = "TEST",
                trialIndex = 0,
                taskId = spec.id,
                condition = RuntimeStudyCondition.STEPWISE,
                injectError = false,
            ),
            spec = spec,
            participantUtterance = spec.instructionDe,
            token = ArmedTrialCoordinator.ClaimToken(1),
        )
        val runner = NativeDeterministicStudyRunner(
            host = host,
            gateway = gateway,
            gate = AutoApproveGate,
            errorRecorder = { error("no error expected") },
            claimActive = { false },
        )

        val result = runner.run(claim)

        assertTrue(result is NativeTaskResult.Paused)
        assertEquals(null, gateway.action)
    }

    private fun correctionContext(
        variants: List<ErrorVariant>,
        correction: String = "nicht 08:40, sondern 08:00",
    ) = CorrectionContext(
        instruction = "Aufgabe",
        taskId = "task",
        correction = correction,
        executedStepIds = emptyList(),
        nextStepId = "step",
        activeVariants = variants,
    )

    private fun observation(vararg nodes: UiNode) = UiObservation(
        id = "observation",
        capturedAtElapsedRealtimeMillis = 1,
        completeness = SnapshotCompleteness.ALL_INTERACTIVE_WINDOWS,
        inputWindows = listOf(
            UiWindow(
                id = 1,
                type = UiWindowType.APPLICATION,
                layer = 0,
                active = true,
                focused = true,
                interactionBarrier = false,
                nodes = nodes.toList(),
            ),
        ),
    )

    private class FakeGateway(private val observation: UiObservation) : ExecutionGateway {
        var target: SemanticTarget? = null
        var action: RequestedAction? = null

        override suspend fun observe(): UiObservation = observation

        override suspend fun performSemantic(
            target: SemanticTarget,
            action: RequestedAction,
        ): ActionOutcome {
            this.target = target
            this.action = action
            return ActionOutcome.Accepted
        }
    }

    private class SequencedGateway(
        observations: List<UiObservation>,
    ) : ExecutionGateway {
        private val observations = ArrayDeque(observations)
        private var latest = observations.last()
        var observeCalls: Int = 0
            private set

        override suspend fun observe(): UiObservation {
            observeCalls++
            if (observations.isNotEmpty()) latest = observations.removeFirst()
            return latest
        }

        override suspend fun performSemantic(
            target: SemanticTarget,
            action: RequestedAction,
        ): ActionOutcome = ActionOutcome.Accepted
    }

    private object AutoApproveGate : StudyGate {
        override suspend fun confirmStep(
            call: com.caddie.agent.core.ModelDelta.ToolCall,
            confirmationText: String?,
        ) = OversightDecision(true)

        override suspend fun confirmFinal(
            calls: List<com.caddie.agent.core.ModelDelta.ToolCall>,
            summaryLines: List<String>,
        ) = OversightDecision(true)
    }

    private class InMemoryStore : SessionStore {
        private val records = linkedMapOf<RunId, MutableList<RunRecord>>()

        override suspend fun snapshot(runId: RunId): RunSnapshot =
            com.caddie.agent.core.reduce(records[runId].orEmpty(), recoveredProcess = false)

        override suspend fun append(event: RunRecord) {
            records.getOrPut(event.runId) { mutableListOf() } += event
        }
    }
}
