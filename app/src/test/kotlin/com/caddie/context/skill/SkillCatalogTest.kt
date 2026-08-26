package com.caddie.context.skill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SkillCatalogTest {
    @Test
    fun `catalog sorts skills by id`() {
        val catalog = SkillCatalog(listOf(skill("settings.wifi"), skill("apps.clock")))

        assertEquals(listOf("apps.clock", "settings.wifi"), catalog.all.map(Skill::id))
        assertEquals("Clock", catalog.get("apps.clock")?.title)
    }

    @Test
    fun `catalog rejects duplicate ids`() {
        assertThrows(IllegalArgumentException::class.java) {
            SkillCatalog(listOf(skill("apps.clock"), skill("apps.clock")))
        }
    }

    @Test
    fun `catalog rejects blank required metadata`() {
        assertThrows(IllegalArgumentException::class.java) {
            SkillCatalog(listOf(skill("apps.clock").copy(description = " ")))
        }
    }

    private fun skill(id: String) = Skill(
        id = id,
        title = "Clock",
        description = "Opens the clock application.",
        triggers = listOf("open clock"),
        body = "## Verification\nClock is visible.",
        sourcePath = "apps/clock.md",
        sourceSha256 = "a".repeat(64),
        replayId = null,
    )
}
