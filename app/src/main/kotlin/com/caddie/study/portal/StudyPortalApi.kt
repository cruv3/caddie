package com.caddie.study.portal

import com.caddie.study.runtime.model.ErrorObservation
import com.caddie.study.store.entity.StudySessionEntity

/** Defines the portal operations exposed through the embedded HTTP server. */
interface StudyPortalApi {
    /** Returns the passwordless investigator landing-page catalog. */
    suspend fun participantCatalog(): StudyParticipantCatalogResponse =
        StudyParticipantCatalogResponse()

    suspend fun participantDetail(participantId: String): StudyParticipantDetailResponse =
        StudyParticipantDetailResponse(participant_id = participantId)

    suspend fun deleteParticipant(participantId: String): Int

    suspend fun sessionDetail(sessionId: Long): StudyParticipantDetailResponse =
        throw IllegalArgumentException("session not found")

    suspend fun correctParticipantResponse(
        sessionId: Long,
        instrumentId: String,
        position: Int,
        answers: kotlinx.serialization.json.JsonObject,
        missing: kotlinx.serialization.json.JsonObject,
        expectedRevision: Int,
    ): StudyStoredResponse

    fun studyTestOptions(): StudyTestOptionsResponse =
        StudyTestOptionsResponse(tasks = emptyList(), conditions = emptyList())

    suspend fun startStudyTest(
        taskId: String,
        condition: String,
        injectError: Boolean,
    ): StudyTestRunResponse = error("study tests unavailable")

    suspend fun createLiveSession(participantId: String): Long
    suspend fun getSession(sessionId: Long): StudySessionEntity
    suspend fun transition(
        sessionId: Long,
        event: String,
        expectedRevision: Int,
        actor: String = "investigator",
    ): StudySessionEntity
    suspend fun prepareNextTrial(sessionId: Long, expectedRevision: Int): PreparedTrialResult
    suspend fun retryFailedTrial(sessionId: Long, expectedRevision: Int): PreparedTrialResult =
        throw IllegalArgumentException("failed-trial retry unavailable")
    suspend fun saveObservation(
        sessionId: Long,
        observation: ErrorObservation?,
        expectedRevision: Int,
        actor: String = "investigator",
    ): StudySessionEntity
    suspend fun recordLiveConsent(
        sessionId: Long,
        fullName: String,
        acknowledgements: List<String>,
    ): Long
    /** Records digital consent and advances to training as one operation. */
    suspend fun recordAndConfirmLiveConsent(
        sessionId: Long,
        fullName: String,
        acknowledgements: List<String>,
    ): StudySessionEntity
    suspend fun confirmLiveConsent(
        sessionId: Long,
        expectedRevision: Int,
        actor: String = "investigator",
    ): StudySessionEntity
    suspend fun submitParticipantResponse(
        sessionId: Long,
        answers: Map<String, Any>,
        expectedResponseRevision: Int,
        expectedWorkflowRevision: Int,
    ): ParticipantResponseResult
    suspend fun resetAfterTrial(sessionId: Long, expectedRevision: Int): StudySessionEntity
    suspend fun abortSession(
        sessionId: Long,
        reason: String,
        expectedRevision: Int,
    ): StudySessionEntity
    suspend fun submitLiveInterview(
        sessionId: Long,
        answers: Map<String, String>,
        expectedRevision: Int,
        actor: String = "investigator",
    ): StudySessionEntity
    suspend fun getSessionAssignment(sessionId: Long): Map<String, Any>
    suspend fun participantDebrief(sessionId: Long): List<Map<String, String>>
    suspend fun consentStatus(sessionId: Long): ConsentStatus?
    suspend fun latestSession(): StudySessionEntity?
    fun studySnapshot(): Map<String, Any>
    fun trialPrepared(session: StudySessionEntity, study: Map<String, Any>): Boolean
    suspend fun participantState(sessionId: Long): ParticipantStateResponse
    suspend fun reconcileParticipantFlow(sessionId: Long): ParticipantStateResponse =
        participantState(sessionId)
    suspend fun continueParticipantFlow(
        sessionId: Long,
        expectedRevision: Int,
    ): ParticipantStateResponse = throw IllegalArgumentException("participant continuation unavailable")
    suspend fun startParticipantTraining(
        sessionId: Long,
        expectedRevision: Int,
    ): ParticipantStateResponse = throw IllegalArgumentException("participant training unavailable")
    suspend fun saveParticipantDraft(
        sessionId: Long,
        answers: Map<String, Any>,
        expectedRevision: Int,
    ): ParticipantDraftResult

    suspend fun saveCourseBonus(fullName: String, matriculationNumber: String, consentedAt: String): Long
    suspend fun activeCourseBonus(): List<CourseBonusSummary>
    suspend fun deleteCourseBonus(id: Long)

    suspend fun researchExport(): ByteArray
    suspend fun consentExport(): ByteArray
    suspend fun courseBonusExport(): ByteArray
    suspend fun createBackup(): StudyBackup
}
