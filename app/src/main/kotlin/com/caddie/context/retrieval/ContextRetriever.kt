package com.caddie.context.retrieval

import com.caddie.context.embedding.EmbeddingProvider
import com.caddie.context.skill.Skill
import com.caddie.context.skill.SkillCatalog
import java.text.Normalizer
import java.util.Locale
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException

/** Performs deterministic hybrid retrieval with a conservative lexical fallback. */
class ContextRetriever(
    catalog: SkillCatalog,
    private val embeddingProvider: EmbeddingProvider?,
    skillEmbeddings: Map<String, FloatArray>,
    hints: List<ContextHint> = emptyList(),
) {
    private val dimension = embeddingProvider?.dimension
    private val skills = catalog.all.map { skill ->
        SkillDocument(
            skill = skill,
            lexical = lexicalDocument(
                skill.title,
                skill.description,
                triggers = skill.triggers,
            ),
            embedding = skillEmbeddings[skill.id]?.validatedCopy(),
        )
    }
    private val hints = hints
        .sortedBy(ContextHint::id)
        .map { hint ->
            require(hint.id.isNotBlank()) { "Hint ID must not be blank" }
            HintDocument(
                hint = hint,
                lexical = lexicalDocument(hint.text, triggers = hint.triggers),
                embedding = hint.embedding?.validatedCopy(),
            )
        }

    suspend fun retrieve(query: String): RetrievalResult {
        val lexicalQuery = LexicalText.from(query)
        if (lexicalQuery.tokens.isEmpty() || lexicalQuery.nonStopwords.isEmpty()) {
            return empty(RetrievalMode.SEMANTIC)
        }
        if (
            lexicalQuery.tokens.any(NEGATIONS::contains) ||
            OPPOSING_ACTIONS.any { (left, right) ->
                left in lexicalQuery.tokens && right in lexicalQuery.tokens
            }
        ) {
            return empty(RetrievalMode.SEMANTIC)
        }

        val queryEmbedding = tryEmbedding(query)
        val mode = if (queryEmbedding == null) {
            RetrievalMode.DEGRADED_LEXICAL
        } else {
            RetrievalMode.SEMANTIC
        }
        val selectedSkills = skills.mapNotNull { document ->
            val score = score(
                query = lexicalQuery,
                document = document.lexical,
                queryEmbedding = queryEmbedding,
                documentEmbedding = document.embedding,
                mode = mode,
            ) ?: return@mapNotNull null
            RetrievedSkill(document.skill, score)
        }.sortedWith(
            compareByDescending<RetrievedSkill> { it.score.total }
                .thenByDescending { it.score.exactTriggerCount }
                .thenBy { it.skill.id },
        ).take(MAX_SKILLS)
        val selectedHints = hints.mapNotNull { document ->
            val score = score(
                query = lexicalQuery,
                document = document.lexical,
                queryEmbedding = queryEmbedding,
                documentEmbedding = document.embedding,
                mode = mode,
            ) ?: return@mapNotNull null
            RetrievedHint(document.hint.id, document.hint.text, score)
        }.sortedWith(
            compareByDescending<RetrievedHint> { it.score.total }
                .thenByDescending { it.score.exactTriggerCount }
                .thenBy { it.id },
        ).take(MAX_HINTS)

        return RetrievalResult(mode, selectedSkills, selectedHints)
    }

    private suspend fun tryEmbedding(query: String): FloatArray? {
        val provider = embeddingProvider ?: return null
        return try {
            provider.embedQuery(query).takeIf { vector ->
                vector.size == provider.dimension &&
                    vector.all(Float::isFinite) &&
                    squaredNorm(vector) > 0.0
            }?.copyOf()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    private fun score(
        query: LexicalText,
        document: LexicalDocument,
        queryEmbedding: FloatArray?,
        documentEmbedding: FloatArray?,
        mode: RetrievalMode,
    ): RetrievalScore? {
        val exactTriggerCount = document.triggers.count { trigger ->
            query.tokens.containsSequence(trigger)
        }
        val overlapCount = query.nonStopwords.intersect(document.tokens).size
        val triggerBoost = if (exactTriggerCount > 0) TRIGGER_BOOST else 0f
        val tokenBoost = if (query.nonStopwords.isEmpty()) {
            0f
        } else {
            (overlapCount.toFloat() / query.nonStopwords.size * MAX_TOKEN_BOOST)
                .coerceAtMost(MAX_TOKEN_BOOST)
        }
        val cosine = if (queryEmbedding != null && documentEmbedding != null) {
            cosine(queryEmbedding, documentEmbedding)
        } else {
            0f
        }
        val eligible = when (mode) {
            RetrievalMode.SEMANTIC ->
                cosine >= COSINE_THRESHOLD || exactTriggerCount > 0
            RetrievalMode.DEGRADED_LEXICAL ->
                exactTriggerCount > 0 || overlapCount >= MIN_FALLBACK_OVERLAP
        }
        if (!eligible) return null

        val reasons = buildList {
            if (cosine >= COSINE_THRESHOLD) add("cosine-threshold")
            if (exactTriggerCount > 0) add("exact-trigger")
            if (overlapCount > 0) add("token-overlap:$overlapCount")
            if (mode == RetrievalMode.DEGRADED_LEXICAL) add("lexical-fallback")
        }
        return RetrievalScore(
            cosine = cosine,
            triggerBoost = triggerBoost,
            tokenBoost = tokenBoost,
            exactTriggerCount = exactTriggerCount,
            reasons = reasons,
        )
    }

    private fun FloatArray.validatedCopy(): FloatArray {
        val expected = requireNotNull(dimension) {
            "Stored embeddings require an embedding provider"
        }
        require(size == expected) { "Stored embedding has the wrong dimension" }
        require(all(Float::isFinite) && squaredNorm(this) > 0.0) {
            "Stored embedding must be finite and non-zero"
        }
        return copyOf()
    }

    private fun empty(mode: RetrievalMode): RetrievalResult =
        RetrievalResult(mode, emptyList(), emptyList())

    private data class SkillDocument(
        val skill: Skill,
        val lexical: LexicalDocument,
        val embedding: FloatArray?,
    )

    private data class HintDocument(
        val hint: ContextHint,
        val lexical: LexicalDocument,
        val embedding: FloatArray?,
    )

    private companion object {
        const val COSINE_THRESHOLD = 0.55f
        const val TRIGGER_BOOST = 0.15f
        const val MAX_TOKEN_BOOST = 0.05f
        const val MIN_FALLBACK_OVERLAP = 2
        const val MAX_SKILLS = 3
        const val MAX_HINTS = 2
    }
}

private data class LexicalDocument(
    val tokens: Set<String>,
    val triggers: List<List<String>>,
)

private data class LexicalText(
    val tokens: List<String>,
    val nonStopwords: Set<String>,
) {
    companion object {
        fun from(text: String): LexicalText {
            val tokens = tokenize(text)
            return LexicalText(tokens, tokens.filterNot(STOPWORDS::contains).toSet())
        }
    }
}

private fun lexicalDocument(
    vararg text: String,
    triggers: List<String> = emptyList(),
): LexicalDocument {
    val triggerTokens = triggers.map(::tokenize).filter(List<String>::isNotEmpty)
    val tokens = (text.asSequence().flatMap { tokenize(it).asSequence() } +
        triggerTokens.asSequence().flatten())
        .filterNot(STOPWORDS::contains)
        .toSet()
    return LexicalDocument(tokens, triggerTokens)
}

private fun tokenize(text: String): List<String> {
    val normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
    return TOKEN.findAll(normalized).map(MatchResult::value).toList()
}

private fun List<String>.containsSequence(candidate: List<String>): Boolean {
    if (candidate.isEmpty() || candidate.size > size) return false
    return (0..size - candidate.size).any { start ->
        candidate.indices.all { offset -> this[start + offset] == candidate[offset] }
    }
}

private fun cosine(left: FloatArray, right: FloatArray): Float {
    var dot = 0.0
    left.indices.forEach { index -> dot += left[index] * right[index] }
    val denominator = sqrt(squaredNorm(left) * squaredNorm(right))
    return (dot / denominator).toFloat().coerceIn(-1f, 1f)
}

private fun squaredNorm(vector: FloatArray): Double =
    vector.fold(0.0) { sum, value -> sum + value * value }

private val TOKEN = Regex("[\\p{L}\\p{N}]+")

private val STOPWORDS = setOf(
    "a", "an", "and", "bitte", "das", "der", "die", "ein", "eine", "for", "für",
    "in", "of", "please", "the", "to", "und", "zu",
)

private val NEGATIONS = setOf(
    "kein", "keine", "keinen", "nicht", "no", "not", "ohne", "without",
)

private val OPPOSING_ACTIONS = setOf(
    "activate" to "deactivate",
    "close" to "open",
    "disable" to "enable",
    "ausschalten" to "einschalten",
    "öffnen" to "schließen",
)
