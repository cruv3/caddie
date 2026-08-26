package com.caddie.app.diagnostics.snapshot

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeUiSnapshotCoordinatorTest {
    @Test
    fun `worker invocation posts capture and waits for result`() {
        val service = Any()
        val current = AtomicReference(service)
        val scheduler = RecordingScheduler(mainThread = false, onPost = Runnable::run)

        val coordinator =
            coordinator(
                scheduler = scheduler,
                current = current,
                capturer = { """{"snapshotId":"worker"}""" },
            )

        assertEquals("""{"snapshotId":"worker"}""", coordinator.currentJson())
        assertEquals(1, scheduler.postCount)
    }

    @Test
    fun `main thread invocation runs inline without posting`() {
        val service = Any()
        val current = AtomicReference(service)
        val scheduler =
            RecordingScheduler(mainThread = true) {
                throw AssertionError("must not post from main")
            }

        val coordinator =
            coordinator(
                scheduler = scheduler,
                current = current,
                capturer = { """{"snapshotId":"main"}""" },
            )

        assertEquals("""{"snapshotId":"main"}""", coordinator.currentJson())
        assertEquals(0, scheduler.postCount)
    }

    @Test
    fun `timeout and capture failures return stable unavailable schema`() {
        val service = Any()
        val current = AtomicReference(service)

        assertEquals(
            UNAVAILABLE,
            coordinator(
                scheduler = RecordingScheduler(mainThread = false) {},
                current = current,
                capturer = { """{"unexpected":true}""" },
                timeoutMillis = 1,
            ).currentJson(),
        )
        assertEquals(
            UNAVAILABLE,
            coordinator(
                scheduler = RecordingScheduler(mainThread = true, onPost = Runnable::run),
                current = current,
                capturer = { throw IllegalStateException("query failed") },
            ).currentJson(),
        )
        assertEquals(
            UNAVAILABLE,
            coordinator(
                scheduler = RecordingScheduler(mainThread = true, onPost = Runnable::run),
                current = current,
                capturer = { null },
            ).currentJson(),
        )
    }

    @Test
    fun `timed out queued work cannot capture later`() {
        val service = Any()
        val current = AtomicReference(service)
        val queued = AtomicReference<Runnable>()
        val captures = AtomicInteger()
        val scheduler = RecordingScheduler(mainThread = false, onPost = queued::set)

        assertEquals(
            UNAVAILABLE,
            coordinator(
                scheduler = scheduler,
                current = current,
                capturer = {
                    captures.incrementAndGet()
                    "{}"
                },
                timeoutMillis = 1,
            ).currentJson(),
        )

        queued.get().run()

        assertEquals(0, captures.get())
    }

    @Test
    fun `destroyed and rebound connections are rechecked before capture`() {
        val first = EqualConnection(1)
        val rebound = EqualConnection(1)
        val current = AtomicReference(first)
        val captures = AtomicInteger()

        val destroyedScheduler =
            RecordingScheduler(mainThread = false) { runnable ->
                current.set(null)
                runnable.run()
            }
        assertEquals(
            DISCONNECTED,
            coordinator(
                scheduler = destroyedScheduler,
                current = current,
                capturer = {
                    captures.incrementAndGet()
                    "{}"
                },
            ).currentJson(),
        )

        current.set(first)
        val reboundScheduler =
            RecordingScheduler(mainThread = false) { runnable ->
                current.set(rebound)
                runnable.run()
            }
        assertEquals(
            UNAVAILABLE,
            coordinator(
                scheduler = reboundScheduler,
                current = current,
                capturer = {
                    captures.incrementAndGet()
                    "{}"
                },
            ).currentJson(),
        )
        assertEquals(0, captures.get())
    }

    @Test
    fun `post rejection and scheduler failure return unavailable`() {
        val current = AtomicReference(Any())
        val rejectedScheduler =
            object : NativeUiSnapshotCoordinator.MainThreadScheduler {
                override fun isMainThread() = false

                override fun post(runnable: Runnable) = false
            }
        val failingScheduler =
            object : NativeUiSnapshotCoordinator.MainThreadScheduler {
                override fun isMainThread() = false

                override fun post(runnable: Runnable): Boolean {
                    throw IllegalStateException("scheduler stopped")
                }
            }

        assertEquals(UNAVAILABLE, coordinator(rejectedScheduler, current).currentJson())
        assertEquals(UNAVAILABLE, coordinator(failingScheduler, current).currentJson())
    }

    @Test
    fun `interruption cancels queued work and restores interrupt status`() {
        val current = AtomicReference(Any())
        val queued = AtomicReference<Runnable>()
        val captures = AtomicInteger()
        val posted = CountDownLatch(1)
        val scheduler =
            RecordingScheduler(mainThread = false) { runnable ->
                queued.set(runnable)
                posted.countDown()
            }
        val result = AtomicReference<String>()
        val interruptedAfterCall = AtomicReference(false)
        val worker =
            Thread {
                result.set(
                    coordinator(
                        scheduler = scheduler,
                        current = current,
                        capturer = {
                            captures.incrementAndGet()
                            "{}"
                        },
                        timeoutMillis = 5_000,
                    ).currentJson(),
                )
                interruptedAfterCall.set(Thread.currentThread().isInterrupted)
            }

        worker.start()
        assertTrue(posted.await(1, TimeUnit.SECONDS))
        worker.interrupt()
        worker.join(1_000)
        assertFalse(worker.isAlive)
        queued.get().run()

        assertEquals(UNAVAILABLE, result.get())
        assertTrue(interruptedAfterCall.get())
        assertEquals(0, captures.get())
    }

    @Test
    fun `connection scoped source uses referential identity and clear matches identity`() {
        val created = AtomicInteger()
        val sources =
            ConnectionScopedSource<EqualConnection, Any> {
                created.incrementAndGet()
                Any()
            }
        val firstConnection = EqualConnection(1)
        val equalButDistinctConnection = EqualConnection(1)

        val firstSource = sources.sourceFor(firstConnection)

        assertSame(firstSource, sources.sourceFor(firstConnection))
        assertEquals(1, created.get())
        sources.clear(equalButDistinctConnection)
        assertSame(firstSource, sources.sourceFor(firstConnection))

        val replacement = sources.sourceFor(equalButDistinctConnection)
        assertNotSame(firstSource, replacement)
        assertSame(replacement, sources.sourceFor(equalButDistinctConnection))
        assertEquals(2, created.get())

        sources.clear(equalButDistinctConnection)
        assertNotSame(replacement, sources.sourceFor(equalButDistinctConnection))
        assertEquals(3, created.get())
    }

    private fun <C : Any> coordinator(
        scheduler: NativeUiSnapshotCoordinator.MainThreadScheduler,
        current: AtomicReference<C>,
        capturer: (C) -> String? = { "{}" },
        timeoutMillis: Long = 100,
    ): NativeUiSnapshotCoordinator<C> =
        NativeUiSnapshotCoordinator(
            scheduler = scheduler,
            connections = current::get,
            capturer = capturer,
            timeoutMillis = timeoutMillis,
            disconnectedJson = DISCONNECTED,
            unavailableJson = UNAVAILABLE,
        )

    private class RecordingScheduler(
        private val mainThread: Boolean,
        private val onPost: (Runnable) -> Unit,
    ) : NativeUiSnapshotCoordinator.MainThreadScheduler {
        var postCount = 0
            private set

        override fun isMainThread() = mainThread

        override fun post(runnable: Runnable): Boolean {
            postCount++
            onPost(runnable)
            return true
        }
    }

    private data class EqualConnection(val id: Int)

    private companion object {
        const val DISCONNECTED = """{"connected":false,"schemaVersion":1,"windows":[]}"""
        const val UNAVAILABLE =
            """{"schemaVersion":1,"snapshotId":"accessibility-unavailable","capturedAtElapsedRealtimeMillis":0,"completeness":"UNAVAILABLE","windows":[]}"""
    }
}
