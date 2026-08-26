package com.caddie.executor.accessibility

import com.caddie.agent.core.ActionAttemptJournal
import com.caddie.agent.core.ActionDispatchClaim
import com.caddie.agent.core.ActionResolution
import com.caddie.agent.core.AttemptId
import com.caddie.agent.core.RunId
import com.caddie.agent.core.RunRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** Links an action attempt to its durable run and attempt identifiers. */
data class ActionRecoveryMetadata(
    val runId: RunId,
    val attemptId: AttemptId,
)

/** Contains the recovery metadata, target, and operation for one action attempt. */
data class ActionRequest(
    val recovery: ActionRecoveryMetadata,
    val target: SemanticTarget,
    val action: RequestedAction,
    val selectorFingerprint: String? = null,
    val postconditionFingerprint: String? = null,
    val recoverySpecId: String? = null,
)

/** Performs one semantic action against the Android platform. */
fun interface ActionPerformer {
    suspend fun execute(
        target: SemanticTarget,
        action: RequestedAction,
    ): ActionOutcome
}

/** Describes whether observation confirms the expected postcondition. */
enum class VerificationDecision {
    Satisfied,
    Contradicted,
}

/** Lists the final statuses returned by verified action execution. */
enum class ExecutionStatus {
    VERIFIED,
    ALREADY_SATISFIED,
    TARGET_MISSING,
    TARGET_AMBIGUOUS,
    TARGET_NOT_VISIBLE,
    TARGET_DISABLED,
    BLOCKED_BY_WINDOW,
    ACTION_UNAVAILABLE,
    ACTION_REJECTED,
    STALE_OBSERVATION,
    POSTCONDITION_UNMET,
    OUTCOME_UNKNOWN,
    ACCESSIBILITY_UNAVAILABLE,
    INVALID_TARGET,
}

/** Contains the durable attempt identifier and final execution outcome. */
data class ExecutionResult(
    val attemptId: String,
    val status: ExecutionStatus,
    val dispatchOutcome: ActionOutcome,
)

/** Signals that durable state already contains the same action attempt. */
class ActionAlreadyDispatchedException :
    IllegalStateException("Action attempt was already dispatched")

/** Executes, journals, and verifies one real-world action without retrying it. */
class VerifiedActionExecutor(
    private val actionAttemptJournal: ActionAttemptJournal,
    private val actionPerformer: ActionPerformer,
    private val verificationTimeoutMillis: Long = 10_000,
    private val verificationPollIntervalMillis: Long = 100,
    private val verificationErrorReporter: (Exception) -> Unit = {},
) {
    init {
        require(verificationTimeoutMillis > 0) {
            "Verification timeout must be positive"
        }
        require(verificationPollIntervalMillis > 0) {
            "Verification poll interval must be positive"
        }
    }

    suspend fun execute(
        request: ActionRequest,
        verifier: suspend () -> VerificationDecision,
    ): ExecutionResult {
        when (
            actionAttemptJournal.dispatched(
                request.toDispatchedRecord(),
            )
        ) {
            ActionDispatchClaim.CLAIMED -> Unit
            ActionDispatchClaim.ALREADY_CLAIMED ->
                throw ActionAlreadyDispatchedException()
        }

        val actionOutcome =
            actionPerformer.execute(
                request.target,
                request.action,
            )
        actionAttemptJournal.executed(
            RunRecord.ActionExecuted(
                runId = request.recovery.runId,
                attemptId = request.recovery.attemptId,
                dispatchOutcome = actionOutcome.wireName(),
            ),
        )

        val status =
            if (actionOutcome == ActionOutcome.Accepted) {
                val verificationDecision = verifyUntilSettled(verifier)

                when (verificationDecision) {
                    VerificationDecision.Satisfied -> ExecutionStatus.VERIFIED
                    VerificationDecision.Contradicted -> ExecutionStatus.POSTCONDITION_UNMET
                    null -> ExecutionStatus.OUTCOME_UNKNOWN
                }
            } else {
                actionOutcome.toExecutionStatus()
            }

        val result =
            ExecutionResult(
                attemptId = request.recovery.attemptId.value,
                status = status,
                dispatchOutcome = actionOutcome,
            )
        actionAttemptJournal.terminal(
            RunRecord.ActionTerminal(
                runId = request.recovery.runId,
                attemptId = request.recovery.attemptId,
                resolution = status.toActionResolution(),
            ),
        )
        return result
    }

    /** Polls changing Android windows without ever repeating the physical action. */
    private suspend fun verifyUntilSettled(
        verifier: suspend () -> VerificationDecision,
    ): VerificationDecision? {
        var observedContradiction = false
        var reportedVerificationError = false
        val satisfied =
            withTimeoutOrNull<Boolean>(verificationTimeoutMillis) {
                while (true) {
                    val decision =
                        try {
                            verifier()
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (error: Exception) {
                            if (!reportedVerificationError) {
                                verificationErrorReporter(error)
                                reportedVerificationError = true
                            }
                            null
                        }
                    when (decision) {
                        VerificationDecision.Satisfied -> return@withTimeoutOrNull true
                        VerificationDecision.Contradicted -> observedContradiction = true
                        null -> Unit
                    }
                    delay(verificationPollIntervalMillis)
                }
                false
            } ?: false
        return when {
            satisfied -> VerificationDecision.Satisfied
            observedContradiction -> VerificationDecision.Contradicted
            else -> null
        }
    }

    private fun ActionRequest.toDispatchedRecord() =
        RunRecord.ActionDispatched(
            runId = recovery.runId,
            attemptId = recovery.attemptId,
            actionKind = action.stableKind(),
            selectorFingerprint = selectorFingerprint,
            postconditionFingerprint = postconditionFingerprint,
            recoverySpecId = recoverySpecId,
        )

    private fun RequestedAction.stableKind(): String =
        when (this) {
            is RequestedAction.OpenApp -> "OPEN_APP"
            is RequestedAction.OpenUrl -> "OPEN_URL"
            RequestedAction.Back -> "BACK"
            RequestedAction.DismissInputMethod -> "BACK"
            RequestedAction.Click -> "CLICK"
            RequestedAction.LongClick -> "LONG_CLICK"
            is RequestedAction.SetText -> "SET_TEXT"
            is RequestedAction.SetChecked -> "SET_CHECKED"
            is RequestedAction.Scroll -> "SCROLL"
        }

    private fun ExecutionStatus.toActionResolution(): ActionResolution =
        when (this) {
            ExecutionStatus.VERIFIED,
            ExecutionStatus.ALREADY_SATISFIED,
            -> ActionResolution.VERIFIED
            ExecutionStatus.POSTCONDITION_UNMET,
            ExecutionStatus.OUTCOME_UNKNOWN,
            -> ActionResolution.OUTCOME_UNKNOWN
            else -> ActionResolution.FAILED
        }

    private fun ActionOutcome.wireName(): String =
        when (this) {
            ActionOutcome.Accepted -> "Accepted"
            ActionOutcome.AlreadySatisfied -> "AlreadySatisfied"
            ActionOutcome.InvalidTarget -> "InvalidTarget"
            ActionOutcome.TargetMissing -> "TargetMissing"
            ActionOutcome.TargetAmbiguous -> "TargetAmbiguous"
            ActionOutcome.TargetNotVisible -> "TargetNotVisible"
            ActionOutcome.TargetDisabled -> "TargetDisabled"
            ActionOutcome.BlockedByWindow -> "BlockedByWindow"
            ActionOutcome.ActionUnavailable -> "ActionUnavailable"
            ActionOutcome.ActionRejected -> "ActionRejected"
            ActionOutcome.StaleObservation -> "StaleObservation"
            ActionOutcome.AccessibilityUnavailable -> "AccessibilityUnavailable"
        }

    private fun ActionOutcome.toExecutionStatus(): ExecutionStatus =
        when (this) {
            ActionOutcome.Accepted -> error("Accepted outcomes require verification")
            ActionOutcome.AlreadySatisfied -> ExecutionStatus.ALREADY_SATISFIED
            ActionOutcome.InvalidTarget -> ExecutionStatus.INVALID_TARGET
            ActionOutcome.TargetMissing -> ExecutionStatus.TARGET_MISSING
            ActionOutcome.TargetAmbiguous -> ExecutionStatus.TARGET_AMBIGUOUS
            ActionOutcome.TargetNotVisible -> ExecutionStatus.TARGET_NOT_VISIBLE
            ActionOutcome.TargetDisabled -> ExecutionStatus.TARGET_DISABLED
            ActionOutcome.BlockedByWindow -> ExecutionStatus.BLOCKED_BY_WINDOW
            ActionOutcome.ActionUnavailable -> ExecutionStatus.ACTION_UNAVAILABLE
            ActionOutcome.ActionRejected -> ExecutionStatus.ACTION_REJECTED
            ActionOutcome.StaleObservation -> ExecutionStatus.STALE_OBSERVATION
            ActionOutcome.AccessibilityUnavailable -> ExecutionStatus.ACCESSIBILITY_UNAVAILABLE
        }
}
