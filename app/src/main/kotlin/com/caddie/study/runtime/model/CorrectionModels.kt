package com.caddie.study.runtime.model

/**
 * Correction domain models ported from caddie.study.correction_policy.
 *
 * [CorrectionJournal] holds the append-only in-memory facts that route a
 * participant correction safely (matches the Python CorrectionJournal).
 */

/** One deterministic, non-commit UI action in a compensation recipe. */
data class CorrectionStep(
    val id: String,
    val action: String,
    val narration: String,
    val confirmationText: String,
    val minNarrationMs: Int = 0,
    val runIfTargetPresent: Boolean = false,
    val readyPackage: String? = null,
) {
    init {
        require(id.isNotBlank()) { "CorrectionStep.id must be non-empty" }
        require(action.isNotBlank()) { "CorrectionStep.action must be non-empty" }
        require(narration.isNotBlank()) { "CorrectionStep.narration must be non-empty" }
        require(confirmationText.isNotBlank()) { "CorrectionStep.confirmationText must be non-empty" }
        require(minNarrationMs >= 0) { "CorrectionStep.minNarrationMs must be >= 0" }
        readyPackage?.let { require(it.isNotBlank()) { "CorrectionStep.readyPackage must be non-empty" } }
    }
}

/** A visible-state postcondition for a completed correction. */
data class CorrectionAssertion(
    val checkType: String,
    val value: String,
) {
    init {
        require(checkType in ASSERTION_TYPES) {
            "CorrectionAssertion.checkType must be one of ${ASSERTION_TYPES.sorted()}"
        }
        require(value.isNotBlank()) { "CorrectionAssertion.value must be non-empty" }
    }

    companion object {
        val ASSERTION_TYPES = setOf(
            "text_present",
            "text_absent",
            "accessibility_present",
            "accessibility_absent",
        )
    }
}

/** Post-commit correction behavior declared by an error variant. */
data class CorrectionPolicy(
    val afterCommit: PostCommitPolicy,
    val rewindToStepId: String? = null,
    val rewindSteps: List<CorrectionStep> = emptyList(),
    val steps: List<CorrectionStep> = emptyList(),
    val assertions: List<CorrectionAssertion> = emptyList(),
    val irreversibleMessageDe: String? = null,
) {
    init {
        rewindToStepId?.let { require(it.isNotBlank()) { "rewindToStepId must be non-empty" } }
        val allStepIds = (rewindSteps + steps).map { it.id }
        require(allStepIds.size == allStepIds.toSet().size) { "CorrectionPolicy step IDs must be unique" }
        when (afterCommit) {
            PostCommitPolicy.COMPENSATE -> {
                require(steps.isNotEmpty()) { "compensate policy requires correction steps" }
                require(assertions.isNotEmpty()) { "compensate policy requires assertions" }
                require(irreversibleMessageDe == null) { "compensate policy must not define irreversible_message_de" }
            }
            PostCommitPolicy.REJECT_IRREVERSIBLE -> {
                require(steps.isEmpty()) { "reject_irreversible policy must not define correction steps" }
                require(assertions.isEmpty()) { "reject_irreversible policy must not define assertions" }
                require(!irreversibleMessageDe.isNullOrBlank()) { "reject_irreversible policy requires irreversible_message_de" }
            }
        }
    }
}

/** One successful ordinary or compensation action. */
data class JournalEntry(
    val stepId: String,
    val commit: Boolean,
    val kind: String = "trial", // "trial" or "compensation"
) {
    init {
        require(stepId.isNotBlank()) { "JournalEntry.stepId must be non-empty" }
        require(kind in setOf("trial", "compensation")) { "JournalEntry.kind must be trial or compensation" }
    }
}

/**
 * Append-only in-memory facts used to route a correction safely.
 * Mirrors Python `CorrectionJournal`.
 */
class CorrectionJournal(val variantId: String) {
    init {
        require(variantId.isNotBlank()) { "variant_id must be non-empty" }
    }

    private val _entries = mutableListOf<JournalEntry>()
    private var compensationComplete = false

    val entries: List<JournalEntry> get() = _entries.toList()
    val committed: Boolean get() = _entries.any { it.commit }

    fun recordStep(stepId: String, commit: Boolean, kind: String = "trial") {
        _entries.add(JournalEntry(stepId, commit, kind))
    }

    fun finishCompensation(stepIds: List<String>) {
        for (id in stepIds) recordStep(id, commit = false, kind = "compensation")
        compensationComplete = true
    }

    fun route(policy: PostCommitPolicy): CorrectionRoute = when {
        compensationComplete -> CorrectionRoute.ALREADY_CORRECT
        !committed -> CorrectionRoute.REWIND
        policy == PostCommitPolicy.COMPENSATE -> CorrectionRoute.COMPENSATE
        else -> CorrectionRoute.REJECT_IRREVERSIBLE
    }
}
