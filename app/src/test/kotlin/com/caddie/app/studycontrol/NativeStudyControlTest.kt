package com.caddie.app.studycontrol

import com.caddie.study.gateway.StudyDeviceResetter
import com.caddie.study.runtime.coordinator.ArmedTrialCoordinator
import com.caddie.study.runtime.model.CriticalityClass
import com.caddie.study.runtime.model.RuntimeStudyCondition
import com.caddie.study.runtime.model.StepType
import com.caddie.study.runtime.model.StudyStep
import com.caddie.study.runtime.model.TriggerContract
import com.caddie.study.runtime.model.TrialSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies the explicit Study Control flow retained from the main branch. */
class NativeStudyControlTest {
    private val spec = TrialSpec(
        version = "v1",
        id = "task_maps_messenger",
        instructionDe = "Ermittle die Ankunftszeit und teile sie Anna.",
        criticality = CriticalityClass.LOW,
        steps = listOf(StudyStep("open", "open com.example", "Öffne App", StepType.NORMAL)),
        errorSteps = emptyList(),
        trigger = TriggerContract(
            referencePhrases = listOf("Ermittle die Ankunftszeit und teile sie Anna"),
            requiredConcepts = listOf(listOf("ankunftszeit"), listOf("anna")),
            wakeWords = listOf("jarvis"),
        ),
    )

    @Test
    fun `test mode blocks normal agent before a trial is armed`() {
        val coordinator = ArmedTrialCoordinator()
        val control = NativeStudyControl(coordinator, mapOf(spec.id to spec))

        control.setMode(NativeStudyControl.Mode.TEST)

        assertEquals(
            ArmedTrialCoordinator.CoordinatorDecision.RETRY,
            coordinator.routeAndClaim("anything").decision,
        )
    }

    @Test
    fun `arming selected condition creates one deterministic test claim`() {
        val coordinator = ArmedTrialCoordinator()
        var resets = 0
        val control = NativeStudyControl(
            coordinator,
            mapOf(spec.id to spec),
            StudyDeviceResetter { resets += 1; true },
        )
        control.setMode(NativeStudyControl.Mode.TEST)

        val armed = control.armTest(
            spec.id,
            RuntimeStudyCondition.STEPWISE,
            injectError = true,
        )
        val routed = coordinator.routeAndClaim("Jarvis, ermittle die Ankunftszeit und teile sie Anna")

        assertEquals(1, resets)
        assertEquals(ArmedTrialCoordinator.ArmedState.ARMED, armed.trial.state)
        assertEquals(ArmedTrialCoordinator.CoordinatorDecision.CLAIMED, routed.decision)
        assertEquals(ArmedTrialCoordinator.StudyRunScope.TEST, routed.claim?.config?.runScope)
        assertTrue(routed.claim?.config?.injectError == true)
    }

    @Test
    fun `normal mode restores normal routing after a terminal test trial`() {
        val coordinator = ArmedTrialCoordinator()
        val control = NativeStudyControl(coordinator, mapOf(spec.id to spec))
        control.setMode(NativeStudyControl.Mode.TEST)
        control.armTest(spec.id, RuntimeStudyCondition.STEPWISE, false)
        control.abort()

        control.setMode(NativeStudyControl.Mode.NORMAL)

        assertEquals(
            ArmedTrialCoordinator.CoordinatorDecision.PASS_THROUGH,
            coordinator.routeAndClaim("normal task").decision,
        )
    }

    @Test
    fun `mode changes remain blocked while a trial is active`() {
        val control = NativeStudyControl(
            ArmedTrialCoordinator(),
            mapOf(spec.id to spec),
        )
        control.setMode(NativeStudyControl.Mode.TEST)
        control.armTest(spec.id, RuntimeStudyCondition.STEPWISE, false)

        assertThrows(IllegalStateException::class.java) {
            control.setMode(NativeStudyControl.Mode.NORMAL)
        }
    }

    @Test
    fun `active trial states are monitored until terminal`() {
        val coordinator = ArmedTrialCoordinator()
        val control = NativeStudyControl(coordinator, mapOf(spec.id to spec))
        control.setMode(NativeStudyControl.Mode.TEST)
        val armed = control.armTest(spec.id, RuntimeStudyCondition.STEPWISE, false)

        assertTrue(shouldMonitorTrial(armed))
        control.abort()
        assertTrue(!shouldMonitorTrial(control.snapshot()))
    }

    @Test
    fun `abort stops the active executor and pending confirmation`() {
        val coordinator = ArmedTrialCoordinator()
        var stopped = 0
        val cancellations = mutableListOf<String>()
        val control = NativeStudyControl(
            coordinator = coordinator,
            specs = mapOf(spec.id to spec),
            stopActiveRun = { stopped += 1; true },
            cancelPendingConfirmation = { cancellations += it },
        )
        control.setMode(NativeStudyControl.Mode.TEST)
        control.armTest(spec.id, RuntimeStudyCondition.STEPWISE, false)
        coordinator.routeAndClaim(spec.instructionDe)

        val aborted = control.abort()

        assertEquals(1, stopped)
        assertEquals(listOf("experimenter_abort"), cancellations)
        assertEquals(ArmedTrialCoordinator.ArmedState.ABORTED, aborted.trial.state)
    }

    @Test
    fun `active normal run blocks reset and test arming`() {
        val coordinator = ArmedTrialCoordinator()
        val control = NativeStudyControl(
            coordinator = coordinator,
            specs = mapOf(spec.id to spec),
            runtimeIdle = { false },
        )
        control.setMode(NativeStudyControl.Mode.TEST)

        assertThrows(IllegalStateException::class.java) { control.reset() }
        assertThrows(IllegalStateException::class.java) {
            control.armTest(spec.id, RuntimeStudyCondition.STEPWISE, false)
        }
        assertEquals(null, coordinator.status().state)
    }

    @Test
    fun `runtime reservation blocks reset and arming before device mutation`() {
        val coordinator = ArmedTrialCoordinator()
        var resets = 0
        val control = NativeStudyControl(
            coordinator = coordinator,
            specs = mapOf(spec.id to spec),
            deviceResetter = StudyDeviceResetter { resets += 1; true },
            reserveRuntime = { null },
        )
        control.setMode(NativeStudyControl.Mode.TEST)

        assertThrows(IllegalStateException::class.java) { control.reset() }
        assertThrows(IllegalStateException::class.java) {
            control.armTest(spec.id, RuntimeStudyCondition.STEPWISE, false)
        }
        assertEquals(0, resets)
    }
}
