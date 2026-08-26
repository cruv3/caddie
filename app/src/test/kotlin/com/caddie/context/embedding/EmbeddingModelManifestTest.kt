package com.caddie.context.embedding

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EmbeddingModelManifestTest {
    @Test
    fun `manifest preserves the pinned E5 runtime contract`() {
        val bytes = "model".encodeToByteArray()
        val manifest = EmbeddingModelManifest.parse(manifestJson(bytes))

        assertEquals("Teradata/multilingual-e5-small", manifest.modelId)
        assertEquals("intfloat/multilingual-e5-small", manifest.tokenizerId)
        assertEquals(
            "26d0d6b003e8f5bde1f7dff0c6996c04cf73d675",
            manifest.upstreamRevision,
        )
        assertEquals("INT8", manifest.quantization)
        assertEquals(384, manifest.dimension)
        assertEquals(512, manifest.maxTokens)
        assertEquals(listOf("input_ids", "attention_mask"), manifest.inputNames)
        assertEquals("sentence_embedding", manifest.outputName)
        assertEquals("query: ", manifest.queryPrefix)
        assertEquals("passage: ", manifest.passagePrefix)
        manifest.verify { ByteArrayInputStream(bytes) }
    }

    @Test
    fun `manifest rejects an asset hash mismatch before inference`() {
        val manifest = EmbeddingModelManifest.parse(
            manifestJson("expected".encodeToByteArray()),
        )

        assertThrows(IllegalArgumentException::class.java) {
            manifest.verify {
                ByteArrayInputStream("corrupt!".encodeToByteArray())
            }
        }
    }

    private fun manifestJson(bytes: ByteArray): String = """
        {
          "schemaVersion": 1,
          "modelId": "Teradata/multilingual-e5-small",
          "tokenizerId": "intfloat/multilingual-e5-small",
          "upstreamRevision": "26d0d6b003e8f5bde1f7dff0c6996c04cf73d675",
          "quantization": "INT8",
          "dimension": 384,
          "maxTokens": 512,
          "inputNames": ["input_ids", "attention_mask"],
          "outputName": "sentence_embedding",
          "normalizationRule": "L2",
          "queryPrefix": "query: ",
          "passagePrefix": "passage: ",
          "assets": [{
            "path": "context/model/model.onnx",
            "sha256": "9372c470eeadd5ecd9c3c74c2b3cb633f8e2f2fad799250a0f70d652b6b825e4",
            "sizeBytes": ${bytes.size}
          }]
        }
    """.trimIndent()
}
