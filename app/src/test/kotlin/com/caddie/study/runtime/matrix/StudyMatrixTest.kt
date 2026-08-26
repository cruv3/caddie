package com.caddie.study.runtime.matrix

import com.caddie.study.runtime.model.CriticalityClass
import com.caddie.study.runtime.model.ParticipantConfig
import com.caddie.study.runtime.model.ParticipantId
import com.caddie.study.runtime.model.RuntimeStudyCondition
import com.caddie.study.runtime.model.ScreenOffMode
import com.caddie.study.runtime.model.StepType
import com.caddie.study.runtime.model.StudyStep
import com.caddie.study.runtime.model.TrialSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyMatrixTest {

    @Test
    fun `generates exactly 18 participants`() {
        val configs = StudyMatrix.generateMatrix(fakeSpecs())
        assertEquals(18, configs.size)
        for (i in 1..18) assertTrue("P%02d present".format(i), "P%02d".format(i) in configs)
    }

    @Test
    fun `participants after P18 repeat the balanced schedule block deterministically`() {
        val specs = fakeSpecs()

        val p01 = StudyMatrix.generateParticipant("P01", specs)
        val p19 = StudyMatrix.generateParticipant("P19", specs)
        val p02 = StudyMatrix.generateParticipant("P02", specs)
        val p20 = StudyMatrix.generateParticipant("P20", specs)
        val p12 = StudyMatrix.generateParticipant("P12", specs)
        val p30 = StudyMatrix.generateParticipant("P30", specs)

        assertEquals("P19", p19.participantId)
        assertEquals(p01.taskOrder, p19.taskOrder)
        assertEquals(p01.conditionOrder, p19.conditionOrder)
        assertEquals(p01.errorTasks, p19.errorTasks)
        assertEquals(p01.screenOffOrder, p19.screenOffOrder)
        assertEquals(p02.taskOrder, p20.taskOrder)
        assertEquals(p02.conditionOrder, p20.conditionOrder)
        assertEquals(p12.taskOrder, p30.taskOrder)
        assertEquals(p12.conditionOrder, p30.conditionOrder)
        assertEquals(p12.errorTasks, p30.errorTasks)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `participant zero is rejected`() {
        StudyMatrix.generateParticipant("P00", fakeSpecs())
    }

    @Test
    fun `participant ids have one canonical spelling`() {
        assertEquals("P01", ParticipantId.canonical("P1"))
        assertEquals("P01", ParticipantId.canonical("01"))
        assertEquals("P19", ParticipantId.canonical("P019"))
        assertEquals("P100", ParticipantId.canonical("P100"))
    }

    @Test
    fun `every participant has 6 tasks, 2 of each condition, 3 error tasks`() {
        val configs = StudyMatrix.generateMatrix(fakeSpecs())
        for ((pid, cfg) in configs) {
            assertEquals("$pid tasks", 6, cfg.taskOrder.size)
            val condCounts = cfg.conditionOrder.groupingBy { it }.eachCount()
            for (cond in RuntimeStudyCondition.entries) {
                assertEquals("$pid condition ${cond.wireValue}", 2, condCounts[cond])
            }
            assertEquals("$pid error tasks", 3, cfg.errorTasks.size)
        }
    }

    @Test
    fun `each participant has exactly one error task per condition`() {
        val configs = StudyMatrix.generateMatrix(fakeSpecs())
        for ((pid, cfg) in configs) {
            val errorCondCounts = cfg.taskOrder.zip(cfg.conditionOrder)
                .filter { (tid, _) -> tid in cfg.errorTasks }
                .groupingBy { (_, cond) -> cond }.eachCount()
            for (cond in RuntimeStudyCondition.entries) {
                assertEquals("$pid one error in ${cond.wireValue}", 1, errorCondCounts[cond])
            }
        }
    }

    @Test
    fun `all 18 schedules are unique`() {
        val configs = StudyMatrix.generateMatrix(fakeSpecs())
        val schedules = configs.values.map { it.taskOrder to it.conditionOrder }
        assertEquals(18, schedules.toSet().size)
    }

    @Test
    fun `P01 through P11 retain the original task orders`() {
        val configs = StudyMatrix.generateMatrix(fakeSpecs())
        val originalFirstBlock = listOf(MAPS, EMAIL, GALLERY, DND, CHAT, BANKING)
        val originalSecondBlock = listOf(EMAIL, MAPS, GALLERY, DND, CHAT, BANKING)

        for (participantNumber in 1..6) {
            assertEquals(ParticipantId.format(participantNumber), originalFirstBlock, configs.getValue(ParticipantId.format(participantNumber)).taskOrder)
        }
        for (participantNumber in 7..11) {
            assertEquals(ParticipantId.format(participantNumber), originalSecondBlock, configs.getValue(ParticipantId.format(participantNumber)).taskOrder)
        }
    }

    @Test
    fun `P12 through P18 use the amended task orders`() {
        val configs = StudyMatrix.generateMatrix(fakeSpecs())
        val expected = mapOf(
            "P12" to listOf(BANKING, CHAT, EMAIL, MAPS, DND, GALLERY),
            "P13" to listOf(DND, GALLERY, BANKING, CHAT, EMAIL, MAPS),
            "P14" to listOf(DND, GALLERY, BANKING, CHAT, MAPS, EMAIL),
            "P15" to listOf(BANKING, CHAT, EMAIL, MAPS, DND, GALLERY),
            "P16" to listOf(DND, GALLERY, BANKING, CHAT, MAPS, EMAIL),
            "P17" to listOf(DND, GALLERY, BANKING, CHAT, EMAIL, MAPS),
            "P18" to listOf(DND, GALLERY, BANKING, CHAT, MAPS, EMAIL),
        )

        for ((participantId, taskOrder) in expected) {
            assertEquals(participantId, taskOrder, configs.getValue(participantId).taskOrder)
        }
    }

    @Test
    fun `amended schedules move every remaining pair away from its original period`() {
        val configs = StudyMatrix.generateMatrix(fakeSpecs())
        val originalPairByPeriod = listOf(
            setOf(MAPS, EMAIL),
            setOf(GALLERY, DND),
            setOf(CHAT, BANKING),
        )

        for (participantNumber in StudyMatrix.TASK_ORDER_AMENDMENT_START_SLOT..StudyMatrix.NUM_PARTICIPANTS) {
            val config = configs.getValue(ParticipantId.format(participantNumber))
            val actualPairs = config.taskOrder.chunked(2).map { it.toSet() }
            for (period in originalPairByPeriod.indices) {
                assertTrue(
                    "${config.participantId} period ${period + 1} should move away from the original pair",
                    actualPairs[period] != originalPairByPeriod[period],
                )
            }
        }
    }

    @Test
    fun `condition order remains balanced across periods after the amendment`() {
        val configs = StudyMatrix.generateMatrix(fakeSpecs())
        val counts = HashMap<Pair<Int, RuntimeStudyCondition>, Int>()

        for (config in configs.values) {
            for ((trialIndex, condition) in config.conditionOrder.withIndex()) {
                val key = (trialIndex / 2) to condition
                counts[key] = (counts[key] ?: 0) + 1
            }
        }

        for (period in 0 until StudyMatrix.NUM_PAIRS_PER_PARTICIPANT) {
            for (condition in RuntimeStudyCondition.entries) {
                assertEquals("period ${period + 1}, ${condition.wireValue}", 12, counts[period to condition])
            }
        }
    }

    @Test
    fun `amendment preserves exact task condition and error balance`() {
        val configs = StudyMatrix.generateMatrix(fakeSpecs())
        val taskConditionErrors = HashMap<Pair<String, RuntimeStudyCondition>, Int>()
        val criticalityConditionErrors = HashMap<Pair<CriticalityClass, RuntimeStudyCondition>, Int>()

        for (config in configs.values) {
            val criticalityByTask = config.pairAssignments.toMap()
            for ((taskId, condition) in config.taskOrder.zip(config.conditionOrder)) {
                if (taskId !in config.errorTasks) continue
                val taskKey = taskId to condition
                taskConditionErrors[taskKey] = (taskConditionErrors[taskKey] ?: 0) + 1
                val criticalityKey = criticalityByTask.getValue(taskId) to condition
                criticalityConditionErrors[criticalityKey] = (criticalityConditionErrors[criticalityKey] ?: 0) + 1
            }
        }

        for (taskId in listOf(MAPS, EMAIL, GALLERY, DND, CHAT, BANKING)) {
            for (condition in RuntimeStudyCondition.entries) {
                assertEquals("$taskId, ${condition.wireValue}", 3, taskConditionErrors[taskId to condition])
            }
        }
        for (criticality in CriticalityClass.entries) {
            for (condition in RuntimeStudyCondition.entries) {
                assertEquals("$criticality, ${condition.wireValue}", 9, criticalityConditionErrors[criticality to condition])
            }
        }
    }

    @Test
    fun `amendment improves within-pair order without rewriting completed schedules`() {
        val configs = StudyMatrix.generateMatrix(fakeSpecs())

        assertEquals(9 to 9, lowFirstCounts(configs.values, MAPS, EMAIL))
        assertEquals(11 to 7, lowFirstCounts(configs.values, GALLERY, DND))
        assertEquals(11 to 7, lowFirstCounts(configs.values, CHAT, BANKING))
    }

    @Test
    fun `cohort error balance holds across condition and criticality`() {
        val configs = StudyMatrix.generateMatrix(fakeSpecs())
        // each task is an error task exactly 9 times
        val taskTotals = HashMap<String, Int>()
        for (cfg in configs.values) {
            for (tid in cfg.errorTasks) taskTotals[tid] = (taskTotals[tid] ?: 0) + 1
        }
        for (tid in taskTotals.keys) assertEquals("task $tid error count", 9, taskTotals[tid])
    }

    @Test
    fun `extract_task_pairs groups 3 low_high pairs`() {
        val pairs = StudyMatrix.extractTaskPairs(fakeSpecs())
        assertEquals(3, pairs.size)
        for (pair in pairs) {
            assertTrue("${pair.id} low != high", pair.lowTask != pair.highTask)
        }
    }

    @Test
    fun `screen off order is rotated per participant`() {
        val configs = StudyMatrix.generateMatrix(fakeSpecs())
        // P01 and P02 should differ in screen-off rotation
        val p01 = configs.getValue("P01").screenOffOrder
        val p02 = configs.getValue("P02").screenOffOrder
        assertTrue("P01/P02 screen-off should differ", p01 != p02)
        for (cfg in configs.values) assertEquals(3, cfg.screenOffOrder.size)
    }

    private fun lowFirstCounts(
        configs: Collection<ParticipantConfig>,
        lowTask: String,
        highTask: String,
    ): Pair<Int, Int> {
        var lowFirst = 0
        var highFirst = 0
        for (config in configs) {
            if (config.taskOrder.indexOf(lowTask) < config.taskOrder.indexOf(highTask)) {
                lowFirst += 1
            } else {
                highFirst += 1
            }
        }
        return lowFirst to highFirst
    }

    /** Build the 6 fake specs matching the real task IDs + criticalities, each with one error step. */
    private fun fakeSpecs(): Map<String, TrialSpec> {
        data class TaskDef(val id: String, val criticality: CriticalityClass)
        val defs = listOf(
            TaskDef("task_maps_messenger", CriticalityClass.LOW),
            TaskDef("task_email_calendar", CriticalityClass.HIGH),
            TaskDef("task_gallery_notes", CriticalityClass.LOW),
            TaskDef("task_calendar_dnd", CriticalityClass.HIGH),
            TaskDef("task_chat_spotify", CriticalityClass.LOW),
            TaskDef("task_banking_payment", CriticalityClass.HIGH),
        )
        return defs.associate { d -> d.id to fakeSpec(d.id, d.criticality) }
    }

    private fun fakeSpec(id: String, criticality: CriticalityClass): TrialSpec {
        val step = StudyStep(
            id = "s1",
            action = "click 'x'",
            narration = "narration",
            stepType = StepType.NORMAL,
        )
        return TrialSpec(
            version = "v1",
            id = id,
            instructionDe = "instruction for $id",
            criticality = criticality,
            steps = listOf(step),
            errorSteps = listOf("s1"),
        )
    }

    private companion object {
        const val MAPS = "task_maps_messenger"
        const val EMAIL = "task_email_calendar"
        const val GALLERY = "task_gallery_notes"
        const val DND = "task_calendar_dnd"
        const val CHAT = "task_chat_spotify"
        const val BANKING = "task_banking_payment"
    }
}
