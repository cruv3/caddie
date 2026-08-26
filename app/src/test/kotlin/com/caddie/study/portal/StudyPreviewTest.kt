package com.caddie.study.portal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** Verifies that the click-through preview is built from the real study instruments. */
class StudyPreviewTest {
    @Test
    fun `preview contains every participant screen and exact questionnaire items`() {
        val preview = studyPreviewResponse()

        assertEquals(
            listOf(
                "consent",
                "training",
                "task_card",
                "trial_running",
                "task_questionnaire",
                "block_questionnaire",
                "demographics",
                "preference_ranking",
                "interview",
                "debrief",
                "completed",
            ),
            preview.steps.map { it.session.workflow_state },
        )
        assertEquals(
            TASK.items.map { it.id },
            preview.steps[4].instrument!!.items.map { it.id },
        )
        assertEquals(
            BLOCK.items.map { it.id },
            preview.steps[5].instrument!!.items.map { it.id },
        )
        assertEquals(
            END.items.filter { it.id in END_DEMOGRAPHICS_IDS }.map { it.id },
            preview.steps[6].instrument!!.items.map { it.id },
        )
        assertEquals(
            END.items.filter { it.id in END_RANKING_IDS }.map { it.id },
            preview.steps[7].instrument!!.items.map { it.id },
        )
        assertNull(preview.steps[0].instrument)
        val briefing = preview.steps[2].task!!.getValue("briefing").jsonObject
        assertTrue(briefing.getValue("situation").jsonPrimitive.content.isNotBlank())
        assertTrue(briefing.getValue("goal").jsonPrimitive.content.isNotBlank())
        assertTrue(briefing.getValue("reference_values").jsonArray.isNotEmpty())
        assertEquals(
            preview.steps[2].task!!.getValue("instruction"),
            preview.steps[3].task!!.getValue("instruction"),
        )
        assertEquals(STUDY_INTERVIEW_QUESTIONS, preview.steps[8].interview_guide)
    }
}
