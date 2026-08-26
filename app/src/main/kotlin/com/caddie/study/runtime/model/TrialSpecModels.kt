package com.caddie.study.runtime.model

/**
 * Trial specification models ported from caddie.study.model.
 * Immutable data classes with validation in init blocks (mirrors Python
 * frozen dataclass __post_init__ invariants).
 */

/** A controlled parameter deviation for error injection. */
data class ErrorVariant(
    val id: String,
    val field: String,
    val wrongValue: String,
    val correctValue: String,
    val description: String,
    val summaryWrongValue: String? = null,
    val summaryCorrectValue: String? = null,
    val correction: CorrectionPolicy? = null,
) {
    init {
        require(id.isNotBlank()) { "ErrorVariant.id must be non-empty" }
        require(field.isNotBlank()) { "ErrorVariant.field must be non-empty" }
        require(wrongValue.isNotBlank()) { "ErrorVariant.wrongValue must be non-empty" }
        require(correctValue.isNotBlank()) { "ErrorVariant.correctValue must be non-empty" }
        require(description.isNotBlank()) { "ErrorVariant.description must be non-empty" }
        val hasWrong = summaryWrongValue != null
        val hasCorrect = summaryCorrectValue != null
        require(hasWrong == hasCorrect) {
            "summaryWrongValue and summaryCorrectValue must be provided together"
        }
        if (hasWrong) {
            require(summaryWrongValue!!.isNotBlank() && summaryCorrectValue!!.isNotBlank()) {
                "ErrorVariant summary values must be non-empty"
            }
        }
    }
}

/** Identifies a screen that proves a navigation step was already completed. */
data class UiStateEvidence(
    val packageName: String? = null,
    val resourceId: String? = null,
    val text: String? = null,
) {
    init {
        require(listOf(packageName, resourceId, text).any { !it.isNullOrBlank() }) {
            "UiStateEvidence requires at least one matcher"
        }
        packageName?.let { require(it.isNotBlank()) { "packageName must not be blank" } }
        resourceId?.let { require(it.isNotBlank()) { "resourceId must not be blank" } }
        text?.let { require(it.isNotBlank()) { "text must not be blank" } }
    }
}

/** A single deterministic step in a trial spec. */
data class StudyStep(
    val id: String,
    val action: String,
    val narration: String,
    val stepType: StepType,
    val confirmationText: String? = null,
    val errorVariant: ErrorVariant? = null,
    val minNarrationMs: Int = 800,
    val confirmInC1: Boolean = true,
    val readyPackage: String? = null,
    val readyResourceId: String? = null,
    val alreadySatisfied: UiStateEvidence? = null,
) {
    // Derived flags (Python derives these from step_type).
    val consequential: Boolean = stepType == StepType.CONSEQUENTIAL || stepType == StepType.COMMIT
    val commit: Boolean = stepType == StepType.COMMIT

    init {
        require(id.isNotBlank()) { "StudyStep.id must be non-empty" }
        require(action.isNotBlank()) { "StudyStep.action must be non-empty" }
        require(narration.isNotBlank()) { "StudyStep.narration must be non-empty" }
        require(minNarrationMs >= 0) { "minNarrationMs must be >= 0" }
        readyPackage?.let { require(it.isNotBlank()) { "readyPackage must be non-empty when set" } }
        readyResourceId?.let {
            require(it.isNotBlank()) { "readyResourceId must be non-empty when set" }
        }
    }
}

/** A deterministic postcondition check after a trial. */
data class VerificationRule(
    val id: String,
    val assertion: String,
    val checkType: String,
    val parameters: Map<String, Any?> = emptyMap(),
    val screenshotEvidence: Boolean = true,
) {
    init {
        require(id.isNotBlank()) { "VerificationRule.id must be non-empty" }
        require(assertion.isNotBlank()) { "VerificationRule.assertion must be non-empty" }
        require(checkType.isNotBlank()) { "VerificationRule.checkType must be non-empty" }
    }
}

/** Deterministic phrases and concepts that identify a study task. */
data class TriggerContract(
    val referencePhrases: List<String>,
    val requiredConcepts: List<List<String>>,
    val forbiddenConcepts: List<String> = emptyList(),
    val wakeWords: List<String> = listOf("jarvis", "caddie"),
) {
    init {
        require(referencePhrases.isNotEmpty()) { "referencePhrases must not be empty" }
        require(referencePhrases.all { it.isNotBlank() }) { "referencePhrases must not contain blanks" }
        require(requiredConcepts.isNotEmpty()) { "requiredConcepts must not be empty" }
        requiredConcepts.forEach { group ->
            require(group.isNotEmpty()) { "required_concepts groups must not be empty" }
            require(group.all { it.isNotBlank() }) { "required_concepts must not contain blanks" }
        }
        require(forbiddenConcepts.all { it.isNotBlank() }) { "forbiddenConcepts must not contain blanks" }
        require(wakeWords.all { it.isNotBlank() }) { "wakeWords must not contain blanks" }
    }
}

/** Explains one app's role without prescribing the agent's individual steps. */
data class ParticipantAppUse(
    val name: String,
    val purposeDe: String,
) {
    init {
        require(name.isNotBlank()) { "ParticipantAppUse.name must be non-empty" }
        require(purposeDe.isNotBlank()) { "ParticipantAppUse.purposeDe must be non-empty" }
    }
}

/** Participant-facing context shown before a study task starts. */
data class ParticipantTaskBriefing(
    val situationDe: String,
    val apps: List<ParticipantAppUse>,
    val goalDe: String,
    val preparationDe: String,
    val referenceValuesDe: List<String> = emptyList(),
) {
    init {
        require(situationDe.isNotBlank()) { "ParticipantTaskBriefing.situationDe must be non-empty" }
        require(apps.isNotEmpty()) { "ParticipantTaskBriefing.apps must not be empty" }
        require(goalDe.isNotBlank()) { "ParticipantTaskBriefing.goalDe must be non-empty" }
        require(preparationDe.isNotBlank()) { "ParticipantTaskBriefing.preparationDe must be non-empty" }
        require(referenceValuesDe.all { it.isNotBlank() }) {
            "ParticipantTaskBriefing.referenceValuesDe must not contain blanks"
        }
    }
}

/** A complete task specification loaded from a JSON spec file. */
data class TrialSpec(
    val version: String,
    val id: String,
    val instructionDe: String,
    val criticality: CriticalityClass,
    val requiredPackages: List<String> = emptyList(),
    val seededArtifacts: List<String> = emptyList(),
    val resetChecklist: List<String> = emptyList(),
    val steps: List<StudyStep>,
    val errorSteps: List<String> = emptyList(),
    val c2SummaryLines: List<String> = emptyList(),
    val verification: List<VerificationRule> = emptyList(),
    val maxDurationS: Int = 300,
    val perGateTimeoutS: Int = 30,
    val trigger: TriggerContract? = null,
    val completionMessageDe: String = "",
    val participantBriefing: ParticipantTaskBriefing? = null,
) {
    init {
        require(id.isNotBlank()) { "TrialSpec.id must be non-empty" }
        require(version.isNotBlank()) { "TrialSpec.version must be non-empty" }
        require(instructionDe.isNotBlank()) { "TrialSpec.instructionDe must be non-empty" }
        require(steps.isNotEmpty()) { "TrialSpec.steps must contain at least one step" }
        require(maxDurationS > 0) { "maxDurationS must be > 0" }
        require(perGateTimeoutS > 0) { "perGateTimeoutS must be > 0" }
        if (completionMessageDe.isNotBlank()) {
            require(completionMessageDe.isNotBlank()) { "completionMessageDe must be non-empty when set" }
        }
    }
}

/** Two tasks grouped as a criticality pair for counterbalancing. */
data class TaskPair(
    val id: String,
    val lowTask: String,
    val highTask: String,
)

/** Full deterministic assignment for one participant (P01–P18). */
data class ParticipantConfig(
    val participantId: String,
    val conditionOrder: List<RuntimeStudyCondition>,
    val taskOrder: List<String>,
    val errorTasks: List<String>,
    val screenOffOrder: List<ScreenOffMode>,
    val screenOffTasks: List<String>,
    val pairAssignments: List<Pair<String, CriticalityClass>> = emptyList(),
) {
    init {
        require(participantId.isNotBlank()) { "participantId must be non-empty" }
        require(taskOrder.size == 6) { "taskOrder must have exactly 6 tasks, got ${taskOrder.size}" }
        require(conditionOrder.size == 6) { "conditionOrder must have exactly 6 conditions, got ${conditionOrder.size}" }
        require(taskOrder.size == conditionOrder.size) { "taskOrder and conditionOrder lengths must match" }
        require(errorTasks.size == 3) { "errorTasks must have exactly 3 tasks, got ${errorTasks.size}" }
        errorTasks.forEach { require(it in taskOrder) { "error_task $it not in taskOrder" } }
        require(screenOffOrder.size == 3) { "screenOffOrder must have exactly 3 modes" }
        require(screenOffTasks.size == 3) { "screenOffTasks must have exactly 3 tasks" }
        require(screenOffOrder.size == screenOffTasks.size) { "screenOffOrder and screenOffTasks lengths must match" }
    }
}
