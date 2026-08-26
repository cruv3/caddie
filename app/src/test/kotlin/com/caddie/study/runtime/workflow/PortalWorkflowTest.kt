package com.caddie.study.runtime.workflow

import com.caddie.study.runtime.model.WorkflowState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PortalWorkflowTest {

    @Test
    fun `happy path from setup to completed`() {
        var s = WorkflowState.SETUP
        s = step(s, "start_consent")
        s = step(s, "consent_confirmed")
        s = step(s, "training_confirmed")
        s = step(s, "release_task_card")
        s = step(s, "participant_started")
        s = step(s, "trial_finished")
        s = step(s, "observation_saved")
        s = step(s, "task_response_submitted")
        s = step(s, "result_reviewed")
        s = step(s, "next_trial")
        assertEquals(WorkflowState.TRIAL_READY, s)
    }

    @Test
    fun `block questionnaire path leads to demographics`() {
        var s = WorkflowState.TRIAL_RESET
        s = step(s, "release_block_questionnaire")
        assertEquals(WorkflowState.BLOCK_QUESTIONNAIRE, s)
        s = step(s, "all_trials_completed")
        assertEquals(WorkflowState.DEMOGRAPHICS, s)
        s = step(s, "demographics_submitted")
        s = step(s, "ranking_submitted")
        s = step(s, "interview_submitted")
        s = step(s, "debrief_confirmed")
        assertEquals(WorkflowState.COMPLETED, s)
    }

    @Test
    fun `block questionnaire can loop back to trial ready`() {
        var s = WorkflowState.BLOCK_QUESTIONNAIRE
        s = step(s, "block_response_submitted")
        assertEquals(WorkflowState.TRIAL_READY, s)
    }

    @Test
    fun `abort from any non terminal state leads to aborted`() {
        for (state in WorkflowState.entries) {
            if (state == WorkflowState.COMPLETED || state == WorkflowState.ABORTED) continue
            val next = PortalWorkflow.nextState(state, "abort")
            assertEquals("abort from ${state.wireValue}", WorkflowState.ABORTED, next)
        }
    }

    @Test
    fun `technical hold and resume roundtrip`() {
        val ready = WorkflowState.TRIAL_READY
        val held = PortalWorkflow.nextState(ready, "technical_hold")
        assertEquals(WorkflowState.TECHNICAL_HOLD, held)
        val resumed = PortalWorkflow.nextState(held, "resume", storedResumeState = ready)
        assertEquals(ready, resumed)
    }

    @Test
    fun `running trial cannot be resumed after its native execution ended`() {
        assertThrows(PortalWorkflow.InvalidPortalTransition::class.java) {
            PortalWorkflow.nextState(
                WorkflowState.TECHNICAL_HOLD,
                "resume",
                storedResumeState = WorkflowState.TRIAL_RUNNING,
            )
        }
    }

    @Test
    fun `technical hold resume without stored state is rejected`() {
        assertThrows(PortalWorkflow.InvalidPortalTransition::class.java) {
            PortalWorkflow.nextState(WorkflowState.TECHNICAL_HOLD, "resume")
        }
    }

    @Test
    fun `failed running trial is retried from trial ready instead of fake resume`() {
        assertEquals(
            WorkflowState.TRIAL_READY,
            PortalWorkflow.nextState(
                WorkflowState.TECHNICAL_HOLD,
                "retry_failed_trial",
                storedResumeState = WorkflowState.TRIAL_RUNNING,
            ),
        )
    }

    @Test
    fun `prematurely finished trial can be returned to trial ready`() {
        assertEquals(
            WorkflowState.TRIAL_READY,
            PortalWorkflow.nextState(
                WorkflowState.INVESTIGATOR_OBSERVATION,
                "retry_failed_trial",
            ),
        )
    }

    @Test
    fun `completed controlled error can leave a legacy running hold`() {
        assertEquals(
            WorkflowState.INVESTIGATOR_OBSERVATION,
            PortalWorkflow.nextState(
                WorkflowState.TECHNICAL_HOLD,
                "accept_completed_trial",
                storedResumeState = WorkflowState.TRIAL_RUNNING,
            ),
        )
    }

    @Test
    fun `invalid event from state is rejected`() {
        assertThrows(PortalWorkflow.InvalidPortalTransition::class.java) {
            PortalWorkflow.nextState(WorkflowState.SETUP, "consent_confirmed")
        }
    }

    @Test
    fun `abort from terminal state is rejected`() {
        assertThrows(PortalWorkflow.InvalidPortalTransition::class.java) {
            PortalWorkflow.nextState(WorkflowState.COMPLETED, "abort")
        }
    }

    private fun step(state: WorkflowState, event: String) = PortalWorkflow.nextState(state, event)
}
