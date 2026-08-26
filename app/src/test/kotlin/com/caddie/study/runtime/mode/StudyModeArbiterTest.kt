package com.caddie.study.runtime.mode

import com.caddie.study.gateway.NativeStudyGateway
import com.caddie.study.runtime.coordinator.ArmedTrialCoordinator
import com.caddie.study.runtime.model.CriticalityClass
import com.caddie.study.runtime.model.RuntimeStudyCondition
import com.caddie.study.runtime.model.StepType
import com.caddie.study.runtime.model.StudyStep
import com.caddie.study.runtime.model.TrialSpec
import com.caddie.study.runtime.model.TriggerContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Verifies one fail-closed mode across the app UI and study portal lifecycles. */
class StudyModeArbiterTest {
    @Test
    fun `starting and closing portal cannot clear app-owned test mode`() {
        val coordinator = ArmedTrialCoordinator()
        val arbiter = StudyModeArbiter(coordinator)
        val appOwner = Any()
        arbiter.set(appOwner, StudyModeArbiter.Mode.TEST)

        val gateway = NativeStudyGateway(
            coordinator = coordinator,
            configs = emptyMap(),
            specs = emptyMap(),
            modeArbiter = arbiter,
        )
        gateway.close()

        assertEquals(StudyModeArbiter.Mode.TEST, arbiter.current())
        assertEquals(
            ArmedTrialCoordinator.CoordinatorDecision.RETRY,
            coordinator.routeAndClaim("study input").decision,
        )
    }

    @Test
    fun `portal test mode also fails closed before arming`() {
        val coordinator = ArmedTrialCoordinator()
        val arbiter = StudyModeArbiter(coordinator)
        val gateway = NativeStudyGateway(
            coordinator = coordinator,
            configs = emptyMap(),
            specs = emptyMap(),
            modeArbiter = arbiter,
        )

        gateway.setMode("test")

        assertEquals(StudyModeArbiter.Mode.TEST, arbiter.current())
        assertEquals(
            ArmedTrialCoordinator.CoordinatorDecision.RETRY,
            coordinator.routeAndClaim("study input").decision,
        )
    }

    @Test
    fun `portal abort cannot clear app-owned test mode`() {
        val coordinator = ArmedTrialCoordinator()
        val arbiter = StudyModeArbiter(coordinator)
        val appOwner = Any()
        val gateway = NativeStudyGateway(
            coordinator = coordinator,
            configs = emptyMap(),
            specs = emptyMap(),
            modeArbiter = arbiter,
        )
        arbiter.set(appOwner, StudyModeArbiter.Mode.TEST)

        gateway.abortTrial("portal cleanup")

        assertEquals(StudyModeArbiter.Mode.TEST, arbiter.current())
        assertEquals(
            ArmedTrialCoordinator.CoordinatorDecision.RETRY,
            coordinator.routeAndClaim("study input").decision,
        )
    }

    @Test
    fun `local control cannot clear portal-owned live mode`() {
        val coordinator = ArmedTrialCoordinator()
        val arbiter = StudyModeArbiter(coordinator)
        val portal = Any()
        val local = Any()
        arbiter.set(portal, StudyModeArbiter.Mode.LIVE, StudyModeArbiter.Priority.PORTAL)

        assertThrows(IllegalStateException::class.java) {
            arbiter.set(local, StudyModeArbiter.Mode.NORMAL)
        }
        assertEquals(StudyModeArbiter.Mode.LIVE, arbiter.current())
        assertEquals(
            ArmedTrialCoordinator.CoordinatorDecision.RETRY,
            coordinator.routeAndClaim("study input").decision,
        )
    }

    @Test
    fun `portal cannot seize mode while local trial is active`() {
        val coordinator = ArmedTrialCoordinator()
        val arbiter = StudyModeArbiter(coordinator)
        val local = Any()
        val portal = Any()
        val spec = TrialSpec(
            version = "1",
            id = "task",
            instructionDe = "Öffne App",
            criticality = CriticalityClass.LOW,
            steps = listOf(StudyStep("open", "open app", "Öffnen", StepType.NORMAL)),
            trigger = TriggerContract(
                referencePhrases = listOf("Öffne App"),
                requiredConcepts = listOf(listOf("öffne")),
            ),
        )
        arbiter.set(local, StudyModeArbiter.Mode.TEST)
        coordinator.arm(
            ArmedTrialCoordinator.ArmedTrialConfig(
                participantId = "TEST",
                trialIndex = 0,
                taskId = spec.id,
                condition = RuntimeStudyCondition.STEPWISE,
                injectError = false,
            ),
            spec,
        )

        assertThrows(IllegalStateException::class.java) {
            arbiter.set(portal, StudyModeArbiter.Mode.LIVE, StudyModeArbiter.Priority.PORTAL)
        }
        assertEquals(StudyModeArbiter.Mode.TEST, arbiter.current())
    }
}
