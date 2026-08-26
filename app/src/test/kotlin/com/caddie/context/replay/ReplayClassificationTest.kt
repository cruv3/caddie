package com.caddie.context.replay

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayClassificationTest {
    @Test
    fun `only complete semantic replay is eligible`() {
        assertEquals(ReplayClassification.ELIGIBLE, ReplayClassifier.classify(complete()))

        listOf(
            complete().copy(startPredicate = null),
            complete().copy(terminalPredicate = null),
            complete().copy(environment = complete().environment.copy(localeTag = null)),
            complete().copy(steps = emptyList()),
            complete().copy(steps = listOf(complete().steps.single().copy(precondition = null))),
            complete().copy(steps = listOf(complete().steps.single().copy(postcondition = null))),
            complete().copy(legacyContainsCoordinates = true),
        ).forEach {
            assertEquals(ReplayClassification.GUIDANCE_ONLY, ReplayClassifier.classify(it))
        }
    }

    @Test
    fun `rejected and superseded replay is disabled`() {
        listOf(ReplayStatus.REJECTED, ReplayStatus.SUPERSEDED).forEach { status ->
            assertEquals(
                ReplayClassification.DISABLED,
                ReplayClassifier.classify(complete().copy(status = status)),
            )
        }
    }

    @Test
    fun `semantic steps reject coordinate-shaped arguments`() {
        listOf("x", "y", "bounds", "tap").forEach { key ->
            assertThrows(IllegalArgumentException::class.java) {
                complete().steps.single().copy(arguments = mapOf(key to "unsafe"))
            }
        }
    }

    @Test
    fun `all imported legacy trajectories remain guidance only`() {
        val replayDir = listOf(
            File("src/main/assets/context/corpus/replays"),
            File("app/src/main/assets/context/corpus/replays"),
        ).single(File::isDirectory)

        val imported = collectReplayFiles(replayDir).flatMap { file ->
            LegacyReplayImporter.parse(file.readText())
        }

        assertEquals(38, imported.size)
        assertEquals(38, imported.map(ImportedReplay::id).distinct().size)
        assertTrue(imported.all { it.classification == ReplayClassification.GUIDANCE_ONLY })
    }

    private fun collectReplayFiles(dir: File): List<File> {
        val files = mutableListOf<File>()
        for (entry in dir.listFiles().orEmpty()) {
            if (entry.isFile && entry.name.endsWith(".json")) {
                files.add(entry)
            } else if (entry.isDirectory) {
                files.addAll(collectReplayFiles(entry))
            }
        }
        return files
    }

    private fun complete() = ReplayTrajectory(
        trajectoryId = "settings.wifi",
        revisionId = "settings.wifi@1",
        skillId = "settings.wifi",
        status = ReplayStatus.ACTIVE,
        environment = ReplayEnvironment(
            packageName = "com.android.settings",
            appVersion = "36",
            minSdk = 36,
            maxSdk = 36,
            buildFingerprint = "google/panther/test",
            localeTag = "de-DE",
        ),
        startPredicate = ReplayPredicate("package", "com.android.settings"),
        steps = listOf(
            ReplayStep(
                toolName = "smartphone_tap_element",
                arguments = mapOf("resourceId" to "android:id/title"),
                selectorFingerprint = "selector-sha256",
                precondition = ReplayPredicate("text-visible", "Network & internet"),
                postcondition = ReplayPredicate("text-visible", "Internet"),
            ),
        ),
        terminalPredicate = ReplayPredicate("text-visible", "Internet"),
        provenance = ReplayProvenance("settings/wifi.json", "a".repeat(64)),
        success = ReplaySuccessMetadata(verifiedRuns = 2, lastVerifiedBuild = "build-1"),
        legacyContainsCoordinates = false,
    )
}
