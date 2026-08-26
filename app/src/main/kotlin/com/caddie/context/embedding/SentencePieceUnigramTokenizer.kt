package com.caddie.context.embedding

import java.io.InputStream
import java.text.Normalizer

/** Tokenizes XLM-R text on-device from its pinned SentencePiece model without native extensions. */
internal class SentencePieceUnigramTokenizer private constructor(
    pieces: List<TokenPiece>,
    rawSentencePieceIds: Boolean,
    private val normalizer: (String) -> String,
) : RawTokenizer {
    private val candidates = pieces.withIndex()
        .filter { (_, piece) -> piece.type == PieceType.NORMAL || piece.type == PieceType.USER_DEFINED }
        .associate { (id, piece) ->
            val tokenId = if (rawSentencePieceIds) targetTokenId(id, piece.text) else id.toLong()
            piece.text to Candidate(tokenId, piece.score)
        }
    private val unknownId = pieces.indexOfFirst { it.type == PieceType.UNKNOWN }
        .takeIf { it >= 0 }
        ?.let { id -> if (rawSentencePieceIds) targetTokenId(id, pieces[id].text) else id.toLong() }
        ?: UNKNOWN_TOKEN
    private val unknownScore = pieces.minOfOrNull(TokenPiece::score)?.minus(10f) ?: -100f
    private val maxPieceCodePoints = candidates.keys.maxOfOrNull { it.codePointCount(0, it.length) } ?: 1

    override fun tokenize(text: String): LongArray {
        val normalized = normalizer(text)
        val source = METASPACE + normalized.replace(' ', METASPACE)
        val best = DoubleArray(source.length + 1) { Double.NEGATIVE_INFINITY }
        val previous = IntArray(source.length + 1) { -1 }
        val tokenAt = LongArray(source.length + 1) { unknownId }
        best[0] = 0.0

        var start = 0
        while (start < source.length) {
            if (best[start].isFinite()) {
                var end = start
                var codePoints = 0
                var matched = false
                while (end < source.length && codePoints < maxPieceCodePoints) {
                    end += Character.charCount(source.codePointAt(end))
                    codePoints++
                    candidates[source.substring(start, end)]?.let { candidate ->
                        matched = true
                        update(best, previous, tokenAt, start, end, candidate.id, candidate.score)
                    }
                }
                if (!matched) {
                    val unknownEnd = start + Character.charCount(source.codePointAt(start))
                    update(best, previous, tokenAt, start, unknownEnd, unknownId, unknownScore)
                }
            }
            start += Character.charCount(source.codePointAt(start))
        }

        check(previous[source.length] >= 0) { "SentencePiece could not tokenize input" }
        val reversed = ArrayList<Long>()
        var cursor = source.length
        while (cursor > 0) {
            reversed += tokenAt[cursor]
            cursor = previous[cursor]
        }
        val forward = ArrayList<Long>(reversed.size)
        reversed.asReversed().forEach { token ->
            if (token != unknownId || forward.lastOrNull() != unknownId) forward += token
        }
        return LongArray(forward.size + 2).also { tokens ->
            tokens[0] = START_TOKEN
            forward.forEachIndexed { index, token -> tokens[index + 1] = token }
            tokens[tokens.lastIndex] = END_TOKEN
        }
    }

    private fun update(
        best: DoubleArray,
        previous: IntArray,
        tokenAt: LongArray,
        start: Int,
        end: Int,
        token: Long,
        score: Float,
    ) {
        val total = best[start] + score
        if (total > best[end]) {
            best[end] = total
            previous[end] = start
            tokenAt[end] = token
        }
    }

    companion object {
        private const val START_TOKEN = 0L
        private const val END_TOKEN = 2L
        private const val UNKNOWN_TOKEN = 3L
        private const val METASPACE = '▁'

        fun fromModel(input: InputStream): SentencePieceUnigramTokenizer {
            val model = SentencePieceModelReader(input).readModel()
            return SentencePieceUnigramTokenizer(
                model.pieces,
                rawSentencePieceIds = true,
                normalizer = PrecompiledCharsMap(model.precompiledCharsMap)::normalize,
            )
        }

        internal fun fromPieces(pieces: List<TokenPiece>): SentencePieceUnigramTokenizer =
            SentencePieceUnigramTokenizer(
                pieces,
                rawSentencePieceIds = false,
                normalizer = ::fallbackNormalize,
            )

        private fun targetTokenId(rawId: Int, text: String): Long = when (text) {
            "<s>" -> START_TOKEN
            "</s>" -> END_TOKEN
            "<unk>" -> UNKNOWN_TOKEN
            else -> (rawId + 1).toLong()
        }

        private fun fallbackNormalize(text: String): String =
            Normalizer.normalize(text, Normalizer.Form.NFKC)
                .map { character -> if (character.isWhitespace()) ' ' else character }
                .joinToString("")
                .replace(Regex(" {2,}"), " ")
                .trim()
    }
}

internal data class TokenPiece(
    val text: String,
    val score: Float,
    val type: PieceType = PieceType.NORMAL,
)

internal enum class PieceType(val wireValue: Int) {
    NORMAL(1),
    UNKNOWN(2),
    CONTROL(3),
    USER_DEFINED(4),
    UNUSED(5),
    BYTE(6),
    ;

    companion object {
        fun fromWire(value: Int): PieceType = entries.firstOrNull { it.wireValue == value } ?: NORMAL
    }
}

private data class Candidate(val id: Long, val score: Float)

private data class ParsedSentencePieceModel(
    val pieces: List<TokenPiece>,
    val precompiledCharsMap: ByteArray,
)

/** Applies the normalization trie embedded in the pinned SentencePiece model. */
private class PrecompiledCharsMap(blob: ByteArray) {
    private val units: IntArray
    private val normalized: ByteArray

    init {
        require(blob.size >= 8) { "SentencePiece normalization map is missing" }
        val trieSize = blob.readLittleEndianInt(0)
        require(trieSize >= 4 && trieSize % 4 == 0 && trieSize + 4 <= blob.size) {
            "Invalid SentencePiece normalization trie"
        }
        units = IntArray(trieSize / 4) { index -> blob.readLittleEndianInt(4 + index * 4) }
        normalized = blob.copyOfRange(4 + trieSize, blob.size)
    }

    fun normalize(text: String): String {
        val source = text.toByteArray(Charsets.UTF_8)
        val output = ArrayList<Byte>(source.size)
        var position = 0
        while (position < source.size) {
            val match = longestPrefix(source, position)
            if (match != null) {
                appendReplacement(output, match.value)
                position += match.length
            } else {
                val length = utf8CodePointLength(source[position])
                repeat(length.coerceAtMost(source.size - position)) { output += source[position++] }
            }
        }
        return output.toByteArray().toString(Charsets.UTF_8)
            .replace(Regex(" {2,}"), " ")
            .trim()
    }

    private fun longestPrefix(source: ByteArray, start: Int): TrieMatch? {
        var node = unitOffset(units[0])
        var best: TrieMatch? = null
        var index = start
        while (index < source.size) {
            val label = source[index].toInt() and 0xff
            node = node xor label
            if (node !in units.indices) break
            val unit = units[node]
            if (unitLabel(unit) != label) break
            node = node xor unitOffset(unit)
            if (unitHasLeaf(unit)) {
                if (node !in units.indices) break
                best = TrieMatch(index - start + 1, units[node] and Int.MAX_VALUE)
            }
            index++
        }
        return best
    }

    private fun appendReplacement(output: MutableList<Byte>, offset: Int) {
        require(offset in normalized.indices) { "Invalid SentencePiece normalization value" }
        var index = offset
        while (index < normalized.size && normalized[index].toInt() != 0) output += normalized[index++]
        require(index < normalized.size) { "Unterminated SentencePiece normalization value" }
    }

    private fun unitOffset(unit: Int): Int =
        (unit ushr 10) shl ((unit and 0x200) ushr 6)

    private fun unitLabel(unit: Int): Int = unit and (Int.MIN_VALUE or 0xff)

    private fun unitHasLeaf(unit: Int): Boolean = (unit ushr 8) and 1 == 1

    private fun utf8CodePointLength(first: Byte): Int = when (first.toInt() and 0xf0) {
        in 0x00..0x70 -> 1
        0xc0, 0xd0 -> 2
        0xe0 -> 3
        else -> 4
    }

    private data class TrieMatch(val length: Int, val value: Int)
}

private fun ByteArray.readLittleEndianInt(offset: Int): Int =
    (this[offset].toInt() and 0xff) or
        ((this[offset + 1].toInt() and 0xff) shl 8) or
        ((this[offset + 2].toInt() and 0xff) shl 16) or
        ((this[offset + 3].toInt() and 0xff) shl 24)

/** Reads the vocabulary and normalization map needed from a SentencePiece ModelProto. */
private class SentencePieceModelReader(input: InputStream) {
    private val data = input.use { it.readBytes() }
    private var position = 0

    fun readModel(): ParsedSentencePieceModel {
        val pieces = ArrayList<TokenPiece>(250_000)
        var precompiledCharsMap = byteArrayOf()
        while (position < data.size) {
            val tag = readVarint().toInt()
            val field = tag ushr 3
            val wireType = tag and 7
            if (field == 1 && wireType == LENGTH_DELIMITED) {
                pieces += readPiece(readVarint().toInt())
            } else if (field == 3 && wireType == LENGTH_DELIMITED) {
                precompiledCharsMap = readNormalizer(readVarint().toInt())
            } else {
                skip(wireType)
            }
        }
        require(pieces.isNotEmpty()) { "SentencePiece model contains no vocabulary" }
        require(precompiledCharsMap.isNotEmpty()) { "SentencePiece model contains no normalization map" }
        return ParsedSentencePieceModel(pieces, precompiledCharsMap)
    }

    private fun readNormalizer(length: Int): ByteArray {
        val limit = position + length
        require(limit in position..data.size) { "Invalid SentencePiece normalizer length" }
        var charsMap = byteArrayOf()
        while (position < limit) {
            val tag = readVarint().toInt()
            if (tag ushr 3 == 2 && tag and 7 == LENGTH_DELIMITED) charsMap = readBytes() else skip(tag and 7)
        }
        require(position == limit) { "SentencePiece normalizer exceeds its boundary" }
        return charsMap
    }

    private fun readPiece(length: Int): TokenPiece {
        val limit = position + length
        require(limit in position..data.size) { "Invalid SentencePiece entry length" }
        var text = ""
        var score = 0f
        var type = PieceType.NORMAL
        while (position < limit) {
            val tag = readVarint().toInt()
            when (tag ushr 3) {
                1 -> text = readString()
                2 -> score = Float.fromBits(readFixed32())
                3 -> type = PieceType.fromWire(readVarint().toInt())
                else -> skip(tag and 7)
            }
        }
        require(position == limit) { "SentencePiece entry exceeds its boundary" }
        return TokenPiece(text, score, type)
    }

    private fun readString(): String {
        return readBytes().toString(Charsets.UTF_8)
    }

    private fun readBytes(): ByteArray {
        val length = readVarint().toInt()
        require(length >= 0 && position + length <= data.size) { "Invalid SentencePiece bytes" }
        return data.copyOfRange(position, position + length).also { position += length }
    }

    private fun readFixed32(): Int {
        require(position + 4 <= data.size) { "Truncated SentencePiece fixed32" }
        return (data[position++].toInt() and 0xff) or
            ((data[position++].toInt() and 0xff) shl 8) or
            ((data[position++].toInt() and 0xff) shl 16) or
            ((data[position++].toInt() and 0xff) shl 24)
    }

    private fun readVarint(): Long {
        var value = 0L
        var shift = 0
        while (shift < 64) {
            require(position < data.size) { "Truncated SentencePiece varint" }
            val byte = data[position++].toInt() and 0xff
            value = value or ((byte and 0x7f).toLong() shl shift)
            if (byte and 0x80 == 0) return value
            shift += 7
        }
        error("Invalid SentencePiece varint")
    }

    private fun skip(wireType: Int) {
        when (wireType) {
            VARINT -> readVarint()
            FIXED64 -> position += 8
            LENGTH_DELIMITED -> {
                val length = readVarint().toInt()
                position += length
            }
            START_GROUP -> {
                while (position < data.size) {
                    val nestedWireType = readVarint().toInt() and 7
                    if (nestedWireType == END_GROUP) break
                    skip(nestedWireType)
                }
            }
            END_GROUP -> Unit
            FIXED32 -> position += 4
            else -> error("Unsupported SentencePiece wire type $wireType at byte $position")
        }
        require(position <= data.size) { "Truncated SentencePiece field" }
    }

    private companion object {
        const val VARINT = 0
        const val FIXED64 = 1
        const val LENGTH_DELIMITED = 2
        const val START_GROUP = 3
        const val END_GROUP = 4
        const val FIXED32 = 5
    }
}
