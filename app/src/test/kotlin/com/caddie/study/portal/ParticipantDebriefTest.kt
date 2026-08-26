package com.caddie.study.portal

import org.junit.Assert.assertEquals
import org.junit.Test

/** Verifies that the participant debrief reveals only deviations they actually encountered. */
class ParticipantDebriefTest {
    @Test
    fun `debrief excludes unassigned and non-injected error variants`() {
        val assignment: Map<String, Any> = mapOf(
            "error_tasks" to listOf("task_maps", "task_music"),
            "tasks" to listOf(
                task("task_maps", "Route senden", "err_route", "Falsches Ziel"),
                task("task_calendar", "Termin ändern", "err_hour", "Falsche Uhrzeit"),
                task("task_music", "Lied speichern", "err_song", "Falsches Lied"),
            ),
        )

        val result = participantDebriefEntries(
            assignment = assignment,
            injectedVariants = setOf(
                "task_maps" to "err_route",
                "task_calendar" to "err_hour",
            ),
        )

        assertEquals(
            listOf(
                mapOf(
                    "id" to "err_route",
                    "task_id" to "task_maps",
                    "task_instruction" to "Route senden",
                    "description" to "Falsches Ziel",
                ),
            ),
            result,
        )
    }

    private fun task(
        id: String,
        instruction: String,
        variantId: String,
        description: String,
    ): Map<String, Any> = mapOf(
        "id" to id,
        "instruction" to instruction,
        "assigned_error_variants" to listOf(
            mapOf("id" to variantId, "description" to description),
        ),
    )
}
