package com.caddie.study.export

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.Rule

class StudyBackupWriterTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `archive excludes course bonus identity data and removes legacy copies`() {
        val root = temporary.newFolder("study-backups")
        val writer = StudyBackupWriter(
            root = root,
            clock = Clock.fixed(Instant.parse("2026-08-02T12:34:56.789Z"), ZoneOffset.UTC),
        )

        val backup = writer.write(byteArrayOf(1), byteArrayOf(2))
        val directory = root.resolve("20260802-123456-789")

        assertEquals("study-backups/20260802-123456-789", backup.directory)
        assertArrayEquals(byteArrayOf(1), directory.resolve("study-research.zip").readBytes())
        assertArrayEquals(byteArrayOf(2), directory.resolve("study-consent.csv").readBytes())
        assertFalse(directory.resolve("study-course-bonus.csv").exists())
        assertFalse(root.listFiles().orEmpty().any { it.name.startsWith(".tmp-") })

        directory.resolve("study-course-bonus.csv").writeBytes(byteArrayOf(3))
        writer.deleteCourseBonusCopies()
        assertFalse(directory.resolve("study-course-bonus.csv").exists())
    }

    @Test
    fun `cleanup also removes course bonus copies from the legacy root`() {
        val root = temporary.newFolder("new-archives")
        val legacy = temporary.newFolder("legacy-archives")
        val legacyArchive = legacy.resolve("old").apply { mkdirs() }
        val identity = legacyArchive.resolve("study-course-bonus.csv").apply { writeText("identity") }
        val consent = legacyArchive.resolve("study-consent.csv").apply { writeText("consent") }

        StudyBackupWriter(root, legacyRoots = listOf(legacy)).deleteCourseBonusCopies()

        assertFalse(identity.exists())
        org.junit.Assert.assertTrue(consent.exists())
    }
}
