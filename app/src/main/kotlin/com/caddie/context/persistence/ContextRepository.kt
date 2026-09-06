package com.caddie.context.persistence

/** Defines one complete generated context corpus before encrypted persistence. */
data class ContextCorpusGeneration(
    val generationId: String,
    val contents: List<ContextContentInput>,
    val vectors: List<ContextVectorInput>,
    val projections: List<ContextProjectionInput>,
    val replays: List<ContextReplayInput>,
)

data class ContextContentInput(val id: String, val ownerId: String, val sourceHash: String, val payload: ByteArray)
data class ContextVectorInput(val id: String, val contentId: String, val ownerId: String, val sourceHash: String, val modelId: String, val dimension: Int, val payload: ByteArray)
data class ContextProjectionInput(val id: String, val ownerId: String, val sourceHash: String, val frontier: String, val payload: ByteArray)
data class ContextReplayInput(val id: String, val ownerId: String, val trajectoryId: String, val revisionId: String, val classification: String, val safetyState: String, val payload: ByteArray)

/** Encrypts and atomically persists derived context without touching the run journal. */
class ContextRepository(
    private val database: ContextDatabase,
    private val cipher: ContextCipher,
) {
    suspend fun deleteOwner(ownerId: String) = database.dao().deleteOwner(ownerId)

    suspend fun activate(
        input: ContextCorpusGeneration,
        afterStaging: suspend () -> Unit = {},
    ) {
        validate(input)
        val owners = (
            input.contents.map(ContextContentInput::ownerId) +
                input.vectors.map(ContextVectorInput::ownerId) +
                input.projections.map(ContextProjectionInput::ownerId) +
                input.replays.map(ContextReplayInput::ownerId)
            ).distinct()
        database.dao().activateCorpus(
            generation = input.generationId,
            owners = owners,
            contents = input.contents.map {
                val envelope = encrypt("context_content", it.ownerId, it.payload)
                ContextContentEntity(it.id, input.generationId, it.ownerId, it.sourceHash, envelope.version, envelope.nonce, envelope.ciphertext, false)
            },
            vectors = input.vectors.map {
                val envelope = encrypt("context_vector", it.ownerId, it.payload)
                ContextVectorEntity(it.id, it.contentId, input.generationId, it.ownerId, it.sourceHash, it.modelId, it.dimension, envelope.version, envelope.nonce, envelope.ciphertext, false)
            },
            projections = input.projections.map {
                val envelope = encrypt("context_projection", it.ownerId, it.payload)
                ContextProjectionEntity(it.id, input.generationId, it.ownerId, it.sourceHash, it.frontier, envelope.version, envelope.nonce, envelope.ciphertext, false)
            },
            replays = input.replays.map {
                val envelope = encrypt("context_replay", it.ownerId, it.payload)
                ContextReplayEntity(it.id, input.generationId, it.ownerId, it.trajectoryId, it.revisionId, it.classification, it.safetyState, envelope.version, envelope.nonce, envelope.ciphertext, false)
            },
            afterStaging = afterStaging,
        )
    }

    suspend fun activeContent(ownerId: String): List<ByteArray> =
        database.dao().activeContentRows(ownerId).map { row ->
            when (
                val result = cipher.decrypt(
                    EncryptedContextEnvelope(row.envelopeVersion, row.nonce, row.ciphertext),
                    ContextAad("context_content", row.ownerId, SCHEMA_VERSION),
                )
            ) {
                is ContextCipherResult.Success -> result.value
                is ContextCipherResult.ResetRequired -> throw ContextPersistenceResetRequired(result.reason)
            }
        }

    private fun encrypt(table: String, owner: String, payload: ByteArray): EncryptedContextEnvelope =
        when (val result = cipher.encrypt(payload, ContextAad(table, owner, SCHEMA_VERSION))) {
            is ContextCipherResult.Success -> result.value
            is ContextCipherResult.ResetRequired -> throw ContextPersistenceResetRequired(result.reason)
        }

    private fun validate(input: ContextCorpusGeneration) {
        require(input.generationId.isNotBlank())
        require(
            (input.contents.map(ContextContentInput::ownerId) +
                input.vectors.map(ContextVectorInput::ownerId) +
                input.projections.map(ContextProjectionInput::ownerId) +
                input.replays.map(ContextReplayInput::ownerId)).all(String::isNotBlank),
        )
        val contentIds = input.contents.map(ContextContentInput::id).toSet()
        val contentOwners = input.contents.associate { it.id to it.ownerId }
        require(contentIds.size == input.contents.size)
        require(
            input.vectors.all {
                it.contentId in contentIds &&
                    contentOwners[it.contentId] == it.ownerId &&
                    it.dimension > 0
            },
        )
        require((input.contents.map { it.sourceHash } + input.vectors.map { it.sourceHash } +
            input.projections.map { it.sourceHash }).all { it.matches(SHA256) })
        require(
            listOf(
                input.vectors.map(ContextVectorInput::id),
                input.projections.map(ContextProjectionInput::id),
                input.replays.map(ContextReplayInput::id),
            ).all { ids -> ids.distinct().size == ids.size },
        )
    }

    private companion object {
        const val SCHEMA_VERSION = 1
        val SHA256 = Regex("^[0-9a-f]{64}$")
    }
}

class ContextPersistenceResetRequired(val resetReason: ContextResetReason) :
    IllegalStateException("Encrypted context reset required")
