package com.caddie.study.runtime.workflow

import com.caddie.study.runtime.model.WorkflowState

/**
 * Explicit portal workflow state transitions, ported from
 * `caddie.study.portal.workflow` (Python).
 *
 * The portal drives a live study session through a fixed sequence of
 * states. [nextState] returns the state committed by [event] or rejects
 * the transition. `abort` and `technical_hold` are handled specially.
 */
object PortalWorkflow {

    /** Thrown when an event is not valid for the current workflow state. */
    class InvalidPortalTransition(message: String) : RuntimeException(message)

    private val TERMINAL_STATES = setOf(WorkflowState.COMPLETED, WorkflowState.ABORTED)

    private val TRANSITIONS: Map<Pair<WorkflowState, String>, WorkflowState> = mapOf(
        WorkflowState.SETUP to "start_consent" to WorkflowState.CONSENT,
        WorkflowState.CONSENT to "consent_confirmed" to WorkflowState.TRAINING,
        WorkflowState.TRAINING to "training_confirmed" to WorkflowState.TRIAL_READY,
        WorkflowState.TRIAL_READY to "release_task_card" to WorkflowState.TASK_CARD,
        WorkflowState.TASK_CARD to "participant_started" to WorkflowState.TRIAL_RUNNING,
        WorkflowState.TRIAL_RUNNING to "trial_finished" to WorkflowState.INVESTIGATOR_OBSERVATION,
        WorkflowState.INVESTIGATOR_OBSERVATION to "retry_failed_trial" to WorkflowState.TRIAL_READY,
        WorkflowState.INVESTIGATOR_OBSERVATION to "observation_saved" to WorkflowState.TASK_QUESTIONNAIRE,
        WorkflowState.TASK_QUESTIONNAIRE to "task_response_submitted" to WorkflowState.INVESTIGATOR_RESULT_REVIEW,
        WorkflowState.INVESTIGATOR_RESULT_REVIEW to "result_reviewed" to WorkflowState.TRIAL_RESET,
        WorkflowState.TRIAL_RESET to "next_trial" to WorkflowState.TRIAL_READY,
        WorkflowState.TRIAL_RESET to "release_block_questionnaire" to WorkflowState.BLOCK_QUESTIONNAIRE,
        WorkflowState.BLOCK_QUESTIONNAIRE to "block_response_submitted" to WorkflowState.TRIAL_READY,
        WorkflowState.BLOCK_QUESTIONNAIRE to "all_trials_completed" to WorkflowState.DEMOGRAPHICS,
        WorkflowState.DEMOGRAPHICS to "demographics_submitted" to WorkflowState.PREFERENCE_RANKING,
        WorkflowState.PREFERENCE_RANKING to "ranking_submitted" to WorkflowState.INTERVIEW,
        WorkflowState.INTERVIEW to "interview_submitted" to WorkflowState.DEBRIEF,
        WorkflowState.DEBRIEF to "debrief_confirmed" to WorkflowState.COMPLETED,
    )

    /**
     * Return the state committed by [event] from [state], or reject.
     *
     * [storedResumeState] is reserved for trusted state loaded from the
     * portal store when resuming from a technical hold.
     */
    fun nextState(
        state: WorkflowState,
        event: String,
        storedResumeState: WorkflowState? = null,
    ): WorkflowState {
        if (event == "abort" && state !in TERMINAL_STATES) {
            return WorkflowState.ABORTED
        }
        if (event == "technical_hold" && state !in TERMINAL_STATES && state != WorkflowState.TECHNICAL_HOLD) {
            return WorkflowState.TECHNICAL_HOLD
        }
        if (state == WorkflowState.TECHNICAL_HOLD && event == "resume") {
            val resume = storedResumeState
            if (
                resume != null &&
                resume !in TERMINAL_STATES &&
                resume != WorkflowState.TECHNICAL_HOLD &&
                resume != WorkflowState.TRIAL_RUNNING
            ) {
                return resume
            }
            throw InvalidPortalTransition("technical hold resume requires a valid stored resume state")
        }
        if (state == WorkflowState.TECHNICAL_HOLD && event == "retry_failed_trial") {
            if (storedResumeState == WorkflowState.TRIAL_RUNNING) {
                return WorkflowState.TRIAL_READY
            }
            throw InvalidPortalTransition("failed-trial retry requires a held running trial")
        }
        if (state == WorkflowState.TECHNICAL_HOLD && event == "accept_completed_trial") {
            if (storedResumeState == WorkflowState.TRIAL_RUNNING) {
                return WorkflowState.INVESTIGATOR_OBSERVATION
            }
            throw InvalidPortalTransition("completed-trial recovery requires a held running trial")
        }
        return TRANSITIONS[state to event]
            ?: throw InvalidPortalTransition("event '$event' is invalid from state '${state.wireValue}'")
    }
}
