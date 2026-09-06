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

/** Explicitly entered reference facts; no tools or automatic conversation ingestion. */
@Serializable
data class PersonalFact(
    val id: String,
    val title: String,
    val text: String,
    val provenance: String = "owner-entered-and-confirmed",
)

data class PersonalContextSnapshot(val revision: Long, val facts: List<PersonalFact>)

/** One encrypted corpus snapshot, updated atomically. No plaintext fact hashes or vectors on disk. */
class PersonalContextStore internal constructor(
    private val repository: ContextRepository,
    private val resetKey: () -> Unit = {},
) {
    private val mutex = Mutex()
    @Volatile var revision: Long = 0
        private set

    suspend fun read(): PersonalContextSnapshot = mutex.withLock {
        withContext(Dispatchers.IO) {
            val rows = repository.activeContent(OWNER)
            require(rows.size <= 1)
            val facts = rows.singleOrNull()?.let {
                require(it.size <= MAX_BYTES)
                codec.decodeFromString<List<PersonalFact>>(it.toString(Charsets.UTF_8))
            }.orEmpty()
            validate(facts)
            PersonalContextSnapshot(revision, facts)
        }
    }

    suspend fun replace(facts: List<PersonalFact>, ownerConfirmed: Boolean = false) = mutex.withLock {
        val candidate = facts.toList()
        validate(candidate)
        require(ownerConfirmed) { "Explicit owner confirmation required" }
        // A confirmed mutation must finish its commit/invalidation even if the editor closes.
        withContext(NonCancellable + Dispatchers.IO) {
            // Random opaque metadata avoids dictionary attacks against short personal facts.
            val generation = UUID.randomUUID().toString()
            repository.activate(ContextCorpusGeneration(
                generationId = generation,
                contents = listOf(ContextContentInput(
                    id = OWNER, ownerId = OWNER,
                    sourceHash = UUID.randomUUID().toString().replace("-", "") +
                        UUID.randomUUID().toString().replace("-", ""),
                    payload = codec.encodeToString(candidate).toByteArray(Charsets.UTF_8),
                )), vectors = emptyList(), projections = emptyList(), replays = emptyList(),
            ))
            revision++
        }
    }

    /** Works even if old ciphertext cannot be decrypted. Other context owners are untouched. */
    suspend fun clear() = mutex.withLock {
        withContext(NonCancellable + Dispatchers.IO) {
            repository.deleteOwner(OWNER)
            revision++
            resetKey()
        }
    }

    companion object {
        const val MAX_FACTS = 32
        const val MAX_TITLE = 80
        const val MAX_TEXT = 700
        private const val MAX_BYTES = 160_000
        private const val OWNER = "personal-facts-v1"
        private const val KEY_ALIAS = "caddie_personal_context_v1"
        private val codec = Json { encodeDefaults = true }
        @Volatile private var instance: PersonalContextStore? = null

        fun production(context: Context): PersonalContextStore = instance ?: synchronized(this) {
            instance ?: PersonalContextStore(ContextRepository(
                ContextDatabase.open(context.applicationContext), AndroidKeystoreContextCipher(KEY_ALIAS),
            ), resetKey = { AndroidKeystoreContextCipher.deleteKey(KEY_ALIAS) }).also { instance = it }
        }

        fun validate(facts: List<PersonalFact>) {
            require(facts.size <= MAX_FACTS) { "At most 32 facts" }
            require(facts.map { it.id }.distinct().size == facts.size) { "Duplicate fact ID" }
            require(facts.map { PersonalMemoryPolicy.canonical(it.title) }.distinct().size == facts.size) { "Duplicate topic: edit the existing note" }
            require(facts.map { PersonalMemoryPolicy.canonical(it.text) }.distinct().size == facts.size) { "Duplicate fact text" }
            facts.forEach {
                require(it.provenance == "owner-entered-and-confirmed")
                require(PersonalMemoryPolicy.decision(it, true) == PersonalMemoryPolicy.Decision.ALLOW_CONFIRMED_NOTE) { "Memory policy rejected a note" }
                require(runCatching { UUID.fromString(it.id).toString() == it.id }.getOrDefault(false))
                require(it.title.isNotBlank() && it.title.length <= MAX_TITLE)
                require(it.text.isNotBlank() && it.text.length <= MAX_TEXT)
                require((it.title + it.text).none { c -> c.isISOControl() && c != '\n' && c != '\t' })
            }
        }
    }
}
