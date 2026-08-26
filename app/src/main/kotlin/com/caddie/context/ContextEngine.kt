package com.caddie.context

import com.caddie.context.persistence.ContextPersistenceResetRequired
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Describes the externally visible lifecycle of the optional context engine. */
enum class ContextEngineStatus {
    INITIALIZING,
    READY,
    DEGRADED_LEXICAL,
    RESET_REQUIRED,
    FAILED,
    CLOSED,
}

/** Exposes context readiness without leaking persistence or model details. */
data class ContextEngineState(
    val status: ContextEngineStatus,
    val replayEnabled: Boolean,
)

/** Supplies ordered initialization operations and closeable owned resources. */
interface ContextEngineInitializer {
    suspend fun openDatabase(): AutoCloseable
    suspend fun validateCorpus()
    suspend fun activateCorpus()
    suspend fun openEmbedder(): AutoCloseable
    suspend fun validateVectors()
}

class ContextEmbeddingUnavailable : IllegalStateException()
class ContextCipherResetRequired : IllegalStateException()

/**
 * Owns one cancellable initialization child while leaving the caller's scope
 * and all unrelated owner work untouched.
 */
class ContextEngine(
    ownerScope: CoroutineScope,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val initializer: ContextEngineInitializer,
) {
    private val mutableState = MutableStateFlow(
        ContextEngineState(ContextEngineStatus.INITIALIZING, replayEnabled = false),
    )
    private val attempts = AtomicInteger()
    private val closed = AtomicBoolean()
    private val resourcesClosed = AtomicBoolean()
    private var database: AutoCloseable? = null
    private var embedder: AutoCloseable? = null
    private val initializationJob: Job = ownerScope.launch(dispatcher) { initialize() }

    val state: StateFlow<ContextEngineState> = mutableState.asStateFlow()
    val initializationAttempts: Int get() = attempts.get()
    val closeCalls: Int get() = if (closed.get()) 1 else 0

    private suspend fun initialize() {
        attempts.incrementAndGet()
        try {
            database = initializer.openDatabase()
            initializer.validateCorpus()
            initializer.activateCorpus()
            embedder = initializer.openEmbedder()
            initializer.validateVectors()
            mutableState.value = ContextEngineState(ContextEngineStatus.READY, replayEnabled = true)
        } catch (cancelled: CancellationException) {
            closeOwnedResources()
            mutableState.value = ContextEngineState(ContextEngineStatus.CLOSED, replayEnabled = false)
            throw cancelled
        } catch (_: ContextEmbeddingUnavailable) {
            mutableState.value = ContextEngineState(
                ContextEngineStatus.DEGRADED_LEXICAL,
                replayEnabled = true,
            )
        } catch (_: ContextCipherResetRequired) {
            resetRequired()
        } catch (_: ContextPersistenceResetRequired) {
            resetRequired()
        } catch (_: Exception) {
            mutableState.value = ContextEngineState(ContextEngineStatus.FAILED, replayEnabled = false)
        }
    }

    private fun resetRequired() {
        mutableState.value = ContextEngineState(
            ContextEngineStatus.RESET_REQUIRED,
            replayEnabled = false,
        )
    }

    suspend fun close() {
        if (!closed.compareAndSet(false, true)) return
        initializationJob.cancelAndJoin()
        closeOwnedResources()
        mutableState.value = ContextEngineState(ContextEngineStatus.CLOSED, replayEnabled = false)
    }

    private fun closeOwnedResources() {
        if (!resourcesClosed.compareAndSet(false, true)) return
        try {
            embedder?.close()
        } finally {
            database?.close()
        }
    }
}
