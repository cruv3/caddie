package com.caddie.study

import com.caddie.agent.core.ModelDelta
import com.caddie.agent.core.OversightDecision
import com.caddie.agent.core.ToolCallId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyOversightPolicyTest {
    private val call = ModelDelta.ToolCall(
        ToolCallId("call-1"),
        "smartphone_tap_element",
        """{"index":7}""",
    )

    @Test
    fun `C1 asks before each participant meaningful action`() = runTest {
        val gate = RecordingGate(approved = true)
        val assessment = StudyActionAssessment(
            StudyActionKind.PARTICIPANT_MEANINGFUL,
            confirmationText = "Neue Startzeit übernehmen",
        )
        val policy = StudyOversightPolicy(
            StudyCondition.C1_STEPWISE,
            gate,
            FixedClassifier(assessment),
        )

        assertTrue(policy.approve(call).approved)
        assertEquals(listOf(call.id), gate.stepCalls)
        assertEquals(listOf("Neue Startzeit übernehmen"), gate.stepTexts)
        assertEquals(emptyList<ToolCallId>(), gate.finalCalls)
    }

    @Test
    fun `C1 does not gate explicitly preparatory actions`() = runTest {
        val gate = RecordingGate(approved = false)
        val policy = StudyOversightPolicy(
            StudyCondition.C1_STEPWISE,
            gate,
            FixedClassifier(StudyActionAssessment(StudyActionKind.PREPARATORY)),
        )

        assertTrue(policy.approve(call).approved)
        assertEquals(0, gate.totalCalls)
    }

    @Test
    fun `C2 gates commit immediately and propagates decline`() = runTest {
        val gate = RecordingGate(approved = false)
        val assessment = StudyActionAssessment(
            StudyActionKind.COMMIT,
            finalSummary = listOf("Projektsitzung auf 15:00–16:00 Uhr aktualisieren"),
        )
        val policy = StudyOversightPolicy(
            StudyCondition.C2_FINAL_CHECKPOINT,
            gate,
            FixedClassifier(assessment),
        )

        assertFalse(policy.approve(call).approved)
        assertEquals(emptyList<ToolCallId>(), gate.stepCalls)
        assertEquals(listOf(call.id), gate.finalCalls)
        assertEquals(assessment.finalSummary, gate.finalSummaries.single())
    }

    @Test
    fun `C2 allows preparation without a gate`() = runTest {
        val gate = RecordingGate(approved = false)
        val policy = StudyOversightPolicy(
            StudyCondition.C2_FINAL_CHECKPOINT,
            gate,
            FixedClassifier(StudyActionAssessment(StudyActionKind.PREPARATORY)),
        )

        assertTrue(policy.approve(call).approved)
        assertEquals(0, gate.totalCalls)
    }

    @Test
    fun `C3 adds no mandatory gate`() = runTest {
        val gate = RecordingGate(approved = false)
        val policy = StudyOversightPolicy(
            StudyCondition.C3_VOLUNTARY_INTERVENTION,
            gate,
            FixedClassifier(StudyActionAssessment(StudyActionKind.COMMIT)),
        )

        assertTrue(policy.approve(call).approved)
        assertEquals(0, gate.totalCalls)
    }

    @Test
    fun `condition wire values match the Python contract`() {
        assertEquals("c1_stepwise", StudyCondition.C1_STEPWISE.wireValue)
        assertEquals(
            "c2_final_checkpoint",
            StudyCondition.C2_FINAL_CHECKPOINT.wireValue,
        )
        assertEquals(
            "c3_voluntary_intervention",
            StudyCondition.C3_VOLUNTARY_INTERVENTION.wireValue,
        )
    }

    private class FixedClassifier(
        private val assessment: StudyActionAssessment,
    ) : StudyActionClassifier {
        override fun assess(call: ModelDelta.ToolCall): StudyActionAssessment = assessment
    }

    private class RecordingGate(
        private val approved: Boolean,
    ) : StudyGate {
        val stepCalls = mutableListOf<ToolCallId>()
        val stepTexts = mutableListOf<String?>()
        val finalCalls = mutableListOf<ToolCallId>()
        val finalSummaries = mutableListOf<List<String>>()
        val totalCalls: Int
            get() = stepCalls.size + finalCalls.size

        override suspend fun confirmStep(
            call: ModelDelta.ToolCall,
            confirmationText: String?,
        ): OversightDecision {
            stepCalls += call.id
            stepTexts += confirmationText
            return OversightDecision(approved)
        }

        override suspend fun confirmFinal(
            calls: List<ModelDelta.ToolCall>,
            summaryLines: List<String>,
        ): OversightDecision {
            finalCalls += calls.map { it.id }
            finalSummaries += summaryLines
            return OversightDecision(approved)
        }
    }
}
