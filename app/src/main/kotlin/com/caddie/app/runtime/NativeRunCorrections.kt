package com.caddie.app.runtime

import com.caddie.agent.core.AgentMessage
import com.caddie.agent.core.ModelRequest
import com.caddie.agent.core.RequestFactory
import com.caddie.agent.core.RunId
import com.caddie.agent.core.RunSnapshot
import com.caddie.agent.core.ToolDefinition

/** Holds one participant correction until the active run's next model turn consumes it. */
internal class NativeRunCorrections {
    private var pending: Pair<RunId, String>? = null

    @Synchronized
    fun submit(runId: RunId, text: String) {
        require(text.isNotBlank()) { "correction must not be blank" }
        pending = runId to text.trim()
    }

    @Synchronized
    fun hasPending(runId: RunId): Boolean = pending?.first == runId

    @Synchronized
    fun take(runId: RunId): String? {
        val correction = pending?.takeIf { it.first == runId } ?: return null
        pending = null
        return correction.second
    }

    @Synchronized
    fun clear(runId: RunId) {
        if (pending?.first == runId) pending = null
    }
}

/** Adds a queued participant correction to the next request without starting a second run. */
internal class CorrectionAwareRequestFactory(
    private val delegate: RequestFactory,
    private val corrections: NativeRunCorrections,
    private val onCorrectionCaptured: (RunId, String) -> Unit = { _, _ -> },
    private val onCorrection: (String) -> Unit = {},
) : RequestFactory {
    override fun create(snapshot: RunSnapshot, tools: List<ToolDefinition>): ModelRequest {
        val correction = corrections.take(snapshot.runId)
        correction?.let(onCorrection)
        correction?.let { onCorrectionCaptured(snapshot.runId, it) }
        val request = delegate.create(snapshot, tools)
        correction ?: return request
        return request.copy(
            messages = request.messages + AgentMessage(
                role = AgentMessage.Role.USER,
                content = "Participant correction: $correction. " +
                    "Observe the current UI again and adjust the remaining plan. " +
                    "Do not repeat an action whose outcome is already verified.",
            ),
        )
    }
}
