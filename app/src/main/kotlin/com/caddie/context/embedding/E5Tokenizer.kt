package com.caddie.context.embedding

/** Runs the pinned raw tokenizer without exposing its native implementation. */
fun interface RawTokenizer {
    fun tokenize(text: String): LongArray
}

/** Holds one bounded E5 token sequence and its matching attention mask. */
data class TokenizedInput(
    val tokenIds: LongArray,
    val attentionMask: LongArray,
)

/** Applies E5 prefixes and XLM-R-safe truncation to raw tokenizer output. */
class E5Tokenizer(
    private val maxTokens: Int,
    private val rawTokenizer: RawTokenizer,
) {
    init {
        require(maxTokens >= 2) { "Token limit must preserve boundary tokens" }
    }

    fun encode(prefix: String, text: String): TokenizedInput {
        val raw = rawTokenizer.tokenize(prefix + text)
        require(raw.size >= 2 && raw.first() == START_TOKEN && raw.last() == END_TOKEN) {
            "Tokenizer output must contain XLM-R boundary tokens"
        }
        val bounded = if (raw.size <= maxTokens) {
            raw.copyOf()
        } else {
            raw.copyOf(maxTokens).also { it[it.lastIndex] = END_TOKEN }
        }
        return TokenizedInput(
            tokenIds = bounded,
            attentionMask = LongArray(bounded.size) { 1L },
        )
    }

    private companion object {
        const val START_TOKEN = 0L
        const val END_TOKEN = 2L
    }
}

/** Validates and L2-normalizes vectors returned by the embedding model. */
object EmbeddingMath {
    fun normalize(vector: FloatArray, expectedDimension: Int): FloatArray {
        require(vector.size == expectedDimension) { "Unexpected embedding dimension" }
        require(vector.all(Float::isFinite)) { "Embedding contains non-finite values" }
        val squaredNorm = vector.fold(0.0) { sum, value -> sum + value * value }
        require(squaredNorm > 0.0 && squaredNorm.isFinite()) {
            "Embedding norm must be finite and positive"
        }
        val norm = kotlin.math.sqrt(squaredNorm).toFloat()
        return FloatArray(vector.size) { index -> vector[index] / norm }
    }
}
