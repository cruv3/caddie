package com.caddie.study.runtime.android

import com.caddie.study.runtime.coordinator.ArmedTrialCoordinator
import com.caddie.study.runtime.model.RuntimeStudyCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Verifies that test trials do not require live-study persistence identifiers. */
class NativeStudyExecutionTest {
    @Test
    fun `controlled test error stays ephemeral`() {
        assertNull(controlledErrorEvent(config(ArmedTrialCoordinator.StudyRunScope.TEST, null)))
    }

    @Test
    fun `controlled live error keeps durable run identity`() {
        val event = controlledErrorEvent(config(ArmedTrialCoordinator.StudyRunScope.LIVE, "run-1"))

        assertEquals("run-1", event?.studyRunId)
        assertEquals("task", event?.taskId)
        assertEquals("attempt-1", event?.trialAttemptId)
    }

    @Test
    fun `trial completion exposes cumulative confirmation metrics`() {
        val details = trialCompletionDetails(
            outcome = "success",
            totalSteps = 6,
            errorsInjected = 0,
            durationMs = 20_000.0,
            confirmationResponseTimeMs = 8_250.0,
            confirmationCount = 6,
        )

        assertEquals(8_250.0, details["confirmation_response_latency_total_ms"])
        assertEquals(6, details["confirmation_response_count"])
        assertEquals(1_375.0, details["confirmation_response_latency_mean_ms"])
    }

    @Test
    fun `resolved confirmation keeps legacy-compatible latency field`() {
        val details = confirmationResolvedDetails(
            gateType = "c1",
            stepId = "save_note",
            decision = "confirmed",
            responseTimeMs = 1_840.0,
            participantResponse = true,
        )

        assertEquals("c1", details["gate_type"])
        assertEquals("save_note", details["step_id"])
        assertEquals("confirmed", details["decision"])
        assertEquals(1_840.0, details["response_latency_ms"])
        assertEquals(true, details["participant_response"])
    }

    private fun controlledErrorEvent(config: ArmedTrialCoordinator.ArmedTrialConfig) =
        controlledErrorEvent(
            config = config,
            stepId = "step",
            errorVariantId = "variant",
            field = "value",
            wrongValue = "wrong",
            correctValue = "correct",
        )

    private fun config(scope: ArmedTrialCoordinator.StudyRunScope, runId: String?) =
        ArmedTrialCoordinator.ArmedTrialConfig(
            participantId = "TEST",
            trialIndex = 0,
            taskId = "task",
            condition = RuntimeStudyCondition.STEPWISE,
            injectError = true,
            runScope = scope,
            studyRunId = runId,
            trialAttemptId = "attempt-1",
        )
}
