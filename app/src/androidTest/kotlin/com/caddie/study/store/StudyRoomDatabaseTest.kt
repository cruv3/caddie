package com.caddie.study.store

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.caddie.study.runtime.audit.ControlledErrorEvent
import com.caddie.study.runtime.audit.StudyRuntimeEvent
import com.caddie.study.portal.INSTRUMENT_VERSION
import com.caddie.study.store.entity.StudyDraftEntity
import com.caddie.study.store.entity.StudyInterviewNoteEntity
import com.caddie.study.store.entity.StudyObservationEntity
import com.caddie.study.store.entity.StudyResponseEntity
import com.caddie.study.store.entity.StudyTrialMarkerEntity

/** Verifies the native study store against a real in-memory Room database. */
@RunWith(AndroidJUnit4::class)
class StudyRoomDatabaseTest {

    private lateinit var database: StudyRoomDatabase
    private lateinit var store: StudyStore

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            StudyRoomDatabase::class.java,
        ).build()
        store = StudyStore(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun generatedIdsAllowRepeatedSessionsConsentsAndAuditEvents() = runTest {
        val firstSession = createSession("P01")
        val secondSession = createSession("P02")

        assertNotEquals(firstSession, secondSession)

        val firstConsent = recordConsent(firstSession, "Participant One")
        val secondConsent = recordConsent(secondSession, "Participant Two")

        assertNotEquals(firstConsent, secondConsent)
    }

    @Test
    fun confirmedConsentAndWorkflowAdvanceRollBackTogether() = runTest {
        val sessionId = createSession("P-CONSENT")
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_training_update
            BEFORE UPDATE OF workflow_state ON sessions
            WHEN NEW.workflow_state = 'training'
            BEGIN
                SELECT RAISE(ABORT, 'forced training failure');
            END
            """.trimIndent(),
        )

        try {
            store.recordConfirmedConsentAndUpdateWorkflowState(
                sessionId = sessionId,
                fullName = "Participant One",
                consentVersion = "test",
                consentChecksum = "sha256:test",
                acknowledgementsJson = "[]",
                method = "live_digital",
                consentedAt = "2026-08-01T00:00:00Z",
                confirmedAt = "2026-08-01T00:00:00Z",
                workflowState = "training",
                expectedRevision = 0,
                event = "consent_confirmed",
                actor = "system",
            )
            throw AssertionError("expected stale workflow revision")
        } catch (_: RuntimeException) {
            // The forced session-update failure occurs after the consent insert.
        }

        assertEquals(null, store.getConsent(sessionId))
        assertEquals("setup", store.getSession(sessionId).workflowState)

        database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_training_update")
        val revision = store.recordConfirmedConsentAndUpdateWorkflowState(
            sessionId = sessionId,
            fullName = "Participant One",
            consentVersion = "test",
            consentChecksum = "sha256:test",
            acknowledgementsJson = "[]",
            method = "live_digital",
            consentedAt = "2026-08-01T00:00:00Z",
            confirmedAt = "2026-08-01T00:00:00Z",
            workflowState = "training",
            expectedRevision = 0,
            event = "consent_confirmed",
            actor = "system",
        )

        assertEquals(1, revision)
        assertEquals("2026-08-01T00:00:00Z", store.getConsent(sessionId)?.investigatorConfirmedAt)
        assertEquals("training", store.getSession(sessionId).workflowState)
        assertEquals(1, store.getSession(sessionId).workflowRevision)
        assertEquals("system", store.researchExport().auditEvents.last().actor)
    }

    @Test
    fun savingObservationAdvancesThePersistedWorkflowRevision() = runTest {
        val sessionId = createSession("P03")
        store.updateWorkflowState(
            sessionId = sessionId,
            workflowState = "investigator_observation",
            resumeState = null,
            expectedRevision = 0,
            event = "test_observation",
            actor = "test",
        )
        store.setTrial(sessionId, trialIndex = 0, expectedRevision = 1, actor = "test")

        val revision = store.saveObservation(
            sessionId = sessionId,
            trialIndex = 0,
            taskId = "task_1",
            condition = "stepwise",
            intendedCriticality = "low",
            errorVariantId = null,
            observationData = null,
            expectedRevision = 2,
            actor = "investigator",
        )

        assertEquals(3, revision)
        assertEquals(3, store.getSession(sessionId).workflowRevision)
        assertEquals("task_questionnaire", store.getSession(sessionId).workflowState)
    }

    @Test
    fun draftRoundTripPreservesNumbersAndRankingLists() = runTest {
        val sessionId = createSession("P04")

        val revision = store.saveDraft(
            sessionId = sessionId,
            instrumentId = "end",
            position = 1,
            answers = mapOf("age" to 30, "condition_ranking" to listOf("C1", "C2", "C3")),
            expectedRevision = 0,
        )
        val draft = store.getDraft(sessionId, "end", 1)!!
        val answers = Json.parseToJsonElement(draft.answersJson).jsonObject

        assertEquals(1L, revision)
        assertEquals(30, answers["age"]!!.jsonPrimitive.content.toInt())
        assertEquals("C2", answers["condition_ranking"]!!.jsonArray[1].jsonPrimitive.content)
    }

    @Test
    fun courseBonusAndConsentStayOutOfResearchRows() = runTest {
        val sessionId = createSession("TEST-NATIVE-READINESS")
        recordConsent(sessionId, "Named Participant")
        val bonusId = store.saveCourseBonus(
            "Named Participant",
            "12345",
            "2026-08-02T12:00:00Z",
        )

        val research = store.researchExport()

        assertEquals(listOf("TEST-NATIVE-READINESS"), research.sessions.map { it.participantId })
        assertEquals("Named Participant", store.consentExport().single().fullName)
        assertEquals("12345", store.courseBonusExport().single().matriculationNumber)

        store.deleteCourseBonus(bonusId)
        assertTrue(store.courseBonusExport().isEmpty())
        database.openHelper.readableDatabase.query(
            "SELECT count(*) FROM course_bonus_records WHERE id = ?",
            arrayOf(bonusId.toString()),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
    }

    @Test
    fun deletingParticipantRemovesOnlyTheirSessionOwnedRows() = runTest {
        val first = createSession("P01")
        val second = createSession("P01")
        val other = createSession("P02")
        val testRun = createSession("TEST-P01", mode = "test", source = "test_digital")
        listOf(first, second).forEach { sessionId ->
            store.updateWorkflowState(
                sessionId = sessionId,
                workflowState = "aborted",
                resumeState = null,
                expectedRevision = 0,
                event = "abort",
                actor = "test",
            )
        }
        recordConsent(first, "Participant One")
        database.draftDao().insert(
            StudyDraftEntity(
                sessionId = first,
                instrumentId = "task_questionnaire",
                position = 0,
                answersJson = "{}",
                updatedAt = "2026-08-06T10:00:00Z",
            ),
        )
        database.responseDao().insert(
            StudyResponseEntity(
                sessionId = first,
                instrumentId = "task_questionnaire",
                position = 0,
                instrumentVersion = INSTRUMENT_VERSION,
                answersJson = "{}",
                source = "live_digital",
                actor = "participant",
                submittedAt = "2026-08-06T10:00:00Z",
            ),
        )
        database.observationDao().insertObservation(
            StudyObservationEntity(
                sessionId = first,
                trialIndex = 0,
                taskId = "task_maps_messenger",
                condition = "c1_stepwise",
                intendedCriticality = "low",
                detectionStage = "none",
                evidenceType = "none",
                submittedAt = "2026-08-06T10:00:00Z",
            ),
        )
        database.observationDao().insertMarker(
            StudyTrialMarkerEntity(
                sessionId = first,
                trialIndex = 0,
                assignedError = false,
                outcome = "aborted",
                submittedAt = "2026-08-06T10:00:00Z",
            ),
        )
        database.interviewDao().insert(
            StudyInterviewNoteEntity(
                sessionId = first,
                questionId = "feedback",
                answer = "Test",
                submittedAt = "2026-08-06T10:00:00Z",
            ),
        )
        store.saveCourseBonus("Participant One", "12345", "2026-08-06T10:00:00Z")

        val deleted = store.deleteParticipant("P01")

        assertEquals(2, deleted)
        assertEquals(setOf(other, testRun), store.researchExport().sessions.map { it.id }.toSet())
        assertTrue(database.draftDao().getAll().none { it.sessionId in setOf(first, second) })
        assertTrue(store.researchExport().responses.none { it.sessionId in setOf(first, second) })
        assertTrue(store.researchExport().observations.none { it.sessionId in setOf(first, second) })
        assertTrue(store.researchExport().markers.none { it.sessionId in setOf(first, second) })
        assertTrue(store.researchExport().interviews.none { it.sessionId in setOf(first, second) })
        assertTrue(store.researchExport().auditEvents.none { it.sessionId in setOf(first, second) })
        assertTrue(store.consentExport().none { it.sessionId in setOf(first, second) })
        assertEquals("12345", store.courseBonusExport().single().matriculationNumber)
    }

    @Test
    fun activeParticipantMustBeAbortedBeforeDeletion() = runTest {
        val sessionId = createSession("P03")

        try {
            store.deleteParticipant("P03")
            throw AssertionError("expected active participant deletion to fail")
        } catch (_: IllegalArgumentException) {
            // An in-progress study must be explicitly aborted before destructive deletion.
        }

        assertEquals(sessionId, store.getSession(sessionId).id)
    }

    @Test
    fun participantDeletionRollsBackWhenSessionDeleteFails() = runTest {
        val sessionId = createSession("P04-DELETE")
        store.updateWorkflowState(
            sessionId = sessionId,
            workflowState = "aborted",
            resumeState = null,
            expectedRevision = 0,
            event = "abort",
            actor = "test",
        )
        recordConsent(sessionId, "Participant Four")
        database.responseDao().insert(
            StudyResponseEntity(
                sessionId = sessionId,
                instrumentId = "end_questionnaire",
                position = 0,
                instrumentVersion = INSTRUMENT_VERSION,
                answersJson = "{}",
                source = "live_digital",
                actor = "participant",
                submittedAt = "2026-08-06T10:00:00Z",
            ),
        )
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_participant_delete
            BEFORE DELETE ON sessions
            BEGIN
                SELECT RAISE(ABORT, 'forced participant delete failure');
            END
            """.trimIndent(),
        )

        try {
            store.deleteParticipant("P04-DELETE")
            throw AssertionError("expected participant deletion failure")
        } catch (_: RuntimeException) {
            // Child deletions must roll back when the final session delete fails.
        }

        assertEquals(sessionId, store.getSession(sessionId).id)
        assertEquals(sessionId, store.consentExport().single().sessionId)
        assertEquals(sessionId, store.researchExport().responses.single().sessionId)
        assertTrue(store.researchExport().auditEvents.any { it.sessionId == sessionId })
    }

    @Test
    fun controlledErrorIsDurableAndIncludedInResearchAudit() = runTest {
        val sessionId = createSession("TEST-NATIVE-READINESS")

        store.recordControlledError(
            ControlledErrorEvent(
                studyRunId = sessionId.toString(),
                trialAttemptId = "attempt-1",
                trialIndex = 2,
                taskId = "task_banking_payment",
                stepId = "amount",
                errorVariantId = "err_amount",
                field = "payment_amount",
                correctValue = "30.00",
                wrongValue = "80.00",
            ),
        )
        store.recordControlledError(
            ControlledErrorEvent(
                studyRunId = sessionId.toString(),
                trialAttemptId = "attempt-1",
                trialIndex = 2,
                taskId = "task_banking_payment",
                stepId = "amount",
                errorVariantId = "err_amount",
                field = "payment_amount",
                correctValue = "30.00",
                wrongValue = "80.00",
            ),
        )

        val event = store.researchExport().auditEvents.single { it.eventType == "error_injected" }
        assertEquals(sessionId, event.sessionId)
        org.junit.Assert.assertTrue(event.detailsJson.contains("err_amount"))
        org.junit.Assert.assertTrue(event.detailsJson.contains("80.00"))
        org.junit.Assert.assertTrue(event.detailsJson.contains("attempt-1"))
    }

    @Test
    fun controlledErrorRetriesRemainSeparateAuditAttempts() = runTest {
        val sessionId = createSession("TEST-NATIVE-RETRY")
        listOf("attempt-1", "attempt-2").forEach { attemptId ->
            store.recordControlledError(
                ControlledErrorEvent(
                    studyRunId = sessionId.toString(),
                    trialAttemptId = attemptId,
                    trialIndex = 2,
                    taskId = "task_banking_payment",
                    stepId = "amount",
                    errorVariantId = "err_amount",
                    field = "payment_amount",
                    correctValue = "30.00",
                    wrongValue = "80.00",
                ),
            )
        }

        val events = store.researchExport().auditEvents.filter { it.eventType == "error_injected" }
        assertEquals(2, events.size)
        assertTrue(events.any { it.detailsJson.contains("attempt-1") })
        assertTrue(events.any { it.detailsJson.contains("attempt-2") })
    }

    @Test
    fun investigatorCorrectionIsRevisionGuardedAndAudited() = runTest {
        val sessionId = createSession("P19")
        database.responseDao().insert(
            StudyResponseEntity(
                sessionId = sessionId,
                instrumentId = "task_questionnaire",
                position = 0,
                instrumentVersion = INSTRUMENT_VERSION,
                answersJson = "{\"trust\":3,\"comment\":\"old\"}",
                source = "live_digital",
                actor = "participant",
                submittedAt = "2026-08-05T10:00:00Z",
                revision = 1,
            ),
        )

        val corrected = store.correctResponse(
            sessionId = sessionId,
            instrumentId = "task_questionnaire",
            position = 0,
            answersJson = "{\"trust\":6,\"comment\":\"corrected\"}",
            missingJson = "{}",
            expectedRevision = 1,
        )

        assertEquals(2, corrected.revision)
        assertEquals(6, Json.parseToJsonElement(corrected.answersJson).jsonObject["trust"]!!.jsonPrimitive.content.toInt())
        val audit = store.researchExport().auditEvents.single {
            it.eventType == "investigator_value_corrected"
        }
        assertTrue(audit.detailsJson.contains("\"old_answers\""))
        assertTrue(audit.detailsJson.contains("\"new_answers\""))

        try {
            store.correctResponse(
                sessionId = sessionId,
                instrumentId = "task_questionnaire",
                position = 0,
                answersJson = "{\"trust\":1}",
                missingJson = "{}",
                expectedRevision = 1,
            )
            throw AssertionError("expected stale response revision")
        } catch (_: RevisionConflict) {
            // A stale browser must not overwrite the correction.
        }
        assertEquals(2, database.responseDao().getByPosition(sessionId, "task_questionnaire", 0)!!.revision)
        assertEquals(1, store.researchExport().auditEvents.count { it.eventType == "investigator_value_corrected" })
    }

    @Test
    fun nativeRuntimeEventsAreAttachedToTheirLiveOrTestSession() = runTest {
        val sessionId = createSession("TEST-P01")

        store.recordRuntimeEvent(
            StudyRuntimeEvent(
                studyRunId = sessionId.toString(),
                participantId = "TEST-P01",
                trialIndex = 0,
                taskId = "task_maps_messenger",
                eventType = "trial_completed",
                details = mapOf("outcome" to "success", "duration_ms" to 1250.0),
            ),
        )

        val event = store.researchExport().auditEvents.single {
            it.eventType == "trial_completed"
        }
        assertEquals(sessionId, event.sessionId)
        assertEquals("native_executor", event.actor)
        assertTrue(event.detailsJson.contains("task_maps_messenger"))
        assertTrue(event.detailsJson.contains("success"))
    }

    private suspend fun createSession(
        participantId: String,
        mode: String = "live",
        source: String = "live_digital",
    ): Long =
        store.createSession(
            participantId = participantId,
            mode = mode,
            source = source,
            assignmentJson = "{}",
            assignmentHash = "sha256:test",
        )

    private suspend fun recordConsent(sessionId: Long, fullName: String): Long =
        store.recordConsent(
            sessionId = sessionId,
            fullName = fullName,
            consentVersion = "test",
            consentChecksum = "sha256:test",
            acknowledgementsJson = "[]",
            method = "live_digital",
            consentedAt = "2026-08-01T00:00:00Z",
        )
}
