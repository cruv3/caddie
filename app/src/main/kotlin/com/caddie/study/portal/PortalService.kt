package com.caddie.study.portal

import com.caddie.study.store.StudyStore
import com.caddie.study.export.StudyExportBuilder
import com.caddie.study.export.StudyBackupWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import com.caddie.study.store.RevisionConflict
import com.caddie.study.store.entity.StudySessionEntity
import com.caddie.study.gateway.StudyGateway
import com.caddie.study.serialization.JsonValueCodec
import com.caddie.study.runtime.model.ErrorObservation
import com.caddie.study.runtime.model.ParticipantId
import com.caddie.study.runtime.model.WorkflowState
import com.caddie.study.runtime.model.RuntimeStudyCondition
import com.caddie.study.runtime.audit.StudyRuntimeEvent
import com.caddie.study.runtime.model.validateErrorObservation
import com.caddie.study.runtime.workflow.PortalWorkflow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Core portal orchestration service.
 * Bridges HTTP endpoints (Layer 7) → StudyGateway (Layer 5) → StudyStore (Layer 6).
 *
 * All public methods are `suspend` because StudyStore uses Room (coroutines).
 * The Ktor endpoints in [StudyPortalServer] call these directly.
 */
class PortalService(
    private val store: StudyStore,
    private val gateway: StudyGateway,
    private val exports: StudyExportBuilder = StudyExportBuilder(),
    private val backups: StudyBackupWriter? = null,
) : StudyPortalApi {

    private val courseBonusDeletionMutex = Mutex()

    override suspend fun participantCatalog(): StudyParticipantCatalogResponse {
        val data = store.researchExport()
        return StudyParticipantDashboard.catalog(
            sessions = data.sessions,
            markers = data.markers,
            audits = data.auditEvents,
        )
    }

    override suspend fun participantDetail(participantId: String): StudyParticipantDetailResponse {
        val canonicalId = ParticipantId.canonical(participantId)
        val data = store.researchExport()
        return StudyParticipantDashboard.detail(
            participantId = canonicalId,
            sessions = data.sessions,
            responses = data.responses,
            observations = data.observations,
            markers = data.markers,
            interviews = data.interviews,
            audits = data.auditEvents,
        )
    }

    override suspend fun deleteParticipant(participantId: String): Int = workflowMutex.withLock {
        store.deleteParticipant(ParticipantId.canonical(participantId))
    }

    override suspend fun sessionDetail(sessionId: Long): StudyParticipantDetailResponse {
        val data = store.researchExport()
        return StudyParticipantDashboard.sessionDetail(
            sessionId = sessionId,
            sessions = data.sessions,
            responses = data.responses,
            observations = data.observations,
            markers = data.markers,
            interviews = data.interviews,
            audits = data.auditEvents,
        )
    }

    override suspend fun correctParticipantResponse(
        sessionId: Long,
        instrumentId: String,
        position: Int,
        answers: kotlinx.serialization.json.JsonObject,
        missing: kotlinx.serialization.json.JsonObject,
        expectedRevision: Int,
    ): StudyStoredResponse {
        val schema = correctionSchema(instrumentId, position)
        validateInstrumentCorrection(
            instrument = schema.instrument,
            requiredItemIds = schema.itemIds,
            answers = JsonValueCodec.decodeObject(answers),
            missing = JsonValueCodec.decodeObject(missing),
        )
        val response = store.correctResponse(
            sessionId = sessionId,
            instrumentId = instrumentId,
            position = position,
            answersJson = answers.toString(),
            missingJson = missing.toString(),
            expectedRevision = expectedRevision,
        )
        return StudyStoredResponse(
            instrument_id = response.instrumentId,
            position = response.position,
            answers = json.parseToJsonElement(response.answersJson).jsonObject,
            missing = json.parseToJsonElement(response.missingJson).jsonObject,
            submitted_at = response.submittedAt,
            revision = response.revision,
        )
    }

    override fun studyTestOptions(): StudyTestOptionsResponse = StudyTestOptionsResponse(
        tasks = gateway.testTasks().map { task ->
            StudyTestTaskDto(
                id = task.getValue("id"),
                instruction = task.getValue("instruction"),
                criticality = task.getValue("criticality"),
            )
        },
        conditions = RuntimeStudyCondition.entries.map { it.wireValue },
    )

    override suspend fun startStudyTest(
        taskId: String,
        condition: String,
        injectError: Boolean,
    ): StudyTestRunResponse = workflowMutex.withLock {
        val parsedCondition = RuntimeStudyCondition.fromWire(condition)
        val task = studyTestOptions().tasks.singleOrNull { it.id == taskId }
            ?: throw IllegalArgumentException("unknown study task")
        val participantId = "TEST-${java.util.UUID.randomUUID().toString().take(8).uppercase()}"
        val assignment = mapOf(
            "participant_id" to participantId,
            "task_order" to listOf(taskId),
            "condition_order" to listOf(parsedCondition.wireValue),
            "error_tasks" to if (injectError) listOf(taskId) else emptyList<String>(),
            "tasks" to listOf(
                mapOf(
                    "id" to task.id,
                    "instruction" to task.instruction,
                    "criticality" to task.criticality,
                ),
            ),
            "run_scope" to "test",
        )
        val assignmentJson = JsonValueCodec.encode(assignment).toString()
        val sessionId = store.createSession(
            participantId = participantId,
            mode = "test",
            source = "test_digital",
            assignmentJson = assignmentJson,
            assignmentHash = "sha256:" + sha256Hex(assignmentJson),
        )
        val runtime = try {
            gateway.armTestTrial(
                participantId = participantId,
                taskId = taskId,
                condition = parsedCondition.wireValue,
                injectError = injectError,
                studyRunId = sessionId.toString(),
            )
        } catch (error: Throwable) {
            store.recordRuntimeEvent(
                StudyRuntimeEvent(
                    studyRunId = sessionId.toString(),
                    participantId = participantId,
                    trialIndex = 0,
                    taskId = taskId,
                    eventType = "technical_failure",
                    details = mapOf("reason" to error.message.orEmpty().take(500)),
                ),
            )
            throw error
        }
        store.updateWorkflowState(
            sessionId = sessionId,
            workflowState = "trial_running",
            resumeState = null,
            expectedRevision = 0,
            event = "test_trial_armed",
            actor = "investigator",
        )
        StudyTestRunResponse(
            session_id = sessionId,
            participant_id = participantId,
            instruction = task.instruction,
            condition = parsedCondition.wireValue,
            inject_error = injectError,
            runtime = JsonValueCodec.encode(runtime).jsonObject,
        )
    }

    override suspend fun saveCourseBonus(
        fullName: String,
        matriculationNumber: String,
        consentedAt: String,
    ): Long = store.saveCourseBonus(fullName, matriculationNumber, consentedAt)

    override suspend fun activeCourseBonus(): List<CourseBonusSummary> =
        store.courseBonusExport().map {
            CourseBonusSummary(it.id, it.fullName, it.matriculationNumber, it.consentedAt)
        }

    override suspend fun deleteCourseBonus(id: Long) {
        courseBonusDeletionMutex.withLock {
            if (store.activeCourseBonus(id) == null) return
            withContext(Dispatchers.IO) { backups?.deleteCourseBonusCopies() }
            store.deleteCourseBonus(id)
        }
    }

    override suspend fun researchExport(): ByteArray = withContext(Dispatchers.IO) {
        exports.researchZip(store.researchExport().withoutIsolatedTests())
    }

    override suspend fun consentExport(): ByteArray = withContext(Dispatchers.IO) {
        exports.consentCsv(store.consentExport())
    }

    override suspend fun courseBonusExport(): ByteArray = withContext(Dispatchers.IO) {
        exports.courseBonusCsv(store.courseBonusExport())
    }

    override suspend fun createBackup(): StudyBackup {
        val writer = requireNotNull(backups) { "backup storage unavailable" }
        val research = researchExport()
        val consent = consentExport()
        return withContext(Dispatchers.IO) { writer.write(research, consent) }
    }

    private val workflowMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    // ── Session creation ──

    override suspend fun createLiveSession(participantId: String): Long = workflowMutex.withLock {
        val canonicalId = ParticipantId.canonical(participantId)
        val activeExists = store.researchExport().sessions.any {
            it.participantId == canonicalId &&
                it.mode == "live" &&
                it.source == "live_digital" &&
                it.status == "in_progress"
        }
        require(!activeExists) { "participant already has an active live session" }
        val assignment = gateway.nextAssignment(canonicalId)
            ?: throw IllegalArgumentException("no assignment for participant $canonicalId")
        val assignmentJson = JsonValueCodec.encode(assignment).toString()
        val assignmentHash = "sha256:" + sha256Hex(assignmentJson)

        store.createSession(
            participantId = canonicalId,
            mode = "live",
            source = "live_digital",
            assignmentJson = assignmentJson,
            assignmentHash = assignmentHash,
        )
    }

    // ── Workflow transitions ──

    override suspend fun transition(
        sessionId: Long,
        event: String,
        expectedRevision: Int,
        actor: String,
    ): StudySessionEntity = workflowMutex.withLock {
        transitionWithoutLock(sessionId, event, expectedRevision, actor)
    }

    // ── Trial preparation ──

    override suspend fun prepareNextTrial(
        sessionId: Long,
        expectedRevision: Int,
    ): PreparedTrialResult = workflowMutex.withLock {
        prepareNextTrialWithoutLock(sessionId, expectedRevision)
    }

    private suspend fun prepareNextTrialWithoutLock(
        sessionId: Long,
        expectedRevision: Int,
        retryTrialIndex: Int? = null,
        actor: String = "investigator",
    ): PreparedTrialResult {
            val session = store.getSession(sessionId)
            require(WorkflowState.fromWire(session.workflowState) == WorkflowState.TRIAL_READY) {
                "prepare requires trial_ready state"
            }
            require(session.mode == "live") { "prepare requires live mode" }
            if (session.workflowRevision != expectedRevision) {
                throw RevisionConflict("stale workflow revision")
            }

            // Validate that no previous real-world trial is still active.
            val snapshot = gateway.snapshot()
            require(snapshot["mode"] in listOf("normal", "live")) { "gateway mode invalid" }
            val trialMap = snapshot["trial"] as? Map<String, Any> ?: emptyMap()
            require(trialMap["state"] !in listOf("armed", "running")) { "trial already active" }

            val captured = retryTrialIndex?.let { capturedAssignment(session, it) }
                ?: capturedAssignment(session)
            val authoritative = gateway.nextAssignment(session.participantId)
            require(authoritative != null) { "no authoritative assignment" }
            require(
                assignmentMatchesCaptured(
                    captured = json.parseToJsonElement(session.assignmentJson).jsonObject,
                    authoritative = JsonValueCodec.encode(authoritative).jsonObject,
                ),
            ) {
                "assignment changed after session creation"
            }

            val trialAttemptId = "trial-attempt-${java.util.UUID.randomUUID()}"

            // Persist the exact trial and attempt before fallible phone preparation. A retry can
            // therefore never lose its index even if preflight, reset, activation, or arming fails.
            store.setTrial(
                sessionId = sessionId,
                trialIndex = captured["trial_index"] as Int,
                expectedRevision = expectedRevision,
                actor = actor,
                trialAttemptId = trialAttemptId,
            )

            gateway.setMode("live")
            val afterMode = gateway.snapshot()
            require(afterMode["mode"] == "live") { "failed to enter live mode" }
            require(afterMode["phone_ready"] == true) { "phone not ready" }
            require(gateway.preflight()) { "preflight failed" }
            require(gateway.resetDevice()) { "study device reset failed" }

            gateway.activateParticipant(session.participantId)
            val afterActivation = gateway.snapshot()
            require(afterActivation["active_participant"] == session.participantId) {
                "participant activation failed"
            }

            val armedAssignment = captured + mapOf(
                "study_run_id" to sessionId.toString(),
                "trial_attempt_id" to trialAttemptId,
            )
            val armed = gateway.armNextTrial(armedAssignment)
            require(armedTrialMatches(armed, session, armedAssignment)) {
                "runtime did not arm the expected study trial"
            }

            return PreparedTrialResult(
                sessionId = sessionId,
                assignment = captured,
                armed = armed,
            )
    }

    override suspend fun retryFailedTrial(
        sessionId: Long,
        expectedRevision: Int,
    ): PreparedTrialResult = workflowMutex.withLock {
        retryFailedTrialWithoutLock(sessionId, expectedRevision, actor = "investigator")
    }

    /** Re-arms the same failed index; callers still own the surrounding workflow lock. */
    private suspend fun retryFailedTrialWithoutLock(
        sessionId: Long,
        expectedRevision: Int,
        actor: String,
    ): PreparedTrialResult {
        val session = store.getSession(sessionId)
        if (session.workflowRevision != expectedRevision) {
            throw RevisionConflict("stale workflow revision")
        }
        val trialIndex = requireNotNull(session.currentTrialIndex) {
            "retry requires the failed trial index"
        }
        val heldRunningTrial =
            session.workflowState == WorkflowState.TECHNICAL_HOLD.wireValue &&
                session.resumeState == WorkflowState.TRIAL_RUNNING.wireValue
        val prematurelyFinishedTrial =
            session.workflowState == WorkflowState.INVESTIGATOR_OBSERVATION.wireValue &&
                latestRuntimeEventForCurrentAttempt(
                    sessionId,
                    trialIndex,
                    "trial_completed",
                )?.let { terminal ->
                    val details = eventDetails(terminal.detailsJson)
                    details["outcome"] != "success" && !isCompletedControlledError(details)
                } == true
        require(heldRunningTrial || prematurelyFinishedTrial) {
            "retry requires a failed running trial"
        }

        val stopped = gateway.abortTrial("investigator_retry_failed_trial")
        require(stopped["mode"] == "normal") { "failed trial could not be stopped safely" }
        val ready = transitionWithoutLock(
            sessionId,
            "retry_failed_trial",
            expectedRevision,
            actor,
        )
        return prepareNextTrialWithoutLock(
            sessionId = sessionId,
            expectedRevision = ready.workflowRevision,
            retryTrialIndex = trialIndex,
            actor = actor,
        )
    }

    // ── Observation ──

    override suspend fun saveObservation(
        sessionId: Long,
        observation: ErrorObservation?,
        expectedRevision: Int,
        actor: String,
    ): StudySessionEntity = workflowMutex.withLock {
        saveObservationWithoutLock(sessionId, observation, expectedRevision, actor)
    }

    private suspend fun saveObservationWithoutLock(
        sessionId: Long,
        observation: ErrorObservation?,
        expectedRevision: Int,
        actor: String,
    ): StudySessionEntity {
            val session = store.getSession(sessionId)
            require(WorkflowState.fromWire(session.workflowState) == WorkflowState.INVESTIGATOR_OBSERVATION) {
                "observation requires investigator_observation state"
            }
            require(session.currentTrialIndex != null) { "observation requires current trial" }

            val captured = capturedAssignment(
                session = session,
                trialIndex = session.currentTrialIndex!!,
            )
            val injectError = captured["inject_error"] as Boolean

            if (injectError) {
                require(observation != null || actor == "system") {
                    "assigned-error trial requires observation"
                }
                observation?.let(::validateErrorObservation)
            } else {
                require(observation == null) { "no-error trial requires null observation" }
            }

            // Convert ErrorObservation model to Map for Room storage
            val observationData = observation?.let {
                mapOf(
                    "spontaneous_detection" to it.spontaneousDetection,
                    "detection_stage" to it.detectionStage.wireValue,
                    "evidence_type" to it.evidenceType.wireValue,
                    "moderator_prompt_given" to it.moderatorPromptGiven,
                    "detected_only_after_prompt" to it.detectedOnlyAfterPrompt,
                    "notes" to it.notes,
                )
            }

            // Save observation (revision-guarded, returns new revision)
            store.saveObservation(
                sessionId = sessionId,
                trialIndex = session.currentTrialIndex!!,
                taskId = captured["task_id"] as String,
                condition = captured["condition"] as String,
                intendedCriticality = captured["criticality"] as String,
                errorVariantId = if (injectError) captured["error_variant_ids"].toStringList().firstOrNull() else null,
                observationData = observationData,
                expectedRevision = expectedRevision,
                actor = actor,
            )

            return store.getSession(sessionId)
    }

    // ── Consent ──

    override suspend fun recordLiveConsent(
        sessionId: Long,
        fullName: String,
        acknowledgements: List<String>,
    ): Long = workflowMutex.withLock {
        val session = store.getSession(sessionId)
        require(session.mode == "live" && session.workflowState == WorkflowState.CONSENT.wireValue) {
            "consent is not available"
        }
        require(fullName.isNotBlank()) { "full name is required" }
        require(acknowledgements.toSet() == LIVE_CONSENT_ACKNOWLEDGEMENTS) {
            "all required consent acknowledgements are required"
        }
        val acknowledgementsJson = json.encodeToString<List<String>>(acknowledgements)
        store.recordConsent(
            sessionId = sessionId,
            fullName = fullName,
            consentVersion = LIVE_CONSENT_VERSION,
            consentChecksum = LIVE_CONSENT_CHECKSUM,
            acknowledgementsJson = acknowledgementsJson,
            method = "live_digital",
            consentedAt = utcNow(),
        )
    }

    override suspend fun recordAndConfirmLiveConsent(
        sessionId: Long,
        fullName: String,
        acknowledgements: List<String>,
    ): StudySessionEntity = workflowMutex.withLock {
        val session = store.getSession(sessionId)
        require(session.mode == "live" && session.workflowState == WorkflowState.CONSENT.wireValue) {
            "consent is not available"
        }
        require(fullName.isNotBlank()) { "full name is required" }
        require(acknowledgements.toSet() == LIVE_CONSENT_ACKNOWLEDGEMENTS) {
            "all required consent acknowledgements are required"
        }
        val nextState = PortalWorkflow.nextState(
            WorkflowState.CONSENT,
            "consent_confirmed",
            session.resumeState?.let(WorkflowState::fromWire),
        )
        val now = utcNow()
        store.recordConfirmedConsentAndUpdateWorkflowState(
            sessionId = sessionId,
            fullName = fullName,
            consentVersion = LIVE_CONSENT_VERSION,
            consentChecksum = LIVE_CONSENT_CHECKSUM,
            acknowledgementsJson = json.encodeToString<List<String>>(acknowledgements),
            method = "live_digital",
            consentedAt = now,
            confirmedAt = now,
            workflowState = nextState.wireValue,
            expectedRevision = session.workflowRevision,
            event = "consent_confirmed",
            actor = "system",
        )
        store.getSession(sessionId)
    }

    override suspend fun confirmLiveConsent(
        sessionId: Long,
        expectedRevision: Int,
        actor: String,
    ): StudySessionEntity = workflowMutex.withLock {
        val session = store.getSession(sessionId)
        val nextState = PortalWorkflow.nextState(
            WorkflowState.fromWire(session.workflowState),
            "consent_confirmed",
            session.resumeState?.let(WorkflowState::fromWire),
        )
        store.confirmConsentAndUpdateWorkflowState(
            sessionId = sessionId,
            confirmedAt = utcNow(),
            workflowState = nextState.wireValue,
            expectedRevision = expectedRevision,
            event = "consent_confirmed",
            actor = actor,
        )
        store.getSession(sessionId)
    }

    // ── Participant response ──

    override suspend fun submitParticipantResponse(
        sessionId: Long,
        answers: Map<String, Any>,
        expectedResponseRevision: Int,
        expectedWorkflowRevision: Int,
    ): ParticipantResponseResult = workflowMutex.withLock {
            val session = store.getSession(sessionId)
            require(session.mode == "live") { "requires live session" }

            val state = WorkflowState.fromWire(session.workflowState)
            val info = responseInfo(session)

            validateInstrumentAnswers(info.instrument, info.itemIds, answers)
            val answersJson = JsonValueCodec.encode(answers).toString()

            // Save as final response (revision-guarded against draft state)
            val destination = PortalWorkflow.nextState(state, info.event, null)
            val responseId = store.submitParticipantResponse(
                sessionId = sessionId,
                instrumentId = info.instrument.id,
                position = info.position,
                answersJson = answersJson,
                expectedResponseRevision = expectedResponseRevision,
                expectedWorkflowRevision = expectedWorkflowRevision,
                expectedWorkflowState = state.wireValue,
                destinationWorkflowState = destination.wireValue,
                instrumentVersion = INSTRUMENT_VERSION,
            )

            ParticipantResponseResult(
                responseId = responseId,
                sessionId = sessionId,
            )
    }

    // ── Reset after trial ──

    override suspend fun resetAfterTrial(
        sessionId: Long,
        expectedRevision: Int,
    ): StudySessionEntity = workflowMutex.withLock {
        resetAfterTrialWithoutLock(sessionId, expectedRevision)
    }

    private suspend fun resetAfterTrialWithoutLock(
        sessionId: Long,
        expectedRevision: Int,
    ): StudySessionEntity {
            val session = store.getSession(sessionId)
            require(WorkflowState.fromWire(session.workflowState) == WorkflowState.TRIAL_RESET) {
                "reset requires trial_reset state"
            }
            require(session.currentTrialIndex != null) { "reset requires current trial" }

            if (!gateway.resetDevice()) {
                return transitionWithoutLock(
                    sessionId, "technical_hold", expectedRevision, "system",
                )
            }

            val event = if (session.currentTrialIndex!! % 2 == 1) {
                "release_block_questionnaire"
            } else {
                "next_trial"
            }
            return transitionWithoutLock(sessionId, event, expectedRevision, "investigator")
    }

    override suspend fun abortSession(
        sessionId: Long,
        reason: String,
        expectedRevision: Int,
    ): StudySessionEntity = workflowMutex.withLock {
        val session = store.getSession(sessionId)
        if (session.workflowRevision != expectedRevision) {
            throw RevisionConflict("stale workflow revision")
        }
        val state = WorkflowState.fromWire(session.workflowState)
        PortalWorkflow.nextState(state, "abort", null)

        val snapshot = gateway.abortTrial(reason)
        require(snapshot["mode"] == "normal") { "gateway abort did not leave live mode" }
        require(gateway.resetDevice()) { "gateway reset after abort failed" }
        transitionWithoutLock(sessionId, "abort", expectedRevision, "investigator")
    }

    // ── Interview ──

    override suspend fun submitLiveInterview(
        sessionId: Long,
        answers: Map<String, String>,
        expectedRevision: Int,
        actor: String,
    ): StudySessionEntity = workflowMutex.withLock {
        validateInterviewAnswers(answers)
        val session = store.getSession(sessionId)
        val currentState = WorkflowState.fromWire(session.workflowState)
        val destination = PortalWorkflow.nextState(currentState, "interview_submitted", null)
        val orderedAnswers = STUDY_INTERVIEW_QUESTIONS.associate { question ->
            question.id to answers.getValue(question.id).trim()
        }
        store.submitInterview(
            sessionId = sessionId,
            answers = orderedAnswers,
            expectedWorkflowState = currentState.wireValue,
            destinationWorkflowState = destination.wireValue,
            expectedRevision = expectedRevision,
            actor = actor,
        )
        store.getSession(sessionId)
    }

    // ── Helpers ──

    private suspend fun transitionWithoutLock(
        sessionId: Long,
        event: String,
        expectedRevision: Int,
        actor: String,
    ): StudySessionEntity {
        val session = store.getSession(sessionId)
        val currentState = WorkflowState.fromWire(session.workflowState)
        val nextState = PortalWorkflow.nextState(
            currentState,
            event,
            session.resumeState?.let { WorkflowState.fromWire(it) },
        )
        val resumeState = if (nextState == WorkflowState.TECHNICAL_HOLD) {
            session.workflowState
        } else {
            null
        }

        store.updateWorkflowState(
            sessionId = sessionId,
            workflowState = nextState.wireValue,
            resumeState = resumeState,
            expectedRevision = expectedRevision,
            event = event,
            actor = actor,
        )
        return store.getSession(sessionId)
    }

    override suspend fun getSession(sessionId: Long): StudySessionEntity =
        store.getSession(sessionId)

    override suspend fun getSessionAssignment(sessionId: Long): Map<String, Any> {
        val session = store.getSession(sessionId)
        return JsonValueCodec.decodeObject(json.parseToJsonElement(session.assignmentJson).jsonObject)
    }

    override suspend fun participantDebrief(sessionId: Long): List<Map<String, String>> {
        val assignment = getSessionAssignment(sessionId)
        val injectedVariants = store.auditEvents(sessionId)
            .asSequence()
            .filter { it.eventType == "error_injected" }
            .mapNotNull {
                val details = eventDetails(it.detailsJson)
                val taskId = details["task_id"] as? String
                val variantId = details["error_variant_id"] as? String
                if (taskId == null || variantId == null) null else taskId to variantId
            }
            .toSet()
        return participantDebriefEntries(assignment, injectedVariants)
    }

    override suspend fun consentStatus(sessionId: Long): ConsentStatus? {
        val consent = store.getConsent(sessionId) ?: return null
        return ConsentStatus(
            recorded = true,
            confirmed = consent.investigatorConfirmedAt != null,
        )
    }

    override suspend fun latestSession(): StudySessionEntity? = store.latestSession()

    override fun studySnapshot(): Map<String, Any> = gateway.snapshot()

    override fun trialPrepared(
        session: StudySessionEntity,
        study: Map<String, Any>,
    ): Boolean {
        val trial = study["trial"] as? Map<*, *> ?: return false
        return session.workflowState == WorkflowState.TRIAL_READY.wireValue &&
            session.currentTrialIndex != null &&
            trial["state"] == "armed"
    }

    override suspend fun participantState(sessionId: Long): ParticipantStateResponse {
        val session = store.getSession(sessionId)
        val state = WorkflowState.fromWire(session.workflowState)
        val assignment = json.parseToJsonElement(session.assignmentJson).jsonObject
        val storedTask = if (state in PARTICIPANT_TASK_STATES) {
            session.currentTrialIndex?.let { assignment["tasks"]?.jsonArray?.getOrNull(it)?.jsonObject }
        } else {
            null
        }
        val task = storedTask?.let { captured ->
            participantTaskResponse(captured, gateway::participantTaskBriefing)
        }
        val info = responseInfoOrNull(session)
        val draft = info?.let { store.getDraft(sessionId, it.instrument.id, it.position) }
        return ParticipantStateResponse(
            session = ParticipantSessionSummary(
                id = session.id,
                participant_id = session.participantId,
                workflow_state = session.workflowState,
                workflow_revision = session.workflowRevision,
                current_trial_index = session.currentTrialIndex,
                resume_state = session.resumeState,
            ),
            task = task,
            instrument = info?.let { instrumentDto(it.instrument, it.title, it.itemIds) },
            draft = draft?.let {
                DraftDto(
                    answers = json.parseToJsonElement(it.answersJson).jsonObject,
                    missing = json.parseToJsonElement(it.missingJson).jsonObject,
                    revision = it.revision,
                )
            },
            error_variants = if (state in setOf(WorkflowState.DEBRIEF, WorkflowState.COMPLETED)) {
                participantDebrief(sessionId)
            } else {
                null
            },
            interview_guide = if (state == WorkflowState.INTERVIEW) {
                STUDY_INTERVIEW_QUESTIONS
            } else {
                null
            },
        )
    }

    /** Advances only internal workflow states and stops on the next participant-facing page. */
    override suspend fun reconcileParticipantFlow(sessionId: Long): ParticipantStateResponse {
        workflowMutex.withLock { reconcileParticipantFlowWithoutLock(sessionId) }
        return participantState(sessionId)
    }

    /** Handles participant-owned continue actions such as completing training or the debrief. */
    override suspend fun continueParticipantFlow(
        sessionId: Long,
        expectedRevision: Int,
    ): ParticipantStateResponse {
        workflowMutex.withLock {
            val session = store.getSession(sessionId)
            if (session.workflowRevision != expectedRevision) {
                throw RevisionConflict("stale workflow revision")
            }
            when (WorkflowState.fromWire(session.workflowState)) {
                WorkflowState.TRAINING -> transitionWithoutLock(
                    sessionId, "training_confirmed", expectedRevision, "participant",
                )
                WorkflowState.DEBRIEF -> transitionWithoutLock(
                    sessionId, "debrief_confirmed", expectedRevision, "participant",
                )
                WorkflowState.TECHNICAL_HOLD -> {
                    if (session.resumeState == WorkflowState.TRIAL_RUNNING.wireValue) {
                        val trialIndex = requireNotNull(session.currentTrialIndex)
                        val terminal = latestRuntimeEventForCurrentAttempt(
                            sessionId,
                            trialIndex,
                            "trial_completed",
                        )
                        if (terminal != null && isCompletedControlledError(terminal.detailsJson)) {
                            transitionWithoutLock(
                                sessionId,
                                "accept_completed_trial",
                                expectedRevision,
                                "system",
                            )
                        } else {
                            retryFailedTrialWithoutLock(
                                sessionId = sessionId,
                                expectedRevision = expectedRevision,
                                actor = "participant",
                            )
                        }
                    } else {
                        transitionWithoutLock(
                            sessionId, "resume", expectedRevision, "participant",
                        )
                    }
                }
                else -> throw IllegalArgumentException(
                    "participant continuation unavailable in ${session.workflowState}",
                )
            }
            reconcileParticipantFlowWithoutLock(sessionId)
        }
        return participantState(sessionId)
    }

    /** Prepares free practice in normal mode while keeping the participant in the training stage. */
    override suspend fun startParticipantTraining(
        sessionId: Long,
        expectedRevision: Int,
    ): ParticipantStateResponse {
        workflowMutex.withLock {
            val session = store.getSession(sessionId)
            if (session.workflowRevision != expectedRevision) {
                throw RevisionConflict("stale workflow revision")
            }
            require(WorkflowState.fromWire(session.workflowState) == WorkflowState.TRAINING) {
                "training sandbox requires training state"
            }
            require(gateway.startTraining()) { "normal practice mode could not be started" }
        }
        return participantState(sessionId)
    }

    private suspend fun reconcileParticipantFlowWithoutLock(sessionId: Long) {
        repeat(10) {
            val session = store.getSession(sessionId)
            when (WorkflowState.fromWire(session.workflowState)) {
                WorkflowState.TRIAL_READY -> {
                    val captured = capturedAssignment(session)
                    val trialAttemptId = latestPreparedAttemptId(
                        sessionId,
                        captured["trial_index"] as Int,
                    )
                    val alreadyArmed = session.preparedTrialIndex != null &&
                        trialAttemptId != null &&
                        armedTrialMatches(
                            gateway.snapshot(),
                            session,
                            captured + ("trial_attempt_id" to trialAttemptId),
                        )
                    if (!alreadyArmed) {
                        try {
                            prepareNextTrialWithoutLock(
                                sessionId,
                                session.workflowRevision,
                                actor = "system",
                            )
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            val latest = store.getSession(sessionId)
                            transitionWithoutLock(
                                sessionId,
                                "technical_hold",
                                latest.workflowRevision,
                                "system",
                            )
                            return
                        }
                    }
                    val prepared = store.getSession(sessionId)
                    transitionWithoutLock(
                        sessionId,
                        "release_task_card",
                        prepared.workflowRevision,
                        "system",
                    )
                }
                WorkflowState.TRIAL_RUNNING -> {
                    val trialIndex = session.currentTrialIndex ?: return
                    val terminal = latestRuntimeEventForCurrentAttempt(
                        sessionId,
                        trialIndex,
                        "trial_completed",
                    )
                        ?: return
                    val details = eventDetails(terminal.detailsJson)
                    val outcome = details["outcome"] as? String
                    if (outcome == "success" || isCompletedControlledError(details)) {
                        transitionWithoutLock(
                            sessionId,
                            "trial_finished",
                            session.workflowRevision,
                            "system",
                        )
                    } else {
                        transitionWithoutLock(
                            sessionId,
                            "technical_hold",
                            session.workflowRevision,
                            "system",
                        )
                        return
                    }
                }
                WorkflowState.INVESTIGATOR_OBSERVATION -> {
                    saveObservationWithoutLock(
                        sessionId,
                        null,
                        session.workflowRevision,
                        "system",
                    )
                }
                WorkflowState.INVESTIGATOR_RESULT_REVIEW -> transitionWithoutLock(
                    sessionId,
                    "result_reviewed",
                    session.workflowRevision,
                    "system",
                )
                WorkflowState.TRIAL_RESET -> resetAfterTrialWithoutLock(
                    sessionId,
                    session.workflowRevision,
                )
                else -> return
            }
        }
        error("participant workflow reconciliation did not reach a visible state")
    }

    private suspend fun latestRuntimeEventForCurrentAttempt(
        sessionId: Long,
        trialIndex: Int,
        eventType: String,
    ): com.caddie.study.store.entity.StudyAuditEntity? {
        val events = store.auditEvents(sessionId)
        val attemptBoundary = events.lastOrNull {
            it.eventType == "trial_prepared" && eventTrialIndex(it.detailsJson) == trialIndex
        } ?: return null
        return events.lastOrNull {
            it.id > attemptBoundary.id &&
                it.eventType == eventType &&
                eventTrialIndex(it.detailsJson) == trialIndex
        }
    }

    private suspend fun latestPreparedAttemptId(
        sessionId: Long,
        trialIndex: Int,
    ): String? = store.auditEvents(sessionId).lastOrNull {
        it.eventType == "trial_prepared" && eventTrialIndex(it.detailsJson) == trialIndex
    }?.let { event ->
        eventDetails(event.detailsJson)["trial_attempt_id"] as? String
    }

    private fun eventDetails(detailsJson: String): Map<String, Any> = runCatching {
        JsonValueCodec.decodeObject(json.parseToJsonElement(detailsJson).jsonObject)
    }.getOrDefault(emptyMap())

    private fun eventTrialIndex(detailsJson: String): Int? =
        eventDetails(detailsJson)["trial_index"] as? Int

    private fun isCompletedControlledError(detailsJson: String): Boolean =
        isCompletedControlledError(eventDetails(detailsJson))

    private fun isCompletedControlledError(details: Map<String, Any>): Boolean {
        val outcome = details["outcome"] as? String
        val errorsInjected = (details["errors_injected"] as? Number)?.toInt() ?: 0
        return outcome == "error_injected" ||
            (outcome == "verification_failed" && errorsInjected > 0)
    }

    private fun armedTrialMatches(
        snapshot: Map<String, Any>,
        session: StudySessionEntity,
        captured: Map<String, Any>,
    ): Boolean {
        val trial = snapshot["trial"] as? Map<*, *> ?: return false
        return snapshot["mode"] == "live" &&
            snapshot["active_participant"] == session.participantId &&
            trial["state"] == "armed" &&
            trial["task_id"] == captured["task_id"] &&
            trial["trial_index"] == captured["trial_index"] &&
            trial["condition"] == captured["condition"] &&
            trial["inject_error"] == captured["inject_error"] &&
            trial["trial_attempt_id"] == captured["trial_attempt_id"]
    }

    override suspend fun saveParticipantDraft(
        sessionId: Long,
        answers: Map<String, Any>,
        expectedRevision: Int,
    ): ParticipantDraftResult = workflowMutex.withLock {
        val info = responseInfo(store.getSession(sessionId))
        val revision = store.saveDraft(
            sessionId = sessionId,
            instrumentId = info.instrument.id,
            position = info.position,
            answers = answers,
            expectedRevision = expectedRevision,
        )
        ParticipantDraftResult(info.instrument.id, info.position, revision.toInt())
    }

    private fun capturedAssignment(
        session: StudySessionEntity,
        trialIndex: Int = session.preparedTrialIndex
            ?: session.currentTrialIndex?.plus(1)
            ?: 0,
    ): Map<String, Any> {
        val assignment = JsonValueCodec.decodeObject(
            json.parseToJsonElement(session.assignmentJson).jsonObject,
        )
        val taskOrder = (assignment["task_order"] as? List<String>).orEmpty()
        val conditions = (assignment["condition_order"] as? List<String>).orEmpty()
        val tasks = (assignment["tasks"] as? List<Map<String, Any>>).orEmpty()
        val errorTasks = (assignment["error_tasks"] as? List<String>).orEmpty()

        require(trialIndex < taskOrder.size) { "trial index out of range" }
        val taskId = taskOrder[trialIndex]
        val task = tasks[trialIndex]
        require(task["id"] == taskId) { "task metadata mismatch" }

        @Suppress("UNCHECKED_CAST")
        val variantIds = (task["assigned_error_variant_ids"] as? List<String>).orEmpty()
        return mapOf(
            "trial_index" to trialIndex,
            "task_id" to taskId,
            "condition" to conditions[trialIndex],
            "inject_error" to (taskId in errorTasks),
            "criticality" to (task["criticality"] as String),
            "error_variant_ids" to variantIds,
        )
    }

    private fun itemIdsForInstrument(instrument: Instrument): Set<String> =
        instrument.items.mapTo(mutableSetOf()) { it.id }

    private fun responseInfo(session: StudySessionEntity): ResponseInfo =
        responseInfoOrNull(session)
            ?: throw IllegalArgumentException("response unavailable in ${session.workflowState}")

    private fun correctionSchema(instrumentId: String, position: Int): CorrectionSchema =
        when (instrumentId) {
            TASK.id -> {
                require(position in 0..5) { "task response position must be between 0 and 5" }
                CorrectionSchema(TASK, TASK_ITEM_IDS)
            }
            BLOCK.id -> {
                require(position in 0..2) { "block response position must be between 0 and 2" }
                CorrectionSchema(BLOCK, BLOCK_ITEM_IDS)
            }
            END.id -> when (position) {
                0 -> CorrectionSchema(END, END_DEMOGRAPHICS_IDS)
                1 -> CorrectionSchema(END, END_RANKING_IDS)
                else -> throw IllegalArgumentException("end response position must be 0 or 1")
            }
            else -> throw IllegalArgumentException("unknown response instrument: $instrumentId")
        }

    private fun responseInfoOrNull(session: StudySessionEntity): ResponseInfo? {
        val state = WorkflowState.fromWire(session.workflowState)
        val trialIndex = session.currentTrialIndex
        return when (state) {
            WorkflowState.TASK_QUESTIONNAIRE -> {
                require(trialIndex != null) { "task response requires current trial" }
                ResponseInfo(
                    TASK,
                    itemIdsForInstrument(TASK),
                    trialIndex,
                    "task_response_submitted",
                    "Fragen zur Aufgabe",
                )
            }
            WorkflowState.BLOCK_QUESTIONNAIRE -> {
                require(trialIndex != null && trialIndex % 2 == 1 && trialIndex in 0..5) {
                    "block response requires completed block"
                }
                ResponseInfo(
                    BLOCK,
                    itemIdsForInstrument(BLOCK),
                    trialIndex / 2,
                    if (trialIndex / 2 == 2) "all_trials_completed" else "block_response_submitted",
                    "Fragen zum Aufgabenblock",
                )
            }
            WorkflowState.DEMOGRAPHICS -> ResponseInfo(
                END, END_DEMOGRAPHICS_IDS, 0, "demographics_submitted", "Angaben zu deiner Person",
            )
            WorkflowState.PREFERENCE_RANKING -> ResponseInfo(
                END, END_RANKING_IDS, 1, "ranking_submitted", "Abschließende Präferenz",
            )
            else -> null
        }
    }

    companion object {
        const val LIVE_CONSENT_VERSION = "01_einwilligungserklaerung.md:v1"
        const val LIVE_CONSENT_CHECKSUM =
            "sha256:16c4c3455fe8b6f5f015cfa36df6f5061bcb7fb6308d7e70858483a77fe94852"
        val LIVE_CONSENT_ACKNOWLEDGEMENTS = setOf(
            "study_information_read",
            "voluntary_participation",
            "data_processing_agreed",
            "withdrawal_understood",
        )

        fun sha256Hex(input: String): String {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val digest = md.digest(input.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }

        fun utcNow(): String =
            java.time.Instant.now().atZone(java.time.ZoneOffset.UTC).toString()

        private fun Any?.toStringList(): List<String> =
            when (this) {
                is List<*> -> this.map { it.toString() }
                is String -> listOf(this)
                else -> emptyList()
            }
    }

    private data class CorrectionSchema(
        val instrument: Instrument,
        val itemIds: Set<String>,
    )
}

internal fun participantTaskResponse(
    captured: JsonObject,
    lookup: (String) -> Map<String, Any>?,
): JsonObject {
    val taskId = captured["id"]?.jsonPrimitive?.contentOrNull
    val currentBriefing = taskId?.let(lookup)
    val enriched = if (currentBriefing == null) {
        captured
    } else {
        JsonObject(captured + ("briefing" to JsonValueCodec.encode(currentBriefing)))
    }
    return JsonObject(
        listOf("id", "instruction", "briefing")
            .mapNotNull { key -> enriched[key]?.let { key to it } }
            .toMap(),
    )
}

internal fun assignmentMatchesCaptured(
    captured: JsonObject,
    authoritative: JsonObject,
): Boolean {
    if (captured == authoritative) return true
    val capturedTasks = captured["tasks"]?.jsonArray ?: return false
    val authoritativeTasks = authoritative["tasks"]?.jsonArray ?: return false
    if (capturedTasks.size != authoritativeTasks.size) return false
    fun withoutBriefings(assignment: JsonObject, tasks: JsonArray): JsonObject =
        JsonObject(
            assignment + (
                "tasks" to JsonArray(tasks.map { task -> JsonObject(task.jsonObject - "briefing") })
            ),
        )
    return withoutBriefings(captured, capturedTasks) ==
        withoutBriefings(authoritative, authoritativeTasks)
}

internal fun participantDebriefEntries(
    assignment: Map<String, Any>,
    injectedVariants: Set<Pair<String, String>>,
): List<Map<String, String>> {
    @Suppress("UNCHECKED_CAST")
    val tasks = (assignment["tasks"] as? List<Map<String, Any>>).orEmpty()
    val assignedTaskIds = (assignment["error_tasks"] as? List<*>)
        .orEmpty()
        .filterIsInstance<String>()
        .toSet()
    return tasks
        .asSequence()
        .filter { it["id"] in assignedTaskIds }
        .flatMap { task ->
            val taskId = task["id"] as? String ?: return@flatMap emptySequence()
            @Suppress("UNCHECKED_CAST")
            val variants = (task["assigned_error_variants"] as? List<Map<String, Any>>).orEmpty()
            variants.asSequence()
                .filter { variant ->
                    val variantId = variant["id"] as? String
                    variantId != null && (taskId to variantId) in injectedVariants
                }
                .map { variant ->
                    mapOf(
                        "id" to (variant["id"] as? String).orEmpty(),
                        "task_id" to taskId,
                        "task_instruction" to (task["instruction"] as? String).orEmpty(),
                        "description" to (variant["description"] as? String).orEmpty(),
                    )
                }
        }
        .distinctBy { it["task_id"] to it["id"] }
        .toList()
}

private val PARTICIPANT_TASK_STATES = setOf(
    WorkflowState.TASK_CARD,
    WorkflowState.TRIAL_RUNNING,
    WorkflowState.TASK_QUESTIONNAIRE,
)

private data class ResponseInfo(
    val instrument: Instrument,
    val itemIds: Set<String>,
    val position: Int,
    val event: String,
    val title: String,
)

data class PreparedTrialResult(
    val sessionId: Long,
    val assignment: Map<String, Any>,
    val armed: Map<String, Any>,
)

data class ParticipantResponseResult(
    val responseId: Long,
    val sessionId: Long,
)

data class ParticipantDraftResult(
    val instrumentId: String,
    val position: Int,
    val revision: Int,
)

data class ConsentStatus(
    val recorded: Boolean,
    val confirmed: Boolean,
)
