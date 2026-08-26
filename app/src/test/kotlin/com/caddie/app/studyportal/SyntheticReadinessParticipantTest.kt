package com.caddie.app.studyportal

import com.caddie.study.runtime.model.ParticipantConfig
import com.caddie.study.runtime.model.RuntimeStudyCondition
import com.caddie.study.runtime.model.ScreenOffMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SyntheticReadinessParticipantTest {
    @Test
    fun `readiness session copies P01 assignment without using a real participant id`() {
        val p01 = ParticipantConfig(
            participantId = "P01",
            conditionOrder = List(6) { RuntimeStudyCondition.entries[it % 3] },
            taskOrder = List(6) { "task-${it + 1}" },
            errorTasks = listOf("task-1", "task-3", "task-5"),
            screenOffOrder = ScreenOffMode.entries,
            screenOffTasks = listOf("task-1", "task-2", "task-3"),
        )

        val result = StudyPortalRuntime.withSyntheticReadinessParticipant(mapOf("P01" to p01))
        val synthetic = result.getValue(StudyPortalRuntime.SYNTHETIC_READINESS_PARTICIPANT)

        assertEquals(StudyPortalRuntime.SYNTHETIC_READINESS_PARTICIPANT, synthetic.participantId)
        assertEquals(p01.taskOrder, synthetic.taskOrder)
        assertEquals(p01.conditionOrder, synthetic.conditionOrder)
        assertEquals(p01.errorTasks, synthetic.errorTasks)
        assertFalse(result.getValue("P01") === synthetic)
    }
}
