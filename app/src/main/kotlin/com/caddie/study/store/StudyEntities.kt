package com.caddie.study.store.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entities for the study portal research database.
 * Mirrors the Python PortalStores SQLite schema (stores.py).
 */

@Entity(tableName = "sessions")
data class StudySessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "participant_id") val participantId: String,
    @ColumnInfo(name = "mode") val mode: String,
    @ColumnInfo(name = "source") val source: String,
    @ColumnInfo(name = "original_study_at") val originalStudyAt: String? = null,
    @ColumnInfo(name = "original_time_precision") val originalTimePrecision: String? = null,
    @ColumnInfo(name = "entered_at") val enteredAt: String,
    @ColumnInfo(name = "status") val status: String = "in_progress",
    @ColumnInfo(name = "workflow_state") val workflowState: String,
    @ColumnInfo(name = "workflow_revision") val workflowRevision: Int = 0,
    @ColumnInfo(name = "resume_state") val resumeState: String? = null,
    @ColumnInfo(name = "current_trial_index") val currentTrialIndex: Int? = null,
    @ColumnInfo(name = "prepared_trial_index") val preparedTrialIndex: Int? = null,
    @ColumnInfo(name = "assignment_json") val assignmentJson: String,
    @ColumnInfo(name = "assignment_hash") val assignmentHash: String,
)

@Entity(tableName = "drafts", primaryKeys = ["session_id", "instrument_id", "position"])
data class StudyDraftEntity(
    @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "instrument_id") val instrumentId: String,
    @ColumnInfo(name = "position") val position: Int,
    val id: Long = 0,
    @ColumnInfo(name = "answers_json") val answersJson: String,
    @ColumnInfo(name = "missing_json") val missingJson: String = "{}",
    @ColumnInfo(name = "revision") val revision: Int = 0,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
)

@Entity(tableName = "responses", primaryKeys = ["session_id", "instrument_id", "position"])
data class StudyResponseEntity(
    @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "instrument_id") val instrumentId: String,
    @ColumnInfo(name = "position") val position: Int,
    val id: Long = 0,
    @ColumnInfo(name = "instrument_version") val instrumentVersion: String,
    @ColumnInfo(name = "answers_json") val answersJson: String,
    @ColumnInfo(name = "missing_json") val missingJson: String = "{}",
    @ColumnInfo(name = "source") val source: String,
    @ColumnInfo(name = "actor") val actor: String,
    @ColumnInfo(name = "submitted_at") val submittedAt: String,
    @ColumnInfo(name = "revision") val revision: Int = 0,
)

@Entity(tableName = "error_observations", primaryKeys = ["session_id", "trial_index"])
data class StudyObservationEntity(
    @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "trial_index") val trialIndex: Int,
    val id: Long = 0,
    @ColumnInfo(name = "task_id") val taskId: String,
    @ColumnInfo(name = "condition") val condition: String,
    @ColumnInfo(name = "intended_criticality") val intendedCriticality: String,
    @ColumnInfo(name = "error_variant_id") val errorVariantId: String? = null,
    @ColumnInfo(name = "spontaneous_detection") val spontaneousDetection: Boolean = false,
    @ColumnInfo(name = "detection_stage") val detectionStage: String,
    @ColumnInfo(name = "evidence_type") val evidenceType: String,
    @ColumnInfo(name = "moderator_prompt_given") val moderatorPromptGiven: Boolean = false,
    @ColumnInfo(name = "detected_only_after_prompt") val detectedOnlyAfterPrompt: Boolean = false,
    @ColumnInfo(name = "notes") val notes: String = "",
    @ColumnInfo(name = "submitted_at") val submittedAt: String,
)

@Entity(tableName = "trial_observation_markers", primaryKeys = ["session_id", "trial_index"])
data class StudyTrialMarkerEntity(
    @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "trial_index") val trialIndex: Int,
    val id: Long = 0,
    @ColumnInfo(name = "assigned_error") val assignedError: Boolean,
    @ColumnInfo(name = "outcome") val outcome: String,
    @ColumnInfo(name = "submitted_at") val submittedAt: String,
)

@Entity(tableName = "interview_notes", primaryKeys = ["session_id", "question_id"])
data class StudyInterviewNoteEntity(
    @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "question_id") val questionId: String,
    val id: Long = 0,
    @ColumnInfo(name = "answer") val answer: String,
    @ColumnInfo(name = "submitted_at") val submittedAt: String,
)

@Entity(tableName = "consents")
data class StudyConsentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "full_name") val fullName: String,
    @ColumnInfo(name = "consent_version") val consentVersion: String,
    @ColumnInfo(name = "consent_checksum") val consentChecksum: String,
    @ColumnInfo(name = "acknowledgements_json") val acknowledgementsJson: String,
    @ColumnInfo(name = "method") val method: String,
    @ColumnInfo(name = "consented_at") val consentedAt: String,
    @ColumnInfo(name = "investigator_confirmed_at") val investigatorConfirmedAt: String? = null,
    @ColumnInfo(name = "withdrawn_at") val withdrawnAt: String? = null,
)

/** Optional course-credit identity kept out of the research export. */
@Entity(tableName = "course_bonus_records")
data class StudyCourseBonusEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "full_name") val fullName: String,
    @ColumnInfo(name = "matriculation_number") val matriculationNumber: String,
    @ColumnInfo(name = "consented_at") val consentedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String? = null,
)

@Entity(tableName = "audit_events")
data class StudyAuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: Long? = null,
    @ColumnInfo(name = "actor") val actor: String,
    @ColumnInfo(name = "event_type") val eventType: String,
    @ColumnInfo(name = "details_json") val detailsJson: String,
    @ColumnInfo(name = "created_at") val createdAt: String,
)
