package com.caddie.context.skill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetSkillLoaderTest {
    @Test
    fun `loader decodes individual skill files`() {
        val files = mapOf(
            "context/corpus/skills/apps/clock.json" to """
                {
                  "id":"apps.clock",
                  "title":"Clock",
                  "description":"Opens the clock application.",
                  "triggers":["open clock"],
                  "body":"## Verification\nClock is visible.",
                  "sourcePath":"apps/clock.md",
                  "sourceSha256":"${"a".repeat(64)}",
                  "replayId":null
                }
            """.trimIndent(),
        )

        val loader = AssetSkillLoader(
            AssetTextSource { path -> files[path] ?: throw NoSuchElementException(path) },
            AssetDirectorySource { path ->
                when (path) {
                    "context/corpus/skills" -> arrayOf("apps")
                    "context/corpus/skills/apps" -> arrayOf("clock.json")
                    else -> emptyArray()
                }
            },
        )

        assertEquals("apps.clock", loader.load().all.single().id)
    }

    @Test
    fun `loader collects skills from nested subdirectories`() {
        val files = mapOf(
            "context/corpus/skills/apps/clock.json" to skillJson("apps.clock"),
            "context/corpus/skills/settings/battery.json" to skillJson("settings.battery"),
        )

        val loader = AssetSkillLoader(
            AssetTextSource { path -> files[path] ?: throw NoSuchElementException(path) },
            AssetDirectorySource { path ->
                when (path) {
                    "context/corpus/skills" -> arrayOf("apps", "settings")
                    "context/corpus/skills/apps" -> arrayOf("clock.json")
                    "context/corpus/skills/settings" -> arrayOf("battery.json")
                    else -> emptyArray()
                }
            },
        )

        val catalog = loader.load()
        assertEquals(2, catalog.all.size)
        assertEquals("apps.clock", catalog.all[0].id)
        assertEquals("settings.battery", catalog.all[1].id)
    }

    @Test
    fun `empty corpus loads without error`() {
        val loader = AssetSkillLoader(
            AssetTextSource { throw IllegalStateException("should not be called") },
            AssetDirectorySource { emptyArray() },
        )

        assertTrue(loader.load().all.isEmpty())
    }

    private fun skillJson(id: String) = """
        {
          "id": "$id",
          "title": "Test",
          "description": "Test skill",
          "triggers": [],
          "body": "## Test",
          "sourcePath": "test/skill.md",
          "sourceSha256": "${"b".repeat(64)}",
          "replayId": null
        }
    """.trimIndent()
}
