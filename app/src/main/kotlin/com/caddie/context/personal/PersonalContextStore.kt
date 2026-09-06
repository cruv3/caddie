package com.caddie.context.personal

import android.content.Context
import com.caddie.context.persistence.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/** A persisted fact. Evidence records observed input, not a truth judgement. */
@Serializable
data class PersonalFact(
    val id: String,
    val title: String,
    val text: String,
    val provenance: String = "owner-entered-and-confirmed",
    val memoryKey: String? = null,
    val version: Long = 1,
    val evidence: PersonalMemoryEvidence? = null,
    val updatedAtMillis: Long = 0,
)

data class PersonalContextSnapshot(
    val revision: Long,
    val facts: List<PersonalFact>,
    val pending: List<PersonalMemoryCandidate> = emptyList(),
    val learningEnabled: Boolean = true,
) {
    /** A pending correction must not be silently used until its owner resolves it. */
    val retrievableFacts: List<PersonalFact>
        get() = facts.filterNot { fact -> pending.any { it.targetId == fact.id } }
}

@Serializable
private data class PersonalContextEnvelope(
    val facts: List<PersonalFact> = emptyList(),
    val pending: List<PersonalMemoryCandidate> = emptyList(),
    val learningEnabled: Boolean = true,
)

/** One encrypted corpus snapshot, updated atomically. No plaintext fact hashes or vectors are on disk. */
class PersonalContextStore internal constructor(
    private val repository: ContextRepository,
    private val resetKey: () -> Unit = {},
) {
    private val mutex = Mutex()
    @Volatile var revision: Long = 0
        private set

    suspend fun read(): PersonalContextSnapshot = mutex.withLock {
        withContext(Dispatchers.IO) { snapshot(loadEnvelope()) }
    }

    /** The manual editor replaces active facts only; unrelated pending proposals survive. */
    suspend fun replace(facts: List<PersonalFact>, ownerConfirmed: Boolean = false) = mutex.withLock {
        require(ownerConfirmed) { "Explicit owner confirmation required" }
        val requested = facts.toList()
        withContext(NonCancellable + Dispatchers.IO) {
            val previous = loadEnvelope()
            val nextFacts = normalizeManualFacts(previous.facts, requested, System.currentTimeMillis())
            val retainedPending = previous.pending.filter { candidate ->
                val target = candidate.targetId ?: return@filter true
                val old = previous.facts.firstOrNull { it.id == target }
                val replacement = nextFacts.firstOrNull { it.id == target }
                old != null && replacement != null && sameContent(old, replacement)
            }
            val next = PersonalContextEnvelope(nextFacts, retainedPending, previous.learningEnabled)
            validate(next)
            if (next != previous || hasLegacyArrayPayload()) write(next)
        }
    }

    /** Runtime-owned proposals are admitted only through the deterministic automatic policy. */
    suspend fun propose(proposal: MemoryProposal, evidence: TrustedMemoryEvidence): MemoryWriteResult = mutex.withLock {
        withContext(NonCancellable + Dispatchers.IO) {
            val current = loadEnvelope()
            if (!current.learningEnabled) return@withContext rejected("Automatic learning is disabled")
            val admission = AutomaticMemoryPolicy.evaluate(proposal, evidence, current.facts, current.pending)
            if (admission.outcome == MemoryOutcome.REJECT) return@withContext rejected(admission.reason)
            admission.duplicateId?.let { return@withContext MemoryWriteResult(admission.outcome, admission.reason, it) }

            val title = admission.title ?: return@withContext rejected("Missing admitted title")
            val text = admission.text ?: return@withContext rejected("Missing admitted text")
            val runtimeEvidence = evidenceFor(proposal, evidence) ?: return@withContext rejected("Evidence quote is not in trusted input")
            val canonicalMatch = current.facts.firstOrNull {
                PersonalMemoryPolicy.canonical(it.title) == PersonalMemoryPolicy.canonical(title) &&
                    PersonalMemoryPolicy.canonical(it.text) == PersonalMemoryPolicy.canonical(text)
            }
            if (canonicalMatch != null) return@withContext MemoryWriteResult(admission.outcome, "Already stored", canonicalMatch.id)

            when (admission.outcome) {
                MemoryOutcome.AUTO_SAVE -> autoSave(current, admission, title, text, runtimeEvidence)
                MemoryOutcome.PENDING_CONFIRMATION -> addPending(current, admission, title, text, runtimeEvidence)
                MemoryOutcome.REJECT -> error("Handled above")
            }
        }
    }

    /** Both approval and rejection are owner actions; a stale target remains pending for review. */
    suspend fun reviewCandidate(id: String, accept: Boolean, ownerConfirmed: Boolean = false): MemoryWriteResult = mutex.withLock {
        require(ownerConfirmed) { "Explicit owner confirmation required" }
        withContext(NonCancellable + Dispatchers.IO) {
            val current = loadEnvelope()
            val candidate = current.pending.firstOrNull { it.id == id }
                ?: return@withContext rejected("Proposal no longer exists")
            if (!accept) {
                write(current.copy(pending = current.pending.filterNot { it.id == id }))
                return@withContext MemoryWriteResult(MemoryOutcome.REJECT, "Owner rejected proposal", id, changed = true)
            }
            if (PersonalMemoryPolicy.containsForbiddenPayload("${candidate.title}\n${candidate.text}")) {
                return@withContext rejected("Proposal contains forbidden content")
            }
            val target = candidate.targetId?.let { targetId -> current.facts.firstOrNull { it.id == targetId } }
            if (candidate.targetId != null && (target == null || target.version != candidate.targetVersion)) {
                return@withContext rejected("Target changed; review the current note")
            }
            if (target == null && current.facts.size >= MAX_FACTS) return@withContext rejected("Personal memory is full")
            val now = System.currentTimeMillis()
            val approvedProvenance = if (candidate.provenance == "model-inference")
                "model-inference-confirmed" else OWNER_PROVENANCE
            val approved = if (target == null) PersonalFact(
                id = UUID.randomUUID().toString(), title = candidate.title, text = candidate.text,
                provenance = approvedProvenance, memoryKey = candidate.memoryKey,
                evidence = candidate.evidence, updatedAtMillis = now,
            ) else target.copy(
                title = candidate.title, text = candidate.text, provenance = approvedProvenance,
                memoryKey = candidate.memoryKey, version = target.version + 1,
                evidence = candidate.evidence, updatedAtMillis = now,
            )
            val facts = if (target == null) current.facts + approved else current.facts.map {
                if (it.id == target.id) approved else it
            }
            val next = current.copy(facts = facts, pending = current.pending.filterNot { it.id == candidate.id })
            validate(next)
            write(next)
            MemoryWriteResult(MemoryOutcome.AUTO_SAVE, "Owner approved proposal", approved.id, changed = true)
        }
    }

    /** Explicit owner preference for future runtime proposals. */
    suspend fun setLearningEnabled(enabled: Boolean) = mutex.withLock {
        withContext(NonCancellable + Dispatchers.IO) {
            val current = loadEnvelope()
            if (current.learningEnabled != enabled) write(current.copy(learningEnabled = enabled))
        }
    }

    /** Works even if old ciphertext cannot be decrypted. Other context owners are untouched. */
    suspend fun clear() = mutex.withLock {
        withContext(NonCancellable + Dispatchers.IO) {
            repository.deleteOwner(OWNER)
            revision++
            // The preference is part of the encrypted envelope. A new empty store defaults to enabled.
            resetKey()
        }
    }

    private suspend fun autoSave(
        current: PersonalContextEnvelope, admission: MemoryAdmission, title: String, text: String,
        runtimeEvidence: PersonalMemoryEvidence,
    ): MemoryWriteResult {
        val target = admission.targetId?.let { id -> current.facts.firstOrNull { it.id == id } }
        if (admission.targetId != null && target == null) return rejected("Target no longer exists")
        if (target == null && current.facts.size >= MAX_FACTS) return rejected("Personal memory is full")
        val now = System.currentTimeMillis()
        val fact = if (target == null) PersonalFact(
            id = UUID.randomUUID().toString(), title = title, text = text, provenance = admission.provenance,
            memoryKey = admission.memoryKey, evidence = runtimeEvidence, updatedAtMillis = now,
        ) else target.copy(
            title = title, text = text, provenance = admission.provenance, memoryKey = admission.memoryKey,
            version = target.version + 1, evidence = runtimeEvidence, updatedAtMillis = now,
        )
        val next = current.copy(facts = if (target == null) current.facts + fact else current.facts.map {
            if (it.id == target.id) fact else it
        })
        validate(next)
        write(next)
        return MemoryWriteResult(MemoryOutcome.AUTO_SAVE, admission.reason, fact.id, changed = true)
    }

    private suspend fun addPending(
        current: PersonalContextEnvelope, admission: MemoryAdmission, title: String, text: String,
        runtimeEvidence: PersonalMemoryEvidence,
    ): MemoryWriteResult {
        if (current.pending.size >= MAX_PENDING) return rejected("Pending review queue is full")
        val pendingMatch = current.pending.firstOrNull {
            PersonalMemoryPolicy.canonical(it.title) == PersonalMemoryPolicy.canonical(title) &&
                PersonalMemoryPolicy.canonical(it.text) == PersonalMemoryPolicy.canonical(text) && it.targetId == admission.targetId
        }
        if (pendingMatch != null) return MemoryWriteResult(MemoryOutcome.PENDING_CONFIRMATION, "Already pending review", pendingMatch.id)
        val target = admission.targetId?.let { id -> current.facts.firstOrNull { it.id == id } }
        if (admission.targetId != null && target == null) return rejected("Target no longer exists")
        val candidate = PersonalMemoryCandidate(
            id = UUID.randomUUID().toString(), title = title, text = text, provenance = admission.provenance,
            evidence = runtimeEvidence, reason = admission.reason, memoryKey = admission.memoryKey,
            targetId = admission.targetId, targetVersion = target?.version, createdAtMillis = System.currentTimeMillis(),
        )
        val next = current.copy(pending = current.pending + candidate)
        validate(next)
        write(next)
        return MemoryWriteResult(MemoryOutcome.PENDING_CONFIRMATION, admission.reason, candidate.id, changed = true)
    }

    private suspend fun loadEnvelope(): PersonalContextEnvelope {
        val rows = repository.activeContent(OWNER)
        require(rows.size <= 1)
        val payload = rows.singleOrNull()?.let {
            require(it.size <= MAX_BYTES)
            it.toString(Charsets.UTF_8)
        } ?: return PersonalContextEnvelope()
        // Version one wrote a bare JSON array. Read it as the default envelope during migration.
        val envelope = if (payload.trimStart().startsWith("[")) {
            PersonalContextEnvelope(facts = codec.decodeFromString(payload))
        } else codec.decodeFromString<PersonalContextEnvelope>(payload)
        validate(envelope)
        return envelope
    }

    private suspend fun hasLegacyArrayPayload(): Boolean = repository.activeContent(OWNER).singleOrNull()
        ?.toString(Charsets.UTF_8)?.trimStart()?.startsWith("[") == true

    private suspend fun write(envelope: PersonalContextEnvelope) {
        validate(envelope)
        val payload = codec.encodeToString(envelope).toByteArray(Charsets.UTF_8)
        require(payload.size <= MAX_BYTES) { "Personal memory byte capacity exceeded" }
        repository.activate(ContextCorpusGeneration(
            generationId = UUID.randomUUID().toString(),
            contents = listOf(ContextContentInput(
                id = OWNER, ownerId = OWNER,
                sourceHash = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""),
                payload = payload,
            )), vectors = emptyList(), projections = emptyList(), replays = emptyList(),
        ))
        revision++
    }

    private fun snapshot(envelope: PersonalContextEnvelope) = PersonalContextSnapshot(
        revision, envelope.facts, envelope.pending, envelope.learningEnabled,
    )

    private fun normalizeManualFacts(previous: List<PersonalFact>, requested: List<PersonalFact>, now: Long): List<PersonalFact> =
        requested.map { input ->
            val old = previous.firstOrNull { it.id == input.id }
            if (old != null && sameContent(old, input)) old else if (old != null) old.copy(
                title = input.title, text = input.text, provenance = OWNER_PROVENANCE,
                memoryKey = null, evidence = null,
                version = old.version + 1, updatedAtMillis = now,
            ) else PersonalFact(input.id, input.title, input.text, OWNER_PROVENANCE, version = 1, updatedAtMillis = now)
        }

    private fun evidenceFor(proposal: MemoryProposal, trusted: TrustedMemoryEvidence): PersonalMemoryEvidence? {
        val quote = proposal.evidenceQuote.trim()
        if (quote.isEmpty() || quote.length > MAX_EVIDENCE_QUOTE || !trusted.text.contains(quote)) return null
        if (trusted.runId.isBlank() || trusted.turnId.isBlank() || trusted.source.isBlank() || trusted.observedAtMillis < 0) return null
        return PersonalMemoryEvidence(trusted.runId, trusted.turnId, trusted.source, quote, trusted.observedAtMillis)
    }

    private fun rejected(reason: String) = MemoryWriteResult(MemoryOutcome.REJECT, reason)

    companion object {
        const val MAX_FACTS = 32
        const val MAX_PENDING = 16
        const val MAX_TITLE = 80
        const val MAX_TEXT = 700
        private const val MAX_EVIDENCE_QUOTE = 700
        private const val MAX_BYTES = 160_000
        private const val OWNER = "personal-facts-v1"
        private const val KEY_ALIAS = "caddie_personal_context_v1"
        private const val OWNER_PROVENANCE = "owner-entered-and-confirmed"
        private val VALID_PROVENANCE = setOf(OWNER_PROVENANCE, "user-explicit", "model-inference", "model-inference-confirmed")
        private val codec = Json { encodeDefaults = true }
        @Volatile private var instance: PersonalContextStore? = null

        fun production(context: Context): PersonalContextStore = instance ?: synchronized(this) {
            instance ?: PersonalContextStore(ContextRepository(
                ContextDatabase.open(context.applicationContext), AndroidKeystoreContextCipher(KEY_ALIAS),
            ), resetKey = { AndroidKeystoreContextCipher.deleteKey(KEY_ALIAS) }).also { instance = it }
        }

        fun validate(facts: List<PersonalFact>) = validate(PersonalContextEnvelope(facts = facts))

        private fun validate(envelope: PersonalContextEnvelope) {
            val facts = envelope.facts
            require(facts.size <= MAX_FACTS) { "At most $MAX_FACTS facts" }
            require(envelope.pending.size <= MAX_PENDING) { "At most $MAX_PENDING pending proposals" }
            require(facts.map { it.id }.distinct().size == facts.size) { "Duplicate fact ID" }
            require(facts.map { PersonalMemoryPolicy.canonical(it.title) }.distinct().size == facts.size) { "Duplicate topic: edit the existing note" }
            require(facts.map { PersonalMemoryPolicy.canonical(it.text) }.distinct().size == facts.size) { "Duplicate fact text" }
            facts.forEach(::validateFact)
            require(envelope.pending.map { it.id }.distinct().size == envelope.pending.size) { "Duplicate proposal ID" }
            require(envelope.pending.none { pending -> facts.any { it.id == pending.id } }) { "Proposal ID collides with fact" }
            envelope.pending.forEach { pending ->
                require(isCanonicalUuid(pending.id))
                require(pending.title.isNotBlank() && pending.title.length <= MAX_TITLE)
                require(pending.text.isNotBlank() && pending.text.length <= MAX_TEXT)
                require(pending.reason.isNotBlank() && pending.reason.length <= MAX_TEXT)
                require(pending.provenance in VALID_PROVENANCE)
                validateEvidence(pending.evidence)
                require(!PersonalMemoryPolicy.containsForbiddenPayload("${pending.title}\n${pending.text}"))
                require(pending.memoryKey == null || (pending.memoryKey.isNotBlank() && pending.memoryKey.length <= MAX_TITLE))
                require(pending.createdAtMillis >= 0)
                require(pending.targetId == null || isCanonicalUuid(pending.targetId))
                require(pending.targetVersion == null || pending.targetVersion >= 1)
                require((pending.targetId == null) == (pending.targetVersion == null))
                require(pending.targetId == null || facts.any { it.id == pending.targetId })
                require((pending.title + pending.text).none { it.isISOControl() && it != '\n' && it != '\t' })
            }
        }

        private fun validateFact(fact: PersonalFact) {
            require(isCanonicalUuid(fact.id))
            require(fact.provenance in VALID_PROVENANCE) { "Unknown provenance" }
            require(fact.title.isNotBlank() && fact.title.length <= MAX_TITLE)
            require(fact.text.isNotBlank() && fact.text.length <= MAX_TEXT)
            require(fact.version >= 1 && fact.updatedAtMillis >= 0)
            require(fact.memoryKey == null || (fact.memoryKey.isNotBlank() && fact.memoryKey.length <= MAX_TITLE))
            fact.evidence?.let(::validateEvidence)
            require(fact.provenance == OWNER_PROVENANCE || fact.evidence != null) { "Automatic fact lacks evidence" }
            require(!PersonalMemoryPolicy.containsForbiddenPayload("${fact.title}\n${fact.text}")) { "Memory policy rejected a note" }
            require((fact.title + fact.text).none { it.isISOControl() && it != '\n' && it != '\t' })
        }

        private fun validateEvidence(evidence: PersonalMemoryEvidence) {
            require(evidence.runId.isNotBlank() && evidence.turnId.isNotBlank() && evidence.source.isNotBlank())
            require(evidence.quote.isNotBlank() && evidence.quote.length <= MAX_EVIDENCE_QUOTE)
            require(evidence.observedAtMillis >= 0)
        }

        private fun isCanonicalUuid(value: String) = runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)
        private fun sameContent(left: PersonalFact, right: PersonalFact) = left.title == right.title && left.text == right.text
    }
}
