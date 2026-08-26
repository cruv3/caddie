package com.caddie.study.runtime.matrix

import com.caddie.study.runtime.model.CriticalityClass
import com.caddie.study.runtime.model.ParticipantConfig
import com.caddie.study.runtime.model.ParticipantId
import com.caddie.study.runtime.model.RuntimeStudyCondition
import com.caddie.study.runtime.model.ScreenOffMode
import com.caddie.study.runtime.model.TaskPair
import com.caddie.study.runtime.model.TrialSpec

/**
 * Deterministic 18-participant assignment block, repeated for larger cohorts.
 *
 * P01–P11 retain the schedules used before the task-order protocol amendment.
 * From P12 onward within each block, task pairs move to different periods while
 * their condition and controlled-error assignments remain unchanged. Every
 * participant performs all 6 tasks exactly once, each condition appears
 * exactly twice, and exactly 3 tasks are scheduled for error injection
 * (one per condition, balanced by criticality and task across the cohort).
 */
object StudyMatrix {

    const val NUM_PARTICIPANTS = 18
    const val NUM_ERROR_TASKS = 3
    const val NUM_TASKS_PER_PARTICIPANT = 6
    const val NUM_PAIRS_PER_PARTICIPANT = 3
    const val TASK_ORDER_AMENDMENT_START_SLOT = 12

    val ALL_CONDITIONS = RuntimeStudyCondition.entries
    val ALL_SCREEN_OFF_MODES = ScreenOffMode.entries

    private val C1 = RuntimeStudyCondition.STEPWISE
    private val C2 = RuntimeStudyCondition.FINAL_CHECKPOINT
    private val C3 = RuntimeStudyCondition.VOLUNTARY_INTERVENTION

    /** 6 condition orders: position-wise (C1,C1,C2,C2,C3,C3) assigned to pairs A/B/C. */
    private val CONDITION_ORDERS: List<List<RuntimeStudyCondition>> = listOf(
        // Order 0: A→C1, B→C2, C→C3
        listOf(C1, C1, C2, C2, C3, C3),
        // Order 1: A→C1, B→C3, C→C2
        listOf(C1, C1, C3, C3, C2, C2),
        // Order 2: A→C2, B→C1, C→C3
        listOf(C2, C2, C1, C1, C3, C3),
        // Order 3: A→C2, B→C3, C→C1
        listOf(C2, C2, C3, C3, C1, C1),
        // Order 4: A→C3, B→C1, C→C2
        listOf(C3, C3, C1, C1, C2, C2),
        // Order 5: A→C3, B→C2, C→C1
        listOf(C3, C3, C2, C2, C1, C1),
    )

    /** Original task orders used to derive the frozen condition and error assignments. */
    private val ORIGINAL_TASK_ORDERS: List<List<String>> = listOf(
        // Rotation 0: [maps, email, gallery, dnd, chat, banking]
        listOf("task_maps_messenger", "task_email_calendar", "task_gallery_notes", "task_calendar_dnd", "task_chat_spotify", "task_banking_payment"),
        // Rotation 1: swap Pair A → [email, maps, gallery, dnd, chat, banking]
        listOf("task_email_calendar", "task_maps_messenger", "task_gallery_notes", "task_calendar_dnd", "task_chat_spotify", "task_banking_payment"),
        // Rotation 2: swap Pair B → [maps, email, dnd, gallery, chat, banking]
        listOf("task_maps_messenger", "task_email_calendar", "task_calendar_dnd", "task_gallery_notes", "task_chat_spotify", "task_banking_payment"),
    )

    /**
     * P12–P18 task orders after the protocol amendment.
     *
     * The completed P01–P11 schedules cannot be rebalanced retroactively. The
     * amended orders therefore move every remaining pair away from its former
     * fixed period while preserving the original task-to-condition and
     * controlled-error assignments. The chosen pair rotations also preserve
     * exact condition-by-period balance across the complete P01–P18 block.
     */
    private val AMENDED_TASK_ORDERS: Map<Int, List<String>> = mapOf(
        12 to listOf("task_banking_payment", "task_chat_spotify", "task_email_calendar", "task_maps_messenger", "task_calendar_dnd", "task_gallery_notes"),
        13 to listOf("task_calendar_dnd", "task_gallery_notes", "task_banking_payment", "task_chat_spotify", "task_email_calendar", "task_maps_messenger"),
        14 to listOf("task_calendar_dnd", "task_gallery_notes", "task_banking_payment", "task_chat_spotify", "task_maps_messenger", "task_email_calendar"),
        15 to listOf("task_banking_payment", "task_chat_spotify", "task_email_calendar", "task_maps_messenger", "task_calendar_dnd", "task_gallery_notes"),
        16 to listOf("task_calendar_dnd", "task_gallery_notes", "task_banking_payment", "task_chat_spotify", "task_maps_messenger", "task_email_calendar"),
        17 to listOf("task_calendar_dnd", "task_gallery_notes", "task_banking_payment", "task_chat_spotify", "task_email_calendar", "task_maps_messenger"),
        18 to listOf("task_calendar_dnd", "task_gallery_notes", "task_banking_payment", "task_chat_spotify", "task_maps_messenger", "task_email_calendar"),
    )

    private val SCREEN_OFF_TASKS = listOf(
        "screen_off_weather",
        "screen_off_project_group",
        "screen_off_email_calendar",
    )

    /** Group trial specs into low/high criticality pairs (deterministic order). */
    fun extractTaskPairs(specs: Map<String, TrialSpec>): List<TaskPair> {
        val lowTasks = specs.entries.filter { it.value.criticality == CriticalityClass.LOW }.sortedBy { it.key }
        val highTasks = specs.entries.filter { it.value.criticality == CriticalityClass.HIGH }.sortedBy { it.key }
        require(lowTasks.isNotEmpty() && highTasks.isNotEmpty()) {
            "Need at least one low and one high criticality task."
        }
        require(lowTasks.size >= NUM_PAIRS_PER_PARTICIPANT) {
            "Need at least $NUM_PAIRS_PER_PARTICIPANT low criticality tasks, got ${lowTasks.size}."
        }
        require(highTasks.size >= NUM_PAIRS_PER_PARTICIPANT) {
            "Need at least $NUM_PAIRS_PER_PARTICIPANT high criticality tasks, got ${highTasks.size}."
        }
        return lowTasks.zip(highTasks).map { (low, high) ->
            TaskPair(id = "pair_${low.key}_${high.key}", lowTask = low.key, highTask = high.key)
        }
    }

    /** Generate the original balanced P01–P18 cohort from loaded specs. */
    fun generateMatrix(specs: Map<String, TrialSpec>): Map<String, ParticipantConfig> {
        val configs = LinkedHashMap<String, ParticipantConfig>()

        for (participantIdx in 0 until NUM_PARTICIPANTS) {
            val participantId = ParticipantId.format(participantIdx + 1)
            configs[participantId] = generateParticipant(participantId, specs)
        }
        val expectedTaskIds = expectedTaskIds(specs)
        validateCohortBalance(configs, expectedTaskIds)
        return configs
    }

    /** Resolve any P-number by repeating the balanced 18-participant block. */
    fun generateParticipant(
        participantId: String,
        specs: Map<String, TrialSpec>,
    ): ParticipantConfig {
        val participantNumber = ParticipantId.requireNumber(participantId)
        val blockIndex = (participantNumber - 1) % NUM_PARTICIPANTS
        val conditionOrder = CONDITION_ORDERS[blockIndex % CONDITION_ORDERS.size]
        val taskIds = ORIGINAL_TASK_ORDERS[blockIndex / CONDITION_ORDERS.size]
        val expectedTaskIds = expectedTaskIds(specs)
        val criticalities = expectedTaskIds.associateWith { specs.getValue(it).criticality }
        val originalConfig = generateParticipantConfig(
            participantId = participantId,
            scheduleIndex = blockIndex,
            taskIds = taskIds,
            criticalities = criticalities,
            conditionOrder = conditionOrder,
            specs = specs,
        )
        return applyTaskOrderAmendment(
            config = originalConfig,
            scheduleSlot = blockIndex + 1,
            criticalities = criticalities,
        )
    }

    private fun applyTaskOrderAmendment(
        config: ParticipantConfig,
        scheduleSlot: Int,
        criticalities: Map<String, CriticalityClass>,
    ): ParticipantConfig {
        val amendedTaskOrder = AMENDED_TASK_ORDERS[scheduleSlot] ?: return config
        require(
            amendedTaskOrder.size == config.taskOrder.size &&
                amendedTaskOrder.toSet() == config.taskOrder.toSet(),
        ) {
            "Task-order amendment for slot $scheduleSlot must contain the original six tasks."
        }
        val conditionByTask = config.taskOrder.zip(config.conditionOrder).toMap()
        return config.copy(
            taskOrder = amendedTaskOrder,
            conditionOrder = amendedTaskOrder.map(conditionByTask::getValue),
            pairAssignments = amendedTaskOrder.map { it to criticalities.getValue(it) },
        )
    }

    private fun expectedTaskIds(specs: Map<String, TrialSpec>): List<String> = listOf(
        "task_maps_messenger", "task_gallery_notes", "task_chat_spotify",
        "task_email_calendar", "task_calendar_dnd", "task_banking_payment",
    ).also { ids ->
        for (taskId in ids) require(taskId in specs) { "Missing required spec: $taskId" }
    }

    private fun generateParticipantConfig(
        participantId: String,
        scheduleIndex: Int,
        taskIds: List<String>,
        criticalities: Map<String, CriticalityClass>,
        conditionOrder: List<RuntimeStudyCondition>,
        specs: Map<String, TrialSpec>,
    ): ParticipantConfig {
        val eligibleTaskIds = taskIds.filter { tid ->
            specs[tid]?.let { it.errorSteps.isNotEmpty() } ?: false
        }.let { if (it.isEmpty()) taskIds.toSet() else it.toSet() }

        val errorTasks = selectBalancedErrorTasks(
            participantIndex = scheduleIndex,
            taskIds = taskIds,
            conditionOrder = conditionOrder,
            criticalities = criticalities,
            eligibleTaskIds = eligibleTaskIds,
        ).sorted()

        val screenOffOrder = rotateScreenOff(scheduleIndex)
        val pairAssignments = taskIds.map { it to (criticalities[it] ?: CriticalityClass.LOW) }

        return ParticipantConfig(
            participantId = participantId,
            conditionOrder = conditionOrder,
            taskOrder = taskIds,
            errorTasks = errorTasks,
            screenOffOrder = screenOffOrder,
            screenOffTasks = SCREEN_OFF_TASKS,
            pairAssignments = pairAssignments,
        )
    }

    /** Choose one eligible low/high-alternating error task per condition. */
    private fun selectBalancedErrorTasks(
        participantIndex: Int,
        taskIds: List<String>,
        conditionOrder: List<RuntimeStudyCondition>,
        criticalities: Map<String, CriticalityClass>,
        eligibleTaskIds: Set<String>,
    ): List<String> {
        val selected = mutableListOf<String>()
        val orderIndex = CONDITION_ORDERS.indexOf(conditionOrder)
        val rotationIndex = participantIndex / CONDITION_ORDERS.size
        for (condition in ALL_CONDITIONS) {
            val candidates = taskIds.filterIndexed { i, _ -> conditionOrder[i] == condition }
            val pairIndexes = conditionOrder.indices
                .filter { conditionOrder[it] == condition }
                .map { it / 2 }
                .toSet()
            require(pairIndexes.size == 1) { "${condition.wireValue} must be assigned to exactly one task pair" }
            val pairIndex = pairIndexes.first()
            val occurrenceIndex = CONDITION_ORDERS.subList(0, orderIndex)
                .count { it[pairIndex * 2] == condition }
            val low = candidates.filter { criticalities[it] == CriticalityClass.LOW }
            val high = candidates.filter { criticalities[it] == CriticalityClass.HIGH }
            require(low.size == 1 && high.size == 1) {
                "${condition.wireValue} must contain one low and one high task; got low=$low, high=$high"
            }
            val chooseLow = (rotationIndex + occurrenceIndex + pairIndex) % 2 == 0
            val chosen = if (chooseLow) low.first() else high.first()
            require(chosen in eligibleTaskIds) { "Scheduled error task has no error step: $chosen" }
            selected.add(chosen)
        }
        return selected
    }

    private fun rotateScreenOff(participantIndex: Int): List<ScreenOffMode> {
        val n = ALL_SCREEN_OFF_MODES.size
        val offset = participantIndex % n
        return List(n) { ALL_SCREEN_OFF_MODES[(it + offset) % n] }
    }

    /** Validate cohort-level balance invariants after matrix generation. */
    private fun validateCohortBalance(
        configs: Map<String, ParticipantConfig>,
        allTaskIds: List<String>,
    ) {
        check(configs.size == NUM_PARTICIPANTS) { "Expected $NUM_PARTICIPANTS participants, got ${configs.size}" }
        for ((pid, cfg) in configs.entries.sortedBy { it.key }) {
            check(cfg.taskOrder.size == NUM_TASKS_PER_PARTICIPANT) { "$pid: expected 6 tasks, got ${cfg.taskOrder.size}" }
            check(cfg.errorTasks.size == NUM_ERROR_TASKS) { "$pid: expected 3 error tasks, got ${cfg.errorTasks.size}" }
            val condCounts = cfg.conditionOrder.groupingBy { it }.eachCount()
            for (cond in ALL_CONDITIONS) {
                check(condCounts[cond] == 2) { "$pid: condition ${cond.wireValue} appears ${condCounts[cond]} times, expected 2" }
            }
            val errorCondCounts = cfg.taskOrder.zip(cfg.conditionOrder)
                .filter { (tid, _) -> tid in cfg.errorTasks }
                .groupingBy { (_, cond) -> cond }.eachCount()
            for (cond in ALL_CONDITIONS) {
                check(errorCondCounts[cond] == 1) { "$pid: expected one error in ${cond.wireValue}, got ${errorCondCounts[cond]}" }
            }
            for (tid in cfg.taskOrder) check(tid in allTaskIds) { "$pid: unknown task '$tid'" }
        }
        // cohort-level error balance: each (condition, criticality) cell appears equally
        val errorCells = HashMap<Pair<RuntimeStudyCondition, CriticalityClass>, Int>()
        val taskCondition = HashMap<Pair<String, RuntimeStudyCondition>, Int>()
        val taskTotals = HashMap<String, Int>()
        for (cfg in configs.values) {
            val crit = cfg.pairAssignments.toMap()
            for ((tid, cond) in cfg.taskOrder.zip(cfg.conditionOrder)) {
                if (tid !in cfg.errorTasks) continue
                val key = cond to (crit[tid] ?: CriticalityClass.LOW)
                errorCells[key] = (errorCells[key] ?: 0) + 1
                val tk = tid to cond
                taskCondition[tk] = (taskCondition[tk] ?: 0) + 1
                taskTotals[tid] = (taskTotals[tid] ?: 0) + 1
            }
        }
        val expectedCells = ALL_CONDITIONS.flatMap { c -> CriticalityClass.entries.map { c to it } }.toSet()
        check(errorCells.keys == expectedCells) { "error cell coverage mismatch" }
        check(errorCells.values.toSet() == setOf(9)) { "expected each (cond,criticality) error cell to appear 9 times, got ${errorCells.values.toSet()}" }
        check(taskCondition.values.toSet() == setOf(3)) { "expected each (task,condition) error to appear 3 times" }
        check(taskTotals.values.toSet() == setOf(9)) { "expected each task to be an error task 9 times" }
    }
}
