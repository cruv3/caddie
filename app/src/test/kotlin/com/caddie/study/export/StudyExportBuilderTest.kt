package com.caddie.study.export

import com.caddie.study.store.entity.StudyConsentEntity
import com.caddie.study.store.entity.StudyCourseBonusEntity
import com.caddie.study.store.entity.StudySessionEntity
import com.caddie.study.store.entity.StudyResponseEntity
import com.caddie.study.store.entity.StudyAuditEntity
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyExportBuilderTest {
    @Test
    fun `canonical research data excludes test sessions and their dependent rows`() {
        val live = session(id = 1, participantId = "P01", mode = "live", source = "live_digital")
        val test = session(id = 2, participantId = "TEST-ABC", mode = "test", source = "test_digital")
        val filtered = StudyResearchExport(
            sessions = listOf(live, test),
            responses = listOf(response(1), response(2)),
            auditEvents = listOf(audit(1, 1), audit(2, 2), audit(3, null)),
        ).withoutIsolatedTests()

        assertEquals(listOf(1L), filtered.sessions.map { it.id })
        assertEquals(listOf(1L), filtered.responses.map { it.sessionId })
        assertEquals(listOf(1L, null), filtered.auditEvents.map { it.sessionId })
    }

    @Test
    fun `research zip contains research rows but excludes direct identifiers`() {
        val bytes = StudyExportBuilder().researchZip(
            StudyResearchExport(
                sessions = listOf(
                    StudySessionEntity(
                        id = 7,
                        participantId = "TEST-NATIVE-READINESS",
                        mode = "live",
                        source = "live",
                        enteredAt = "2026-08-02T12:00:00Z",
                        workflowState = "completed",
                        assignmentJson = "{}",
                        assignmentHash = "hash",
                    ),
                ),
            ),
        )

        val entries = unzip(bytes)

        assertTrue(entries.keys.containsAll(listOf("manifest.json", "sessions.jsonl")))
        assertFalse(entries.containsKey("drafts.jsonl"))
        assertTrue(entries.getValue("sessions.jsonl").contains("TEST-NATIVE-READINESS"))
        assertFalse(entries.values.any { it.contains("Andreas Example") })
        assertFalse(entries.keys.any { it.contains("consent") || it.contains("course_bonus") })
    }

    @Test
    fun `identifier exports are separate escaped csv files`() {
        val builder = StudyExportBuilder()
        val consent = builder.consentCsv(
            listOf(
                StudyConsentEntity(
                    id = 1,
                    sessionId = 7,
                    fullName = "Example, \"A\"",
                    consentVersion = "v1",
                    consentChecksum = "sum",
                    acknowledgementsJson = "[]",
                    method = "digital",
                    consentedAt = "2026-08-02T12:00:00Z",
                ),
            ),
        ).decodeToString()
        val bonus = builder.courseBonusCsv(
            listOf(
                StudyCourseBonusEntity(
                    id = 2,
                    fullName = "Student Name",
                    matriculationNumber = "12345",
                    consentedAt = "2026-08-02T12:00:00Z",
                ),
            ),
        ).decodeToString()

        assertTrue(consent.contains("\"Example, \"\"A\"\"\""))
        assertFalse(consent.contains("12345"))
        assertTrue(bonus.contains("Student Name,12345"))
        assertFalse(bonus.contains("TEST-NATIVE-READINESS"))
    }

    @Test
    fun `identifier csv neutralizes spreadsheet formulas`() {
        val csv = StudyExportBuilder().courseBonusCsv(
            listOf(
                StudyCourseBonusEntity(
                    id = 1,
                    fullName = "=HYPERLINK(\"https://example.invalid\")",
                    matriculationNumber = "+12345",
                    consentedAt = "2026-08-03T12:00:00Z",
                ),
            ),
        ).decodeToString()

        assertTrue(csv.contains("'="))
        assertTrue(csv.contains("'+12345"))
        assertFalse(csv.lines().drop(1).any { it.startsWith("=") || it.startsWith("+") })
    }

    private fun unzip(bytes: ByteArray): Map<String, String> {
        val result = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                result[entry.name] = zip.readBytes().decodeToString()
            }
        }
        return result
    }

    private fun session(id: Long, participantId: String, mode: String, source: String) =
        StudySessionEntity(
            id = id,
            participantId = participantId,
            mode = mode,
            source = source,
            enteredAt = "2026-08-05T12:00:00Z",
            workflowState = "completed",
            assignmentJson = "{}",
            assignmentHash = "hash",
        )

    private fun response(sessionId: Long) = StudyResponseEntity(
        sessionId = sessionId,
        instrumentId = "task",
        position = 0,
        instrumentVersion = "v1",
        answersJson = "{}",
        source = "test",
        actor = "participant",
        submittedAt = "2026-08-05T12:00:00Z",
    )

    private fun audit(id: Long, sessionId: Long?) = StudyAuditEntity(
        id = id,
        sessionId = sessionId,
        actor = "test",
        eventType = "test",
        detailsJson = "{}",
        createdAt = "2026-08-05T12:00:00Z",
    )
}
