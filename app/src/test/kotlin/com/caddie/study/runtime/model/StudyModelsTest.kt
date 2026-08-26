package com.caddie.study.runtime.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class StudyModelsTest {

    // ── StudyStep derivation ──

    @Test
    fun `normal step derives consequential false and commit false`() {
        val step = studyStep(stepType = StepType.NORMAL)
        assertFalse(step.consequential)
        assertFalse(step.commit)
    }

    @Test
    fun `consequential step derives consequential true and commit false`() {
        val step = studyStep(stepType = StepType.CONSEQUENTIAL)
        assertTrue(step.consequential)
        assertFalse(step.commit)
    }

    @Test
    fun `commit step derives consequential true and commit true`() {
        val step = studyStep(stepType = StepType.COMMIT)
        assertTrue(step.consequential)
        assertTrue(step.commit)
    }

    @Test
    fun `step rejects blank id`() {
        assertThrows(IllegalArgumentException::class.java) {
            studyStep(id = "  ")
        }
    }

    @Test
    fun `step rejects negative minNarrationMs`() {
        assertThrows(IllegalArgumentException::class.java) {
            studyStep(minNarrationMs = -1)
        }
    }

    // ── ErrorVariant ──

    @Test
    fun `error variant summary values must be provided together`() {
        assertThrows(IllegalArgumentException::class.java) {
            errorVariant(summaryWrongValue = "80,00", summaryCorrectValue = null)
        }
    }

    @Test
    fun `error variant accepts paired summary values`() {
        val v = errorVariant(summaryWrongValue = "80,00", summaryCorrectValue = "30,00")
        assertEquals("80,00", v.summaryWrongValue)
        assertEquals("30,00", v.summaryCorrectValue)
    }

    // ── CorrectionPolicy ──

    @Test
    fun `reject_irreversible policy requires irreversible message`() {
        assertThrows(IllegalArgumentException::class.java) {
            CorrectionPolicy(afterCommit = PostCommitPolicy.REJECT_IRREVERSIBLE)
        }
    }

    @Test
    fun `reject_irreversible policy must not define steps`() {
        assertThrows(IllegalArgumentException::class.java) {
            CorrectionPolicy(
                afterCommit = PostCommitPolicy.REJECT_IRREVERSIBLE,
                irreversibleMessageDe = "Tut mir leid.",
                steps = listOf(correctionStep()),
            )
        }
    }

    @Test
    fun `compensate policy requires steps and assertions`() {
        assertThrows(IllegalArgumentException::class.java) {
            CorrectionPolicy(afterCommit = PostCommitPolicy.COMPENSATE)
        }
        // valid compensate
        val p = CorrectionPolicy(
            afterCommit = PostCommitPolicy.COMPENSATE,
            steps = listOf(correctionStep()),
            assertions = listOf(CorrectionAssertion("text_present", "done")),
        )
        assertEquals(1, p.steps.size)
    }

    @Test
    fun `correction policy step ids must be unique`() {
        assertThrows(IllegalArgumentException::class.java) {
            CorrectionPolicy(
                afterCommit = PostCommitPolicy.COMPENSATE,
                steps = listOf(correctionStep("dup"), correctionStep("dup")),
                assertions = listOf(CorrectionAssertion("text_present", "done")),
            )
        }
    }

    // ── CorrectionJournal routing ──

    @Test
    fun `journal routes rewind before commit`() {
        val journal = CorrectionJournal("err_x")
        assertEquals(CorrectionRoute.REWIND, journal.route(PostCommitPolicy.REJECT_IRREVERSIBLE))
        assertEquals(CorrectionRoute.REWIND, journal.route(PostCommitPolicy.COMPENSATE))
    }

    @Test
    fun `journal routes reject_irreversible after commit`() {
        val journal = CorrectionJournal("err_x")
        journal.recordStep("send", commit = true)
        assertEquals(CorrectionRoute.REJECT_IRREVERSIBLE, journal.route(PostCommitPolicy.REJECT_IRREVERSIBLE))
    }

    @Test
    fun `journal routes compensate after commit when policy compensates`() {
        val journal = CorrectionJournal("err_x")
        journal.recordStep("send", commit = true)
        assertEquals(CorrectionRoute.COMPENSATE, journal.route(PostCommitPolicy.COMPENSATE))
    }

    @Test
    fun `journal routes already_correct after compensation`() {
        val journal = CorrectionJournal("err_x")
        journal.recordStep("send", commit = true)
        journal.finishCompensation(listOf("fix1", "fix2"))
        assertEquals(CorrectionRoute.ALREADY_CORRECT, journal.route(PostCommitPolicy.COMPENSATE))
    }

    @Test
    fun `journal committed reflects commit entries`() {
        val journal = CorrectionJournal("err_x")
        assertFalse(journal.committed)
        journal.recordStep("input", commit = false)
        assertFalse(journal.committed)
        journal.recordStep("send", commit = true)
        assertTrue(journal.committed)
    }

    // ── ErrorObservation validation ──

    @Test
    fun `not_detected requires evidence none and moderator prompt`() {
        assertThrows(IllegalArgumentException::class.java) {
            validateErrorObservation(
                ErrorObservation(
                    spontaneousDetection = false,
                    detectionStage = DetectionStage.NOT_DETECTED,
                    evidenceType = EvidenceType.NONE,
                    moderatorPromptGiven = false,
                    detectedOnlyAfterPrompt = false,
                ),
            )
        }
        // valid
        validateErrorObservation(
            ErrorObservation(
                spontaneousDetection = false,
                detectionStage = DetectionStage.NOT_DETECTED,
                evidenceType = EvidenceType.NONE,
                moderatorPromptGiven = true,
                detectedOnlyAfterPrompt = false,
            ),
        )
    }

    @Test
    fun `spontaneous detection conflicts with not_detected stage`() {
        assertThrows(IllegalArgumentException::class.java) {
            validateErrorObservation(
                ErrorObservation(
                    spontaneousDetection = true,
                    detectionStage = DetectionStage.NOT_DETECTED,
                    evidenceType = EvidenceType.SPOKEN_IDENTIFICATION,
                    moderatorPromptGiven = true,
                    detectedOnlyAfterPrompt = false,
                ),
            )
        }
    }

    @Test
    fun `after_moderator_prompt requires prompt and detected only after`() {
        // missing detectedOnlyAfterPrompt
        assertThrows(IllegalArgumentException::class.java) {
            validateErrorObservation(
                ErrorObservation(
                    spontaneousDetection = false,
                    detectionStage = DetectionStage.AFTER_MODERATOR_PROMPT,
                    evidenceType = EvidenceType.REJECTION,
                    moderatorPromptGiven = true,
                    detectedOnlyAfterPrompt = false,
                ),
            )
        }
        // valid
        validateErrorObservation(
            ErrorObservation(
                spontaneousDetection = false,
                detectionStage = DetectionStage.AFTER_MODERATOR_PROMPT,
                evidenceType = EvidenceType.REJECTION,
                moderatorPromptGiven = true,
                detectedOnlyAfterPrompt = true,
            ),
        )
    }

    @Test
    fun `pre-prompt stage requires spontaneous detection`() {
        assertThrows(IllegalArgumentException::class.java) {
            validateErrorObservation(
                ErrorObservation(
                    spontaneousDetection = false,
                    detectionStage = DetectionStage.STEPWISE_GATE,
                    evidenceType = EvidenceType.REJECTION,
                    moderatorPromptGiven = false,
                    detectedOnlyAfterPrompt = false,
                ),
            )
        }
    }

    // ── ParticipantConfig ──

    @Test
    fun `participant config requires exactly 6 tasks and 3 error tasks`() {
        assertThrows(IllegalArgumentException::class.java) {
            participantConfig(taskOrder = listOf("t1", "t2", "t3"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            participantConfig(errorTasks = listOf("t1", "t2"))
        }
    }

    @Test
    fun `participant config error task must be in task order`() {
        assertThrows(IllegalArgumentException::class.java) {
            participantConfig(errorTasks = listOf("not_in_order", "t1", "t2"))
        }
    }

    // ── helpers ──

    private fun studyStep(
        id: String = "step_1",
        action: String = "click 'x'",
        narration: String = "narration",
        stepType: StepType = StepType.NORMAL,
        minNarrationMs: Int = 800,
    ) = StudyStep(id, action, narration, stepType, minNarrationMs = minNarrationMs)

    private fun errorVariant(
        summaryWrongValue: String? = null,
        summaryCorrectValue: String? = null,
    ) = ErrorVariant(
        id = "err_1",
        field = "amount",
        wrongValue = "80.00",
        correctValue = "30.00",
        description = "wrong amount",
        summaryWrongValue = summaryWrongValue,
        summaryCorrectValue = summaryCorrectValue,
    )

    private fun correctionStep(id: String = "fix1") = CorrectionStep(
        id = id,
        action = "click 'fix'",
        narration = "fixing",
        confirmationText = "Fix",
    )

    private fun participantConfig(
        taskOrder: List<String> = List(6) { "t${it + 1}" },
        conditionOrder: List<RuntimeStudyCondition> = List(6) { RuntimeStudyCondition.STEPWISE },
        errorTasks: List<String> = listOf("t1", "t2", "t3"),
    ) = ParticipantConfig(
        participantId = "P01",
        conditionOrder = conditionOrder,
        taskOrder = taskOrder,
        errorTasks = errorTasks,
        screenOffOrder = List(3) { ScreenOffMode.NOTIFY_ONLY },
        screenOffTasks = listOf("so1", "so2", "so3"),
    )
}
