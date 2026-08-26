package com.caddie.study.portal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StudyInterviewTest {
    @Test
    fun `guide contains six neutral structured questions`() {
        assertEquals(6, STUDY_INTERVIEW_QUESTIONS.size)
        assertEquals(6, STUDY_INTERVIEW_QUESTIONS.map { it.id }.toSet().size)
        assertEquals("overall_experience", STUDY_INTERVIEW_QUESTIONS.first().id)
        assertEquals("delegation_boundary", STUDY_INTERVIEW_QUESTIONS.last().id)
    }

    @Test
    fun `structured answers must match guide ids`() {
        val answers = STUDY_INTERVIEW_QUESTIONS.associate { it.id to "Antwort" }
        validateInterviewAnswers(answers)

        assertThrows(IllegalArgumentException::class.java) {
            validateInterviewAnswers(answers - STUDY_INTERVIEW_QUESTIONS.first().id)
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateInterviewAnswers(answers + (STUDY_INTERVIEW_QUESTIONS.first().id to ""))
        }
    }
}
