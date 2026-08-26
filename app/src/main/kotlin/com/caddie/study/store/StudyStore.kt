package com.caddie.study.store

import androidx.room.withTransaction
import com.caddie.study.serialization.JsonValueCodec
import com.caddie.study.export.StudyResearchExport
import com.caddie.study.runtime.audit.ControlledErrorEvent
import com.caddie.study.runtime.audit.StudyRuntimeEvent
import com.caddie.study.store.entity.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * High-level study data access layer with revision-guarded writes.
 *
 * Wraps Room DAOs and enforces optimistic concurrency via
 * workflow_revision counters (matching Python PortalStores).
 */
class StudyStore(private val db: StudyRoomDatabase) {

    private val sessionDao = db.sessionDao()
    private val draftDao = db.draftDao()
    private val responseDao = db.responseDao()
    private val observationDao = db.observationDao()
    private val interviewDao = db.interviewDao()
    private val consentDao = db.consentDao()
    private val courseBonusDao = db.courseBonusDao()
    private val auditDao = db.auditDao()
    private val deletionDao = db.deletionDao()

    private val json = Json { ignoreUnknownKeys = true }

    // ── Sessions ──

    suspend fun createSession(
        participantId: String,
        mode: String,
        source: String,
        assignmentJson: String,
        assignmentHash: String,
        originalStudyAt: String? = null,
        originalTimePrecision: String? = null,
    ): Long {
        val entity = StudySessionEntity(
            participantId = participantId,
            mode = mode,
            source = source,
            originalStudyAt = originalStudyAt,
            originalTimePrecision = originalTimePrecision,
            enteredAt = utcNow(),
            status = "in_progress",
            workflowState = "setup",
            workflowRevision = 0,
            resumeState = null,
            currentTrialIndex = null,
            assignmentJson = assignmentJson,
            assignmentHash = assignmentHash,
        )
        val id = sessionDao.insertSession(entity)
        audit(sessionId = id, actor = "system", eventType = "session_created", details = mapOf(
            "mode" to mode,
            "source" to source,
        ))
        return id
    }

    suspend fun getSession(sessionId: Long): StudySessionEntity {
        return sessionDao.getById(sessionId)
            ?: throw SessionNotFoundException(sessionId)
    }

    suspend fun latestSession(): StudySessionEntity? = sessionDao.getLatest()

    /** Removes one participant and all session-owned rows from the active Room database. */
    suspend fun deleteParticipant(participantId: String): Int = db.withTransaction {
        val sessions = deletionDao.sessionsForParticipant(participantId)
        require(sessions.none { it.status == "in_progress" }) {
            "active participant session must be aborted before deletion"
        }
        val sessionIds = sessions.map(StudySessionEntity::id)
        if (sessionIds.isEmpty()) return@withTransaction 0

        deletionDao.deleteDrafts(sessionIds)
        deletionDao.deleteResponses(sessionIds)
        deletionDao.deleteObservations(sessionIds)
        deletionDao.deleteMarkers(sessionIds)
        deletionDao.deleteInterviews(sessionIds)
        deletionDao.deleteConsents(sessionIds)
        deletionDao.deleteAuditEvents(sessionIds)
        deletionDao.deleteSessions(sessionIds)
    }

    // ── Workflow state transitions ──

    suspend fun updateWorkflowState(
        sessionId: Long,
        workflowState: String,
        resumeState: String?,
        expectedRevision: Int,
        event: String,
        actor: String,
    ): Int {
        val session = getSession(sessionId)
        checkRevision(session.workflowRevision, expectedRevision, "workflow")
        val newRevision = session.workflowRevision + 1
        val updated = sessionDao.updateWorkflowState(
            sessionId = sessionId,
            workflowState = workflowState,
            revision = newRevision,
            resumeState = resumeState,
            expectedRevision = expectedRevision,
            event = event,
        )
        if (updated == 0) throw RevisionConflict("workflow update failed — stale revision")
        audit(sessionId, actor, "workflow_transitioned", mapOf(
            "event" to event,
            "from_state" to session.workflowState,
            "to_state" to workflowState,
            "resume_state" to resumeState,
            "revision" to newRevision,
        ))
        return newRevision
    }

    // ── Trial assignment ──

    suspend fun setTrial(
        sessionId: Long,
        trialIndex: Int,
        expectedRevision: Int,
        actor: String,
        trialAttemptId: String = "trial-attempt-${java.util.UUID.randomUUID()}",
    ): Int {
        val session = getSession(sessionId)
        checkRevision(session.workflowRevision, expectedRevision, "trial")
        val newRevision = session.workflowRevision + 1
        val updated = sessionDao.setCurrentTrial(
            sessionId = sessionId,
            trialIndex = trialIndex,
            revision = newRevision,
            expectedRevision = expectedRevision,
        )
        if (updated == 0) throw RevisionConflict("trial update failed — stale revision")
        audit(sessionId, actor, "trial_prepared", mapOf(
            "trial_index" to trialIndex,
            "trial_attempt_id" to trialAttemptId,
            "revision" to newRevision,
        ))
        return newRevision
    }

    // ── Observations ──

    suspend fun saveObservation(
        sessionId: Long,
        trialIndex: Int,
        taskId: String,
        condition: String,
        intendedCriticality: String,
        errorVariantId: String?,
        observationData: Map<String, Any>?,
        expectedRevision: Int,
        actor: String,
    ): Int = db.withTransaction {
        val session = getSession(sessionId)
        checkRevision(session.workflowRevision, expectedRevision, "observation")
        if (
            session.workflowState != "investigator_observation" ||
            session.currentTrialIndex != trialIndex
        ) {
            throw RevisionConflict("observation does not match the current investigator trial")
        }
        val submittedAt = utcNow()

        // Insert marker
        observationDao.insertMarker(StudyTrialMarkerEntity(
            sessionId = sessionId,
            trialIndex = trialIndex,
            assignedError = errorVariantId != null,
            outcome = if (errorVariantId == null) "no_error_assigned" else "error_assigned",
            submittedAt = submittedAt,
        ))

        // Insert detailed observation if present
        if (observationData != null) {
            observationDao.insertObservation(StudyObservationEntity(
                sessionId = sessionId,
                trialIndex = trialIndex,
                taskId = taskId,
                condition = condition,
                intendedCriticality = intendedCriticality,
                errorVariantId = errorVariantId,
                spontaneousDetection = observationData["spontaneous_detection"] as? Boolean ?: false,
                detectionStage = observationData["detection_stage"] as? String ?: "",
                evidenceType = observationData["evidence_type"] as? String ?: "",
                moderatorPromptGiven = observationData["moderator_prompt_given"] as? Boolean ?: false,
                detectedOnlyAfterPrompt = observationData["detected_only_after_prompt"] as? Boolean ?: false,
                notes = observationData["notes"] as? String ?: "",
                submittedAt = submittedAt,
            ))
        }

        val newRevision = session.workflowRevision + 1
        val updated = sessionDao.updateWorkflowState(
            sessionId = sessionId,
            workflowState = "task_questionnaire",
            revision = newRevision,
            resumeState = null,
            expectedRevision = expectedRevision,
            event = "observation_saved",
        )
        if (updated == 0) throw RevisionConflict("observation update failed — stale revision")
        audit(sessionId, actor, "workflow_transitioned", mapOf(
            "event" to "observation_saved",
            "from_state" to session.workflowState,
            "to_state" to "task_questionnaire",
            "resume_state" to null,
            "revision" to newRevision,
        ))
        newRevision
    }

    // ── Drafts ──

    suspend fun saveDraft(
        sessionId: Long,
        instrumentId: String,
        position: Int,
        answers: Map<String, Any>,
        expectedRevision: Int,
        missing: Map<String, String> = emptyMap(),
    ): Long {
        // Check response already final
        if (responseDao.exists(sessionId, instrumentId, position)) {
            throw RevisionConflict("response is already final")
        }

        val existing = draftDao.getByPosition(sessionId, instrumentId, position)
        val currentRevision = existing?.revision ?: 0
        checkRevision(currentRevision, expectedRevision, "draft")
        val newRevision = currentRevision + 1
        val answersJson = JsonValueCodec.encode(answers).toString()
        val missingJson = JsonValueCodec.encode(missing).toString()
        val updatedAt = utcNow()

        if (existing != null) {
            draftDao.updateByPosition(
                sessionId = sessionId,
                instrumentId = instrumentId,
                position = position,
                answersJson = answersJson,
                missingJson = missingJson,
                revision = newRevision,
                updatedAt = updatedAt,
            )
        } else {
            val entity = StudyDraftEntity(
                sessionId = sessionId,
                instrumentId = instrumentId,
                position = position,
                answersJson = answersJson,
                missingJson = missingJson,
                revision = newRevision,
                updatedAt = updatedAt,
            )
            draftDao.insert(entity)
        }
        return newRevision.toLong()
    }

    suspend fun getDraft(
        sessionId: Long,
        instrumentId: String,
        position: Int,
    ): StudyDraftEntity? = draftDao.getByPosition(sessionId, instrumentId, position)

    // ── Responses ──

    suspend fun submitParticipantResponse(
        sessionId: Long,
        instrumentId: String,
        position: Int,
        answersJson: String,
        expectedResponseRevision: Int,
        expectedWorkflowRevision: Int,
        expectedWorkflowState: String,
        destinationWorkflowState: String,
        missingJson: String = "{}",
        instrumentVersion: String,
    ): Long = db.withTransaction {
        val session = getSession(sessionId)
        checkRevision(session.workflowRevision, expectedWorkflowRevision, "workflow")
        if (session.workflowState != expectedWorkflowState) {
            throw RevisionConflict("participant response workflow is stale")
        }
        if (responseDao.exists(sessionId, instrumentId, position)) {
            throw RevisionConflict("response is already final")
        }

        val draft = draftDao.getByPosition(sessionId, instrumentId, position)
        val currentRevision = draft?.revision ?: 0
        checkRevision(currentRevision, expectedResponseRevision, "response")
        val responseRevision = currentRevision + 1
        val submittedAt = utcNow()

        val entity = StudyResponseEntity(
            sessionId = sessionId,
            instrumentId = instrumentId,
            position = position,
            instrumentVersion = instrumentVersion,
            answersJson = answersJson,
            missingJson = missingJson,
            source = session.source,
            actor = "participant",
            submittedAt = submittedAt,
            revision = responseRevision,
        )
        val responseId = responseDao.insert(entity)
        val workflowRevision = session.workflowRevision + 1
        val updated = sessionDao.updateWorkflowStateFromState(
            sessionId = sessionId,
            expectedState = expectedWorkflowState,
            destinationState = destinationWorkflowState,
            revision = workflowRevision,
            expectedRevision = expectedWorkflowRevision,
        )
        if (updated == 0) throw RevisionConflict("participant response workflow changed concurrently")
        audit(sessionId, "participant", "participant_response_submitted", mapOf(
            "response_id" to responseId,
            "instrument_id" to instrumentId,
            "position" to position,
            "response_revision" to responseRevision,
            "from_state" to expectedWorkflowState,
            "to_state" to destinationWorkflowState,
            "workflow_revision" to workflowRevision,
        ))
        responseId
    }

    /** Corrects submitted questionnaire values without changing study progression. */
    suspend fun correctResponse(
        sessionId: Long,
        instrumentId: String,
        position: Int,
        answersJson: String,
        missingJson: String,
        expectedRevision: Int,
    ): StudyResponseEntity = db.withTransaction {
        require(instrumentId.isNotBlank()) { "instrument_id must be non-empty" }
        require(position >= 0) { "position must be non-negative" }
        val newAnswers = JsonValueCodec.decodeObject(
            json.parseToJsonElement(answersJson).jsonObject,
        )
        val newMissing = JsonValueCodec.decodeObject(
            json.parseToJsonElement(missingJson).jsonObject,
        )
        getSession(sessionId)
        val current = responseDao.getByPosition(sessionId, instrumentId, position)
            ?: throw IllegalArgumentException("response not found")
        checkRevision(current.revision, expectedRevision, "response")
        val newRevision = current.revision + 1
        val updated = responseDao.correctByPosition(
            sessionId = sessionId,
            instrumentId = instrumentId,
            position = position,
            answersJson = JsonValueCodec.encode(newAnswers).toString(),
            missingJson = JsonValueCodec.encode(newMissing).toString(),
            expectedRevision = expectedRevision,
            newRevision = newRevision,
        )
        if (updated == 0) throw RevisionConflict("response changed concurrently")
        audit(
            sessionId = sessionId,
            actor = "investigator",
            eventType = "investigator_value_corrected",
            details = mapOf(
                "instrument_id" to instrumentId,
                "position" to position,
                "old_answers" to JsonValueCodec.decodeObject(
                    json.parseToJsonElement(current.answersJson).jsonObject,
                ),
                "new_answers" to newAnswers,
                "old_missing" to JsonValueCodec.decodeObject(
                    json.parseToJsonElement(current.missingJson).jsonObject,
                ),
                "new_missing" to newMissing,
                "old_revision" to current.revision,
                "new_revision" to newRevision,
            ),
        )
        responseDao.getByPosition(sessionId, instrumentId, position)
            ?: error("corrected response disappeared")
    }

    // ── Consent ──

    suspend fun recordConsent(
        sessionId: Long,
        fullName: String,
        consentVersion: String,
        consentChecksum: String,
        acknowledgementsJson: String,
        method: String,
        consentedAt: String,
    ): Long = db.withTransaction {
        if (consentDao.getBySession(sessionId) != null) {
            throw RevisionConflict("consent is already recorded")
        }
        val entity = StudyConsentEntity(
            sessionId = sessionId,
            fullName = fullName,
            consentVersion = consentVersion,
            consentChecksum = consentChecksum,
            acknowledgementsJson = acknowledgementsJson,
            method = method,
            consentedAt = consentedAt,
        )
        consentDao.insert(entity)
    }

    /** Persists confirmed consent and advances the workflow in one Room transaction. */
    suspend fun recordConfirmedConsentAndUpdateWorkflowState(
        sessionId: Long,
        fullName: String,
        consentVersion: String,
        consentChecksum: String,
        acknowledgementsJson: String,
        method: String,
        consentedAt: String,
        confirmedAt: String,
        workflowState: String,
        expectedRevision: Int,
        event: String,
        actor: String,
    ): Int = db.withTransaction {
        val session = getSession(sessionId)
        checkRevision(session.workflowRevision, expectedRevision, "workflow")
        val existing = consentDao.getBySession(sessionId)
        if (existing?.investigatorConfirmedAt != null) {
            throw RevisionConflict("consent is already confirmed")
        }
        if (existing == null) {
            consentDao.insert(
                StudyConsentEntity(
                    sessionId = sessionId,
                    fullName = fullName,
                    consentVersion = consentVersion,
                    consentChecksum = consentChecksum,
                    acknowledgementsJson = acknowledgementsJson,
                    method = method,
                    consentedAt = consentedAt,
                    investigatorConfirmedAt = confirmedAt,
                ),
            )
        } else {
            confirmConsent(sessionId, confirmedAt)
        }
        updateWorkflowState(
            sessionId = sessionId,
            workflowState = workflowState,
            resumeState = null,
            expectedRevision = expectedRevision,
            event = event,
            actor = actor,
        )
    }

    suspend fun confirmConsent(sessionId: Long, confirmedAt: String) {
        val updated = consentDao.confirmConsent(sessionId, confirmedAt)
        if (updated == 0) throw ConsentNotReadyException(sessionId)
    }

    /** Confirms consent and advances the workflow in one Room transaction. */
    suspend fun confirmConsentAndUpdateWorkflowState(
        sessionId: Long,
        confirmedAt: String,
        workflowState: String,
        expectedRevision: Int,
        event: String,
        actor: String,
    ): Int = db.withTransaction {
        confirmConsent(sessionId, confirmedAt)
        updateWorkflowState(
            sessionId = sessionId,
            workflowState = workflowState,
            resumeState = null,
            expectedRevision = expectedRevision,
            event = event,
            actor = actor,
        )
    }

    suspend fun getConsent(sessionId: Long): StudyConsentEntity? =
        consentDao.getBySession(sessionId)

    // ── Separate identity exports ──

    suspend fun saveCourseBonus(
        fullName: String,
        matriculationNumber: String,
        consentedAt: String,
    ): Long {
        require(fullName.isNotBlank()) { "full_name must be non-empty" }
        require(matriculationNumber.isNotBlank()) { "matriculation_number must be non-empty" }
        require(consentedAt.isNotBlank()) { "consented_at must be non-empty" }
        return courseBonusDao.insert(
            StudyCourseBonusEntity(
                fullName = fullName.trim(),
                matriculationNumber = matriculationNumber.trim(),
                consentedAt = consentedAt.trim(),
            ),
        )
    }

    suspend fun researchExport(): StudyResearchExport = db.withTransaction {
        StudyResearchExport(
            sessions = sessionDao.getAll(),
            responses = responseDao.getAll(),
            observations = observationDao.getAllObservations(),
            markers = observationDao.getAllMarkers(),
            interviews = interviewDao.getAll(),
            auditEvents = auditDao.getAll(),
        )
    }

    suspend fun auditEvents(sessionId: Long): List<StudyAuditEntity> =
        auditDao.getForSession(sessionId)

    suspend fun consentExport(): List<StudyConsentEntity> = consentDao.getAll()

    suspend fun courseBonusExport(): List<StudyCourseBonusEntity> = courseBonusDao.getAll()

    suspend fun activeCourseBonus(id: Long): StudyCourseBonusEntity? =
        courseBonusDao.getActiveById(id)

    suspend fun deleteCourseBonus(id: Long) {
        check(courseBonusDao.deleteById(id) == 1) { "course bonus record disappeared: $id" }
    }

    suspend fun recordControlledError(event: ControlledErrorEvent) {
        val sessionId = event.studyRunId.toLongOrNull()
            ?: error("study_run_id must identify a Room session")
        getSession(sessionId)
        val details = mapOf(
            "trial_attempt_id" to event.trialAttemptId,
            "trial_index" to event.trialIndex,
            "task_id" to event.taskId,
            "step_id" to event.stepId,
            "error_variant_id" to event.errorVariantId,
            "field" to event.field,
            "correct_value" to event.correctValue,
            "wrong_value" to event.wrongValue,
        )
        auditDao.insertOnce(
            StudyAuditEntity(
                id = controlledErrorAuditId(event),
                sessionId = sessionId,
                actor = "system",
                eventType = "error_injected",
                detailsJson = JsonValueCodec.encode(details).toString(),
                createdAt = utcNow(),
            ),
        )
    }

    suspend fun recordRuntimeEvent(event: StudyRuntimeEvent) {
        val sessionId = event.studyRunId.toLongOrNull()
            ?: error("study_run_id must identify a Room session")
        getSession(sessionId)
        audit(
            sessionId = sessionId,
            actor = "native_executor",
            eventType = event.eventType,
            details = mapOf(
                "participant_id" to event.participantId,
                "trial_index" to event.trialIndex,
                "task_id" to event.taskId,
            ) + event.details,
        )
    }

    // ── Interview notes ──

    suspend fun submitInterview(
        sessionId: Long,
        answers: Map<String, String>,
        expectedWorkflowState: String,
        destinationWorkflowState: String,
        expectedRevision: Int,
        actor: String,
    ): Int = db.withTransaction {
        val session = getSession(sessionId)
        checkRevision(session.workflowRevision, expectedRevision, "interview workflow")
        if (session.workflowState != expectedWorkflowState) {
            throw RevisionConflict("interview workflow is stale")
        }
        val submittedAt = utcNow()
        answers.forEach { (questionId, answer) ->
            interviewDao.insert(
                StudyInterviewNoteEntity(
                    sessionId = sessionId,
                    questionId = questionId,
                    answer = answer,
                    submittedAt = submittedAt,
                ),
            )
        }
        val newRevision = session.workflowRevision + 1
        val updated = sessionDao.updateWorkflowStateFromState(
            sessionId = sessionId,
            expectedState = expectedWorkflowState,
            destinationState = destinationWorkflowState,
            revision = newRevision,
            expectedRevision = expectedRevision,
        )
        if (updated == 0) throw RevisionConflict("interview workflow changed concurrently")
        audit(sessionId, actor, "workflow_transitioned", mapOf(
            "event" to "interview_submitted",
            "from_state" to expectedWorkflowState,
            "to_state" to destinationWorkflowState,
            "resume_state" to null,
            "revision" to newRevision,
        ))
        newRevision
    }

    // ── Audit ──

    private suspend fun audit(
        sessionId: Long?,
        actor: String,
        eventType: String,
        details: Map<String, Any?>,
    ) {
        val entity = StudyAuditEntity(
            sessionId = sessionId,
            actor = actor,
            eventType = eventType,
            detailsJson = JsonValueCodec.encode(details).toString(),
            createdAt = utcNow(),
        )
        auditDao.insert(entity)
    }

    // ── Helpers ──

    private fun checkRevision(current: Int, expected: Int, context: String) {
        if (current != expected) {
            throw RevisionConflict(
                "expected $context revision $expected, found $current"
            )
        }
    }

    companion object {
        fun utcNow(): String =
            java.time.Instant.now().toString()

        private fun controlledErrorAuditId(event: ControlledErrorEvent): Long {
            val stableKey = listOf(
                event.studyRunId,
                event.trialAttemptId,
                event.trialIndex,
                event.taskId,
                event.stepId,
                event.errorVariantId,
            ).joinToString("\u001f")
            val bits = java.util.UUID.nameUUIDFromBytes(stableKey.encodeToByteArray())
                .mostSignificantBits and Long.MAX_VALUE
            return -(bits.coerceAtMost(Long.MAX_VALUE - 1) + 1)
        }
    }
}

// ── Exceptions ──

sealed class StudyStoreException(message: String) : RuntimeException(message)

class RevisionConflict(message: String) : StudyStoreException(message)

class SessionNotFoundException(id: Long) :
    StudyStoreException("unknown session: $id")

class ConsentNotReadyException(sessionId: Long) :
    StudyStoreException("consent not ready for session $sessionId")
