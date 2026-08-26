package com.caddie.study.runtime.executor

import com.caddie.study.runtime.model.CorrectionAssertion
import com.caddie.study.runtime.model.CorrectionPolicy
import com.caddie.study.runtime.model.CorrectionStep
import com.caddie.study.runtime.model.PostCommitPolicy
import com.caddie.study.runtime.model.RuntimeStudyCondition
import com.caddie.study.runtime.model.StepType
import com.caddie.study.runtime.model.StudyStep
import com.caddie.study.runtime.model.TrialOutcome
import com.caddie.study.runtime.model.TrialSpec
import com.caddie.study.runtime.spec.SpecLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TrialExecutorTest {

    // ── Fakes ──

    /** In-memory backend that records calls and returns canned elements. */
    private class FakeBackend(
        private val elements: List<Map<String, Any?>> = emptyList(),
        private val openAppResult: Map<String, Any?> = mapOf("success" to true),
        private val openUrlResult: Map<String, Any?> = mapOf("success" to true),
        private val tapResult: Map<String, Any?> = mapOf("success" to true),
        private val typeResult: Map<String, Any?> = mapOf("success" to true),
    ) : StudyBackend {
        val calls = mutableListOf<String>()

        override fun listElements(): List<Map<String, Any?>> {
            calls.add("listElements")
            return elements
        }
        override fun openApp(packageName: String): Map<String, Any?> {
            calls.add("openApp($packageName)")
            return openAppResult
        }
        override fun openUrl(url: String): Map<String, Any?> {
            calls.add("openUrl($url)")
            return openUrlResult
        }
        override fun tapElement(index: Int): Map<String, Any?> {
            calls.add("tapElement($index)")
            return tapResult
        }
        override fun scroll(direction: String, amount: Double): Map<String, Any?> {
            calls.add("scroll($direction)")
            return mapOf("success" to true)
        }
        override fun pressButton(button: String): Map<String, Any?> {
            calls.add("pressButton($button)")
            return mapOf("success" to true)
        }
        override fun dismissInputMethod(): Map<String, Any?> {
            calls.add("dismissInputMethod")
            return mapOf("success" to true)
        }
        override fun typeText(text: String, submit: Boolean): Map<String, Any?> {
            calls.add("typeText($text,$submit)")
            return typeResult
        }
        override fun replaceText(text: String, submit: Boolean): Map<String, Any?> {
            calls.add("replaceText($text,$submit)")
            return typeResult
        }
    }

    @Test
    fun `keyboard close step is distinct from a later back navigation`() = runBlocking {
        val backend = FakeBackend()
        val executor = TrialExecutor(
            backend = backend,
            logger = RecordingLogger(),
            oversight = AutoApproveGate(),
            spec = TrialSpec(
                version = "1",
                id = "keyboard-and-save",
                instructionDe = "Notiz speichern",
                criticality = com.caddie.study.runtime.model.CriticalityClass.LOW,
                steps = listOf(
                    StudyStep(
                        id = "hide_keyboard",
                        action = "press BACK",
                        narration = "Tastatur schließen",
                        stepType = StepType.NORMAL,
                        minNarrationMs = 0,
                    ),
                    StudyStep(
                        id = "save_note",
                        action = "press BACK",
                        narration = "Notiz speichern",
                        stepType = StepType.COMMIT,
                        minNarrationMs = 0,
                    ),
                ),
            ),
            condition = RuntimeStudyCondition.VOLUNTARY_INTERVENTION,
        )

        val result = executor.run()

        assertEquals(TrialOutcome.SUCCESS, result.outcome)
        assertTrue("dismissInputMethod" in backend.calls)
        assertTrue("pressButton(BACK)" in backend.calls)
        assertTrue(backend.calls.indexOf("dismissInputMethod") < backend.calls.indexOf("pressButton(BACK)"))
    }

    @Test
    fun `song recommendation capture accepts the seeded chat message`() = runBlocking {
        val backend = FakeBackend(
            elements = listOf(
                mapOf(
                    "index" to 0,
                    "text" to "Song-Tipp: As It Was von Harry Styles.",
                    "content_description" to "",
                ),
            ),
        )
        val executor = TrialExecutor(
            backend = backend,
            logger = RecordingLogger(),
            oversight = AutoApproveGate(),
            spec = TrialSpec(
                version = "1",
                id = "capture-song",
                instructionDe = "Liedempfehlung merken",
                criticality = com.caddie.study.runtime.model.CriticalityClass.LOW,
                steps = listOf(
                    StudyStep(
                        id = "read_recommendation",
                        action = "capture song recommendation as 'song_title'",
                        narration = "Empfehlung lesen",
                        stepType = StepType.NORMAL,
                        minNarrationMs = 0,
                    ),
                ),
            ),
            condition = RuntimeStudyCondition.VOLUNTARY_INTERVENTION,
        )

        val result = executor.run()

        assertEquals(TrialOutcome.SUCCESS, result.outcome)
    }

    @Test
    fun `already satisfied navigation is skipped before C1 confirmation`() = runBlocking {
        val backend = FakeBackend(
            elements = listOf(
                mapOf(
                    "index" to 0,
                    "package_name" to "com.caddie.studymail",
                    "resource_id" to "com.caddie.studymail:id/mail_subject",
                    "text" to "Terminänderung Projektsitzung",
                    "enabled" to true,
                    "visible" to true,
                    "window_foreground" to true,
                ),
            ),
        )
        val gate = AutoApproveGate()
        val logger = RecordingLogger()
        val step = StudyStep(
            id = "find_email",
            action = "click 'com.caddie.studymail:id/mail_meeting_change'",
            narration = "E-Mail öffnen",
            stepType = StepType.CONSEQUENTIAL,
            minNarrationMs = 0,
            alreadySatisfied = com.caddie.study.runtime.model.UiStateEvidence(
                packageName = "com.caddie.studymail",
                resourceId = "com.caddie.studymail:id/mail_subject",
                text = "Terminänderung Projektsitzung",
            ),
        )

        val result = TrialExecutor(
            backend = backend,
            logger = logger,
            oversight = gate,
            spec = simpleSpec(listOf(step)),
            condition = RuntimeStudyCondition.STEPWISE,
        ).run()

        assertEquals(TrialOutcome.SUCCESS, result.outcome)
        assertTrue(gate.confirmedSteps.isEmpty())
        assertTrue(backend.calls.none { it.startsWith("tapElement") })
        assertTrue(logger.events.contains("stepAlreadySatisfied(find_email)"))
    }

    @Test
    fun `commit step is never skipped from observed UI state`() = runBlocking {
        val backend = FakeBackend(
            elements = listOf(
                mapOf(
                    "index" to 7,
                    "resource_id" to "save_event",
                    "text" to "Speichern",
                    "enabled" to true,
                    "visible" to true,
                ),
            ),
        )
        val gate = AutoApproveGate()
        val step = StudyStep(
            id = "save",
            action = "click 'save_event'",
            narration = "Speichern",
            stepType = StepType.COMMIT,
            minNarrationMs = 0,
            alreadySatisfied = com.caddie.study.runtime.model.UiStateEvidence(
                resourceId = "save_event",
                text = "Speichern",
            ),
        )

        val result = TrialExecutor(
            backend = backend,
            logger = RecordingLogger(),
            oversight = gate,
            spec = simpleSpec(listOf(step)),
            condition = RuntimeStudyCondition.STEPWISE,
        ).run()

        assertEquals(TrialOutcome.SUCCESS, result.outcome)
        assertEquals(listOf("save"), gate.confirmedSteps)
        assertTrue("tapElement(7)" in backend.calls)
    }

    @Test
    fun `already satisfied evidence requires exact Android resource identity`() {
        val step = StudyStep(
            id = "find_email",
            action = "click 'mail'",
            narration = "E-Mail öffnen",
            stepType = StepType.NORMAL,
            alreadySatisfied = com.caddie.study.runtime.model.UiStateEvidence(
                packageName = "com.caddie.studymail",
                resourceId = "com.caddie.studymail:id/mail_subject",
            ),
        )
        val backend = FakeBackend(
            elements = listOf(
                mapOf(
                    "package_name" to "com.caddie.studymail",
                    "resource_id" to "com.caddie.studymail:id/MAIL_SUBJECT",
                    "enabled" to true,
                    "visible" to true,
                ),
            ),
        )

        assertFalse(backend.isStepAlreadySatisfied(step))
    }

    @Test
    fun `hidden or background evidence never satisfies a navigation step`() {
        val step = StudyStep(
            id = "find_email",
            action = "click 'mail'",
            narration = "E-Mail öffnen",
            stepType = StepType.NORMAL,
            alreadySatisfied = com.caddie.study.runtime.model.UiStateEvidence(
                packageName = "com.caddie.studymail",
                resourceId = "com.caddie.studymail:id/mail_subject",
            ),
        )
        val hidden = FakeBackend(
            elements = listOf(
                mapOf(
                    "package_name" to "com.caddie.studymail",
                    "resource_id" to "com.caddie.studymail:id/mail_subject",
                    "enabled" to true,
                    "visible" to false,
                    "window_foreground" to true,
                ),
            ),
        )
        val background = FakeBackend(
            elements = listOf(
                mapOf(
                    "package_name" to "com.caddie.studymail",
                    "resource_id" to "com.caddie.studymail:id/mail_subject",
                    "enabled" to true,
                    "visible" to true,
                    "window_foreground" to false,
                ),
            ),
        )

        assertFalse(hidden.isStepAlreadySatisfied(step))
        assertFalse(background.isStepAlreadySatisfied(step))
    }

    /** Oversight gate that auto-confirms everything. */
    private class AutoApproveGate : TrialOversightGate {
        var cancelled = false
        val confirmedSteps = mutableListOf<String>()
        val c2Summaries = mutableListOf<List<String>>()

        override suspend fun confirmConsequentialStep(step: StudyStep, confirmationText: String): OversightGateDecision {
            confirmedSteps.add(step.id)
            return OversightGateDecision(confirmed = true)
        }
        override suspend fun showC2Summary(steps: List<StudyStep>, narrations: List<String>): C2Decision {
            c2Summaries.add(narrations)
            return C2Decision(confirmed = true)
        }
        override fun isCancelled(): Boolean = cancelled
    }

    /** Gate that always declines. */
    private class DeclineGate : TrialOversightGate {
        override suspend fun confirmConsequentialStep(step: StudyStep, confirmationText: String) =
            OversightGateDecision(confirmed = false, declined = true)
        override suspend fun showC2Summary(steps: List<StudyStep>, narrations: List<String>) =
            C2Decision(confirmed = false, declined = true)
        override fun isCancelled() = false
    }

    /** In-memory logger that records events. */
    private class RecordingLogger(override val participantId: String = "P01") : StudyLogger {
        data class ConfirmationRecord(
            val gateType: String,
            val stepId: String?,
            val decision: String,
            val responseTimeMs: Double,
            val participantResponse: Boolean,
        )

        val events = mutableListOf<String>()
        val confirmations = mutableListOf<ConfirmationRecord>()
        var completedConfirmationTimeMs: Double? = null
        var completedConfirmationCount: Int? = null
        private var trialCounter = 0

        override fun trialStart(taskId: String, block: String, variant: String): String {
            trialCounter++
            val id = "trial-$trialCounter"
            events.add("trialStart($taskId,$variant)=$id")
            return id
        }
        override fun trialComplete(trialId: String, outcome: String, totalSteps: Int, errorsInjected: Int, durationMs: Double) {
            events.add("trialComplete($trialId,$outcome,steps=$totalSteps,errs=$errorsInjected)")
        }
        override fun trialComplete(
            trialId: String,
            outcome: String,
            totalSteps: Int,
            errorsInjected: Int,
            durationMs: Double,
            confirmationResponseTimeMs: Double,
            confirmationCount: Int,
        ) {
            trialComplete(trialId, outcome, totalSteps, errorsInjected, durationMs)
            completedConfirmationTimeMs = confirmationResponseTimeMs
            completedConfirmationCount = confirmationCount
        }
        override fun confirmationResolved(
            trialId: String,
            gateType: String,
            stepId: String?,
            decision: String,
            responseTimeMs: Double,
            participantResponse: Boolean,
        ) {
            confirmations += ConfirmationRecord(
                gateType, stepId, decision, responseTimeMs, participantResponse,
            )
        }
        override fun stepStart(stepId: String, narration: String, trialId: String) {
            events.add("stepStart($stepId)")
        }
        override fun stepFinish(stepId: String, trialId: String, action: String) {
            events.add("stepFinish($stepId)")
        }
        override fun stepAlreadySatisfied(stepId: String, trialId: String) {
            events.add("stepAlreadySatisfied($stepId)")
        }
        override fun errorInjected(stepId: String, trialId: String, errorVariantId: String, field: String, wrongValue: String, correctValue: String) {
            events.add("errorInjected($stepId,$errorVariantId,$wrongValue)")
        }
        override fun screenshotCaptured(label: String, trialId: String): String {
            events.add("screenshot($label)")
            return "/tmp/$label.png"
        }
        override fun technicalFailure(error: String) {
            events.add("technicalFailure($error)")
        }
        override fun writeSummary(outcome: String, totalSteps: Int, errorsInjected: Int, durationMs: Double, trialIds: List<String>, verificationResults: Map<String, Any?>?, screenshots: List<String>) {
            events.add("summary($outcome,steps=$totalSteps,errs=$errorsInjected)")
        }
        override fun intervention(text: String, trialId: String, taskId: String, accepted: Boolean, reason: String, errorVariantId: String?) {
            events.add("intervention($text,accepted=$accepted,reason=$reason,variant=$errorVariantId)")
        }
        override fun correctionRoute(errorVariantId: String, route: String, phase: String, trialId: String, taskId: String) {
            events.add("correctionRoute($errorVariantId,$route,$phase)")
        }
        override fun correctionIrreversible(errorVariantId: String, commitStepId: String, message: String, trialId: String, taskId: String) {
            events.add("correctionIrreversible($errorVariantId,$commitStepId)")
        }
        override fun correctionRewind(errorVariantId: String, fromStepId: String?, toStepId: String, trialId: String, taskId: String) {
            events.add("correctionRewind($errorVariantId,$fromStepId->$toStepId)")
        }
        override fun compensationStep(stepId: String, errorVariantId: String, outcome: String, trialId: String, taskId: String) {
            events.add("compensationStep($stepId,$outcome)")
        }
        override fun verificationStart(ruleCount: Int, trialId: String, taskId: String) {
            events.add("verificationStart(rules=$ruleCount,trial=$trialId)")
        }
        override fun verificationComplete(passed: Int, failed: Int, timedOut: Int, skipped: Int, trialId: String, taskId: String) {
            events.add("verificationComplete(passed=$passed,failed=$failed,timedOut=$timedOut,skipped=$skipped)")
        }
        override fun verificationResult(ruleId: String, passed: Boolean, trialId: String, taskId: String, screenshotPath: String?, details: Map<String, Any?>) {
            events.add("verificationResult($ruleId,passed=$passed,shot=$screenshotPath)")
        }
    }

    // ── Test specs ──

    private fun simpleSpec(
        steps: List<StudyStep>,
        errorSteps: List<String> = emptyList(),
        condition: RuntimeStudyCondition = RuntimeStudyCondition.VOLUNTARY_INTERVENTION,
    ) = TrialSpec(
        version = "v1",
        id = "task_test",
        instructionDe = "Test instruction",
        criticality = com.caddie.study.runtime.model.CriticalityClass.HIGH,
        steps = steps,
        errorSteps = errorSteps,
        maxDurationS = 300,
        perGateTimeoutS = 30,
    )

    private val tapStep = StudyStep(
        id = "s1", action = "click 'Senden'", narration = "Auf Senden tippen",
        stepType = StepType.NORMAL, minNarrationMs = 0,
    )

    private val commitStep = StudyStep(
        id = "s2", action = "click 'Bestätigen'", narration = "Bestätigen",
        stepType = StepType.COMMIT, minNarrationMs = 0,
    )

    // ── Tests ──

    @Test
    fun `c3 voluntary intervention runs all steps without gates`() = runBlocking {
        val backend = FakeBackend(elements = listOf(
            mapOf("index" to 0, "text" to "Senden"),
            mapOf("index" to 1, "text" to "Bestätigen"),
        ))
        val gate = AutoApproveGate()
        val logger = RecordingLogger()
        val spec = simpleSpec(listOf(tapStep, commitStep))

        val result = TrialExecutor(backend, logger, gate, spec, RuntimeStudyCondition.VOLUNTARY_INTERVENTION).run()

        assertEquals(TrialOutcome.SUCCESS, result.outcome)
        assertEquals(2, result.stepsDone)
        assertTrue(result.stepsExecuted.all { it.success })
        assertTrue(gate.confirmedSteps.isEmpty())
        assertTrue(gate.c2Summaries.isEmpty())
    }

    @Test
    fun `c1 stepwise confirms each meaningful step`() = runBlocking {
        val backend = FakeBackend(elements = listOf(
            mapOf("index" to 0, "text" to "Senden"),
            mapOf("index" to 1, "text" to "Bestätigen"),
        ))
        val gate = AutoApproveGate()
        val logger = RecordingLogger()
        val spec = simpleSpec(listOf(tapStep, commitStep))

        val result = TrialExecutor(backend, logger, gate, spec, RuntimeStudyCondition.STEPWISE).run()

        assertEquals(TrialOutcome.SUCCESS, result.outcome)
        // Both steps have confirmInC1=true by default
        assertEquals(2, gate.confirmedSteps.size)
    }

    @Test
    fun `c1 stores each response time and cumulative trial metrics`() = runBlocking {
        val backend = FakeBackend(elements = listOf(
            mapOf("index" to 0, "text" to "Senden"),
            mapOf("index" to 1, "text" to "Bestätigen"),
        ))
        val logger = RecordingLogger()
        val clock = listOf(0L, 1_000_000_000L, 1_000_000_000L, 3_500_000_000L).iterator()

        val result = TrialExecutor(
            backend = backend,
            logger = logger,
            oversight = AutoApproveGate(),
            spec = simpleSpec(listOf(tapStep, commitStep)),
            condition = RuntimeStudyCondition.STEPWISE,
            confirmationNanoTime = { clock.next() },
        ).run()

        assertEquals(TrialOutcome.SUCCESS, result.outcome)
        assertEquals(listOf("c1", "c1"), logger.confirmations.map { it.gateType })
        assertEquals(listOf("s1", "s2"), logger.confirmations.map { it.stepId })
        assertEquals(listOf("confirmed", "confirmed"), logger.confirmations.map { it.decision })
        assertEquals(1_000.0, logger.confirmations[0].responseTimeMs, 0.001)
        assertEquals(2_500.0, logger.confirmations[1].responseTimeMs, 0.001)
        assertEquals(3_500.0, logger.completedConfirmationTimeMs!!, 0.001)
        assertEquals(2, logger.completedConfirmationCount)
    }

    @Test
    fun `c1 stores participant decision latency without overlay settle delay`() = runBlocking {
        val gate = object : TrialOversightGate {
            override suspend fun confirmConsequentialStep(
                step: StudyStep,
                confirmationText: String,
            ) = OversightGateDecision(
                confirmed = true,
                responseLatencyMs = 800.0,
            )

            override suspend fun showC2Summary(
                steps: List<StudyStep>,
                narrations: List<String>,
            ) = C2Decision(confirmed = true)

            override fun isCancelled() = false
        }
        val logger = RecordingLogger()
        val clock = listOf(0L, 1_000_000_000L).iterator()

        val result = TrialExecutor(
            backend = FakeBackend(
                elements = listOf(mapOf("index" to 0, "text" to "Senden")),
            ),
            logger = logger,
            oversight = gate,
            spec = simpleSpec(listOf(tapStep)),
            condition = RuntimeStudyCondition.STEPWISE,
            confirmationNanoTime = { clock.next() },
        ).run()

        assertEquals(TrialOutcome.SUCCESS, result.outcome)
        assertEquals(800.0, logger.confirmations.single().responseTimeMs, 0.001)
        assertEquals(800.0, logger.completedConfirmationTimeMs!!, 0.001)
    }

    @Test
    fun `c1 gate decline aborts trial`() = runBlocking {
        val backend = FakeBackend(elements = listOf(mapOf("index" to 0, "text" to "Senden")))
        val gate = DeclineGate()
        val logger = RecordingLogger()
        val spec = simpleSpec(listOf(tapStep))

        val result = TrialExecutor(backend, logger, gate, spec, RuntimeStudyCondition.STEPWISE).run()

        assertEquals(TrialOutcome.ABORTED, result.outcome)
        assertEquals(0, result.stepsDone)
        assertTrue(result.reason.contains("declined"))
    }

    @Test
    fun `c2 final checkpoint shows summary before commit`() = runBlocking {
        val backend = FakeBackend(elements = listOf(
            mapOf("index" to 0, "text" to "Senden"),
            mapOf("index" to 1, "text" to "Bestätigen"),
        ))
        val gate = AutoApproveGate()
        val logger = RecordingLogger()
        val spec = simpleSpec(listOf(tapStep, commitStep), condition = RuntimeStudyCondition.FINAL_CHECKPOINT)

        val result = TrialExecutor(backend, logger, gate, spec, RuntimeStudyCondition.FINAL_CHECKPOINT).run()

        assertEquals(TrialOutcome.SUCCESS, result.outcome)
        assertEquals(1, gate.c2Summaries.size)
        // Non-commit steps are not confirmed in C2
        assertTrue(gate.confirmedSteps.isEmpty())
    }

    @Test
    fun `c2 stores final checkpoint response time`() = runBlocking {
        val backend = FakeBackend(elements = listOf(
            mapOf("index" to 0, "text" to "Senden"),
            mapOf("index" to 1, "text" to "Bestätigen"),
        ))
        val logger = RecordingLogger()
        val clock = listOf(0L, 4_750_000_000L).iterator()

        val result = TrialExecutor(
            backend = backend,
            logger = logger,
            oversight = AutoApproveGate(),
            spec = simpleSpec(
                listOf(tapStep, commitStep),
                condition = RuntimeStudyCondition.FINAL_CHECKPOINT,
            ),
            condition = RuntimeStudyCondition.FINAL_CHECKPOINT,
            confirmationNanoTime = { clock.next() },
        ).run()

        assertEquals(TrialOutcome.SUCCESS, result.outcome)
        assertEquals(1, logger.confirmations.size)
        assertEquals("c2", logger.confirmations.single().gateType)
        assertEquals("s2", logger.confirmations.single().stepId)
        assertEquals(4_750.0, logger.confirmations.single().responseTimeMs, 0.001)
        assertEquals(4_750.0, logger.completedConfirmationTimeMs!!, 0.001)
        assertEquals(1, logger.completedConfirmationCount)
    }

    @Test
    fun `c2 timeout is distinguished from an explicit decline`() = runBlocking {
        val backend = FakeBackend(
            elements = listOf(mapOf("index" to 1, "text" to "Bestätigen")),
        )
        val logger = RecordingLogger()
        val timeoutGate = object : TrialOversightGate {
            override suspend fun confirmConsequentialStep(
                step: StudyStep,
                confirmationText: String,
            ) = OversightGateDecision(confirmed = true)

            override suspend fun showC2Summary(
                steps: List<StudyStep>,
                narrations: List<String>,
            ) = C2Decision(
                confirmed = false,
                declined = true,
                reason = "final checkpoint timeout",
            )

            override fun isCancelled() = false
        }
        val clock = listOf(0L, 30_000_000_000L).iterator()

        val result = TrialExecutor(
            backend = backend,
            logger = logger,
            oversight = timeoutGate,
            spec = simpleSpec(
                listOf(commitStep),
                condition = RuntimeStudyCondition.FINAL_CHECKPOINT,
            ),
            condition = RuntimeStudyCondition.FINAL_CHECKPOINT,
            confirmationNanoTime = { clock.next() },
        ).run()

        assertEquals(TrialOutcome.ABORTED, result.outcome)
        assertEquals("timeout", logger.confirmations.single().decision)
        assertEquals(30_000.0, logger.confirmations.single().responseTimeMs, 0.001)
        assertFalse(logger.confirmations.single().participantResponse)
        assertEquals(0.0, logger.completedConfirmationTimeMs!!, 0.001)
        assertEquals(0, logger.completedConfirmationCount)
    }

    @Test
    fun `system cancellation is excluded from participant response aggregates`() = runBlocking {
        val logger = RecordingLogger()
        val cancelledGate = object : TrialOversightGate {
            override suspend fun confirmConsequentialStep(
                step: StudyStep,
                confirmationText: String,
            ) = OversightGateDecision(
                confirmed = false,
                declined = true,
                reason = "investigator aborted trial",
            )

            override suspend fun showC2Summary(
                steps: List<StudyStep>,
                narrations: List<String>,
            ) = C2Decision(confirmed = true)

            override fun isCancelled() = false
        }
        val clock = listOf(0L, 500_000_000L).iterator()

        val result = TrialExecutor(
            backend = FakeBackend(
                elements = listOf(mapOf("index" to 0, "text" to "Senden")),
            ),
            logger = logger,
            oversight = cancelledGate,
            spec = simpleSpec(listOf(tapStep)),
            condition = RuntimeStudyCondition.STEPWISE,
            confirmationNanoTime = { clock.next() },
        ).run()

        assertEquals(TrialOutcome.ABORTED, result.outcome)
        assertEquals("cancelled", logger.confirmations.single().decision)
        assertFalse(logger.confirmations.single().participantResponse)
        assertEquals(0.0, logger.completedConfirmationTimeMs!!, 0.001)
        assertEquals(0, logger.completedConfirmationCount)
    }

    @Test
    fun `participant response before settle cancellation remains in audit and aggregates`() {
        val logger = RecordingLogger()
        val cancelledAfterResponseGate = object : TrialOversightGate {
            override suspend fun confirmConsequentialStep(
                step: StudyStep,
                confirmationText: String,
            ): OversightGateDecision = throw ParticipantResponseCancellation(
                confirmed = true,
                declined = false,
                reason = null,
                responseLatencyMs = 750.0,
                cause = CancellationException("trial aborted during settle"),
            )

            override suspend fun showC2Summary(
                steps: List<StudyStep>,
                narrations: List<String>,
            ) = C2Decision(confirmed = true)

            override fun isCancelled() = false
        }

        assertThrows(CancellationException::class.java) {
            runBlocking {
                TrialExecutor(
                    backend = FakeBackend(
                        elements = listOf(mapOf("index" to 0, "text" to "Senden")),
                    ),
                    logger = logger,
                    oversight = cancelledAfterResponseGate,
                    spec = simpleSpec(listOf(tapStep)),
                    condition = RuntimeStudyCondition.STEPWISE,
                ).run()
            }
        }

        assertEquals("confirmed", logger.confirmations.single().decision)
        assertTrue(logger.confirmations.single().participantResponse)
        assertEquals(750.0, logger.confirmations.single().responseTimeMs, 0.001)
        assertEquals(750.0, logger.completedConfirmationTimeMs!!, 0.001)
        assertEquals(1, logger.completedConfirmationCount)
    }

    @Test
    fun `c2 gate decline aborts`() = runBlocking {
        val backend = FakeBackend(elements = listOf(
            mapOf("index" to 0, "text" to "Senden"),
            mapOf("index" to 1, "text" to "Bestätigen"),
        ))
        val gate = DeclineGate()
        val logger = RecordingLogger()
        val spec = simpleSpec(listOf(tapStep, commitStep), condition = RuntimeStudyCondition.FINAL_CHECKPOINT)

        val result = TrialExecutor(backend, logger, gate, spec, RuntimeStudyCondition.FINAL_CHECKPOINT).run()

        assertEquals(TrialOutcome.ABORTED, result.outcome)
        // First step (non-commit) should have executed before C2 gate
        assertEquals(1, result.stepsDone)
    }

    @Test
    fun `c2 vague correction rewinds the injected error and completes safely`() = runBlocking {
        val source = FakeCorrectionSource()
        var summaries = 0
        val gate = object : TrialOversightGate {
            override suspend fun confirmConsequentialStep(
                step: StudyStep,
                confirmationText: String,
            ) = OversightGateDecision(confirmed = true)

            override suspend fun showC2Summary(
                steps: List<StudyStep>,
                narrations: List<String>,
            ): C2Decision {
                summaries += 1
                return if (summaries == 1) {
                    source.enqueue("die Uhrzeit stimmt nicht")
                    C2Decision(confirmed = false, declined = true)
                } else {
                    C2Decision(confirmed = true)
                }
            }

            override fun isCancelled() = false
        }
        val variant = com.caddie.study.runtime.model.ErrorVariant(
            id = "wrong-time",
            field = "start_hour",
            correctValue = "15:00",
            wrongValue = "16:00",
            description = "wrong calendar hour",
            correction = CorrectionPolicy(
                afterCommit = PostCommitPolicy.COMPENSATE,
                rewindToStepId = "time_select",
                steps = listOf(
                    CorrectionStep(
                        id = "fix-time",
                        action = "replace text '15:00'",
                        narration = "Zeit korrigieren",
                        confirmationText = "Zeit auf 15 Uhr setzen",
                    ),
                ),
                assertions = listOf(CorrectionAssertion("text_present", "15:00")),
            ),
        )
        val spec = simpleSpec(
            steps = listOf(
                StudyStep(
                    id = "time_select",
                    action = "input text '15:00'",
                    narration = "Startzeit auswählen",
                    stepType = StepType.CONSEQUENTIAL,
                    minNarrationMs = 0,
                    errorVariant = variant,
                ),
                commitStep,
            ),
            errorSteps = listOf("time_select"),
            condition = RuntimeStudyCondition.FINAL_CHECKPOINT,
        )
        val backend = FakeBackend(
            elements = listOf(mapOf("index" to 0, "text" to "Bestätigen")),
        )
        val logger = RecordingLogger()

        val result = TrialExecutor(
            backend = backend,
            logger = logger,
            oversight = gate,
            spec = spec,
            condition = RuntimeStudyCondition.FINAL_CHECKPOINT,
            errorTasks = setOf("task_test"),
            correctionClassifier = com.caddie.study.runtime.android.SingleInjectedErrorCorrectionClassifier,
            correctionSource = source,
        ).run()

        assertEquals(TrialOutcome.SUCCESS, result.outcome)
        assertEquals(2, summaries)
        assertTrue("typeText(16:00,false)" in backend.calls)
        assertTrue("replaceText(15:00,false)" in backend.calls)
        assertTrue(logger.events.any { it.startsWith("correctionRewind(") })
    }

    @Test
    fun `calendar dnd correction refocuses and replaces the entered start time`() = runBlocking {
        val loaded = SpecLoader().parse(
            java.io.File("src/study/assets/study-specs/task_calendar_dnd.json").readText(),
        )
        val spec = loaded.copy(steps = loaded.steps.map { it.copy(minNarrationMs = 0) })
        val source = FakeCorrectionSource()
        var summaries = 0
        val gate = object : TrialOversightGate {
            override suspend fun confirmConsequentialStep(
                step: StudyStep,
                confirmationText: String,
            ) = OversightGateDecision(confirmed = true)

            override suspend fun showC2Summary(
                steps: List<StudyStep>,
                narrations: List<String>,
            ): C2Decision {
                summaries += 1
                return if (summaries == 1) {
                    source.enqueue("die Uhrzeit stimmt nicht")
                    C2Decision(confirmed = false, declined = true)
                } else {
                    C2Decision(confirmed = true)
                }
            }

            override fun isCancelled() = false
        }
        val backend = FakeBackend(
            elements = listOf(
                mapOf("index" to 0, "resource_id" to "com.caddie.studycalendar:id/exam_event"),
                mapOf("index" to 1, "text" to "Neue Regel"),
                mapOf("index" to 2, "resource_id" to "com.caddie.studycalendar:id/dnd_start_time"),
                mapOf("index" to 4, "text" to "Regel speichern"),
            ).map { element ->
                element + mapOf(
                    "package_name" to "com.caddie.studycalendar",
                    "package" to "com.caddie.studycalendar",
                    "visible" to true,
                    "enabled" to true,
                    "window_foreground" to true,
                )
            },
        )
        val logger = RecordingLogger()

        val result = TrialExecutor(
            backend = backend,
            logger = logger,
            oversight = gate,
            spec = spec,
            condition = RuntimeStudyCondition.FINAL_CHECKPOINT,
            errorTasks = setOf(spec.id),
            correctionClassifier = com.caddie.study.runtime.android.SingleInjectedErrorCorrectionClassifier,
            correctionSource = source,
        ).run()

        assertEquals(
            "${result.reason}; calls=${backend.calls}; events=${logger.events}",
            TrialOutcome.SUCCESS,
            result.outcome,
        )
        assertEquals(2, summaries)
        assertEquals(1, backend.calls.count { it == "tapElement(1)" })
        assertEquals(2, backend.calls.count { it == "tapElement(2)" })
        assertTrue("replaceText(12:00,true)" in backend.calls)
        assertTrue("replaceText(10:00,true)" in backend.calls)
        assertTrue(logger.events.any { it.contains("->dnd_time_open") })
    }

    @Test
    fun `c1 correction received at the gate deadline is handled before timeout`() = runBlocking {
        val loaded = SpecLoader().parse(
            java.io.File("src/study/assets/study-specs/task_calendar_dnd.json").readText(),
        )
        val spec = loaded.copy(
            steps = loaded.steps.map { it.copy(minNarrationMs = 0) },
            perGateTimeoutS = 1,
        )
        val source = FakeCorrectionSource()
        var rangePrompts = 0
        val gate = object : TrialOversightGate {
            override suspend fun confirmConsequentialStep(
                step: StudyStep,
                confirmationText: String,
            ): OversightGateDecision {
                if (step.id == "dnd_range_select" && rangePrompts++ == 0) {
                    source.enqueue("die Uhrzeit stimmt nicht")
                    delay(1_100)
                    return OversightGateDecision(confirmed = false, declined = true)
                }
                return OversightGateDecision(confirmed = true)
            }

            override suspend fun showC2Summary(
                steps: List<StudyStep>,
                narrations: List<String>,
            ) = C2Decision(confirmed = true)

            override fun isCancelled() = false
        }
        val backend = FakeBackend(
            elements = listOf(
                mapOf("index" to 0, "resource_id" to "com.caddie.studycalendar:id/exam_event"),
                mapOf("index" to 1, "text" to "Neue Regel"),
                mapOf("index" to 2, "resource_id" to "com.caddie.studycalendar:id/dnd_start_time"),
                mapOf("index" to 4, "text" to "Regel speichern"),
            ).map { element ->
                element + mapOf(
                    "package_name" to "com.caddie.studycalendar",
                    "package" to "com.caddie.studycalendar",
                    "visible" to true,
                    "enabled" to true,
                    "window_foreground" to true,
                )
            },
        )
        val logger = RecordingLogger()

        val result = TrialExecutor(
            backend = backend,
            logger = logger,
            oversight = gate,
            spec = spec,
            condition = RuntimeStudyCondition.STEPWISE,
            errorTasks = setOf(spec.id),
            correctionClassifier = com.caddie.study.runtime.android.SingleInjectedErrorCorrectionClassifier,
            correctionSource = source,
        ).run()

        assertEquals(
            "${result.reason}; calls=${backend.calls}; events=${logger.events}",
            TrialOutcome.SUCCESS,
            result.outcome,
        )
        assertEquals(2, rangePrompts)
        assertFalse("replaceText(12:00,true)" in backend.calls)
        assertTrue("replaceText(10:00,true)" in backend.calls)
        assertTrue(logger.events.any { it.contains("->dnd_time_open") })
    }

    @Test
    fun `email calendar correction refocuses and replaces the wrong start time`() = runBlocking {
        val loaded = SpecLoader().parse(
            java.io.File("src/study/assets/study-specs/task_email_calendar.json").readText(),
        )
        val relevantSteps = loaded.steps
            .dropWhile { it.id != "time_open" }
            .map { it.copy(minNarrationMs = 0) }
        val spec = loaded.copy(steps = relevantSteps)
        val source = FakeCorrectionSource()
        var summaries = 0
        val gate = object : TrialOversightGate {
            override suspend fun confirmConsequentialStep(
                step: StudyStep,
                confirmationText: String,
            ) = OversightGateDecision(confirmed = true)

            override suspend fun showC2Summary(
                steps: List<StudyStep>,
                narrations: List<String>,
            ): C2Decision {
                summaries += 1
                return if (summaries == 1) {
                    source.enqueue("die Uhrzeit stimmt nicht")
                    C2Decision(confirmed = false, declined = true)
                } else {
                    C2Decision(confirmed = true)
                }
            }

            override fun isCancelled() = false
        }
        val backend = object : StudyBackend {
            val calls = mutableListOf<String>()

            override fun listElements(): List<Map<String, Any?>> {
                fun element(index: Int, resource: String) = mapOf<String, Any?>(
                    "index" to index,
                    "package" to "com.caddie.studycalendar",
                    "package_name" to "com.caddie.studycalendar",
                    "resource_id" to resource,
                    "visible" to true,
                    "enabled" to true,
                    "window_foreground" to true,
                )
                return listOf(
                    element(0, "com.caddie.studycalendar:id/start_time"),
                    element(4, "com.caddie.studycalendar:id/save_event"),
                )
            }

            override fun tapElement(index: Int): Map<String, Any?> {
                calls += "tapElement($index)"
                return mapOf("success" to true)
            }

            override fun openApp(packageName: String) = mapOf("success" to true)
            override fun openUrl(url: String) = mapOf("success" to true)
            override fun scroll(direction: String, amount: Double) = mapOf("success" to true)
            override fun pressButton(button: String) = mapOf("success" to true)
            override fun dismissInputMethod() = mapOf("success" to true)
            override fun typeText(text: String, submit: Boolean) = mapOf("success" to true)
            override fun replaceText(text: String, submit: Boolean): Map<String, Any?> {
                calls += "replaceText($text,$submit)"
                return mapOf("success" to true)
            }
        }
        val logger = RecordingLogger()

        val result = TrialExecutor(
            backend = backend,
            logger = logger,
            oversight = gate,
            spec = spec,
            condition = RuntimeStudyCondition.FINAL_CHECKPOINT,
            errorTasks = setOf(spec.id),
            correctionClassifier = com.caddie.study.runtime.android.SingleInjectedErrorCorrectionClassifier,
            correctionSource = source,
        ).run()

        assertEquals(TrialOutcome.SUCCESS, result.outcome)
        assertEquals(2, summaries)
        assertEquals(2, backend.calls.count { it == "tapElement(0)" })
        assertTrue("replaceText(16:00,true)" in backend.calls)
        assertTrue("replaceText(15:00,true)" in backend.calls)
        assertEquals(1, backend.calls.count { it == "tapElement(4)" })
        assertTrue(logger.events.any { it.contains("->time_open") })
    }

    @Test
    fun `correction before a future injected error cannot skip forward`() = runBlocking {
        val source = FakeCorrectionSource()
        val gate = object : TrialOversightGate {
            override suspend fun confirmConsequentialStep(
                step: StudyStep,
                confirmationText: String,
            ): OversightGateDecision {
                source.enqueue("das ist falsch")
                return OversightGateDecision(confirmed = false, declined = true)
            }

            override suspend fun showC2Summary(
                steps: List<StudyStep>,
                narrations: List<String>,
            ) = C2Decision(confirmed = true)

            override fun isCancelled() = false
        }
        val futureVariant = errorVariantWithCompensation.copy(
            correction = errorVariantWithCompensation.correction?.copy(
                rewindToStepId = "future_error",
            ),
        )
        val spec = simpleSpec(
            steps = listOf(
                StudyStep(
                    id = "early_gate",
                    action = "click 'Weiter'",
                    narration = "Weiter",
                    stepType = StepType.CONSEQUENTIAL,
                    minNarrationMs = 0,
                ),
                StudyStep(
                    id = "future_error",
                    action = "input text '50.00'",
                    narration = "Späterer Fehler",
                    stepType = StepType.CONSEQUENTIAL,
                    minNarrationMs = 0,
                    errorVariant = futureVariant,
                ),
            ),
            errorSteps = listOf("future_error"),
            condition = RuntimeStudyCondition.STEPWISE,
        )
        val backend = FakeBackend(elements = listOf(mapOf("index" to 0, "text" to "Weiter")))
        val logger = RecordingLogger()

        val result = TrialExecutor(
            backend = backend,
            logger = logger,
            oversight = gate,
            spec = spec,
            condition = RuntimeStudyCondition.STEPWISE,
            errorTasks = setOf(spec.id),
            correctionClassifier = AcceptClassifier(futureVariant.id),
            correctionSource = source,
        ).run()

        assertEquals(TrialOutcome.ABORTED, result.outcome)
        assertFalse(backend.calls.any { it.startsWith("typeText(") })
        assertFalse(logger.events.any { it.startsWith("correctionRewind(") })
    }

    @Test
    fun `step failure aborts with technical failure`() = runBlocking {
        val backend = FakeBackend(elements = emptyList()) // no elements → tap fails
        val gate = AutoApproveGate()
        val logger = RecordingLogger()
        val spec = simpleSpec(listOf(tapStep))

        val result = TrialExecutor(backend, logger, gate, spec, RuntimeStudyCondition.VOLUNTARY_INTERVENTION).run()

        assertEquals(TrialOutcome.TECHNICAL_FAILURE, result.outcome)
        assertEquals(0, result.stepsDone)
    }

    @Test
    fun `unexpected executor exception is recorded with its concrete reason`() = runBlocking {
        val logger = RecordingLogger()
        val failingGate = object : TrialOversightGate {
            override suspend fun confirmConsequentialStep(
                step: StudyStep,
                confirmationText: String,
            ) = OversightGateDecision(confirmed = true)

            override suspend fun showC2Summary(
                steps: List<StudyStep>,
                narrations: List<String>,
            ): C2Decision = throw IllegalStateException("confirmation overlay unavailable")

            override fun isCancelled() = false
        }
        val result = TrialExecutor(
            backend = FakeBackend(),
            logger = logger,
            oversight = failingGate,
            spec = simpleSpec(listOf(commitStep)),
            condition = RuntimeStudyCondition.FINAL_CHECKPOINT,
        ).run()

        assertEquals(TrialOutcome.TECHNICAL_FAILURE, result.outcome)
        assertTrue(logger.events.contains("technicalFailure(confirmation overlay unavailable)"))
    }

    @Test
    fun `coroutine cancellation propagates after recording a terminal technical failure`() {
        val logger = RecordingLogger()
        val cancelledGate = object : TrialOversightGate {
            override suspend fun confirmConsequentialStep(
                step: StudyStep,
                confirmationText: String,
            ) = OversightGateDecision(confirmed = true)

            override suspend fun showC2Summary(
                steps: List<StudyStep>,
                narrations: List<String>,
            ): C2Decision = throw CancellationException("test cancellation")

            override fun isCancelled() = false
        }

        assertThrows(CancellationException::class.java) {
            runBlocking {
                TrialExecutor(
                    backend = FakeBackend(),
                    logger = logger,
                    oversight = cancelledGate,
                    spec = simpleSpec(listOf(commitStep)),
                    condition = RuntimeStudyCondition.FINAL_CHECKPOINT,
                ).run()
            }
        }
        assertTrue(logger.events.any { it.startsWith("technicalFailure(trial cancelled:") })
        assertTrue(logger.events.any {
            it.startsWith("trialComplete(") && it.contains(",technical_failure,")
        })
    }

    @Test
    fun `error injection substitutes wrong value`() = runBlocking {
        val variant = com.caddie.study.runtime.model.ErrorVariant(
            id = "ev1", field = "amount",
            correctValue = "50.00", wrongValue = "500.00",
            description = "wrong amount",
        )
        val errorStep = StudyStep(
            id = "s1", action = "input text '50.00'", narration = "Betrag eingeben",
            stepType = StepType.NORMAL, minNarrationMs = 0, errorVariant = variant,
        )
        val backend = FakeBackend(elements = emptyList())
        val gate = AutoApproveGate()
        val logger = RecordingLogger()
        val spec = simpleSpec(listOf(errorStep), errorSteps = listOf("s1"))

        val result = TrialExecutor(backend, logger, gate, spec, RuntimeStudyCondition.VOLUNTARY_INTERVENTION, errorTasks = setOf("task_test")).run()

        assertEquals(TrialOutcome.SUCCESS, result.outcome)
        assertTrue(result.stepsExecuted[0].errorInjected)
        assertEquals("ev1", result.stepsExecuted[0].errorVariantId)
        // Backend should have received the wrong value
        assertTrue(backend.calls.any { it == "typeText(500.00,false)" })
        // Logger should record the error injection
        assertTrue(logger.events.any { it == "errorInjected(s1,ev1,500.00)" })
    }

    @Test
    fun `error not injected when task not in error tasks`() = runBlocking {
        val variant = com.caddie.study.runtime.model.ErrorVariant(
            id = "ev1", field = "amount",
            correctValue = "50.00", wrongValue = "500.00",
            description = "wrong amount",
        )
        val errorStep = StudyStep(
            id = "s1", action = "input text '50.00'", narration = "Betrag eingeben",
            stepType = StepType.NORMAL, minNarrationMs = 0, errorVariant = variant,
        )
        val backend = FakeBackend(elements = emptyList())
        val gate = AutoApproveGate()
        val logger = RecordingLogger()
        val spec = simpleSpec(listOf(errorStep), errorSteps = listOf("s1"))

        // errorTasks is empty → no injection
        val result = TrialExecutor(backend, logger, gate, spec, RuntimeStudyCondition.VOLUNTARY_INTERVENTION).run()

        assertEquals(TrialOutcome.SUCCESS, result.outcome)
        assertFalse(result.stepsExecuted[0].errorInjected)
        assertTrue(backend.calls.any { it == "typeText(50.00,false)" })
    }

    @Test
    fun `open app action calls backend openApp`() = runBlocking {
        val step = StudyStep(
            id = "s1", action = "open com.test.app", narration = "App öffnen",
            stepType = StepType.NORMAL, minNarrationMs = 0, readyPackage = null,
        )
        val backend = FakeBackend(elements = listOf(mapOf("index" to 0, "package" to "com.test.app", "text" to "Main")))
        val gate = AutoApproveGate()
        val logger = RecordingLogger()
        val spec = simpleSpec(listOf(step))

        val result = TrialExecutor(backend, logger, gate, spec, RuntimeStudyCondition.VOLUNTARY_INTERVENTION).run()

        assertEquals(TrialOutcome.SUCCESS, result.outcome)
        assertTrue(backend.calls.any { it == "openApp(com.test.app)" })
    }

    @Test
    fun `scroll and press actions execute`() = runBlocking {
        val steps = listOf(
            StudyStep("s1", "scroll down", "Scrollen", StepType.NORMAL, minNarrationMs = 0),
            StudyStep("s2", "press BACK", "Zurück", StepType.NORMAL, minNarrationMs = 0),
        )
        val backend = FakeBackend()
        val gate = AutoApproveGate()
        val logger = RecordingLogger()
        val spec = simpleSpec(steps)

        val result = TrialExecutor(backend, logger, gate, spec, RuntimeStudyCondition.VOLUNTARY_INTERVENTION).run()

        assertEquals(TrialOutcome.SUCCESS, result.outcome)
        assertTrue(backend.calls.any { it == "scroll(down)" })
        assertTrue(backend.calls.any { it == "pressButton(BACK)" })
    }

    @Test
    fun `user cancellation aborts trial`() = runBlocking {
        val backend = FakeBackend(elements = listOf(mapOf("index" to 0, "text" to "Senden")))
        val gate = AutoApproveGate().apply { cancelled = true }
        val logger = RecordingLogger()
        val spec = simpleSpec(listOf(tapStep))

        val result = TrialExecutor(backend, logger, gate, spec, RuntimeStudyCondition.VOLUNTARY_INTERVENTION).run()

        assertEquals(TrialOutcome.ABORTED, result.outcome)
        assertTrue(result.reason.contains("cancelled"))
    }

    @Test
    fun `trial start and complete logged`() = runBlocking {
        val backend = FakeBackend(elements = listOf(mapOf("index" to 0, "text" to "Senden")))
        val gate = AutoApproveGate()
        val logger = RecordingLogger()
        val spec = simpleSpec(listOf(tapStep))

        TrialExecutor(backend, logger, gate, spec, RuntimeStudyCondition.VOLUNTARY_INTERVENTION).run()

        assertTrue(logger.events.any { it.startsWith("trialStart(") })
        assertTrue(logger.events.any { it.startsWith("trialComplete(") })
        assertTrue(logger.events.any { it.startsWith("summary(") })
    }

    @Test
    fun `c1 skips preparatory steps with confirmInC1 false`() = runBlocking {
        val prepStep = tapStep.copy(id = "prep", confirmInC1 = false)
        val backend = FakeBackend(elements = listOf(mapOf("index" to 0, "text" to "Senden")))
        val gate = AutoApproveGate()
        val logger = RecordingLogger()
        val spec = simpleSpec(listOf(prepStep))

        TrialExecutor(backend, logger, gate, spec, RuntimeStudyCondition.STEPWISE).run()

        assertTrue(gate.confirmedSteps.isEmpty())
    }

    @Test
    fun `c2 rejects second commit step`() = runBlocking {
        val steps = listOf(
            tapStep.copy(id = "s1"),
            commitStep.copy(id = "s2"),
            commitStep.copy(id = "s3"),
        )
        val backend = FakeBackend(elements = listOf(
            mapOf("index" to 0, "text" to "Senden"),
            mapOf("index" to 1, "text" to "Bestätigen"),
        ))
        val gate = AutoApproveGate()
        val logger = RecordingLogger()
        val spec = simpleSpec(steps, condition = RuntimeStudyCondition.FINAL_CHECKPOINT)

        val result = TrialExecutor(backend, logger, gate, spec, RuntimeStudyCondition.FINAL_CHECKPOINT).run()

        assertEquals(TrialOutcome.TECHNICAL_FAILURE, result.outcome)
        assertTrue(result.reason.contains("more than one commit"))
    }

    // ── Correction fakes ──

    /** Queued correction source: delivers corrections in order. */
    private class FakeCorrectionSource(
        corrections: List<String> = emptyList(),
    ) : CorrectionSource {
        private val queue = ArrayDeque(corrections)
        var paused = false
            private set

        fun enqueue(text: String) = queue.addLast(text)

        override fun takeCorrection(): String? = queue.removeFirstOrNull()
        override suspend fun waitForCorrection(timeoutS: Double): String? = takeCorrection()
        override fun pauseForUnclearCorrection() { paused = true }
    }

    /** Delivers a correction once after [delay] takeCorrection() calls. */
    private class DelayedCorrectionSource(
        private val delay: Int,
        private val correction: String,
    ) : CorrectionSource {
        private var calls = 0
        private var delivered = false
        var paused = false
            private set

        override fun takeCorrection(): String? {
            calls++
            if (calls > delay && !delivered) {
                delivered = true
                return correction
            }
            return null
        }
        override suspend fun waitForCorrection(timeoutS: Double): String? = takeCorrection()
        override fun pauseForUnclearCorrection() { paused = true }
    }

    /** Accepts corrections matching a specific variant ID. */
    private class AcceptClassifier(private val variantId: String) : CorrectionClassifier {
        override fun classify(context: CorrectionContext): CorrectionDecision =
            CorrectionDecision(accepted = true, errorVariantId = variantId, reason = "accepted")
    }

    /** Rejects all corrections with a given reason. */
    private class RejectClassifier(private val reason: String = "not_correction") : CorrectionClassifier {
        override fun classify(context: CorrectionContext): CorrectionDecision =
            CorrectionDecision(accepted = false, reason = reason)
    }

    // ── Correction test specs ──

    private val compensatePolicy = CorrectionPolicy(
        afterCommit = PostCommitPolicy.COMPENSATE,
        steps = listOf(
            CorrectionStep(
                id = "fix1",
                action = "click 'Bearbeiten'",
                narration = "Bearbeiten öffnen",
                confirmationText = "Auf Bearbeiten tippen",
            ),
            CorrectionStep(
                id = "fix2",
                action = "replace text '50.00' (submit)",
                narration = "Richtigen Betrag eingeben",
                confirmationText = "Text ersetzen: 50.00",
            ),
        ),
        assertions = listOf(CorrectionAssertion("text_present", "50.00")),
    )

    private val rejectPolicy = CorrectionPolicy(
        afterCommit = PostCommitPolicy.REJECT_IRREVERSIBLE,
        irreversibleMessageDe = "Die Überweisung wurde bereits ausgeführt.",
    )

    private val errorVariantWithCompensation = com.caddie.study.runtime.model.ErrorVariant(
        id = "ev1", field = "amount",
        correctValue = "50.00", wrongValue = "500.00",
        description = "wrong amount",
        correction = compensatePolicy,
    )

    private val errorVariantWithReject = com.caddie.study.runtime.model.ErrorVariant(
        id = "ev1", field = "amount",
        correctValue = "50.00", wrongValue = "500.00",
        description = "wrong amount",
        correction = rejectPolicy,
    )

    private fun trialWithCorrection(
        variant: com.caddie.study.runtime.model.ErrorVariant,
        condition: RuntimeStudyCondition = RuntimeStudyCondition.VOLUNTARY_INTERVENTION,
    ): List<StudyStep> = listOf(
        StudyStep(
            id = "s1", action = "input text '50.00'", narration = "Betrag eingeben",
            stepType = StepType.NORMAL, minNarrationMs = 0, errorVariant = variant,
        ),
        StudyStep(
            id = "s2", action = "click 'Bestätigen'", narration = "Bestätigen",
            stepType = StepType.COMMIT, minNarrationMs = 0,
        ),
    )

    // ── Correction tests: REWIND ──

    @Test
    fun `rewind correction before commit replays with correct value`() = runBlocking {
        val spec = simpleSpec(
            trialWithCorrection(errorVariantWithCompensation),
            errorSteps = listOf("s1"),
        )
        val backend = FakeBackend(elements = listOf(
            mapOf("index" to 0, "text" to "Bestätigen"),
            mapOf("index" to 1, "text" to "50.00"),
        ))
        val gate = AutoApproveGate()
        val logger = RecordingLogger()
        // Delay=2: first two takeCorrection() calls (step 0 start + mid-step) return null,
        // third call (post-step) delivers the correction → rewind after s1 fully executed
        val source = DelayedCorrectionSource(delay = 2, correction = "Betrag ist falsch")
        val classifier = AcceptClassifier("ev1")

        val result = TrialExecutor(
            backend, logger, gate, spec, RuntimeStudyCondition.VOLUNTARY_INTERVENTION,
            errorTasks = setOf("task_test"),
            correctionClassifier = classifier,
            correctionSource = source,
        ).run()

        assertEquals(TrialOutcome.SUCCESS, result.outcome)
        // First execution: wrong value injected
        assertTrue(backend.calls.any { it == "typeText(500.00,false)" })
        // After rewind: correct value as replace (journal has entry → type→replace conversion)
        assertTrue(backend.calls.any { it == "replaceText(50.00,false)" })
        // Correction rewind logged
        assertTrue(logger.events.any { it.startsWith("correctionRewind(") })
    }

    @Test
    fun `rewind skips optional navigation when its target is not visible`() = runBlocking {
        val optionalBack = CorrectionStep(
            id = "back_from_review",
            action = "click 'btn_back_review'",
            narration = "Zurück zum Formular",
            confirmationText = "Zurück zum Formular",
            runIfTargetPresent = true,
        )
        val variant = errorVariantWithCompensation.copy(
            correction = errorVariantWithCompensation.correction!!.copy(
                rewindSteps = listOf(optionalBack),
            ),
        )
        val spec = simpleSpec(
            trialWithCorrection(variant),
            errorSteps = listOf("s1"),
        )
        val backend = FakeBackend(
            elements = listOf(
                mapOf("index" to 0, "text" to "Bestätigen"),
                mapOf("index" to 1, "text" to "50.00"),
                mapOf(
                    "index" to 2,
                    "resource_id" to "btn_back_review",
                    "enabled" to true,
                    "visible" to false,
                    "window_foreground" to true,
                ),
            ),
        )
        val logger = RecordingLogger()

        val result = TrialExecutor(
            backend = backend,
            logger = logger,
            oversight = AutoApproveGate(),
            spec = spec,
            condition = RuntimeStudyCondition.VOLUNTARY_INTERVENTION,
            errorTasks = setOf("task_test"),
            correctionClassifier = AcceptClassifier("ev1"),
            correctionSource = DelayedCorrectionSource(2, "Betrag ist falsch"),
        ).run()

        assertEquals(TrialOutcome.SUCCESS, result.outcome)
        assertTrue(logger.events.any { it == "compensationStep(back_from_review,skipped_not_visible)" })
        assertTrue("replaceText(50.00,false)" in backend.calls)
    }

    // ── Correction tests: COMPENSATE ──

    @Test
    fun `compensate correction after commit executes compensation steps`() = runBlocking {
        val spec = simpleSpec(
            trialWithCorrection(errorVariantWithCompensation),
            errorSteps = listOf("s1"),
        )
        val backend = FakeBackend(elements = listOf(
            mapOf("index" to 0, "text" to "Bestätigen"),
            mapOf("index" to 1, "text" to "Bearbeiten"),
            mapOf("index" to 2, "text" to "50.00"),
        ))
        val gate = AutoApproveGate()
        val logger = RecordingLogger()
        val source = FakeCorrectionSource()
        val classifier = AcceptClassifier("ev1")

        val executor = TrialExecutor(
            backend, logger, gate, spec, RuntimeStudyCondition.VOLUNTARY_INTERVENTION,
            errorTasks = setOf("task_test"),
            correctionClassifier = classifier,
            correctionSource = source,
        )
        // Enqueue correction after step 1 (before commit) — it will be taken
        // at the post-step boundary, but since step 1 is not committed yet,
        // route will be REWIND, not COMPENSATE.
        // To test COMPENSATE, we need the correction to arrive after commit.
        // So we enqueue it late — after step 2 has started.
        // Actually, the source delivers in order; we enqueue one correction
        // that will be consumed at the start-of-step check for step 2 (s2).
        // At that point, s1 has been executed (not committed), so route = REWIND.
        // For COMPENSATE we need the correction to arrive after the commit
        // step has executed. Let's enqueue it to be taken after s2.
        source.enqueue("Betrag falsch")

        val result = executor.run()

        // Since the correction arrives before commit (at s2 start), it's a REWIND
        assertEquals(TrialOutcome.SUCCESS, result.outcome)
        assertTrue(logger.events.any { it.startsWith("correctionRewind(") })
    }

    @Test
    fun `compensate correction after commit runs compensation steps`() = runBlocking {
        val spec = simpleSpec(
            trialWithCorrection(errorVariantWithCompensation),
            errorSteps = listOf("s1"),
        )
        // Backend: after commit, elements show "Bearbeiten" and "50.00" for compensation
        val backend = FakeBackend(elements = listOf(
            mapOf("index" to 0, "text" to "Bestätigen"),
            mapOf("index" to 1, "text" to "Bearbeiten"),
            mapOf("index" to 2, "text" to "50.00"),
        ))
        val gate = AutoApproveGate()
        val logger = RecordingLogger()
        val source = FakeCorrectionSource()
        val classifier = AcceptClassifier("ev1")

        val executor = TrialExecutor(
            backend, logger, gate, spec, RuntimeStudyCondition.VOLUNTARY_INTERVENTION,
            errorTasks = setOf("task_test"),
            correctionClassifier = classifier,
            correctionSource = source,
        )
        // Run the trial first (both steps complete, error injected)
        val runResult = executor.run()
        assertEquals(TrialOutcome.SUCCESS, runResult.outcome)

        // Now apply post-trial correction → COMPENSATE route
        val postResult = executor.correctAfterCompletion("Betrag korrigieren")
        assertTrue(postResult.consumed)
        assertTrue(postResult.accepted)
        assertEquals("corrected", postResult.outcome)
        // Compensation steps should have been executed
        assertTrue(logger.events.any { it == "compensationStep(fix1,success)" })
        assertTrue(logger.events.any { it == "compensationStep(fix2,success)" })
    }

    @Test
    fun `stepwise compensation confirmations contribute to response aggregates`() = runBlocking {
        val steps = trialWithCorrection(
            errorVariantWithCompensation,
            condition = RuntimeStudyCondition.STEPWISE,
        ) + tapStep.copy(id = "s3", action = "click 'Senden'")
        val spec = simpleSpec(
            steps,
            errorSteps = listOf("s1"),
            condition = RuntimeStudyCondition.STEPWISE,
        )
        val backend = FakeBackend(elements = listOf(
            mapOf("index" to 0, "text" to "Bestätigen"),
            mapOf("index" to 1, "text" to "Bearbeiten"),
            mapOf("index" to 2, "text" to "50.00"),
            mapOf("index" to 3, "text" to "Senden"),
        ))
        val logger = RecordingLogger()
        val clock = listOf(
            0L, 1_000_000_000L,
            1_000_000_000L, 2_000_000_000L,
            2_000_000_000L, 3_000_000_000L,
            3_000_000_000L, 4_000_000_000L,
            4_000_000_000L, 5_000_000_000L,
        ).iterator()

        val result = TrialExecutor(
            backend = backend,
            logger = logger,
            oversight = AutoApproveGate(),
            spec = spec,
            condition = RuntimeStudyCondition.STEPWISE,
            errorTasks = setOf("task_test"),
            correctionClassifier = AcceptClassifier("ev1"),
            correctionSource = DelayedCorrectionSource(5, "Betrag ist falsch"),
            confirmationNanoTime = { clock.next() },
        ).run()

        assertEquals(TrialOutcome.SUCCESS, result.outcome)
        assertEquals(listOf("s1", "s2", "fix1", "fix2", "s3"), logger.confirmations.map { it.stepId })
        assertEquals(5_000.0, logger.completedConfirmationTimeMs!!, 0.001)
        assertEquals(5, logger.completedConfirmationCount)
        assertTrue(logger.confirmations.all { it.participantResponse })
    }

    // ── Correction tests: REJECT_IRREVERSIBLE ──

    @Test
    fun `reject_irreversible correction after commit aborts trial`() = runBlocking {
        val spec = simpleSpec(
            trialWithCorrection(errorVariantWithReject),
            errorSteps = listOf("s1"),
        )
        val backend = FakeBackend(elements = listOf(
            mapOf("index" to 0, "text" to "Bestätigen"),
        ))
        val gate = AutoApproveGate()
        val logger = RecordingLogger()
        val source = FakeCorrectionSource()
        val classifier = AcceptClassifier("ev1")

        val executor = TrialExecutor(
            backend, logger, gate, spec, RuntimeStudyCondition.VOLUNTARY_INTERVENTION,
            errorTasks = setOf("task_test"),
            correctionClassifier = classifier,
            correctionSource = source,
        )
        val runResult = executor.run()
        assertEquals(TrialOutcome.SUCCESS, runResult.outcome)

        // Post-trial correction → REJECT_IRREVERSIBLE
        val postResult = executor.correctAfterCompletion("Betrag falsch")
        assertTrue(postResult.consumed)
        assertFalse(postResult.accepted)
        assertEquals("correction_rejected_irreversible", postResult.outcome)
        // correctAfterCompletion logs correctionRoute (not correctionIrreversible, which is the running-trial path)
        assertTrue(logger.events.any { it.startsWith("correctionRoute(") && it.contains("recently_completed") })
    }

    // ── Correction tests: ALREADY_CORRECT ──

    @Test
    fun `already_correct when compensating twice`() = runBlocking {
        val spec = simpleSpec(
            trialWithCorrection(errorVariantWithCompensation),
            errorSteps = listOf("s1"),
        )
        val backend = FakeBackend(elements = listOf(
            mapOf("index" to 0, "text" to "Bestätigen"),
            mapOf("index" to 1, "text" to "Bearbeiten"),
            mapOf("index" to 2, "text" to "50.00"),
        ))
        val gate = AutoApproveGate()
        val logger = RecordingLogger()
        val source = FakeCorrectionSource()
        val classifier = AcceptClassifier("ev1")

        val executor = TrialExecutor(
            backend, logger, gate, spec, RuntimeStudyCondition.VOLUNTARY_INTERVENTION,
            errorTasks = setOf("task_test"),
            correctionClassifier = classifier,
            correctionSource = source,
        )
        executor.run()

        // First correction → COMPENSATE
        val r1 = executor.correctAfterCompletion("korrigieren")
        assertTrue(r1.accepted)
        assertEquals("corrected", r1.outcome)

        // Second correction → ALREADY_CORRECT
        val r2 = executor.correctAfterCompletion("nochmal korrigieren")
        assertTrue(r2.consumed)
        assertTrue(r2.accepted)
        assertEquals("already_correct", r2.outcome)
    }

    // ── Correction tests: unclear correction ──

    @Test
    fun `unclear correction pauses and continues`() = runBlocking {
        val spec = simpleSpec(
            trialWithCorrection(errorVariantWithCompensation),
            errorSteps = listOf("s1"),
        )
        val backend = FakeBackend(elements = listOf(
            mapOf("index" to 0, "text" to "Bestätigen"),
        ))
        val gate = AutoApproveGate()
        val logger = RecordingLogger()
        val source = FakeCorrectionSource(listOf("irgendwas unklar"))
        val classifier = RejectClassifier(reason = "insufficient_confidence")

        val result = TrialExecutor(
            backend, logger, gate, spec, RuntimeStudyCondition.VOLUNTARY_INTERVENTION,
            errorTasks = setOf("task_test"),
            correctionClassifier = classifier,
            correctionSource = source,
        ).run()

        // Correction was unclear → paused, but trial continues normally
        assertEquals(TrialOutcome.SUCCESS, result.outcome)
        assertTrue(source.paused)
        assertTrue(logger.events.any { it.contains("accepted=false") })
    }

    // ── Correction tests: correctAfterCompletion edge cases ──

    @Test
    fun `correctAfterCompletion returns not_correction when classifier says not_correction`() = runBlocking {
        val spec = simpleSpec(
            trialWithCorrection(errorVariantWithCompensation),
            errorSteps = listOf("s1"),
        )
        val backend = FakeBackend(elements = listOf(mapOf("index" to 0, "text" to "Bestätigen")))
        val gate = AutoApproveGate()
        val logger = RecordingLogger()
        val source = FakeCorrectionSource()
        val classifier = RejectClassifier(reason = "not_correction")

        val executor = TrialExecutor(
            backend, logger, gate, spec, RuntimeStudyCondition.VOLUNTARY_INTERVENTION,
            errorTasks = setOf("task_test"),
            correctionClassifier = classifier,
            correctionSource = source,
        )
        executor.run()

        val result = executor.correctAfterCompletion("Das Wetter ist schön")
        assertFalse(result.consumed)
        assertFalse(result.accepted)
        assertEquals("not_correction", result.outcome)
    }

    @Test
    fun `correctAfterCompletion returns not_correction when no classifier`() = runBlocking {
        val spec = simpleSpec(
            trialWithCorrection(errorVariantWithCompensation),
            errorSteps = listOf("s1"),
        )
        val backend = FakeBackend(elements = listOf(mapOf("index" to 0, "text" to "Bestätigen")))
        val gate = AutoApproveGate()
        val logger = RecordingLogger()

        val executor = TrialExecutor(
            backend, logger, gate, spec, RuntimeStudyCondition.VOLUNTARY_INTERVENTION,
            errorTasks = setOf("task_test"),
        )
        executor.run()

        val result = executor.correctAfterCompletion("irgendwas")
        assertFalse(result.consumed)
        assertEquals("not_correction", result.outcome)
    }

    @Test
    fun `canCorrectAfterCompletion false without classifier`() = runBlocking {
        val spec = simpleSpec(
            trialWithCorrection(errorVariantWithCompensation),
            errorSteps = listOf("s1"),
        )
        val backend = FakeBackend()
        val gate = AutoApproveGate()
        val logger = RecordingLogger()

        val executor = TrialExecutor(
            backend, logger, gate, spec, RuntimeStudyCondition.VOLUNTARY_INTERVENTION,
        )
        assertFalse(executor.canCorrectAfterCompletion())
    }

    @Test
    fun `canCorrectAfterCompletion true with classifier and correction policy`() = runBlocking {
        val spec = simpleSpec(
            trialWithCorrection(errorVariantWithCompensation),
            errorSteps = listOf("s1"),
        )
        val backend = FakeBackend()
        val gate = AutoApproveGate()
        val logger = RecordingLogger()
        val source = FakeCorrectionSource()
        val classifier = AcceptClassifier("ev1")

        val executor = TrialExecutor(
            backend, logger, gate, spec, RuntimeStudyCondition.VOLUNTARY_INTERVENTION,
            correctionClassifier = classifier,
            correctionSource = source,
        )
        assertTrue(executor.canCorrectAfterCompletion())
    }

    @Test
    fun `compensation verification failure returns correction_failed`() = runBlocking {
        val spec = simpleSpec(
            trialWithCorrection(errorVariantWithCompensation),
            errorSteps = listOf("s1"),
        )
        // Backend has no "50.00" text → assertion text_present("50.00") fails
        val backend = FakeBackend(elements = listOf(
            mapOf("index" to 0, "text" to "Bestätigen"),
            mapOf("index" to 1, "text" to "Bearbeiten"),
        ))
        val gate = AutoApproveGate()
        val logger = RecordingLogger()
        val source = FakeCorrectionSource()
        val classifier = AcceptClassifier("ev1")

        val executor = TrialExecutor(
            backend, logger, gate, spec, RuntimeStudyCondition.VOLUNTARY_INTERVENTION,
            errorTasks = setOf("task_test"),
            correctionClassifier = classifier,
            correctionSource = source,
        )
        executor.run()

        val result = executor.correctAfterCompletion("korrigieren")
        assertTrue(result.consumed)
        assertFalse(result.accepted)
        assertEquals("correction_failed", result.outcome)
    }

    // ── Verification integration tests (Layer 5c) ──

    /** Backend that delegates to StudyBackend's element list for text checks. */
    private class FakeVerificationBackend(
        private val elements: List<Map<String, Any?>>,
        private val screenshotPath: String? = "/tmp/verify.png",
    ) : VerificationBackend {
        val calls = mutableListOf<String>()

        override fun checkTextPresent(text: String): Boolean {
            calls.add("text_present:$text")
            return elements.any { (it["text"]?.toString() ?: "").contains(text) }
        }
        override fun checkTextAbsent(text: String): Boolean {
            calls.add("text_absent:$text")
            return elements.none { (it["text"]?.toString() ?: "").contains(text) }
        }
        override fun checkAccessibilityElement(label: String): Boolean {
            calls.add("accessibility:$label")
            return elements.any { (it["text"]?.toString() ?: "") == label }
        }
        override fun checkFieldCount(containerLabel: String, expected: Int): Boolean {
            calls.add("field_count:$containerLabel=$expected")
            return elements.size == expected
        }
        override fun captureScreenshot(): String? {
            calls.add("screenshot")
            return screenshotPath
        }
    }

    private fun specWithVerification(
        rules: List<com.caddie.study.runtime.model.VerificationRule>,
        steps: List<StudyStep> = listOf(tapStep, commitStep),
    ) = TrialSpec(
        version = "v1",
        id = "task_test",
        instructionDe = "Test",
        criticality = com.caddie.study.runtime.model.CriticalityClass.HIGH,
        steps = steps,
        errorSteps = emptyList(),
        verification = rules,
        maxDurationS = 300,
        perGateTimeoutS = 30,
    )

    @Test
    fun `verification passes yields success outcome`() = runBlocking {
        val elements = listOf(
            mapOf("index" to 0, "text" to "Senden"),
            mapOf("index" to 1, "text" to "Bestätigen"),
            mapOf("index" to 2, "text" to "Gesendet"),
        )
        val backend = FakeBackend(elements = elements)
        val verifyBackend = FakeVerificationBackend(elements)
        val spec = specWithVerification(
            listOf(
                com.caddie.study.runtime.model.VerificationRule(
                    id = "v1",
                    assertion = "message sent",
                    checkType = "text_present",
                    parameters = mapOf("text" to "Gesendet"),
                ),
            ),
        )

        val result = TrialExecutor(
            backend, RecordingLogger(), AutoApproveGate(), spec,
            RuntimeStudyCondition.VOLUNTARY_INTERVENTION,
            verificationBackend = verifyBackend,
        ).run()

        assertEquals(TrialOutcome.SUCCESS, result.outcome)
        assertTrue(result.verificationPassed)
        assertEquals("All steps completed successfully", result.reason)
        assertTrue(verifyBackend.calls.contains("text_present:Gesendet"))
    }

    @Test
    fun `verification fails yields verification_failed outcome`() = runBlocking {
        val elements = listOf(
            mapOf("index" to 0, "text" to "Senden"),
            mapOf("index" to 1, "text" to "Bestätigen"),
            mapOf("index" to 2, "text" to "Entwurf"),
        )
        val backend = FakeBackend(elements = elements)
        val verifyBackend = FakeVerificationBackend(elements)
        val spec = specWithVerification(
            listOf(
                com.caddie.study.runtime.model.VerificationRule(
                    id = "v1",
                    assertion = "message sent",
                    checkType = "text_present",
                    parameters = mapOf("text" to "Gesendet"), // not on screen
                ),
            ),
        )

        val result = TrialExecutor(
            backend, RecordingLogger(), AutoApproveGate(), spec,
            RuntimeStudyCondition.VOLUNTARY_INTERVENTION,
            verificationBackend = verifyBackend,
        ).run()

        assertEquals(TrialOutcome.VERIFICATION_FAILED, result.outcome)
        assertFalse(result.verificationPassed)
        assertEquals("Post-trial verification failed", result.reason)
    }

    @Test
    fun `verification failure from an uncorrected controlled error is a valid study outcome`() = runBlocking {
        val elements = listOf(
            mapOf("index" to 0, "text" to "Bestätigen"),
            mapOf("index" to 1, "text" to "500.00"),
        )
        val logger = RecordingLogger()
        val spec = specWithVerification(
            rules = listOf(
                com.caddie.study.runtime.model.VerificationRule(
                    id = "v1",
                    assertion = "correct amount",
                    checkType = "text_present",
                    parameters = mapOf("text" to "50.00"),
                ),
            ),
            steps = trialWithCorrection(errorVariantWithCompensation),
        ).copy(errorSteps = listOf("s1"))

        val result = TrialExecutor(
            backend = FakeBackend(elements = elements),
            logger = logger,
            oversight = AutoApproveGate(),
            spec = spec,
            condition = RuntimeStudyCondition.VOLUNTARY_INTERVENTION,
            errorTasks = setOf("task_test"),
            verificationBackend = FakeVerificationBackend(elements),
        ).run()

        assertEquals(TrialOutcome.ERROR_INJECTED, result.outcome)
        assertFalse(result.verificationPassed)
        assertEquals("Controlled error remained uncorrected", result.reason)
        assertTrue(logger.events.any { "error_injected" in it && "errs=1" in it })
    }

    @Test
    fun `no verification backend yields success with verificationPassed false`() = runBlocking {
        val backend = FakeBackend(elements = listOf(
            mapOf("index" to 0, "text" to "Senden"),
            mapOf("index" to 1, "text" to "Bestätigen"),
        ))
        val spec = specWithVerification(
            listOf(
                com.caddie.study.runtime.model.VerificationRule(
                    id = "v1", assertion = "x", checkType = "text_present",
                    parameters = mapOf("text" to "x"),
                ),
            ),
        )

        val result = TrialExecutor(
            backend, RecordingLogger(), AutoApproveGate(), spec,
            RuntimeStudyCondition.VOLUNTARY_INTERVENTION,
            // no verificationBackend
        ).run()

        assertEquals(TrialOutcome.SUCCESS, result.outcome)
        assertFalse(result.verificationPassed)
        assertEquals("All steps completed successfully (no verification backend)", result.reason)
    }

    @Test
    fun `multiple verification rules all pass`() = runBlocking {
        val elements = listOf(
            mapOf("index" to 0, "text" to "Senden"),
            mapOf("index" to 1, "text" to "Bestätigen"),
            mapOf("index" to 2, "text" to "Gesendet"),
            mapOf("index" to 3, "text" to "Posteingang"),
        )
        val backend = FakeBackend(elements = elements)
        val verifyBackend = FakeVerificationBackend(elements)
        val spec = specWithVerification(
            listOf(
                com.caddie.study.runtime.model.VerificationRule(
                    id = "v1", assertion = "sent", checkType = "text_present",
                    parameters = mapOf("text" to "Gesendet"),
                ),
                com.caddie.study.runtime.model.VerificationRule(
                    id = "v2", assertion = "no draft", checkType = "text_absent",
                    parameters = mapOf("text" to "Entwurf"),
                ),
            ),
        )

        val result = TrialExecutor(
            backend, RecordingLogger(), AutoApproveGate(), spec,
            RuntimeStudyCondition.VOLUNTARY_INTERVENTION,
            verificationBackend = verifyBackend,
        ).run()

        assertEquals(TrialOutcome.SUCCESS, result.outcome)
        assertTrue(result.verificationPassed)
    }

    @Test
    fun `failed step skips verification and aborts`() = runBlocking {
        // commitStep targets "Bestätigen" which is absent → element not found → technical failure
        val elements = listOf(mapOf("index" to 0, "text" to "Senden"))
        val backend = FakeBackend(elements = elements)
        val verifyBackend = FakeVerificationBackend(elements)
        val spec = specWithVerification(
            listOf(
                com.caddie.study.runtime.model.VerificationRule(
                    id = "v1", assertion = "x", checkType = "text_present",
                    parameters = mapOf("text" to "x"),
                ),
            ),
            steps = listOf(commitStep), // commitStep needs "Bestätigen", absent here
        )

        val result = TrialExecutor(
            backend, RecordingLogger(), AutoApproveGate(), spec,
            RuntimeStudyCondition.VOLUNTARY_INTERVENTION,
            verificationBackend = verifyBackend,
        ).run()

        // Step fails before verification is ever reached
        assertEquals(TrialOutcome.TECHNICAL_FAILURE, result.outcome)
        assertFalse(verifyBackend.calls.any { it.startsWith("text_present") })
    }
}
