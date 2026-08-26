package com.caddie.study.portal

import com.caddie.study.store.entity.StudyAuditEntity
import com.caddie.study.store.entity.StudyInterviewNoteEntity
import com.caddie.study.store.entity.StudyObservationEntity
import com.caddie.study.store.entity.StudyResponseEntity
import com.caddie.study.store.entity.StudySessionEntity
import com.caddie.study.store.entity.StudyTrialMarkerEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies that live participant progress and rehearsal data never share one catalog. */
class StudyParticipantDashboardTest {
    @Test
    fun `catalog lists enrolled participants suggests next id and separates test runs`() {
        val sessions = listOf(
            session(1, "P01", "live", "live_digital", "in_progress", "task_questionnaire"),
            session(2, "P02", "live", "live_digital", "aborted", "aborted"),
            session(3, "TEST-P01", "test", "test_digital", "completed", "completed"),
        )
        val markers = listOf(
            marker(1, 0, "no_error_observed"),
            marker(1, 1, "error_observed"),
            marker(3, 0, "no_error_observed"),
        )
        val audits = listOf(
            audit(1, "trial_completed", "2026-08-05T10:00:00Z"),
            audit(2, "technical_failure", "2026-08-05T11:00:00Z"),
        )

        val catalog = StudyParticipantDashboard.catalog(sessions, markers, audits)

        assertEquals(listOf("P01", "P02"), catalog.participants.map { it.participant_id })
        assertEquals(2, catalog.participants.first { it.participant_id == "P01" }.completed_trials)
        assertEquals("in_progress", catalog.participants.first { it.participant_id == "P01" }.outcome)
        assertTrue(catalog.participants.first { it.participant_id == "P02" }.has_problem)
        assertEquals(listOf("TEST-P01"), catalog.test_runs.map { it.participant_id })
        assertFalse(catalog.participants.any { it.participant_id.startsWith("TEST-") })
        assertEquals(listOf("P03"), catalog.available_participant_ids)
    }

    @Test
    fun `catalog has no fixed participant ceiling`() {
        val catalog = StudyParticipantDashboard.catalog(
            sessions = listOf(
                session(1, "P18", "live", "live_digital", "completed", "completed"),
                session(2, "P19", "live", "live_digital", "in_progress", "consent"),
            ),
            markers = emptyList(),
            audits = emptyList(),
        )

        assertEquals(listOf("P18", "P19"), catalog.participants.map { it.participant_id })
        assertEquals(listOf("P20"), catalog.available_participant_ids)
    }

    @Test
    fun `empty catalog starts with P01`() {
        val catalog = StudyParticipantDashboard.catalog(emptyList(), emptyList(), emptyList())

        assertTrue(catalog.participants.isEmpty())
        assertEquals(listOf("P01"), catalog.available_participant_ids)
    }

    @Test
    fun `native completion events drive success progress and failures`() {
        val sessions = listOf(
            session(1, "P19", "live", "live_digital", "in_progress", "trial_running"),
        )
        val success = StudyAuditEntity(
            sessionId = 1,
            actor = "native_executor",
            eventType = "trial_completed",
            detailsJson = "{\"trial_index\":0,\"outcome\":\"success\"}",
            createdAt = "2026-08-05T12:00:00Z",
        )
        val failure = StudyAuditEntity(
            sessionId = 1,
            actor = "native_executor",
            eventType = "verification_failed",
            detailsJson = "{\"trial_index\":1}",
            createdAt = "2026-08-05T12:01:00Z",
        )

        val participant = StudyParticipantDashboard.catalog(
            sessions,
            emptyList(),
            listOf(success, failure),
        ).participants.single()

        assertEquals(1, participant.completed_trials)
        assertTrue(participant.has_problem)
    }

    @Test
    fun `completed live trials combine across attempts without counting test rows`() {
        val sessions = listOf(
            session(1, "P01", "live", "live_digital", "aborted", "aborted"),
            session(2, "P01", "live", "live_digital", "in_progress", "trial_ready"),
            session(3, "TEST-P01", "test", "test_digital", "completed", "completed"),
        )
        val markers = listOf(
            marker(1, 0, "no_error_observed"),
            marker(2, 1, "error_observed"),
            marker(3, 2, "no_error_observed"),
        )

        val p01 = StudyParticipantDashboard.catalog(sessions, markers, emptyList())
            .participants.first { it.participant_id == "P01" }

        assertEquals(2, p01.completed_trials)
        assertEquals(2L, p01.session_id)
        assertTrue(p01.has_problem)
    }

    @Test
    fun `participant detail exposes filled answers observations results and audit timeline`() {
        val live = session(1, "P01", "live", "live_digital", "in_progress", "trial_ready")
        val rehearsal = session(2, "TEST-P01", "test", "test_digital", "completed", "completed")

        val detail = StudyParticipantDashboard.detail(
            participantId = "P01",
            sessions = listOf(live, rehearsal),
            responses = listOf(
                StudyResponseEntity(
                    sessionId = 1,
                    instrumentId = "task_questionnaire",
                    position = 0,
                    instrumentVersion = "v1",
                    answersJson = "{\"trust\":6}",
                    source = "live_digital",
                    actor = "participant",
                    submittedAt = "2026-08-05T10:00:00Z",
                    revision = 1,
                ),
            ),
            observations = listOf(
                StudyObservationEntity(
                    sessionId = 1,
                    trialIndex = 0,
                    taskId = "task_maps_messenger",
                    condition = "c1",
                    intendedCriticality = "low",
                    errorVariantId = "wrong-route",
                    spontaneousDetection = true,
                    detectionStage = "during_execution",
                    evidenceType = "spoken_identification",
                    notes = "Fehler bemerkt",
                    submittedAt = "2026-08-05T10:01:00Z",
                ),
            ),
            markers = listOf(marker(1, 0, "error_observed")),
            interviews = listOf(
                StudyInterviewNoteEntity(
                    sessionId = 1,
                    questionId = "closing",
                    answer = "War verständlich",
                    submittedAt = "2026-08-05T10:02:00Z",
                ),
            ),
            audits = listOf(audit(1, "trial_completed", "2026-08-05T10:03:00Z")),
        )

        assertEquals("P01", detail.participant_id)
        assertEquals(1, detail.sessions.size)
        assertEquals("6", detail.sessions.single().responses.single().answers["trust"].toString())
        assertEquals("Fehler bemerkt", detail.sessions.single().observations.single().notes)
        assertEquals("error_observed", detail.sessions.single().trials.single().outcome)
        assertEquals("trial_completed", detail.sessions.single().events.single().event_type)
        assertEquals("War verständlich", detail.sessions.single().interviews.single().answer)
    }

    @Test
    fun `session detail can inspect one isolated test run`() {
        val live = session(1, "P01", "live", "live_digital", "in_progress", "trial_ready")
        val rehearsal = session(2, "TEST-P01", "test", "test_digital", "completed", "completed")

        val detail = StudyParticipantDashboard.sessionDetail(
            sessionId = 2,
            sessions = listOf(live, rehearsal),
            responses = emptyList(),
            observations = emptyList(),
            markers = listOf(marker(2, 0, "no_error_observed")),
            interviews = emptyList(),
            audits = listOf(audit(2, "trial_completed", "2026-08-05T12:00:00Z")),
        )

        assertEquals("TEST-P01", detail.participant_id)
        assertEquals(listOf(2L), detail.sessions.map { it.session.id })
        assertEquals(1, detail.sessions.single().trials.size)
    }

    private fun session(
        id: Long,
        participantId: String,
        mode: String,
        source: String,
        status: String,
        workflow: String,
    ) = StudySessionEntity(
        id = id,
        participantId = participantId,
        mode = mode,
        source = source,
        enteredAt = "2026-08-05T0$id:00:00Z",
        status = status,
        workflowState = workflow,
        assignmentJson = "{\"tasks\":[]}",
        assignmentHash = "sha256:test-$id",
    )

    private fun marker(sessionId: Long, trial: Int, outcome: String) =
        StudyTrialMarkerEntity(
            sessionId = sessionId,
            trialIndex = trial,
            assignedError = outcome == "error_observed",
            outcome = outcome,
            submittedAt = "2026-08-05T12:00:00Z",
        )

    private fun audit(sessionId: Long, type: String, createdAt: String) = StudyAuditEntity(
        sessionId = sessionId,
        actor = "system",
        eventType = type,
        detailsJson = "{}",
        createdAt = createdAt,
    )
}
