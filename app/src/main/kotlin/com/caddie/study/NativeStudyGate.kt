package com.caddie.study

import com.caddie.agent.core.ModelDelta
import com.caddie.agent.core.OversightDecision
import com.caddie.agent.core.ToolCallId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Pending oversight confirmation holding a deferred decision.
 *
 * The agent suspends on [decision]. The overlay UI (or HTTP API)
 * completes it via [approve] or [decline].
 */
data class PendingConfirmation(
    val callId: ToolCallId,
    val toolName: String,
    val description: String,
    val isBatch: Boolean,
    val decision: CompletableDeferred<OversightDecision>,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private val presentedAtNs = AtomicLong(NOT_PRESENTED)

    /** Called by the overlay after the confirmation card has been laid out. */
    fun markPresented() {
        presentedAtNs.compareAndSet(NOT_PRESENTED, nanoTime())
    }

    fun approve() = decision.complete(
        OversightDecision(approved = true, responseLatencyMs = responseLatencyMs()),
    )

    fun decline(reason: String? = null) =
        decision.complete(
            OversightDecision(
                approved = false,
                reason = reason,
                responseLatencyMs = responseLatencyMs(),
            ),
        )

    /** Resolves infrastructure cancellation without treating it as participant input. */
    fun cancel(reason: String) =
        decision.complete(OversightDecision(approved = false, reason = reason))

    private fun responseLatencyMs(): Double? {
        val startNs = presentedAtNs.get()
        if (startNs == NOT_PRESENTED) return null
        return ((nanoTime() - startNs) / 1_000_000.0).coerceAtLeast(0.0)
    }

    private companion object {
        const val NOT_PRESENTED = Long.MIN_VALUE
    }
}

/** Cancellation that happened only after a participant decision was already captured. */
class ResolvedConfirmationCancellation(
    val resolvedDecision: OversightDecision,
    cause: CancellationException,
) : CancellationException(cause.message) {
    init {
        initCause(cause)
    }
}

/**
 * Native StudyGate implementation.
 *
 * When [confirmStep] or [confirmFinal] is called:
 * 1. A [PendingConfirmation] is created and sent to [onConfirmationRequested]
 * 2. The coroutine suspends, waiting for user input
 * 3. The overlay UI completes the pending confirmation
 * 4. Execution resumes with the [OversightDecision]
 *
 * The default constructor uses [StudyGateDispatcher] to route confirmations
 * to the overlay UI. For testing or custom routing, pass [onConfirmationRequested].
 *
 * @param onConfirmationRequested Called with each new pending confirmation.
 *   Defaults to [StudyGateDispatcher.dispatch] if not provided.
 * @param timeoutMs How long to wait for a response before auto-declining.
 *   Default 120 seconds.
 * @param approvalSettleMs Time for the confirmation overlay to leave the
 *   accessibility tree before the approved action resumes.
 */
class NativeStudyGate(
    private val onConfirmationRequested: (PendingConfirmation) -> Unit =
        StudyGateDispatcher::dispatch,
    private val timeoutMs: Long = 120_000L,
    private val approvalSettleMs: Long = 200L,
    private val confirmationNanoTime: () -> Long = System::nanoTime,
) : StudyGate {
    private val active = AtomicReference<PendingConfirmation?>()

    /** True while Caddie's confirmation UI must receive participant input directly. */
    val hasPendingConfirmation: Boolean
        get() = active.get() != null

    /** Declines the current confirmation so abort/reset cannot leave an agent suspended. */
    fun cancelPending(reason: String): Boolean {
        require(reason.isNotBlank()) { "cancellation reason must not be blank" }
        val pending = active.getAndSet(null) ?: return false
        pending.cancel(reason)
        return true
    }

    override suspend fun confirmStep(
        call: ModelDelta.ToolCall,
        confirmationText: String?,
    ): OversightDecision {
        val description = confirmationText ?: ToolNarrationBridge.humanLabel(call)
        return suspendForConfirmation(
            callId = call.id,
            toolName = call.name,
            description = description,
            isBatch = false,
        )
    }

    override suspend fun confirmFinal(
        calls: List<ModelDelta.ToolCall>,
        summaryLines: List<String>,
    ): OversightDecision {
        val description = if (summaryLines.isEmpty()) {
            ToolNarrationBridge.batchLabel(calls)
        } else {
            ToolNarrationBridge.batchLabel(summaryLines)
        }
        val primary = calls.firstOrNull() ?: run {
            return OversightDecision(approved = true)
        }
        return suspendForConfirmation(
            callId = primary.id,
            toolName = primary.name,
            description = description,
            isBatch = true,
        )
    }

    private suspend fun suspendForConfirmation(
        callId: ToolCallId,
        toolName: String,
        description: String,
        isBatch: Boolean,
    ): OversightDecision {
        val deferred = CompletableDeferred<OversightDecision>()
        val pending = PendingConfirmation(
            callId = callId,
            toolName = toolName,
            description = description,
            isBatch = isBatch,
            decision = deferred,
            nanoTime = confirmationNanoTime,
        )

        check(active.compareAndSet(null, pending)) {
            "another confirmation is already pending"
        }

        // Notify overlay to show confirmation UI
        try {
            onConfirmationRequested(pending)
        } catch (error: Throwable) {
            active.compareAndSet(pending, null)
            throw error
        }
        val timeoutReason =
            if (isBatch) "final checkpoint timeout" else "step confirmation timeout"

        return try {
            val decision = withTimeoutOrNull(timeoutMs) {
                deferred.await()
            } ?: OversightDecision(approved = false, reason = timeoutReason)
            if (decision.approved && approvalSettleMs > 0L) {
                try {
                    delay(approvalSettleMs)
                } catch (cancelled: CancellationException) {
                    throw ResolvedConfirmationCancellation(decision, cancelled)
                }
            }
            decision
        } catch (e: CancellationException) {
            // Rethrow cancellation so caller can propagate it
            throw e
        } catch (e: Exception) {
            OversightDecision(approved = false, reason = "gate error: ${e.message}")
        } finally {
            active.compareAndSet(pending, null)
            // Ensure deferred is always completed even on cancellation
            withContext(NonCancellable) {
                if (!deferred.isCompleted) {
                    deferred.complete(
                        OversightDecision(
                            approved = false,
                            reason = timeoutReason,
                        ),
                    )
                }
            }
        }
    }
}
