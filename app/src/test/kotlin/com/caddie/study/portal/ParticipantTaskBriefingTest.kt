package com.caddie.study.portal

import com.caddie.study.serialization.JsonValueCodec
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies briefing enrichment for assignments captured before the briefing fields existed. */
class ParticipantTaskBriefingTest {
    @Test
    fun `legacy captured task receives current briefing by task id`() {
        val captured = buildJsonObject {
            put("id", "task_maps_messenger")
            put("instruction", "Unveränderte Aufgabe")
        }

        val enriched = participantTaskResponse(captured) { taskId ->
            assertEquals("task_maps_messenger", taskId)
            mapOf(
                "situation" to "Anna wartet.",
                "apps" to listOf(
                    mapOf("name" to "Maps", "purpose" to "Route prüfen"),
                    mapOf("name" to "Telegram", "purpose" to "Anna informieren"),
                ),
                "goal" to "Anna kennt die Ankunftszeit.",
                "preparation" to "Sieh dir Annas letzte Nachricht an.",
            )
        }

        assertEquals("Unveränderte Aufgabe", enriched.getValue("instruction").jsonPrimitive.content)
        assertEquals(
            "Anna wartet.",
            enriched.getValue("briefing").jsonObject.getValue("situation").jsonPrimitive.content,
        )
        assertEquals(
            "Sieh dir Annas letzte Nachricht an.",
            enriched.getValue("briefing").jsonObject.getValue("preparation").jsonPrimitive.content,
        )
    }

    @Test
    fun `current briefing replaces captured presentation copy`() {
        val captured = buildJsonObject {
            put("id", "task_maps_messenger")
            put("briefing", buildJsonObject { put("situation", "Gespeicherter Kontext") })
        }

        val enriched = participantTaskResponse(captured) {
            mapOf("situation" to "Aktueller Kontext")
        }

        assertEquals(
            "Aktueller Kontext",
            enriched.getValue("briefing").jsonObject.getValue("situation").jsonPrimitive.content,
        )
    }

    @Test
    fun `participant response exposes no execution or manipulation metadata`() {
        val captured = JsonValueCodec.encode(
            mapOf(
                "id" to "task_maps_messenger",
                "instruction" to "Unveränderte Aufgabe",
                "criticality" to "high",
                "error_step_ids" to listOf("tap_send"),
                "assigned_error_variant_ids" to listOf("wrong_arrival"),
                "assigned_error_variants" to listOf(
                    mapOf("id" to "wrong_arrival", "description" to "Geplanter Fehler"),
                ),
            ),
        ).jsonObject

        val response = participantTaskResponse(captured) {
            mapOf("situation" to "Kontext", "apps" to emptyList<Any>(), "goal" to "Ziel")
        }

        assertEquals(setOf("id", "instruction", "briefing"), response.keys)
        assertFalse("criticality" in response)
        assertFalse("error_step_ids" in response)
        assertFalse("assigned_error_variant_ids" in response)
        assertFalse("assigned_error_variants" in response)
    }

    @Test
    fun `assignment identity ignores presentation briefing changes only`() {
        val captured = JsonValueCodec.encode(
            mapOf(
                "participant_id" to "P01",
                "tasks" to listOf(
                    mapOf(
                        "id" to "task_maps_messenger",
                        "instruction" to "Unveränderte Aufgabe",
                        "criticality" to "low",
                    ),
                ),
            ),
        ).jsonObject
        val authoritative = JsonValueCodec.encode(
            mapOf(
                "participant_id" to "P01",
                "tasks" to listOf(
                    mapOf(
                        "id" to "task_maps_messenger",
                        "instruction" to "Unveränderte Aufgabe",
                        "criticality" to "low",
                        "briefing" to mapOf("situation" to "Neuer Kontext"),
                    ),
                ),
            ),
        ).jsonObject

        assertTrue(assignmentMatchesCaptured(captured, authoritative))
        val capturedWithOlderBriefing = kotlinx.serialization.json.JsonObject(
            captured + ("tasks" to kotlinx.serialization.json.JsonArray(
                listOf(kotlinx.serialization.json.JsonObject(
                    captured.getValue("tasks").jsonArray.single().jsonObject +
                        ("briefing" to buildJsonObject { put("situation", "Alter Kontext") }),
                )),
            )),
        )
        assertTrue(assignmentMatchesCaptured(capturedWithOlderBriefing, authoritative))
        assertFalse(
            assignmentMatchesCaptured(
                captured,
                authoritativeWithTaskField(authoritative, "criticality", "high"),
            ),
        )
    }

    private fun authoritativeWithTaskField(
        assignment: kotlinx.serialization.json.JsonObject,
        key: String,
        value: String,
    ): kotlinx.serialization.json.JsonObject {
        val tasks = assignment.getValue("tasks").jsonArray
        val task = tasks.single().jsonObject
        return kotlinx.serialization.json.JsonObject(
            assignment + ("tasks" to kotlinx.serialization.json.JsonArray(
                listOf(kotlinx.serialization.json.JsonObject(task + (key to kotlinx.serialization.json.JsonPrimitive(value)))),
            )),
        )
    }
}
