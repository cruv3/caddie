package com.caddie.context.replay

import java.util.concurrent.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class VerifiedReplayEngineTest {
    @Test
    fun `eligible replay verifies fresh state before and after its only dispatch`() = runTest {
        val replay = completeReplay()
        val source = QueueObservationSource(
            observation(1, "package" to "com.android.settings"),
            observation(2, "text-visible" to "Network & internet"),
            observation(3, "text-visible" to "Internet"),
            observation(4, "text-visible" to "Internet"),
        )
        val dispatcher = JournaledFakeDispatcher()

        val result = engine(source, dispatcher).run(replay)

        assertEquals(
            ReplayRunResult.Completed(listOf("settings.wifi:settings.wifi@1:0")),
            result,
        )
        assertEquals(1, dispatcher.physicalDispatches)
        assertEquals(listOf(1L, 2L, 3L, 4L), source.observedSequences)
    }

    @Test
    fun `guidance disabled incomplete and unsafe starts never dispatch`() = runTest {
        val cases = listOf(
            completeReplay().copy(startPredicate = null),
            completeReplay().copy(status = ReplayStatus.REJECTED),
            completeReplay().copy(terminalPredicate = null),
        )

        cases.forEach { replay ->
            val dispatcher = JournaledFakeDispatcher()
            val result = engine(
                QueueObservationSource(observation(1, "package" to "wrong.package")),
                dispatcher,
            ).run(replay)

            assertEquals(ReplayStopReason.NOT_ELIGIBLE, (result as ReplayRunResult.Stopped).breadcrumb.outcome)
            assertEquals(0, dispatcher.physicalDispatches)
        }
    }

    @Test
    fun `stale ambiguous environment and precondition failures never dispatch`() = runTest {
        val cases = listOf(
            QueueObservationSource(
                observation(1, "package" to "com.android.settings"),
                observation(1, "text-visible" to "Network & internet"),
            ) to ReplayStopReason.STALE_OBSERVATION,
            QueueObservationSource(
                observation(1, "package" to "com.android.settings"),
                observation(
                    2,
                    "text-visible" to "Network & internet",
                    "text-visible" to "Network & internet",
                ),
            ) to ReplayStopReason.AMBIGUOUS_PREDICATE,
            QueueObservationSource(
                observation(
                    sequence = 1,
                    facts = arrayOf("package" to "com.android.settings"),
                    environment = runtimeEnvironment().copy(localeTag = "en-US"),
                ),
            ) to ReplayStopReason.ENVIRONMENT_MISMATCH,
            QueueObservationSource(
                observation(1, "package" to "com.android.settings"),
                observation(2, "text-visible" to "Something else"),
            ) to ReplayStopReason.PREDICATE_NOT_MATCHED,
        )

        cases.forEach { (source, expectedReason) ->
            val dispatcher = JournaledFakeDispatcher()
            val result = engine(source, dispatcher).run(completeReplay())

            val breadcrumb = (result as ReplayRunResult.Stopped).breadcrumb
            assertEquals(expectedReason, breadcrumb.outcome)
            if (expectedReason != ReplayStopReason.ENVIRONMENT_MISMATCH) {
                assertEquals("settings.wifi:settings.wifi@1:0", breadcrumb.currentExecutionId)
            }
            assertEquals(0, dispatcher.physicalDispatches)
        }
    }

    @Test
    fun `dispatch rejection failure and unknown stop with a breadcrumb and never retry`() = runTest {
        listOf(
            ReplayDispatchOutcome.REJECTED to ReplayStopReason.DISPATCH_REJECTED,
            ReplayDispatchOutcome.FAILED to ReplayStopReason.DISPATCH_FAILED,
            ReplayDispatchOutcome.UNKNOWN to ReplayStopReason.DISPATCH_UNKNOWN,
        ).forEach { (outcome, reason) ->
            val dispatcher = JournaledFakeDispatcher(nextOutcome = outcome)
            val first = engine(startAndPreconditions(), dispatcher).run(completeReplay())
            val second = engine(startAndPreconditions(), dispatcher).run(completeReplay())

            assertEquals(reason, (first as ReplayRunResult.Stopped).breadcrumb.outcome)
            assertEquals(reason, (second as ReplayRunResult.Stopped).breadcrumb.outcome)
            assertEquals(1, dispatcher.physicalDispatches)
        }
    }

    @Test
    fun `journaled success survives re-entry without repeating the physical action`() = runTest {
        val dispatcher = JournaledFakeDispatcher()
        val interruptedAfterDispatch = engine(
            QueueObservationSource(
                observation(1, "package" to "com.android.settings"),
                observation(2, "text-visible" to "Network & internet"),
                observation(3, "text-visible" to "Wrong screen"),
            ),
            dispatcher,
        ).run(completeReplay())

        val resumed = engine(
            QueueObservationSource(
                observation(1, "package" to "com.android.settings"),
                observation(2, "text-visible" to "Network & internet"),
                observation(3, "text-visible" to "Internet"),
                observation(4, "text-visible" to "Internet"),
            ),
            dispatcher,
        ).run(completeReplay())

        assertEquals(ReplayStopReason.PREDICATE_NOT_MATCHED, (interruptedAfterDispatch as ReplayRunResult.Stopped).breadcrumb.outcome)
        assertEquals(ReplayRunResult.Completed(listOf("settings.wifi:settings.wifi@1:0")), resumed)
        assertEquals(1, dispatcher.physicalDispatches)
    }

    @Test
    fun `unknown outcome remains stopped across one hundred re-entry attempts`() = runTest {
        val dispatcher = JournaledFakeDispatcher(nextOutcome = ReplayDispatchOutcome.UNKNOWN)

        repeat(100) {
            val result = engine(startAndPreconditions(), dispatcher).run(completeReplay())
            val breadcrumb = (result as ReplayRunResult.Stopped).breadcrumb
            assertEquals(ReplayStopReason.DISPATCH_UNKNOWN, breadcrumb.outcome)
            assertEquals("settings.wifi:settings.wifi@1:0", breadcrumb.currentExecutionId)
        }

        assertEquals(1, dispatcher.physicalDispatches)
    }

    @Test
    fun `postcondition and terminal failures stop after the completed prefix`() = runTest {
        val postDispatcher = JournaledFakeDispatcher()
        val postFailure = engine(
            QueueObservationSource(
                observation(1, "package" to "com.android.settings"),
                observation(2, "text-visible" to "Network & internet"),
                observation(3, "text-visible" to "Wrong screen"),
            ),
            postDispatcher,
        ).run(completeReplay())
        val terminalDispatcher = JournaledFakeDispatcher()
        val terminalFailure = engine(
            QueueObservationSource(
                observation(1, "package" to "com.android.settings"),
                observation(2, "text-visible" to "Network & internet"),
                observation(3, "text-visible" to "Internet"),
                observation(4, "text-visible" to "Wrong screen"),
            ),
            terminalDispatcher,
        ).run(completeReplay())

        assertEquals(
            ReplayStopReason.PREDICATE_NOT_MATCHED,
            (postFailure as ReplayRunResult.Stopped).breadcrumb.outcome,
        )
        assertEquals(emptyList<String>(), postFailure.breadcrumb.completedStepIds)
        assertEquals(
            listOf("settings.wifi:settings.wifi@1:0"),
            (terminalFailure as ReplayRunResult.Stopped).breadcrumb.completedStepIds,
        )
    }

    @Test
    fun `cancellation propagates and durable unknown prevents a second physical action`() {
        repeat(100) {
            val dispatcher = JournaledFakeDispatcher(cancelDuringFirstDispatch = true)

            assertThrows(CancellationException::class.java) {
                runTest {
                    engine(startAndPreconditions(), dispatcher).run(completeReplay())
                }
            }
            runTest {
                val retry = engine(startAndPreconditions(), dispatcher).run(completeReplay())
                assertEquals(
                    ReplayStopReason.DISPATCH_UNKNOWN,
                    (retry as ReplayRunResult.Stopped).breadcrumb.outcome,
                )
            }
            assertEquals(1, dispatcher.physicalDispatches)
        }
    }

    @Test
    fun `engine propagates job cancellation even if dispatcher returns normally`() {
        val cancellationSwallowingDispatcher = ControlledReplayDispatcher { _, _ ->
            currentCoroutineContext().cancel(CancellationException("cancelled during dispatch"))
            ReplayDispatchOutcome.UNKNOWN
        }

        assertThrows(CancellationException::class.java) {
            runTest {
                engine(startAndPreconditions(), cancellationSwallowingDispatcher).run(completeReplay())
            }
        }
    }

    private fun engine(
        source: ReplayObservationSource,
        dispatcher: ControlledReplayDispatcher,
    ) = VerifiedReplayEngine(source, ReplayVerifier(), dispatcher)

    private fun startAndPreconditions() = QueueObservationSource(
        observation(1, "package" to "com.android.settings"),
        observation(2, "text-visible" to "Network & internet"),
    )

    private fun observation(
        sequence: Long,
        vararg facts: Pair<String, String>,
        environment: ReplayRuntimeEnvironment = runtimeEnvironment(),
    ) = ReplayObservation(
        sequence = sequence,
        environment = environment,
        facts = facts.groupBy({ it.first }, { it.second }),
    )

    private fun runtimeEnvironment() = ReplayRuntimeEnvironment(
        packageName = "com.android.settings",
        appVersion = "36",
        sdkInt = 36,
        buildFingerprint = "google/panther/test",
        localeTag = "de-DE",
    )

    private fun completeReplay() = ReplayTrajectory(
        trajectoryId = "settings.wifi",
        revisionId = "settings.wifi@1",
        skillId = "settings.wifi",
        status = ReplayStatus.ACTIVE,
        environment = ReplayEnvironment(
            packageName = "com.android.settings",
            appVersion = "36",
            minSdk = 36,
            maxSdk = 36,
            buildFingerprint = "google/panther/test",
            localeTag = "de-DE",
        ),
        startPredicate = ReplayPredicate("package", "com.android.settings"),
        steps = listOf(
            ReplayStep(
                toolName = "smartphone_tap_element",
                arguments = mapOf("resourceId" to "android:id/title"),
                selectorFingerprint = "selector-sha256",
                precondition = ReplayPredicate("text-visible", "Network & internet"),
                postcondition = ReplayPredicate("text-visible", "Internet"),
            ),
        ),
        terminalPredicate = ReplayPredicate("text-visible", "Internet"),
        provenance = ReplayProvenance("settings/wifi.json", "a".repeat(64)),
        success = ReplaySuccessMetadata(2, "build-1"),
        legacyContainsCoordinates = false,
    )

    private class QueueObservationSource(
        vararg observations: ReplayObservation,
    ) : ReplayObservationSource {
        private val queue = ArrayDeque(observations.toList())
        val observedSequences = mutableListOf<Long>()

        override suspend fun observe(): ReplayObservation =
            queue.removeFirst().also { observedSequences += it.sequence }
    }

    private class JournaledFakeDispatcher(
        private val nextOutcome: ReplayDispatchOutcome = ReplayDispatchOutcome.SUCCEEDED,
        private val cancelDuringFirstDispatch: Boolean = false,
    ) : ControlledReplayDispatcher {
        private val journal = mutableMapOf<String, ReplayDispatchOutcome>()
        var physicalDispatches = 0

        override suspend fun dispatch(
            executionId: String,
            step: ReplayStep,
        ): ReplayDispatchOutcome {
            journal[executionId]?.let { return it }
            physicalDispatches++
            if (cancelDuringFirstDispatch) {
                journal[executionId] = ReplayDispatchOutcome.UNKNOWN
                throw CancellationException("process stopped during physical action")
            }
            return nextOutcome.also { journal[executionId] = it }
        }
    }
}
