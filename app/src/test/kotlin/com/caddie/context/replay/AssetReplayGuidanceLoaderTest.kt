package com.caddie.context.replay

import com.caddie.context.skill.AssetDirectorySource
import com.caddie.context.skill.AssetTextSource
import com.caddie.context.skill.AssetSkillLoader
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetReplayGuidanceLoaderTest {
    @Test
    fun `legacy coordinates are stripped from guidance`() {
        val path = "context/corpus/replays/settings/wifi.json"
        val source = mapOf(
            path to
                """
                {
                  "classification":"GUIDANCE_ONLY",
                  "id":"wifi@legacy",
                  "skillId":"settings.wifi",
                  "sourcePath":"settings/wifi.steps.json",
                  "sourceSha256":"${"a".repeat(64)}",
                  "legacyContainsCoordinates":true,
                  "steps":[
                    {"action":"tap_xy","x":123,"y":456},
                    {"action":"tap","bounds":{"left":1},"index":8,
                     "label":"Internet","resource_id":"android:id/title"}
                  ]
                }
                """.trimIndent(),
        )
        val loader = AssetReplayGuidanceLoader(
            source = AssetTextSource(source::getValue),
            directorySource = AssetDirectorySource { directory ->
                when (directory) {
                    "context/corpus/replays" -> arrayOf("settings")
                    "context/corpus/replays/settings" -> arrayOf("wifi.json")
                    else -> emptyArray()
                }
            },
        )

        val replay = loader.load().get("wifi@legacy")!!

        assertEquals("settings.wifi", replay.skillId)
        assertTrue(replay.guidance.contains("guidance only", ignoreCase = true))
        assertTrue(replay.guidance.contains("Internet"))
        assertTrue(replay.guidance.contains("android:id/title"))
        assertFalse(replay.guidance.contains("123"))
        assertFalse(replay.guidance.contains("456"))
        assertFalse(replay.guidance.contains("bounds"))
        assertFalse(replay.guidance.contains("index"))
        assertFalse(replay.guidance.contains("tap_xy"))
    }

    @Test
    fun `production replay references are complete and coordinate free`() {
        val assets = listOf(File("src/main/assets"), File("app/src/main/assets"))
            .single(File::isDirectory)
        val source = AssetTextSource { path -> File(assets, path).readText() }
        val directories = AssetDirectorySource { path ->
            File(assets, path).list()?.map { it }.orEmpty().toTypedArray()
        }
        val replays = AssetReplayGuidanceLoader(source, directories).load()
        val skills = AssetSkillLoader(source, directories).load()

        assertEquals(38, replays.all.size)
        assertTrue(replays.all.none { replay ->
            listOf("bounds=", "x=", "y=", "index=", "tap_xy").any {
                it in replay.guidance
            }
        })
        val referenced = skills.all.mapNotNull { skill ->
            skill.replayId?.let { replayId -> replayId to skill.id }
        }
        assertEquals(38, referenced.size)
        referenced.forEach { (replayId, skillId) ->
            assertEquals(skillId, replays.get(replayId)?.skillId)
        }
    }
}
