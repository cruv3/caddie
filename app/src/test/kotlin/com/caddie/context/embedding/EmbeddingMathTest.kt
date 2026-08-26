package com.caddie.context.embedding

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EmbeddingMathTest {
    @Test
    fun `normalization returns a copied unit vector`() {
        val source = floatArrayOf(3f, 4f)

        val normalized = EmbeddingMath.normalize(source, expectedDimension = 2)

        assertArrayEquals(floatArrayOf(0.6f, 0.8f), normalized, 0.00001f)
        source[0] = 0f
        assertArrayEquals(floatArrayOf(0.6f, 0.8f), normalized, 0.00001f)
    }

    @Test
    fun `normalization rejects invalid model output`() {
        listOf(
            floatArrayOf(),
            floatArrayOf(Float.NaN, 1f),
            floatArrayOf(Float.POSITIVE_INFINITY, 1f),
            floatArrayOf(0f, 0f),
        ).forEach { vector ->
            assertThrows(IllegalArgumentException::class.java) {
                EmbeddingMath.normalize(vector, expectedDimension = 2)
            }
        }
    }
}
