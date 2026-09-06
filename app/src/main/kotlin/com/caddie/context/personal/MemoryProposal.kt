package com.caddie.context.personal

import kotlinx.serialization.Serializable

/** Model-authored candidate. It carries no permission or trusted provenance. */
data class MemoryProposal(
    val title: String,
    val text: String,
    val evidenceQuote: String,
    val inferred: Boolean = false,
    val targetId: String? = null,
    val targetVersion: Long? = null,
)

/** Created by runtime code from an actual user input, never decoded from tool arguments. */
data class TrustedMemoryEvidence(
    val runId: String,
    val turnId: String,
    val source: String,
    val text: String,
    val observedAtMillis: Long,
)

@Serializable
data class PersonalMemoryEvidence(
    val runId: String,
    val turnId: String,
    val source: String,
    val quote: String,
    val observedAtMillis: Long,
)

enum class MemoryOutcome { AUTO_SAVE, PENDING_CONFIRMATION, REJECT }

data class MemoryAdmission(
    val outcome: MemoryOutcome,
    val reason: String,
    val memoryKey: String? = null,
    val title: String? = null,
    val text: String? = null,
    val provenance: String = "model-inference",
    val targetId: String? = null,
    val duplicateId: String? = null,
)

data class MemoryWriteResult(
    val outcome: MemoryOutcome,
    val reason: String,
    val id: String? = null,
    val changed: Boolean = false,
)

@Serializable
data class PersonalMemoryCandidate(
    val id: String,
    val title: String,
    val text: String,
    val provenance: String,
    val evidence: PersonalMemoryEvidence,
    val reason: String,
    val memoryKey: String? = null,
    val targetId: String? = null,
    val targetVersion: Long? = null,
    val createdAtMillis: Long,
)
