package com.caddie.study.gateway

import com.caddie.study.runtime.coordinator.ArmedTrialCoordinator
import com.caddie.study.runtime.model.CriticalityClass
import com.caddie.study.runtime.model.RuntimeStudyCondition
import com.caddie.study.runtime.model.StepType
import com.caddie.study.runtime.model.StudyStep
import com.caddie.study.runtime.model.TriggerContract
import com.caddie.study.runtime.model.TrialSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Verifies that gateway snapshots and mutations share one exclusive state lock. */
class NativeStudyGatewayTest {
    @Test
    fun `failed test arm rolls mode back to normal`() {
        val gateway = NativeStudyGateway(
            coordinator = ArmedTrialCoordinator(),
            configs = emptyMap(),
            specs = mapOf(testSpec().id to testSpec()),
            readinessProvider = { ready() },
            deviceResetter = StudyDeviceResetter { false },
        )

        assertThrows(IllegalStateException::class.java) {
            gateway.armTestTrial("TEST-1", testSpec().id, "c1_stepwise", false, "1")
        }

        assertEquals("normal", gateway.snapshot()["mode"])
        assertEquals("", gateway.snapshot()["active_participant"])
    }

    @Test
    fun `completed test releases mode before a live study starts`() {
        val coordinator = ArmedTrialCoordinator()
        val spec = testSpec()
        val gateway = NativeStudyGateway(
            coordinator = coordinator,
            configs = emptyMap(),
            specs = mapOf(spec.id to spec),
            readinessProvider = { ready() },
            deviceResetter = StudyDeviceResetter { true },
        )
        gateway.armTestTrial("TEST-1", spec.id, "c1_stepwise", false, "1")
        val claim = requireNotNull(coordinator.routeAndClaim("Jarvis, Testaufgabe").claim)
        coordinator.finishSuccess(claim)

        val snapshot = gateway.setMode("live")

        assertEquals("live", snapshot["mode"])
        assertEquals("", snapshot["active_participant"])
    }

    @Test
    fun liveModePreventsUnarmedInputFromFallingThroughToNormalAgent() {
        val coordinator = ArmedTrialCoordinator()
        val gateway = NativeStudyGateway(
            coordinator = coordinator,
            configs = emptyMap(),
            specs = emptyMap(),
        )

        gateway.setMode("live")
        assertEquals(
            ArmedTrialCoordinator.CoordinatorDecision.RETRY,
            coordinator.routeAndClaim("study input").decision,
        )

        gateway.setMode("normal")
        assertEquals(
            ArmedTrialCoordinator.CoordinatorDecision.PASS_THROUGH,
            coordinator.routeAndClaim("normal input").decision,
        )
    }

    @Test
    fun closingLiveGatewayRestoresNormalAgentRouting() {
        val coordinator = ArmedTrialCoordinator()
        val gateway = NativeStudyGateway(
            coordinator = coordinator,
            configs = emptyMap(),
            specs = emptyMap(),
        )

        gateway.setMode("live")
        gateway.close()

        assertEquals(
            ArmedTrialCoordinator.CoordinatorDecision.PASS_THROUGH,
            coordinator.routeAndClaim("normal input").decision,
        )
    }

    @Test
    fun `reset only clears runtime state after the physical device reset succeeds`() {
        val coordinator = ArmedTrialCoordinator()
        var resetSucceeds = false
        val gateway = NativeStudyGateway(
            coordinator = coordinator,
            configs = emptyMap(),
            specs = emptyMap(),
            deviceResetter = StudyDeviceResetter { resetSucceeds },
        )

        assertFalse(gateway.resetDevice())

        resetSucceeds = true
        assertTrue(gateway.resetDevice())
    }

    @Test
    fun `training start prepares the phone and stays in normal mode`() {
        var resets = 0
        var preparations = 0
        val gateway = NativeStudyGateway(
            coordinator = ArmedTrialCoordinator(),
            configs = emptyMap(),
            specs = emptyMap(),
            deviceResetter = StudyDeviceResetter { resets += 1; true },
            normalPracticePreparer = { preparations += 1; true },
        )

        assertTrue(gateway.startTraining())
        assertEquals(0, resets)
        assertEquals(1, preparations)
        assertEquals("normal", gateway.snapshot()["mode"])
    }

    @Test
    fun `abort and reset cancel any pending participant confirmation`() {
        val reasons = mutableListOf<String>()
        var stops = 0
        var waits = 0
        val gateway = NativeStudyGateway(
            coordinator = ArmedTrialCoordinator(),
            configs = emptyMap(),
            specs = emptyMap(),
            cancelPendingConfirmation = { reasons += it },
            stopActiveRun = { stops += 1; true },
            awaitRuntimeIdle = { waits += 1; true },
        )
        gateway.setMode("live")

        gateway.abortTrial("investigator abort")
        gateway.resetDevice()

        assertEquals(listOf("investigator abort", "study device reset"), reasons)
        assertEquals(1, stops)
        assertEquals(1, waits)
    }


    @Test
    fun `abort cleans up an active portal session after process state was lost`() {
        val gateway = NativeStudyGateway(
            coordinator = ArmedTrialCoordinator(),
            configs = emptyMap(),
            specs = emptyMap(),
        )
        gateway.setMode("live")

        val snapshot = gateway.abortTrial("process_restarted")

        assertEquals("normal", snapshot["mode"])
        assertEquals("aborted", (snapshot["trial"] as Map<*, *>)["state"])
    }

    @Test
    fun preflightRequiresOnlyCapabilitiesUsedByDeterministicStudyExecution() {
        var readiness = NativeStudyReadiness(
            accessibilityConnected = true,
            modelGatewayConnected = true,
            mcpConnected = true,
            studySpecsLoaded = true,
        )
        val gateway = NativeStudyGateway(
            coordinator = ArmedTrialCoordinator(),
            configs = emptyMap(),
            specs = emptyMap(),
            readinessProvider = { readiness },
        )
        gateway.setMode("live")

        assertTrue(gateway.preflight())

        readiness = readiness.copy(accessibilityConnected = false)
        assertFalse(gateway.preflight())
        readiness = readiness.copy(accessibilityConnected = true, modelGatewayConnected = false)
        assertTrue(gateway.preflight())
        readiness = readiness.copy(modelGatewayConnected = true, mcpConnected = false)
        assertTrue(gateway.preflight())
        readiness = readiness.copy(mcpConnected = true, studySpecsLoaded = false)
        assertFalse(gateway.preflight())
    }

    @Test
    fun `aborted trial can pass preflight after live mode is restored for retry`() {
        val gateway = NativeStudyGateway(
            coordinator = ArmedTrialCoordinator(),
            configs = emptyMap(),
            specs = emptyMap(),
            readinessProvider = { ready() },
        )
        gateway.setMode("live")
        gateway.abortTrial("technical failure")

        gateway.setMode("live")

        assertEquals(true, gateway.snapshot()["phone_ready"])
        assertTrue(gateway.preflight())
    }

    @Test
    fun resetWaitsForTheExclusiveMutationOwner() {
        val gateway = NativeStudyGateway(
            coordinator = ArmedTrialCoordinator(),
            configs = emptyMap(),
            specs = emptyMap(),
        )
        val executor = Executors.newSingleThreadExecutor()
        val started = CountDownLatch(1)
        val completed = CountDownLatch(1)

        assertTrue(gateway.tryExclusive())
        try {
            executor.submit {
                started.countDown()
                gateway.resetDevice()
                completed.countDown()
            }
            assertTrue(started.await(1, TimeUnit.SECONDS))
            assertFalse(completed.await(100, TimeUnit.MILLISECONDS))
        } finally {
            gateway.releaseExclusive()
            assertTrue(completed.await(1, TimeUnit.SECONDS))
            executor.shutdownNow()
        }
    }

    @Test
    fun `active normal run blocks portal reset and arming`() {
        var resets = 0
        val gateway = NativeStudyGateway(
            coordinator = ArmedTrialCoordinator(),
            configs = emptyMap(),
            specs = emptyMap(),
            deviceResetter = StudyDeviceResetter { resets += 1; true },
            runtimeIdle = { false },
        )
        gateway.setMode("live")

        assertFalse(gateway.preflight())
        assertFalse(gateway.resetDevice())
        assertThrows(IllegalStateException::class.java) {
            gateway.armNextTrial(emptyMap())
        }
        assertEquals(0, resets)
    }

    @Test
    fun `live assignment carries participant briefing for every task`() {
        val loader = com.caddie.study.runtime.spec.SpecLoader()
        val specs = java.io.File("src/study/assets/study-specs")
            .listFiles { file -> file.extension == "json" }
            .orEmpty()
            .map { file -> loader.parse(file.readText()) }
            .associateBy { it.id }
        val gateway = NativeStudyGateway(
            coordinator = ArmedTrialCoordinator(),
            configs = emptyMap(),
            specs = specs,
        )

        val assignment = requireNotNull(gateway.nextAssignment("P19"))
        @Suppress("UNCHECKED_CAST")
        val tasks = assignment.getValue("tasks") as List<Map<String, Any>>

        assertEquals(6, tasks.size)
        for (task in tasks) {
            @Suppress("UNCHECKED_CAST")
            val briefing = task["briefing"] as Map<String, Any>
            assertTrue((briefing["situation"] as String).isNotBlank())
            assertEquals(2, (briefing["apps"] as List<*>).size)
            assertTrue((briefing["goal"] as String).isNotBlank())
            assertTrue((briefing["preparation"] as String).isNotBlank())
            assertTrue((briefing["reference_values"] as List<*>).isNotEmpty())
        }
        assertEquals(
            tasks.first()["briefing"],
            gateway.participantTaskBriefing(tasks.first().getValue("id") as String),
        )
    }

    private fun ready() = NativeStudyReadiness(
        accessibilityConnected = true,
        modelGatewayConnected = false,
        mcpConnected = false,
        studySpecsLoaded = true,
    )

    private fun testSpec() = TrialSpec(
        version = "v1",
        id = "task_test",
        instructionDe = "Testaufgabe",
        criticality = CriticalityClass.LOW,
        steps = listOf(StudyStep("s1", "click", "Test", StepType.NORMAL)),
        trigger = TriggerContract(
            referencePhrases = listOf("Testaufgabe"),
            requiredConcepts = listOf(listOf("testaufgabe")),
        ),
    )
}
