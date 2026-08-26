package com.caddie.app.diagnostics.snapshot

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Captures a UI snapshot on the main thread while guarding connection changes. */
class NativeUiSnapshotCoordinator<C : Any>(
    private val scheduler: MainThreadScheduler,
    private val connections: () -> C?,
    private val capturer: (C) -> String?,
    private val timeoutMillis: Long,
    private val disconnectedJson: String,
    private val unavailableJson: String,
) {
    /** Provides the minimal main-thread operations required for snapshot capture. */
    interface MainThreadScheduler {
        fun isMainThread(): Boolean

        fun post(runnable: Runnable): Boolean
    }

    fun currentJson(): String {
        val expectedConnection = connections() ?: return disconnectedJson
        if (scheduler.isMainThread()) {
            return captureOnMain(expectedConnection)
        }

        val result = AtomicReference(unavailableJson)
        val state = AtomicInteger(PENDING)
        val finished = CountDownLatch(1)
        return try {
            if (
                !scheduler.post {
                    if (!state.compareAndSet(PENDING, STARTED)) {
                        return@post
                    }
                    try {
                        result.set(captureOnMain(expectedConnection))
                    } finally {
                        finished.countDown()
                    }
                }
            ) {
                state.compareAndSet(PENDING, CANCELLED)
                unavailableJson
            } else if (!finished.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
                state.compareAndSet(PENDING, CANCELLED)
                unavailableJson
            } else {
                result.get()
            }
        } catch (_: InterruptedException) {
            state.compareAndSet(PENDING, CANCELLED)
            Thread.currentThread().interrupt()
            unavailableJson
        } catch (_: RuntimeException) {
            state.compareAndSet(PENDING, CANCELLED)
            unavailableJson
        }
    }

    private fun captureOnMain(expectedConnection: C): String {
        val currentConnection = connections() ?: return disconnectedJson
        if (currentConnection !== expectedConnection) {
            return unavailableJson
        }
        return try {
            capturer(currentConnection) ?: unavailableJson
        } catch (_: RuntimeException) {
            unavailableJson
        }
    }

    private companion object {
        const val PENDING = 0
        const val STARTED = 1
        const val CANCELLED = 2
    }
}
