package com.caddie.app.runtime

import com.caddie.agent.core.*
import org.junit.Assert.*
import org.junit.Test

class RuntimeMemoryEvidenceTest {
    @Test fun `only runtime inputs replace evidence and cleanup prevents cross run reuse`() {
        val run = RunId("one")
        val ledger = RuntimeMemoryEvidence { 42L }
        val snapshot = RunSnapshot(run, RunState.RUNNING, listOf(
            AgentMessage(AgentMessage.Role.SYSTEM, "untrusted reference"),
            AgentMessage(AgentMessage.Role.USER, "I prefer concise answers."),
            AgentMessage(AgentMessage.Role.ASSISTANT, "invented"),
        ))
        ledger.begin(snapshot)
        assertEquals("I prefer concise answers.", ledger.current(run)?.text)
        ledger.correction(run, "I prefer detailed answers.")
        ledger.begin(snapshot)
        assertEquals("user-correction", ledger.current(run)?.source)
        ledger.answer(run, "I prefer metric units.")
        assertEquals("user-answer", ledger.current(run)?.source)
        assertNull(ledger.current(RunId("other")))
        ledger.clear(run)
        assertNull(ledger.current(run))
    }
}
