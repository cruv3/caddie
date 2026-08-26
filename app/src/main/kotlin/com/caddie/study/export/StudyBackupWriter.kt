package com.caddie.study.export

import com.caddie.study.portal.StudyBackup
import java.io.File
import java.time.Clock
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Writes a private on-device export archive without course-bonus identity data. */
class StudyBackupWriter(
    private val root: File,
    private val legacyRoots: List<File> = emptyList(),
    private val clock: Clock = Clock.systemUTC(),
) {
    fun write(research: ByteArray, consent: ByteArray): StudyBackup {
        val name = FORMATTER.format(clock.instant())
        val directory = File(root, name)
        check(!directory.exists()) { "study backup directory already exists" }
        val temporary = File(root, ".tmp-$name-${UUID.randomUUID()}")
        check(temporary.mkdirs()) { "could not create temporary study backup directory" }
        try {
            File(temporary, "study-research.zip").writeBytes(research)
            File(temporary, "study-consent.csv").writeBytes(consent)
            check(temporary.renameTo(directory)) { "could not publish study backup atomically" }
        } finally {
            if (temporary.exists()) temporary.deleteRecursively()
        }
        return StudyBackup("study-backups/$name")
    }

    fun deleteCourseBonusCopies() {
        (listOf(root) + legacyRoots).flatMap { it.listFiles().orEmpty().asList() }
            .filter(File::isDirectory)
            .map { File(it, "study-course-bonus.csv") }
            .filter(File::exists)
            .forEach { check(it.delete()) { "could not delete ${it.name}" } }
    }

    private companion object {
        val FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC)
    }
}
