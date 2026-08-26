package com.caddie.context.embedding

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class SentencePieceUnigramTokenizerTest {
    @Test
    fun `pinned model matches upstream token ids`() {
        val model = File("src/main/assets/context/model/sentencepiece.bpe.model")
        val tokenizer = model.inputStream().use(SentencePieceUnigramTokenizer::fromModel)

        assertArrayEquals(
            longArrayOf(0, 41, 1294, 12, 161675, 599, 204111, 2),
            tokenizer.tokenize("query: WLAN einschalten"),
        )
        assertArrayEquals(
            longArrayOf(0, 46692, 12, 13527, 5140, 9, 6159, 53550, 7, 2),
            tokenizer.tokenize("passage: Open Wi-Fi settings"),
        )
        assertArrayEquals(
            longArrayOf(0, 41, 1294, 12, 2),
            tokenizer.tokenize("query: "),
        )
        assertArrayEquals(
            longArrayOf(0, 41, 1294, 12, 13779, 101811, 21754, 6, 246511, 2),
            tokenizer.tokenize("query: Grüß dich 👋"),
        )
        assertArrayEquals(
            longArrayOf(0, 41, 1294, 12, 10, 876, 2),
            tokenizer.tokenize("query: a\u200Bb"),
        )
        assertArrayEquals(
            longArrayOf(0, 41, 1294, 12, 10, 876, 2),
            tokenizer.tokenize("query: a\uFEFFb"),
        )
        assertArrayEquals(
            longArrayOf(0, 41, 1294, 12, 6619, 1456, 95162, 2),
            tokenizer.tokenize("query: OfWWW"),
        )
        assertArrayEquals(
            longArrayOf(0, 41, 1294, 12, 6, 3, 2),
            tokenizer.tokenize("query: 🫠🫠"),
        )
    }

    @Test
    fun `unigram chooses the highest scoring complete segmentation`() {
        val tokenizer = SentencePieceUnigramTokenizer.fromPieces(
            listOf(
                TokenPiece("<s>", 0f),
                TokenPiece("<pad>", 0f),
                TokenPiece("</s>", 0f),
                TokenPiece("<unk>", 0f),
                TokenPiece("▁hello", -1f),
                TokenPiece("▁", -1f),
                TokenPiece("hello", -5f),
            ),
        )

        assertArrayEquals(longArrayOf(0, 4, 2), tokenizer.tokenize("hello"))
    }

    @Test
    fun `normalization collapses whitespace and preserves unicode code points`() {
        val tokenizer = SentencePieceUnigramTokenizer.fromPieces(
            listOf(
                TokenPiece("<s>", 0f),
                TokenPiece("<pad>", 0f),
                TokenPiece("</s>", 0f),
                TokenPiece("<unk>", 0f),
                TokenPiece("▁Grüß", -1f),
                TokenPiece("▁dich", -1f),
                TokenPiece("▁👋", -1f),
            ),
        )

        assertArrayEquals(longArrayOf(0, 4, 5, 6, 2), tokenizer.tokenize("Grüß  dich\t👋"))
    }
}
