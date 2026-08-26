package com.caddie.agent.core

/** Describes the known final outcome of a real-world action. */
enum class ActionResolution {
    VERIFIED,
    FAILED,
    OUTCOME_UNKNOWN,
    POSTCONDITION_SATISFIED,
    POSTCONDITION_NOT_SATISFIED,
    RECONCILIATION_INCONCLUSIVE,
    RECONCILIATION_UNAVAILABLE,
}

/** Represents one append-only event in an agent run. */
sealed interface RunRecord {
    val recordId: String
    val runId: RunId

    /** Records creation of a new run. */
    data class RunCreated(
        val sessionId: SessionId,
        override val runId: RunId,
        val task: String,
    ) : RunRecord {
        override val recordId = "run-created:${runId.value}"
    }

    /** Records that execution of a run started. */
    data class RunStarted(
        override val runId: RunId,
    ) : RunRecord {
        override val recordId = "run-started:${runId.value}"
    }

    /** Records that a tool call was dispatched. */
    data class ToolDispatched(
        override val runId: RunId,
        val callId: ToolCallId,
        val toolName: String,
        val argumentsJson: String = "{}",
        val assistantText: String = "",
    ) : RunRecord {
        override val recordId =
            "tool-dispatched:${runId.value}:${callId.value}"
    }

    /** Records the returned result of a tool call. */
    data class ToolFinished(
        override val runId: RunId,
        val callId: ToolCallId,
        val contentJson: String,
        val isError: Boolean,
    ) : RunRecord {
        override val recordId =
            "tool-result:${runId.value}:${callId.value}"
    }

    /** Records the final text returned by the assistant. */
    data class AssistantCompleted(
        override val runId: RunId,
        val text: String,
    ) : RunRecord {
        override val recordId = "assistant-completed:${runId.value}"
    }

    /** Records that a real-world action attempt was claimed. */
    data class ActionDispatched(
        override val runId: RunId,
        val attemptId: AttemptId,
        val actionKind: String,
        val selectorFingerprint: String?,
        val postconditionFingerprint: String?,
        val recoverySpecId: String?,
    ) : RunRecord {
        override val recordId =
            "action-dispatched:${runId.value}:${attemptId.value}"
    }

    /** Records execution details for a real-world action. */
    data class ActionExecuted(
        override val runId: RunId,
        val attemptId: AttemptId,
        val dispatchOutcome: String,
    ) : RunRecord {
        override val recordId =
            "action-executed:${runId.value}:${attemptId.value}"
    }

    /** Records the final resolution of a real-world action. */
    data class ActionTerminal(
        override val runId: RunId,
        val attemptId: AttemptId,
        val resolution: ActionResolution,
    ) : RunRecord {
        override val recordId =
            "action-terminal:${runId.value}:${attemptId.value}"
    }

    /** Records that a run paused and why. */
    data class RunPaused(
        override val runId: RunId,
        val stepId: StepId,
        val state: RunState,
        val reason: String?,
    ) : RunRecord {
        init {
            require(
                state == RunState.PAUSED_OVERSIGHT ||
                    state == RunState.PAUSED_NETWORK ||
                    state == RunState.PAUSED_RECOVERABLE,
            ) {
                "RunPaused requires a paused state"
            }
        }

        override val recordId =
            "run-paused:${runId.value}:${stepId.value}:$state"
    }

    /** Records that a paused run resumed. */
    data class RunResumed(
        override val runId: RunId,
        val stepId: StepId,
    ) : RunRecord {
        override val recordId =
            "run-resumed:${runId.value}:${stepId.value}"
    }

    /** Records successful completion of a run. */
    data class RunCompleted(
        override val runId: RunId,
    ) : RunRecord {
        override val recordId = "run-completed:${runId.value}"
    }

    /** Records permanent abortion of a run. */
    data class RunAborted(
        override val runId: RunId,
    ) : RunRecord {
        override val recordId = "run-aborted:${runId.value}"
    }
}

/** Contains the reconstructed state and history of one run. */
data class RunSnapshot(
    val runId: RunId,
    val state: RunState,
    val messages: List<AgentMessage> = emptyList(),
    val unverifiedToolCallId: ToolCallId? = null,
    val unverifiedAttemptId: AttemptId? = null,
    val hasDispatchedAction: Boolean = false,
    val failedToolCallIds: Set<ToolCallId> = emptySet(),
)
