package com.caddie.context.embedding

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class E5TokenizerTest {
    @Test
    fun `tokenizer applies the requested E5 prefix`() {
        var received = ""
        val tokenizer = E5Tokenizer(
            maxTokens = 512,
            rawTokenizer = RawTokenizer {
                received = it
                longArrayOf(0, 41, 1294, 12, 161675, 599, 204111, 2)
            },
        )

        val input = tokenizer.encode("query: ", "WLAN einschalten")

        assertEquals("query: WLAN einschalten", received)
        assertArrayEquals(
            longArrayOf(0, 41, 1294, 12, 161675, 599, 204111, 2),
            input.tokenIds,
        )
        assertArrayEquals(LongArray(8) { 1L }, input.attentionMask)
    }

    @Test
    fun `truncation preserves XLM-R start and end tokens`() {
        val raw = LongArray(600) { it.toLong() }.also {
            it[0] = 0
            it[it.lastIndex] = 2
        }
        val tokenizer = E5Tokenizer(512, RawTokenizer { raw })

        val input = tokenizer.encode("passage: ", "long document")

        assertEquals(512, input.tokenIds.size)
        assertEquals(0L, input.tokenIds.first())
        assertEquals(2L, input.tokenIds.last())
        assertArrayEquals(LongArray(512) { 1L }, input.attentionMask)
    }

    @Test
    fun `tokenizer rejects output without XLM-R boundary tokens`() {
        val tokenizer = E5Tokenizer(512, RawTokenizer { longArrayOf(4, 5, 6) })

        assertThrows(IllegalArgumentException::class.java) {
            tokenizer.encode("query: ", "invalid")
        }
    }
}
