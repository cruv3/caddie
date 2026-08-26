package com.caddie.study.store

import androidx.room.*
import com.caddie.study.store.entity.*

@Dao
interface StudySessionDao {

    @Insert
    suspend fun insertSession(session: StudySessionEntity): Long

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    suspend fun getById(sessionId: Long): StudySessionEntity?

    @Query("SELECT * FROM sessions ORDER BY id DESC LIMIT 1")
    suspend fun getLatest(): StudySessionEntity?

    @Query("SELECT * FROM sessions ORDER BY id")
    suspend fun getAll(): List<StudySessionEntity>

    @Query("""
        UPDATE sessions
        SET workflow_state = :workflowState,
            workflow_revision = :revision,
            resume_state = :resumeState,
            prepared_trial_index = CASE
                WHEN :event IN ('release_task_card', 'abort') THEN NULL
                ELSE prepared_trial_index
            END,
            status = CASE
                WHEN :workflowState = 'completed' THEN 'completed'
                WHEN :workflowState = 'aborted' THEN 'aborted'
                ELSE status
            END
        WHERE id = :sessionId AND workflow_revision = :expectedRevision
    """)
    suspend fun updateWorkflowState(
        sessionId: Long,
        workflowState: String,
        revision: Int,
        resumeState: String?,
        expectedRevision: Int,
        event: String,
    ): Int

    @Query("""
        UPDATE sessions
        SET workflow_state = :destinationState,
            workflow_revision = :revision,
            resume_state = NULL
        WHERE id = :sessionId
          AND workflow_revision = :expectedRevision
          AND workflow_state = :expectedState
    """)
    suspend fun updateWorkflowStateFromState(
        sessionId: Long,
        expectedState: String,
        destinationState: String,
        revision: Int,
        expectedRevision: Int,
    ): Int

    @Query("""
        UPDATE sessions
        SET current_trial_index = :trialIndex,
            prepared_trial_index = :trialIndex,
            workflow_revision = :revision
        WHERE id = :sessionId AND workflow_revision = :expectedRevision
    """)
    suspend fun setCurrentTrial(
        sessionId: Long,
        trialIndex: Int,
        revision: Int,
        expectedRevision: Int,
    ): Int
}

@Dao
interface StudyDraftDao {

    @Insert
    suspend fun insert(draft: StudyDraftEntity): Long

    @Query("SELECT * FROM drafts WHERE session_id = :sessionId AND instrument_id = :instrumentId AND position = :position")
    suspend fun getByPosition(sessionId: Long, instrumentId: String, position: Int): StudyDraftEntity?

    @Query("""
        UPDATE drafts
        SET answers_json = :answersJson,
            missing_json = :missingJson,
            revision = :revision,
            updated_at = :updatedAt
        WHERE session_id = :sessionId AND instrument_id = :instrumentId AND position = :position
    """)
    suspend fun updateByPosition(
        sessionId: Long,
        instrumentId: String,
        position: Int,
        answersJson: String,
        missingJson: String,
        revision: Int,
        updatedAt: String,
    ): Int

    @Query("SELECT * FROM drafts ORDER BY session_id, instrument_id, position")
    suspend fun getAll(): List<StudyDraftEntity>
}

@Dao
interface StudyResponseDao {

    @Insert
    suspend fun insert(response: StudyResponseEntity): Long

    @Query("SELECT 1 FROM responses WHERE session_id = :sessionId AND instrument_id = :instrumentId AND position = :position LIMIT 1")
    suspend fun exists(sessionId: Long, instrumentId: String, position: Int): Boolean

    @Query("SELECT * FROM responses WHERE session_id = :sessionId AND instrument_id = :instrumentId AND position = :position LIMIT 1")
    suspend fun getByPosition(
        sessionId: Long,
        instrumentId: String,
        position: Int,
    ): StudyResponseEntity?

    @Query("""
        UPDATE responses
        SET answers_json = :answersJson,
            missing_json = :missingJson,
            revision = :newRevision
        WHERE session_id = :sessionId
          AND instrument_id = :instrumentId
          AND position = :position
          AND revision = :expectedRevision
    """)
    suspend fun correctByPosition(
        sessionId: Long,
        instrumentId: String,
        position: Int,
        answersJson: String,
        missingJson: String,
        expectedRevision: Int,
        newRevision: Int,
    ): Int

    @Query("SELECT * FROM responses ORDER BY session_id, instrument_id, position")
    suspend fun getAll(): List<StudyResponseEntity>
}

@Dao
interface StudyObservationDao {

    @Insert
    suspend fun insertObservation(obs: StudyObservationEntity): Long

    @Insert
    suspend fun insertMarker(marker: StudyTrialMarkerEntity): Long

    @Query("SELECT 1 FROM trial_observation_markers WHERE session_id = :sessionId AND trial_index = :trialIndex LIMIT 1")
    suspend fun hasMarker(sessionId: Long, trialIndex: Int): Boolean

    @Query("SELECT * FROM error_observations ORDER BY session_id, trial_index")
    suspend fun getAllObservations(): List<StudyObservationEntity>

    @Query("SELECT * FROM trial_observation_markers ORDER BY session_id, trial_index")
    suspend fun getAllMarkers(): List<StudyTrialMarkerEntity>
}

@Dao
interface StudyInterviewDao {

    @Insert
    suspend fun insert(note: StudyInterviewNoteEntity): Long

    @Query("SELECT * FROM interview_notes ORDER BY session_id, question_id")
    suspend fun getAll(): List<StudyInterviewNoteEntity>
}

@Dao
interface StudyConsentDao {

    @Insert
    suspend fun insert(consent: StudyConsentEntity): Long

    @Query("""
        UPDATE consents
        SET investigator_confirmed_at = :confirmedAt
        WHERE session_id = :sessionId AND investigator_confirmed_at IS NULL
    """)
    suspend fun confirmConsent(sessionId: Long, confirmedAt: String): Int

    @Query("SELECT * FROM consents WHERE session_id = :sessionId LIMIT 1")
    suspend fun getBySession(sessionId: Long): StudyConsentEntity?

    @Query("SELECT * FROM consents ORDER BY consented_at, id")
    suspend fun getAll(): List<StudyConsentEntity>
}

@Dao
interface StudyCourseBonusDao {
    @Insert
    suspend fun insert(record: StudyCourseBonusEntity): Long

    @Query("SELECT * FROM course_bonus_records WHERE deleted_at IS NULL ORDER BY consented_at, id")
    suspend fun getAll(): List<StudyCourseBonusEntity>

    @Query("SELECT * FROM course_bonus_records WHERE id = :id AND deleted_at IS NULL LIMIT 1")
    suspend fun getActiveById(id: Long): StudyCourseBonusEntity?

    @Query("DELETE FROM course_bonus_records WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}

@Dao
interface StudyAuditDao {

    @Insert
    suspend fun insert(event: StudyAuditEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOnce(event: StudyAuditEntity): Long

    @Query("SELECT * FROM audit_events ORDER BY id")
    suspend fun getAll(): List<StudyAuditEntity>

    @Query("SELECT * FROM audit_events WHERE session_id = :sessionId ORDER BY id")
    suspend fun getForSession(sessionId: Long): List<StudyAuditEntity>
}

/** Deletes every research row owned by a selected participant's sessions. */
@Dao
interface StudyDeletionDao {
    @Query("SELECT * FROM sessions WHERE participant_id = :participantId ORDER BY id")
    suspend fun sessionsForParticipant(participantId: String): List<StudySessionEntity>

    @Query("DELETE FROM drafts WHERE session_id IN (:sessionIds)")
    suspend fun deleteDrafts(sessionIds: List<Long>): Int

    @Query("DELETE FROM responses WHERE session_id IN (:sessionIds)")
    suspend fun deleteResponses(sessionIds: List<Long>): Int

    @Query("DELETE FROM error_observations WHERE session_id IN (:sessionIds)")
    suspend fun deleteObservations(sessionIds: List<Long>): Int

    @Query("DELETE FROM trial_observation_markers WHERE session_id IN (:sessionIds)")
    suspend fun deleteMarkers(sessionIds: List<Long>): Int

    @Query("DELETE FROM interview_notes WHERE session_id IN (:sessionIds)")
    suspend fun deleteInterviews(sessionIds: List<Long>): Int

    @Query("DELETE FROM consents WHERE session_id IN (:sessionIds)")
    suspend fun deleteConsents(sessionIds: List<Long>): Int

    @Query("DELETE FROM audit_events WHERE session_id IN (:sessionIds)")
    suspend fun deleteAuditEvents(sessionIds: List<Long>): Int

    @Query("DELETE FROM sessions WHERE id IN (:sessionIds)")
    suspend fun deleteSessions(sessionIds: List<Long>): Int
}
