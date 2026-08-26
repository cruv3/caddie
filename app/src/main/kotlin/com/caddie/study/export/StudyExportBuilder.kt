package com.caddie.study.export

import com.caddie.study.store.entity.StudyConsentEntity
import com.caddie.study.store.entity.StudyCourseBonusEntity
import com.caddie.study.store.entity.StudyAuditEntity
import com.caddie.study.store.entity.StudyInterviewNoteEntity
import com.caddie.study.store.entity.StudyObservationEntity
import com.caddie.study.store.entity.StudyResponseEntity
import com.caddie.study.store.entity.StudySessionEntity
import com.caddie.study.store.entity.StudyTrialMarkerEntity
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONObject

/** Builds downloadable study artifacts without mixing identity and research data. */
class StudyExportBuilder {
    fun researchZip(data: StudyResearchExport): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.writeText(
                "manifest.json",
                JSONObject(
                    mapOf(
                        "format" to "caddie-android-native-research-v1",
                        "sessions" to data.sessions.size,
                    ),
                ).toString(),
            )
            zip.writeText(
                "sessions.jsonl",
                jsonLines(data.sessions.map {
                    mapOf(
                            "id" to it.id,
                            "participant_id" to it.participantId,
                            "mode" to it.mode,
                            "source" to it.source,
                            "entered_at" to it.enteredAt,
                            "status" to it.status,
                            "workflow_state" to it.workflowState,
                            "workflow_revision" to it.workflowRevision,
                            "current_trial_index" to it.currentTrialIndex,
                            "assignment_json" to it.assignmentJson,
                            "assignment_hash" to it.assignmentHash,
                    )
                }),
            )
            zip.writeText("responses.jsonl", jsonLines(data.responses.map {
                mapOf("session_id" to it.sessionId, "instrument_id" to it.instrumentId, "position" to it.position, "instrument_version" to it.instrumentVersion, "answers_json" to it.answersJson, "missing_json" to it.missingJson, "source" to it.source, "actor" to it.actor, "submitted_at" to it.submittedAt, "revision" to it.revision)
            }))
            zip.writeText("error_observations.jsonl", jsonLines(data.observations.map {
                mapOf("session_id" to it.sessionId, "trial_index" to it.trialIndex, "task_id" to it.taskId, "condition" to it.condition, "intended_criticality" to it.intendedCriticality, "error_variant_id" to it.errorVariantId, "spontaneous_detection" to it.spontaneousDetection, "detection_stage" to it.detectionStage, "evidence_type" to it.evidenceType, "moderator_prompt_given" to it.moderatorPromptGiven, "detected_only_after_prompt" to it.detectedOnlyAfterPrompt, "notes" to it.notes, "submitted_at" to it.submittedAt)
            }))
            zip.writeText("trial_markers.jsonl", jsonLines(data.markers.map {
                mapOf("session_id" to it.sessionId, "trial_index" to it.trialIndex, "assigned_error" to it.assignedError, "outcome" to it.outcome, "submitted_at" to it.submittedAt)
            }))
            zip.writeText("interview_notes.jsonl", jsonLines(data.interviews.map {
                mapOf("session_id" to it.sessionId, "question_id" to it.questionId, "answer" to it.answer, "submitted_at" to it.submittedAt)
            }))
            zip.writeText("audit_events.jsonl", jsonLines(data.auditEvents.map {
                mapOf("id" to it.id, "session_id" to it.sessionId, "actor" to it.actor, "event_type" to it.eventType, "details_json" to it.detailsJson, "created_at" to it.createdAt)
            }))
        }
        return output.toByteArray()
    }

    fun consentCsv(rows: List<StudyConsentEntity>): ByteArray = csv(
        header = listOf(
            "id", "session_id", "full_name", "consent_version", "consent_checksum",
            "acknowledgements_json", "method", "consented_at",
            "investigator_confirmed_at", "withdrawn_at",
        ),
        rows = rows.map {
            listOf(
                it.id, it.sessionId, it.fullName, it.consentVersion, it.consentChecksum,
                it.acknowledgementsJson, it.method, it.consentedAt,
                it.investigatorConfirmedAt, it.withdrawnAt,
            )
        },
    )

    fun courseBonusCsv(rows: List<StudyCourseBonusEntity>): ByteArray = csv(
        header = listOf("id", "full_name", "matriculation_number", "consented_at", "deleted_at"),
        rows = rows.map {
            listOf(it.id, it.fullName, it.matriculationNumber, it.consentedAt, it.deletedAt)
        },
    )

    private fun csv(
        header: List<String>,
        rows: List<List<Any?>>,
    ): ByteArray = buildString {
        appendLine(header.joinToString(","))
        rows.forEach { row -> appendLine(row.joinToString(",") { csvCell(it?.toString().orEmpty()) }) }
    }.encodeToByteArray()

    private fun csvCell(value: String): String {
        val safeValue = if (value.firstOrNull() in setOf('=', '+', '-', '@', '\t', '\r', '\n')) {
            "'$value"
        } else {
            value
        }
        return if (safeValue.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${safeValue.replace("\"", "\"\"")}\""
        } else {
            safeValue
        }
    }

    private fun ZipOutputStream.writeText(name: String, value: String) {
        putNextEntry(ZipEntry(name))
        write(value.encodeToByteArray())
        closeEntry()
    }

    private fun jsonLines(rows: List<Map<String, Any?>>): String =
        rows.joinToString("\n", postfix = if (rows.isEmpty()) "" else "\n") { JSONObject(it).toString() }
}

/** Research-only rows; direct identifiers are deliberately represented elsewhere. */
data class StudyResearchExport(
    val sessions: List<StudySessionEntity> = emptyList(),
    val responses: List<StudyResponseEntity> = emptyList(),
    val observations: List<StudyObservationEntity> = emptyList(),
    val markers: List<StudyTrialMarkerEntity> = emptyList(),
    val interviews: List<StudyInterviewNoteEntity> = emptyList(),
    val auditEvents: List<StudyAuditEntity> = emptyList(),
) {
    /** Removes isolated test runs from canonical research and backup artifacts. */
    fun withoutIsolatedTests(): StudyResearchExport {
        val researchSessionIds = sessions
            .filterNot { session ->
                session.mode == "test" ||
                    session.source.startsWith("test_") ||
                    session.participantId.startsWith("TEST-")
            }
            .mapTo(mutableSetOf()) { it.id }
        return copy(
            sessions = sessions.filter { it.id in researchSessionIds },
            responses = responses.filter { it.sessionId in researchSessionIds },
            observations = observations.filter { it.sessionId in researchSessionIds },
            markers = markers.filter { it.sessionId in researchSessionIds },
            interviews = interviews.filter { it.sessionId in researchSessionIds },
            auditEvents = auditEvents.filter { it.sessionId == null || it.sessionId in researchSessionIds },
        )
    }
}
