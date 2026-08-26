package com.caddie.context

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ContextEngineTest {
    @Test
    fun `initialization is serialized and reaches ready`() = runTest {
        val events = mutableListOf<String>()
        val engine = ContextEngine(this, StandardTestDispatcher(testScheduler), FakeInitializer(events))

        advanceUntilIdle()

        assertEquals(ContextEngineStatus.READY, engine.state.value.status)
        assertTrue(engine.state.value.replayEnabled)
        assertEquals(listOf("database", "corpus", "activate", "embedder", "vectors"), events)
        engine.close()
        assertEquals(listOf("embedder-close", "database-close"), events.takeLast(2))
    }

    @Test
    fun `embedding cipher and corrupt failures map conservatively without auto resume`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val embedding = ContextEngine(this, dispatcher, FakeInitializer(failAt = "vectors"))
        val cipher = ContextEngine(this, dispatcher, FakeInitializer(failAt = "cipher"))
        val corrupt = ContextEngine(this, dispatcher, FakeInitializer(failAt = "corpus"))
        advanceUntilIdle()

        assertEquals(ContextEngineStatus.DEGRADED_LEXICAL, embedding.state.value.status)
        assertEquals(ContextEngineStatus.RESET_REQUIRED, cipher.state.value.status)
        assertFalse(cipher.state.value.replayEnabled)
        assertEquals(ContextEngineStatus.FAILED, corrupt.state.value.status)
        assertEquals(1, corrupt.initializationAttempts)
        advanceUntilIdle()
        assertEquals(1, corrupt.initializationAttempts)
    }

    @Test
    fun `owner cancellation propagates while close leaves owner siblings alone`() = runTest {
        val owner = Job()
        val events = mutableListOf<String>()
        val ownerScope = kotlinx.coroutines.CoroutineScope(coroutineContext + owner)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val blocking = ContextEngine(ownerScope, dispatcher, FakeInitializer(events, blockAt = "corpus"))
        advanceUntilIdle()
        owner.cancel()
        advanceUntilIdle()
        assertTrue("cancelled" in events)
        assertEquals(1, events.count { it == "database-close" })
        blocking.close()
        assertEquals(1, events.count { it == "database-close" })

        val sibling = launch { awaitCancellation() }
        val closable = ContextEngine(this, dispatcher, FakeInitializer())
        advanceUntilIdle()
        closable.close()
        closable.close()

        assertTrue(sibling.isActive)
        assertEquals(1, closable.closeCalls)
        sibling.cancel()
    }

    private class FakeInitializer(
        private val events: MutableList<String> = mutableListOf(),
        private val failAt: String? = null,
        private val blockAt: String? = null,
    ) : ContextEngineInitializer {
        override suspend fun openDatabase(): AutoCloseable {
            step("database")
            return AutoCloseable { events += "database-close" }
        }

        override suspend fun validateCorpus() = step("corpus")
        override suspend fun activateCorpus() = step(if (failAt == "cipher") "cipher" else "activate")

        override suspend fun openEmbedder(): AutoCloseable {
            step("embedder")
            return AutoCloseable { events += "embedder-close" }
        }

        override suspend fun validateVectors() = step("vectors")

        private suspend fun step(name: String) {
            events += name
            if (blockAt == name) {
                try {
                    awaitCancellation()
                } finally {
                    events += "cancelled"
                }
            }
            when {
                failAt == "vectors" -> if (name == "vectors") throw ContextEmbeddingUnavailable()
                failAt == "cipher" -> if (name == "cipher") throw ContextCipherResetRequired()
                failAt == name -> throw IllegalArgumentException("corrupt")
            }
        }
    }
}
