package com.caddie.app.runtime

import com.caddie.agent.core.AgentMessage
import com.caddie.agent.core.RunId
import com.caddie.agent.core.RunSnapshot
import com.caddie.context.personal.TrustedMemoryEvidence

/** One current user input for the single active run. No UI, model or generic tool text is admitted. */
internal class RuntimeMemoryEvidence(private val clock: () -> Long = System::currentTimeMillis) {
    private var latest: TrustedMemoryEvidence? = null
    private var counter = 0L

    @Synchronized fun begin(snapshot: RunSnapshot) {
        if (latest?.runId == snapshot.runId.value) return
        val originalTask = snapshot.messages.firstOrNull { it.role == AgentMessage.Role.USER }?.content ?: return
        record(snapshot.runId, originalTask, "user-task")
    }

    @Synchronized fun correction(runId: RunId, text: String) = record(runId, text, "user-correction")
    @Synchronized fun answer(runId: RunId, text: String) = record(runId, text, "user-answer")
    @Synchronized fun current(runId: RunId): TrustedMemoryEvidence? = latest?.takeIf { it.runId == runId.value }
    @Synchronized fun clear(runId: RunId) { if (latest?.runId == runId.value) latest = null }

    private fun record(runId: RunId, text: String, source: String) {
        // Oversized input remains in the ordinary task context, but is ineligible for memory.
        latest = TrustedMemoryEvidence(runId.value, "user-${++counter}", source,
            if (text.length <= 16_000) text else "", clock())
    }
}
