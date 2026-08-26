package com.caddie.study.portal

import org.junit.Assert.*
import org.junit.Test

class StudyInstrumentsTest {
    @Test
    fun `validation rejects out of range scales and unknown choices`() {
        val valid = mapOf<String, Any>(
            "task_anomaly_detected" to "Nein",
            "task_criticality" to 5,
            "task_result_match" to "Vollständig",
            "task_assessment_confidence" to 5,
        )

        assertThrows(IllegalArgumentException::class.java) {
            validateInstrumentAnswers(TASK, TASK_ITEM_IDS, valid + ("task_criticality" to 8))
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateInstrumentAnswers(TASK, TASK_ITEM_IDS, valid + ("task_result_match" to "Vielleicht"))
        }
    }


    @Test
    fun `TASK instrument has correct items`() {
        assertTrue(TASK.items.isNotEmpty())
        assertEquals(7, TASK.items.size)
        assertEquals("task_anomaly_detected", TASK.items.first().id)
    }

    @Test
    fun `BLOCK instrument has more items than TASK`() {
        assertTrue(BLOCK.items.size > TASK.items.size)
    }

    @Test
    fun `END instrument has correct items`() {
        assertTrue(END.items.isNotEmpty())
        assertEquals(7, END.items.size)
    }

    @Test
    fun `validateInstrumentAnswers succeeds for correct answers`() {
        val answers = mapOf(
            "task_criticality" to 5,
            "task_result_match" to "Vollständig",
            "task_assessment_confidence" to 6,
            "task_observation" to "Alles ok",
            "task_anomaly_detected" to "Ja",
            "task_anomaly_timing" to "Während der Ausführung",
            "task_anomaly_response" to "Caddie korrigiert",
        )
        assertDoesNotThrow {
            validateInstrumentAnswers(TASK, TASK_ITEM_IDS, answers)
        }
    }

    @Test
    fun `validateInstrumentAnswers throws for wrong item ids`() {
        val answers = mapOf("nonexistent" to "value")
        val ex = assertThrows(
            IllegalArgumentException::class.java,
        ) {
            validateInstrumentAnswers(TASK, TASK_ITEM_IDS, answers)
        }
        assertTrue(ex.message!!.contains("every required item"))
    }

    @Test
    fun `validateInstrumentAnswers throws for empty answers`() {
        val ex = assertThrows(
            IllegalArgumentException::class.java,
        ) {
            validateInstrumentAnswers(TASK, TASK_ITEM_IDS, emptyMap())
        }
        assertTrue(ex.message!!.contains("every required item"))
    }

    @Test
    fun `validateInstrumentAnswers succeeds for BLOCK with all keys`() {
        val answers = BLOCK.items.associate { it.id to when (it.type) {
            InstrumentType.AGREEMENT_SCALE, InstrumentType.RATING_SCALE, InstrumentType.INTEGER -> 5
            InstrumentType.SINGLE_CHOICE, InstrumentType.TEXT -> "filled"
            InstrumentType.RANKING -> listOf<String>()
        } } as Map<String, Any>
        assertDoesNotThrow {
            validateInstrumentAnswers(BLOCK, BLOCK_ITEM_IDS, answers)
        }
    }

    @Test
    fun `validateInstrumentAnswers throws for subset of keys`() {
        val answers = mapOf(TASK.items[0].id to "value")
        val ex = assertThrows(
            IllegalArgumentException::class.java,
        ) {
            validateInstrumentAnswers(TASK, TASK_ITEM_IDS, answers)
        }
        assertTrue(ex.message!!.contains("every required item"))
    }

    @Test
    fun `optional first-impression text may be omitted`() {
        val answers = mapOf<String, Any>(
            "task_anomaly_detected" to "Nein",
            "task_result_match" to "Weitgehend",
            "task_assessment_confidence" to 6,
            "task_criticality" to 4,
        )

        assertDoesNotThrow { validateInstrumentAnswers(TASK, TASK_ITEM_IDS, answers) }
    }

    @Test
    fun `anomaly follow-up answers are required only when something was noticed`() {
        val common = mapOf<String, Any>(
            "task_result_match" to "Weitgehend",
            "task_assessment_confidence" to 6,
            "task_criticality" to 4,
        )

        assertDoesNotThrow {
            validateInstrumentAnswers(
                TASK,
                TASK_ITEM_IDS,
                common + ("task_anomaly_detected" to "Nein"),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateInstrumentAnswers(
                TASK,
                TASK_ITEM_IDS,
                common + ("task_anomaly_detected" to "Ja"),
            )
        }
        assertDoesNotThrow {
            validateInstrumentAnswers(
                TASK,
                TASK_ITEM_IDS,
                common + mapOf(
                    "task_anomaly_detected" to "Ja",
                    "task_anomaly_timing" to "Während der Ausführung",
                    "task_anomaly_response" to "Caddie korrigiert",
                ),
            )
        }
    }

    @Test
    fun `block contains thesis measures without TAM`() {
        val ids = BLOCK.items.map { it.id }

        assertEquals("condition_observation", ids.first())
        assertFalse(ids.any { it.startsWith("tam_") })
        assertTrue(
            ids.containsAll(
                listOf(
                    "nasa_mental",
                    "nasa_physical",
                    "nasa_temporal",
                    "nasa_performance",
                    "nasa_effort",
                    "nasa_frustration",
                ),
            ),
        )
        assertTrue(ids.contains("control_attention_moments"))
    }

    @Test
    fun `end asks oversight preference by consequence level`() {
        assertFalse(END.items.any { it.id == "smartphone_primary_use" })
        assertTrue(END_RANKING_IDS.containsAll(setOf("oversight_low_consequence", "oversight_high_consequence")))
        assertEquals("Keine Angabe", END.items.single { it.id == "gender" }.options.last())
    }

    @Test
    fun `ranking accepts the object shape emitted by the existing frontend`() {
        val anchors = END.items.single { it.id == "condition_ranking" }.options
        val ranking = anchors.mapIndexed { index, anchor -> anchor to index + 1 }.toMap()

        assertDoesNotThrow {
            validateInstrumentAnswers(
                END,
                END_RANKING_IDS,
                mapOf(
                    "condition_ranking" to ranking,
                    "oversight_low_consequence" to "Keine klare Präferenz",
                    "oversight_high_consequence" to "Wichtige Schritte einzeln bestätigen",
                ),
            )
        }
    }

    @Test
    fun `TASK_ITEM_IDS matches TASK items`() {
        assertEquals(TASK.items.map { it.id }.toSet(), TASK_ITEM_IDS)
    }

    @Test
    fun `BLOCK_ITEM_IDS matches BLOCK items`() {
        assertEquals(BLOCK.items.map { it.id }.toSet(), BLOCK_ITEM_IDS)
    }

    @Test
    fun `instrument version is non-empty`() {
        assertNotNull(INSTRUMENT_VERSION)
        assertTrue(INSTRUMENT_VERSION.isNotEmpty())
    }

    @Test
    fun `every task asks neutral error awareness and reaction questions`() {
        val ids = TASK.items.map { it.id }.toSet()

        assertTrue("task_anomaly_detected" in ids)
        assertTrue("task_anomaly_timing" in ids)
        assertTrue("task_anomaly_response" in ids)
        assertTrue(TASK.items.first { it.id == "task_anomaly_detected" }.required)
        assertTrue(TASK.items.none { it.prompt.contains("kontrolliert", ignoreCase = true) })
        assertTrue(TASK.items.none { it.prompt.contains("eingebaut", ignoreCase = true) })
        assertTrue(TASK.items.first().prompt.contains("besonders aufgefallen", ignoreCase = true))
        assertFalse(TASK.items.first().prompt.contains("Fehler", ignoreCase = true))
        assertFalse(TASK.items.first().prompt.contains("falsch", ignoreCase = true))
    }

    @Test
    fun `instrument items have unique ids`() {
        val taskIds = TASK.items.map { it.id }.toSet()
        assertEquals(TASK.items.size, taskIds.size)

        val blockIds = BLOCK.items.map { it.id }.toSet()
        assertEquals(BLOCK.items.size, blockIds.size)

        val endIds = END.items.map { it.id }.toSet()
        assertEquals(END.items.size, endIds.size)
    }

    private fun assertDoesNotThrow(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            fail("Expected no exception but got ${e::class.java.simpleName}: ${e.message}")
        }
    }
}
