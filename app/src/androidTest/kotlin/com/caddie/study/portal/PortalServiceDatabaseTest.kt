package com.caddie.study.portal

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caddie.study.gateway.StudyGateway
import com.caddie.study.runtime.model.DetectionStage
import com.caddie.study.runtime.model.ErrorObservation
import com.caddie.study.runtime.model.EvidenceType
import com.caddie.study.runtime.audit.StudyRuntimeEvent
import com.caddie.study.store.StudyRoomDatabase
import com.caddie.study.store.StudyStore
import com.caddie.study.store.RevisionConflict
import com.caddie.study.store.entity.StudyResponseEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Exercises portal orchestration with the real native Room store. */
@RunWith(AndroidJUnit4::class)
class PortalServiceDatabaseTest {

    private lateinit var database: StudyRoomDatabase
    private lateinit var store: StudyStore
    private lateinit var gateway: FakeStudyGateway
    private lateinit var service: PortalService

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            StudyRoomDatabase::class.java,
        ).build()
        store = StudyStore(database)
        gateway = FakeStudyGateway()
        service = PortalService(store, gateway)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun createLiveSessionPersistsTheGatewayAssignmentAsJson() = runTest {
        val sessionId = service.createLiveSession("P01")

        val assignment = service.getSessionAssignment(sessionId)

        assertTrue(assignment["task_order"] == listOf("task_1", "task_2"))
    }

    @Test
    fun participantCompletesTrainingAndReceivesTheFirstTaskCardInOneAction() = runTest {
        val sessionId = service.createLiveSession("01")
        service.transition(sessionId, "start_consent", 0, actor = "system")
        service.recordAndConfirmLiveConsent(
            sessionId,
            "Participant One",
            PortalService.LIVE_CONSENT_ACKNOWLEDGEMENTS.toList(),
        )

        val state = service.continueParticipantFlow(sessionId, expectedRevision = 2)

        assertEquals("P01", state.session.participant_id)
        assertEquals("task_card", state.session.workflow_state)
        assertEquals(0, state.session.current_trial_index)
        assertTrue(gateway.armed)
    }

    @Test
    fun completedNativeTrialAutomaticallyOpensTheParticipantQuestionnaire() = runTest {
        val sessionId = service.createLiveSession("P01")
        service.transition(sessionId, "start_consent", 0, actor = "system")
        service.recordAndConfirmLiveConsent(
            sessionId,
            "Participant One",
            PortalService.LIVE_CONSENT_ACKNOWLEDGEMENTS.toList(),
        )
        val taskCard = service.continueParticipantFlow(sessionId, expectedRevision = 2)
        service.transition(
            sessionId,
            "participant_started",
            taskCard.session.workflow_revision,
            actor = "participant",
        )
        store.recordRuntimeEvent(
            StudyRuntimeEvent(
                studyRunId = sessionId.toString(),
                participantId = "P01",
                trialIndex = 0,
                taskId = "task_1",
                eventType = "trial_completed",
                details = mapOf("outcome" to "success"),
            ),
        )

        val state = service.reconcileParticipantFlow(sessionId)

        assertEquals("task_questionnaire", state.session.workflow_state)
        assertEquals("task", state.instrument?.id)
        assertEquals("no_error_assigned", store.researchExport().markers.single().outcome)
    }

    @Test
    fun failedArmEntersTechnicalHoldAndRetriesThePersistedClaimWithoutReleasingEarly() = runTest {
        val sessionId = service.createLiveSession("P01")
        service.transition(sessionId, "start_consent", 0, actor = "system")
        service.recordAndConfirmLiveConsent(
            sessionId,
            "Participant One",
            PortalService.LIVE_CONSENT_ACKNOWLEDGEMENTS.toList(),
        )
        gateway.armFails = true

        val held = service.continueParticipantFlow(sessionId, expectedRevision = 2)

        assertEquals("technical_hold", held.session.workflow_state)
        assertEquals(0, held.session.current_trial_index)
        assertTrue(!gateway.armed)

        gateway.armFails = false
        val recovered = service.continueParticipantFlow(
            sessionId,
            expectedRevision = held.session.workflow_revision,
        )

        assertEquals("task_card", recovered.session.workflow_state)
        assertTrue(gateway.armed)
    }

    @Test
    fun mismatchedArmedRuntimeNeverReleasesTheParticipantTaskCard() = runTest {
        val sessionId = service.createLiveSession("P01")
        service.transition(sessionId, "start_consent", 0, actor = "system")
        service.recordAndConfirmLiveConsent(
            sessionId,
            "Participant One",
            PortalService.LIVE_CONSENT_ACKNOWLEDGEMENTS.toList(),
        )
        gateway.armSnapshotMismatch = true

        val held = service.continueParticipantFlow(sessionId, expectedRevision = 2)

        assertEquals("technical_hold", held.session.workflow_state)
        assertEquals("trial_ready", held.session.resume_state)
    }

    @Test
    fun assignedErrorMarkerDoesNotClaimWhetherTheParticipantDetectedIt() = runTest {
        gateway.errorTasks = listOf("task_1")
        val sessionId = service.createLiveSession("P01")
        service.transition(sessionId, "start_consent", 0, actor = "system")
        service.recordAndConfirmLiveConsent(
            sessionId,
            "Participant One",
            PortalService.LIVE_CONSENT_ACKNOWLEDGEMENTS.toList(),
        )
        val taskCard = service.continueParticipantFlow(sessionId, expectedRevision = 2)
        service.transition(
            sessionId,
            "participant_started",
            taskCard.session.workflow_revision,
            actor = "participant",
        )
        store.recordRuntimeEvent(
            StudyRuntimeEvent(
                studyRunId = sessionId.toString(),
                participantId = "P01",
                trialIndex = 0,
                taskId = "task_1",
                eventType = "participant_intervention",
                details = mapOf("source" to "touch"),
            ),
        )
        store.recordRuntimeEvent(
            StudyRuntimeEvent(
                studyRunId = sessionId.toString(),
                participantId = "P01",
                trialIndex = 0,
                taskId = "task_1",
                eventType = "trial_completed",
                details = mapOf("outcome" to "success"),
            ),
        )

        service.reconcileParticipantFlow(sessionId)
        val export = store.researchExport()

        assertTrue(export.observations.isEmpty())
        assertEquals("error_assigned", export.markers.single().outcome)
        assertTrue(export.auditEvents.any { it.eventType == "participant_intervention" })
    }

    @Test
    fun failedNativeTrialCanBeRetriedByTheParticipantWithoutDeletingTheSession() = runTest {
        val sessionId = service.createLiveSession("P01")
        service.transition(sessionId, "start_consent", 0, actor = "system")
        service.recordAndConfirmLiveConsent(
            sessionId,
            "Participant One",
            PortalService.LIVE_CONSENT_ACKNOWLEDGEMENTS.toList(),
        )
        val taskCard = service.continueParticipantFlow(sessionId, expectedRevision = 2)
        service.transition(
            sessionId,
            "participant_started",
            taskCard.session.workflow_revision,
            actor = "participant",
        )
        store.recordRuntimeEvent(
            StudyRuntimeEvent(
                studyRunId = sessionId.toString(),
                participantId = "P01",
                trialIndex = 0,
                taskId = "task_1",
                eventType = "trial_completed",
                details = mapOf("outcome" to "verification_failed"),
            ),
        )
        val resetCallsBeforeReconcile = gateway.resetCalls

        val held = service.reconcileParticipantFlow(sessionId)

        assertEquals("technical_hold", held.session.workflow_state)
        assertEquals("trial_running", held.session.resume_state)
        assertEquals(resetCallsBeforeReconcile, gateway.resetCalls)
        assertTrue(store.researchExport().markers.isEmpty())
        val retried = service.continueParticipantFlow(
            sessionId,
            held.session.workflow_revision,
        )

        assertEquals("task_card", retried.session.workflow_state)
        assertEquals(0, retried.session.current_trial_index)
        assertEquals("P01", retried.session.participant_id)
        assertEquals(0, gateway.lastArmedAssignment?.get("trial_index"))
        assertTrue(gateway.armed)
    }

    @Test
    fun participantCanStartTrainingWithoutAdvancingTheStudyWorkflow() = runTest {
        val sessionId = service.createLiveSession("01")
        service.transition(sessionId, "start_consent", 0, actor = "system")
        service.recordAndConfirmLiveConsent(
            sessionId,
            "Participant One",
            PortalService.LIVE_CONSENT_ACKNOWLEDGEMENTS.toList(),
        )

        val state = service.startParticipantTraining(sessionId, expectedRevision = 2)

        assertEquals("training", state.session.workflow_state)
        assertEquals(2, state.session.workflow_revision)
        assertEquals(1, gateway.trainingStarts)
        assertTrue(!gateway.armed)
    }

    @Test
    fun uncorrectedControlledErrorAutomaticallyOpensTheQuestionnaire() = runTest {
        gateway.errorTasks = listOf("task_1")
        val sessionId = service.createLiveSession("P01")
        service.transition(sessionId, "start_consent", 0, actor = "system")
        service.recordAndConfirmLiveConsent(
            sessionId,
            "Participant One",
            PortalService.LIVE_CONSENT_ACKNOWLEDGEMENTS.toList(),
        )
        val taskCard = service.continueParticipantFlow(sessionId, expectedRevision = 2)
        service.transition(
            sessionId,
            "participant_started",
            taskCard.session.workflow_revision,
            actor = "participant",
        )
        store.recordRuntimeEvent(
            StudyRuntimeEvent(
                studyRunId = sessionId.toString(),
                participantId = "P01",
                trialIndex = 0,
                taskId = "task_1",
                eventType = "trial_completed",
                details = mapOf(
                    "outcome" to "error_injected",
                    "errors_injected" to 1,
                ),
            ),
        )

        val state = service.reconcileParticipantFlow(sessionId)

        assertEquals("task_questionnaire", state.session.workflow_state)
        assertEquals("task", state.instrument?.id)
        assertEquals("error_assigned", store.researchExport().markers.single().outcome)
    }

    @Test
    fun legacyControlledErrorHoldContinuesWithoutRepeatingTheTrial() = runTest {
        gateway.errorTasks = listOf("task_1")
        val sessionId = service.createLiveSession("P01")
        service.transition(sessionId, "start_consent", 0, actor = "system")
        service.recordAndConfirmLiveConsent(
            sessionId,
            "Participant One",
            PortalService.LIVE_CONSENT_ACKNOWLEDGEMENTS.toList(),
        )
        val taskCard = service.continueParticipantFlow(sessionId, expectedRevision = 2)
        val running = service.transition(
            sessionId,
            "participant_started",
            taskCard.session.workflow_revision,
            actor = "participant",
        )
        store.recordRuntimeEvent(
            StudyRuntimeEvent(
                studyRunId = sessionId.toString(),
                participantId = "P01",
                trialIndex = 0,
                taskId = "task_1",
                eventType = "trial_completed",
                details = mapOf(
                    "outcome" to "verification_failed",
                    "errors_injected" to 1,
                ),
            ),
        )
        val held = service.transition(
            sessionId,
            "technical_hold",
            running.workflowRevision,
            actor = "system",
        )
        val armedBeforeContinue = gateway.armCalls
        val resetsBeforeContinue = gateway.resetCalls

        val continued = service.continueParticipantFlow(
            sessionId,
            held.workflowRevision,
        )

        assertEquals("task_questionnaire", continued.session.workflow_state)
        assertEquals(armedBeforeContinue, gateway.armCalls)
        assertEquals(resetsBeforeContinue, gateway.resetCalls)
        assertEquals(0, continued.session.current_trial_index)
    }

    @Test
    fun investigatorRetryRearmsTheSameFailedTrialFromAResetPhone() = runTest {
        val sessionId = service.createLiveSession("P01")
        service.transition(sessionId, "start_consent", 0, actor = "system")
        service.recordAndConfirmLiveConsent(
            sessionId,
            "Participant One",
            PortalService.LIVE_CONSENT_ACKNOWLEDGEMENTS.toList(),
        )
        val taskCard = service.continueParticipantFlow(sessionId, expectedRevision = 2)
        service.transition(
            sessionId,
            "participant_started",
            taskCard.session.workflow_revision,
            actor = "participant",
        )
        store.recordRuntimeEvent(
            StudyRuntimeEvent(
                studyRunId = sessionId.toString(),
                participantId = "P01",
                trialIndex = 0,
                taskId = "task_1",
                eventType = "trial_completed",
                details = mapOf("outcome" to "technical_failure"),
            ),
        )
        val held = service.reconcileParticipantFlow(sessionId)
        val resetCallsBeforeRetry = gateway.resetCalls
        val abortCallsBeforeRetry = gateway.abortCalls

        val retried = service.retryFailedTrial(
            sessionId,
            held.session.workflow_revision,
        )

        val session = store.getSession(sessionId)
        assertEquals("trial_ready", session.workflowState)
        assertEquals(0, session.currentTrialIndex)
        assertEquals(0, session.preparedTrialIndex)
        assertEquals(0, retried.assignment["trial_index"])
        assertEquals(0, gateway.lastArmedAssignment?.get("trial_index"))
        assertEquals(abortCallsBeforeRetry + 1, gateway.abortCalls)
        assertEquals(resetCallsBeforeRetry + 1, gateway.resetCalls)
        assertTrue(gateway.armed)
    }

    @Test
    fun investigatorRetryRearmsPrematurelyFinishedTrialAtTheSameIndex() = runTest {
        val sessionId = service.createLiveSession("P01")
        service.transition(sessionId, "start_consent", 0, actor = "system")
        service.recordAndConfirmLiveConsent(
            sessionId,
            "Participant One",
            PortalService.LIVE_CONSENT_ACKNOWLEDGEMENTS.toList(),
        )
        val taskCard = service.continueParticipantFlow(sessionId, expectedRevision = 2)
        val running = service.transition(
            sessionId,
            "participant_started",
            taskCard.session.workflow_revision,
            actor = "participant",
        )
        val observation = service.transition(
            sessionId,
            "trial_finished",
            running.workflowRevision,
            actor = "investigator",
        )
        store.recordRuntimeEvent(
            StudyRuntimeEvent(
                studyRunId = sessionId.toString(),
                participantId = "P01",
                trialIndex = 0,
                taskId = "task_1",
                eventType = "trial_completed",
                details = mapOf("outcome" to "aborted"),
            ),
        )

        val retried = service.retryFailedTrial(sessionId, observation.workflowRevision)

        val session = store.getSession(sessionId)
        assertEquals("trial_ready", session.workflowState)
        assertEquals(0, session.currentTrialIndex)
        assertEquals(0, session.preparedTrialIndex)
        assertEquals(0, retried.assignment["trial_index"])
        assertEquals(0, gateway.lastArmedAssignment?.get("trial_index"))
        assertTrue(gateway.armed)
    }

    @Test
    fun retriedTrialIgnoresThePreviousAttemptsTerminalEvent() = runTest {
        val sessionId = service.createLiveSession("P01")
        service.transition(sessionId, "start_consent", 0, actor = "system")
        service.recordAndConfirmLiveConsent(
            sessionId,
            "Participant One",
            PortalService.LIVE_CONSENT_ACKNOWLEDGEMENTS.toList(),
        )
        val firstCard = service.continueParticipantFlow(sessionId, expectedRevision = 2)
        service.transition(
            sessionId,
            "participant_started",
            firstCard.session.workflow_revision,
            actor = "participant",
        )
        store.recordRuntimeEvent(
            StudyRuntimeEvent(
                studyRunId = sessionId.toString(),
                participantId = "P01",
                trialIndex = 0,
                taskId = "task_1",
                eventType = "trial_completed",
                details = mapOf("outcome" to "technical_failure"),
            ),
        )
        val held = service.reconcileParticipantFlow(sessionId)
        service.retryFailedTrial(sessionId, held.session.workflow_revision)

        val retryCard = service.reconcileParticipantFlow(sessionId)
        assertEquals("task_card", retryCard.session.workflow_state)
        service.transition(
            sessionId,
            "participant_started",
            retryCard.session.workflow_revision,
            actor = "participant",
        )

        val stillRunning = service.reconcileParticipantFlow(sessionId)
        assertEquals("trial_running", stillRunning.session.workflow_state)

        store.recordRuntimeEvent(
            StudyRuntimeEvent(
                studyRunId = sessionId.toString(),
                participantId = "P01",
                trialIndex = 0,
                taskId = "task_1",
                eventType = "trial_completed",
                details = mapOf("outcome" to "success"),
            ),
        )
        val completed = service.reconcileParticipantFlow(sessionId)
        assertEquals("task_questionnaire", completed.session.workflow_state)
    }

    @Test
    fun failedRetryPreparationKeepsTheFailedTrialClaimedForTheNextAttempt() = runTest {
        val sessionId = service.createLiveSession("P01")
        service.transition(sessionId, "start_consent", 0, actor = "system")
        service.recordAndConfirmLiveConsent(
            sessionId,
            "Participant One",
            PortalService.LIVE_CONSENT_ACKNOWLEDGEMENTS.toList(),
        )
        val firstCard = service.continueParticipantFlow(sessionId, expectedRevision = 2)
        service.transition(
            sessionId,
            "participant_started",
            firstCard.session.workflow_revision,
            actor = "participant",
        )
        store.recordRuntimeEvent(
            StudyRuntimeEvent(
                studyRunId = sessionId.toString(),
                participantId = "P01",
                trialIndex = 0,
                taskId = "task_1",
                eventType = "trial_completed",
                details = mapOf("outcome" to "technical_failure"),
            ),
        )
        val held = service.reconcileParticipantFlow(sessionId)
        gateway.resetSucceeds = false

        try {
            service.retryFailedTrial(sessionId, held.session.workflow_revision)
            fail("expected retry preparation failure")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("reset failed"))
        }

        val retryClaim = store.getSession(sessionId)
        assertEquals("trial_ready", retryClaim.workflowState)
        assertEquals(0, retryClaim.preparedTrialIndex)
        gateway.resetSucceeds = true

        val prepared = service.prepareNextTrial(sessionId, retryClaim.workflowRevision)
        assertEquals(0, prepared.assignment["trial_index"])
        assertEquals(0, gateway.lastArmedAssignment?.get("trial_index"))
    }

    @Test
    fun duplicateActiveParticipantSessionIsRejected() = runTest {
        service.createLiveSession("P1")

        try {
            service.createLiveSession("P01")
            fail("expected duplicate active participant rejection")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains("active live session"))
        }

        assertEquals(listOf("P01"), store.researchExport().sessions.map { it.participantId })
    }

    @Test
    fun isolatedTestRunIsPersistedSeparatelyAndArmedInTestScope() = runTest {
        val run = service.startStudyTest(
            taskId = "task_1",
            condition = "c1_stepwise",
            injectError = true,
        )

        val session = store.getSession(run.session_id)
        assertTrue(session.participantId.startsWith("TEST-"))
        assertEquals("test", session.mode)
        assertEquals("test_digital", session.source)
        assertEquals("trial_running", session.workflowState)
        assertTrue(gateway.testArmed)
        val catalog = service.participantCatalog()
        assertTrue(catalog.participants.isEmpty())
        assertEquals(listOf(session.participantId), catalog.test_runs.map { it.participant_id })
    }

    @Test
    fun invalidInvestigatorCorrectionLeavesResponseAndAuditUnchanged() = runTest {
        val sessionId = service.createLiveSession("P01")
        val session = store.getSession(sessionId)
        database.responseDao().insert(
            StudyResponseEntity(
                sessionId = sessionId,
                instrumentId = TASK.id,
                position = 0,
                instrumentVersion = INSTRUMENT_VERSION,
                answersJson =
                    """{"task_criticality":5,"task_result_match":"Vollständig","task_observation":""}""",
                source = session.source,
                actor = "participant",
                submittedAt = PortalService.utcNow(),
            ),
        )
        val auditCount = store.researchExport().auditEvents.size

        try {
            service.correctParticipantResponse(
                sessionId = sessionId,
                instrumentId = TASK.id,
                position = 0,
                answers = buildJsonObject { put("task_criticality", JsonPrimitive(6)) },
                missing = buildJsonObject { },
                expectedRevision = 0,
            )
            fail("expected schema validation failure")
        } catch (_: IllegalArgumentException) {
            // Validation happens before the revision-guarded correction transaction.
        }

        val response = database.responseDao().getByPosition(sessionId, TASK.id, 0)
        assertEquals(0, response?.revision)
        assertEquals(auditCount, store.researchExport().auditEvents.size)
    }

    @Test
    fun prepareEntersLiveModeResetsThePhoneAndArmsTheFirstTrial() = runTest {
        val sessionId = service.createLiveSession("P01")
        store.updateWorkflowState(
            sessionId = sessionId,
            workflowState = "trial_ready",
            resumeState = null,
            expectedRevision = 0,
            event = "test_ready",
            actor = "test",
        )

        service.prepareNextTrial(sessionId, expectedRevision = 1)

        assertEquals("live", gateway.snapshot()["mode"])
        assertEquals(1, gateway.resetCalls)
        assertEquals(0, store.getSession(sessionId).currentTrialIndex)
        assertTrue(gateway.armed)
    }

    @Test
    fun preparePreservesTheClaimAndCanRetryWhenThePhoneResetFails() = runTest {
        val sessionId = service.createLiveSession("P01")
        store.updateWorkflowState(
            sessionId = sessionId,
            workflowState = "trial_ready",
            resumeState = null,
            expectedRevision = 0,
            event = "test_ready",
            actor = "test",
        )
        gateway.resetSucceeds = false

        try {
            service.prepareNextTrial(sessionId, expectedRevision = 1)
            fail("prepare should reject a failed device reset")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("reset"))
        }

        val failedPreparation = store.getSession(sessionId)
        assertEquals(0, failedPreparation.currentTrialIndex)
        assertEquals(0, failedPreparation.preparedTrialIndex)
        assertTrue(!gateway.armed)

        gateway.resetSucceeds = true
        service.prepareNextTrial(sessionId, expectedRevision = failedPreparation.workflowRevision)

        assertEquals(0, store.getSession(sessionId).currentTrialIndex)
        assertEquals(0, gateway.lastArmedAssignment?.get("trial_index"))
        assertTrue(gateway.armed)
    }

    @Test
    fun prepareAdvancesToTheTrialAfterThePersistedCurrentTrial() = runTest {
        val sessionId = service.createLiveSession("P01")
        store.updateWorkflowState(
            sessionId = sessionId,
            workflowState = "trial_ready",
            resumeState = null,
            expectedRevision = 0,
            event = "test_ready",
            actor = "test",
        )
        store.setTrial(sessionId, trialIndex = 0, expectedRevision = 1, actor = "test")
        store.updateWorkflowState(
            sessionId = sessionId,
            workflowState = "task_card",
            resumeState = null,
            expectedRevision = 2,
            event = "release_task_card",
            actor = "test",
        )
        store.updateWorkflowState(
            sessionId = sessionId,
            workflowState = "trial_ready",
            resumeState = null,
            expectedRevision = 3,
            event = "next_trial",
            actor = "test",
        )

        service.prepareNextTrial(sessionId, expectedRevision = 4)

        assertEquals(1, store.getSession(sessionId).currentTrialIndex)
        assertEquals(1, gateway.lastArmedAssignment?.get("trial_index"))
    }

    @Test
    fun prepareRecoversAPersistedTrialClaimAfterProcessRestart() = runTest {
        val sessionId = service.createLiveSession("P01")
        store.updateWorkflowState(
            sessionId = sessionId,
            workflowState = "trial_ready",
            resumeState = null,
            expectedRevision = 0,
            event = "test_ready",
            actor = "test",
        )
        store.setTrial(sessionId, trialIndex = 0, expectedRevision = 1, actor = "test")
        gateway = FakeStudyGateway()
        service = PortalService(store, gateway)

        service.prepareNextTrial(sessionId, expectedRevision = 2)

        assertEquals(0, store.getSession(sessionId).currentTrialIndex)
        assertEquals(0, gateway.lastArmedAssignment?.get("trial_index"))
    }

    @Test
    fun saveObservationCompletesWithoutHoldingAThreadLockAcrossRoomCalls() = runTest {
        val sessionId = service.createLiveSession("P01")
        store.updateWorkflowState(
            sessionId = sessionId,
            workflowState = "investigator_observation",
            resumeState = null,
            expectedRevision = 0,
            event = "test_observation",
            actor = "test",
        )
        store.setTrial(sessionId, trialIndex = 0, expectedRevision = 1, actor = "test")

        val session = withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000) {
                service.saveObservation(sessionId, observation = null, expectedRevision = 2)
            }
        }

        assertEquals("task_questionnaire", session.workflowState)
        assertEquals(3, session.workflowRevision)
    }

    @Test
    fun observationUsesTheCompletedTrialAfterTaskCardRelease() = runTest {
        gateway.errorTasks = listOf("task_1")
        val sessionId = service.createLiveSession("P01")
        store.setTrial(sessionId, trialIndex = 0, expectedRevision = 0, actor = "test")
        store.updateWorkflowState(
            sessionId = sessionId,
            workflowState = "task_card",
            resumeState = null,
            expectedRevision = 1,
            event = "release_task_card",
            actor = "test",
        )
        store.updateWorkflowState(
            sessionId = sessionId,
            workflowState = "investigator_observation",
            resumeState = null,
            expectedRevision = 2,
            event = "test_observation",
            actor = "test",
        )

        val session = service.saveObservation(
            sessionId = sessionId,
            observation = ErrorObservation(
                spontaneousDetection = true,
                detectionStage = DetectionStage.STEPWISE_GATE,
                evidenceType = EvidenceType.REJECTION,
                moderatorPromptGiven = false,
                detectedOnlyAfterPrompt = false,
            ),
            expectedRevision = 3,
        )

        assertEquals("task_questionnaire", session.workflowState)
        assertEquals(4, session.workflowRevision)
    }

    @Test
    fun staleWorkflowDoesNotPartiallyCommitAParticipantResponse() = runTest {
        val sessionId = service.createLiveSession("P01")
        store.updateWorkflowState(
            sessionId = sessionId,
            workflowState = "demographics",
            resumeState = null,
            expectedRevision = 0,
            event = "test_demographics",
            actor = "test",
        )
        val answers = mapOf<String, Any>(
            "age" to 30,
            "gender" to "Divers",
            "android_experience" to 5,
            "ai_assistant_experience" to 4,
        )

        try {
            service.submitParticipantResponse(
                sessionId = sessionId,
                answers = answers,
                expectedResponseRevision = 0,
                expectedWorkflowRevision = 0,
            )
            fail("expected stale workflow revision")
        } catch (_: RevisionConflict) {
            // Expected: neither response nor workflow transition may be committed.
        }

        val result = service.submitParticipantResponse(
            sessionId = sessionId,
            answers = answers,
            expectedResponseRevision = 0,
            expectedWorkflowRevision = 1,
        )

        assertTrue(result.responseId > 0)
        assertEquals("preference_ranking", store.getSession(sessionId).workflowState)
        assertEquals(2, store.getSession(sessionId).workflowRevision)
        assertEquals(
            INSTRUMENT_VERSION,
            store.researchExport().responses.single().instrumentVersion,
        )
    }

    @Test
    fun staleInterviewDoesNotPersistPartialNotes() = runTest {
        val sessionId = service.createLiveSession("P01")
        store.updateWorkflowState(
            sessionId = sessionId,
            workflowState = "interview",
            resumeState = null,
            expectedRevision = 0,
            event = "test_interview",
            actor = "test",
        )
        val answers = STUDY_INTERVIEW_QUESTIONS.associate { it.id to "Antwort" }

        try {
            service.submitLiveInterview(sessionId, answers, expectedRevision = 0)
            fail("expected stale workflow revision")
        } catch (_: RevisionConflict) {
            // The interview notes and transition must commit together or not at all.
        }

        assertTrue(store.researchExport().interviews.isEmpty())
        val updated = service.submitLiveInterview(sessionId, answers, expectedRevision = 1)
        assertEquals("debrief", updated.workflowState)
        assertEquals(
            STUDY_INTERVIEW_QUESTIONS.map { it.id }.toSet(),
            store.researchExport().interviews.map { it.questionId }.toSet(),
        )
    }

    @Test
    fun staleAbortDoesNotTriggerARealRuntimeAbort() = runTest {
        val sessionId = service.createLiveSession("P01")
        gateway.setMode("live")

        try {
            service.abortSession(sessionId, "investigator_abort", expectedRevision = 1)
            fail("expected stale workflow revision")
        } catch (_: RevisionConflict) {
            // The runtime must not be touched by a stale browser action.
        }

        assertEquals(0, gateway.abortCalls)
        val aborted = service.abortSession(sessionId, "investigator_abort", expectedRevision = 0)
        assertEquals(1, gateway.abortCalls)
        assertEquals(1, gateway.resetCalls)
        assertEquals("aborted", aborted.workflowState)
    }

    @Test
    fun participantStateRestoresTheCurrentInstrumentAndDraft() = runTest {
        val sessionId = service.createLiveSession("P01")
        store.updateWorkflowState(
            sessionId = sessionId,
            workflowState = "task_questionnaire",
            resumeState = null,
            expectedRevision = 0,
            event = "test_questionnaire",
            actor = "test",
        )
        store.setTrial(sessionId, trialIndex = 0, expectedRevision = 1, actor = "test")
        store.saveDraft(
            sessionId = sessionId,
            instrumentId = "task",
            position = 0,
            answers = mapOf("task_criticality" to 5),
            expectedRevision = 0,
        )

        val state = service.participantState(sessionId)

        assertEquals("task", state.instrument?.id)
        assertEquals(1, state.draft?.revision)
        assertEquals("task_1", state.task?.get("id")?.toString()?.trim('"'))
    }

    @Test
    fun liveConsentRequiresTheFrozenCheckboxSetAndCannotBeDuplicated() = runTest {
        val sessionId = service.createLiveSession("P01")
        store.updateWorkflowState(
            sessionId = sessionId,
            workflowState = "consent",
            resumeState = null,
            expectedRevision = 0,
            event = "start_consent",
            actor = "test",
        )

        service.recordLiveConsent(
            sessionId,
            "Participant One",
            PortalService.LIVE_CONSENT_ACKNOWLEDGEMENTS.toList(),
        )

        assertTrue(service.consentStatus(sessionId)?.recorded == true)
        try {
            service.recordLiveConsent(
                sessionId,
                "Participant One",
                PortalService.LIVE_CONSENT_ACKNOWLEDGEMENTS.toList(),
            )
            fail("expected duplicate consent conflict")
        } catch (_: RevisionConflict) {
            // Consent evidence is append-once for a live session.
        }
    }

    @Test
    fun staleConsentConfirmationDoesNotPartiallyConfirmConsent() = runTest {
        val sessionId = service.createLiveSession("P01")
        store.updateWorkflowState(
            sessionId = sessionId,
            workflowState = "consent",
            resumeState = null,
            expectedRevision = 0,
            event = "start_consent",
            actor = "test",
        )
        service.recordLiveConsent(
            sessionId,
            "Participant One",
            PortalService.LIVE_CONSENT_ACKNOWLEDGEMENTS.toList(),
        )

        try {
            service.confirmLiveConsent(sessionId, expectedRevision = 0)
            fail("expected stale workflow revision")
        } catch (_: RevisionConflict) {
            // Consent confirmation and workflow transition must commit together.
        }

        assertNull(store.getConsent(sessionId)?.investigatorConfirmedAt)
        assertEquals("consent", store.getSession(sessionId).workflowState)
        assertEquals(1, store.getSession(sessionId).workflowRevision)
    }
}

private class FakeStudyGateway : StudyGateway {
    private var mode = "normal"
    private var activeParticipant = ""
    var armed = false
        private set
    var lastArmedAssignment: Map<String, Any>? = null
        private set
    var abortCalls = 0
        private set
    var resetCalls = 0
        private set
    var armCalls = 0
        private set
    var resetSucceeds = true
    var errorTasks: List<String> = emptyList()
    var armFails = false
    var armSnapshotMismatch = false
    var testArmed = false
        private set
    var trainingStarts = 0
        private set

    override fun snapshot(): Map<String, Any> = mapOf(
        "mode" to mode,
        "phone_ready" to (mode == "live"),
        "active_participant" to activeParticipant,
        "trial" to (
            mapOf("state" to if (armed) "armed" else "idle") +
                if (armed) {
                    mapOf(
                        "task_id" to lastArmedAssignment?.get("task_id"),
                        "trial_index" to lastArmedAssignment?.get("trial_index"),
                        "condition" to lastArmedAssignment?.get("condition"),
                        "inject_error" to lastArmedAssignment?.get("inject_error"),
                        "trial_attempt_id" to lastArmedAssignment?.get("trial_attempt_id"),
                    ) + if (armSnapshotMismatch) mapOf("task_id" to "wrong_task") else emptyMap()
                } else emptyMap()
            ),
    )

    override fun preflight(): Boolean = mode == "live"

    override fun setMode(mode: String): Map<String, Any> {
        this.mode = mode
        return snapshot()
    }

    override fun activateParticipant(participantId: String): Map<String, Any> {
        activeParticipant = participantId
        return snapshot()
    }

    override fun nextAssignment(participantId: String): Map<String, Any> = mapOf(
        "participant_id" to participantId,
        "task_order" to listOf("task_1", "task_2"),
        "condition_order" to listOf("stepwise", "final"),
        "error_tasks" to errorTasks,
        "tasks" to listOf(
            mapOf(
                "id" to "task_1",
                "criticality" to "low",
                "assigned_error_variant_ids" to
                    if ("task_1" in errorTasks) listOf("err_task_1") else emptyList<String>(),
                "assigned_error_variants" to emptyList<Map<String, String>>(),
            ),
            mapOf(
                "id" to "task_2",
                "criticality" to "high",
                "assigned_error_variant_ids" to emptyList<String>(),
                "assigned_error_variants" to emptyList<Map<String, String>>(),
            ),
        ),
    )

    override fun testTasks(): List<Map<String, String>> = listOf(
        mapOf("id" to "task_1", "instruction" to "Testaufgabe", "criticality" to "low"),
    )

    override fun armTestTrial(
        participantId: String,
        taskId: String,
        condition: String,
        injectError: Boolean,
        studyRunId: String,
    ): Map<String, Any> {
        mode = "test"
        activeParticipant = participantId
        testArmed = true
        armed = true
        return snapshot()
    }

    override fun armNextTrial(assignment: Map<String, Any>): Map<String, Any> {
        armCalls += 1
        lastArmedAssignment = assignment
        if (armFails) error("simulated arm failure")
        armed = true
        return snapshot()
    }
    override fun abortTrial(reason: String): Map<String, Any> {
        abortCalls += 1
        mode = "normal"
        armed = false
        return snapshot()
    }
    override fun resetDevice(): Boolean {
        resetCalls += 1
        return resetSucceeds
    }
    override fun startTraining(): Boolean {
        trainingStarts += 1
        return true
    }
    override fun tryExclusive(): Boolean = true
    override fun releaseExclusive() = Unit
}
