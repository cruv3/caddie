package com.caddie.app.runtime

import com.caddie.agent.core.RunId
import com.caddie.agent.core.RunRecord
import com.caddie.agent.core.RunSnapshot
import com.caddie.agent.core.SessionStore
import com.caddie.agent.core.StepOutcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeRuntimeHostTest {
    @Test
    fun `warm up completes recovery before the first submitted task`() = runTest {
        val order = mutableListOf<String>()
        val runner = NativeAgentRunner(
            store = InMemoryStore(),
            step = { _, _ ->
                order += "step"
                StepOutcome.PAUSED_NETWORK
            },
        )
        val host = NativeRuntimeHost(
            runner = runner,
            beforeFirstRun = { order += "recover" },
        )

        assertTrue(host.warmUp())
        host.run("first")

        assertEquals(listOf("recover", "step"), order)
    }

    @Test
    fun `recovery gate runs once before any native task step`() = runTest {
        val order = mutableListOf<String>()
        val runner = NativeAgentRunner(
            store = InMemoryStore(),
            step = { _, _ ->
                order += "step"
                StepOutcome.PAUSED_NETWORK
            },
        )
        val host = NativeRuntimeHost(
            runner = runner,
            beforeFirstRun = { order += "recover" },
        )

        host.run("first")
        host.run("second")

        assertEquals(listOf("recover", "step", "step"), order)
    }

    @Test
    fun `deterministic task completes without invoking default step`() = runTest {
        var defaultCalls = 0
        var deterministicCalls = 0
        val runner = NativeAgentRunner(
            store = InMemoryStore(),
            step = { _, _ ->
                defaultCalls++
                StepOutcome.PAUSED_NETWORK
            },
        )
        val host = NativeRuntimeHost(runner)

        val result = host.runDeterministic("study task", "Studie abgeschlossen") {
            deterministicCalls++
            true
        }

        assertTrue(result is NativeTaskResult.Completed)
        assertEquals("Studie abgeschlossen", (result as NativeTaskResult.Completed).answer)
        assertEquals(1, deterministicCalls)
        assertEquals(0, defaultCalls)
    }

    private class InMemoryStore : SessionStore {
        private val records = linkedMapOf<RunId, MutableList<RunRecord>>()

        override suspend fun snapshot(runId: RunId): RunSnapshot =
            com.caddie.agent.core.reduce(records[runId].orEmpty(), recoveredProcess = false)

        override suspend fun append(event: RunRecord) {
            records.getOrPut(event.runId) { mutableListOf() } += event
        }
    }
}
