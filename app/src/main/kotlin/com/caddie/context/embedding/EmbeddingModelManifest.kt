package com.caddie.context.embedding

import java.io.InputStream
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Defines and verifies the pinned files and tensor contract of the local embedding model. */
@Serializable
data class EmbeddingModelManifest(
    val schemaVersion: Int,
    val modelId: String,
    val tokenizerId: String,
    val upstreamRevision: String,
    val quantization: String,
    val dimension: Int,
    val maxTokens: Int,
    val inputNames: List<String>,
    val outputName: String,
    val normalizationRule: String,
    val queryPrefix: String,
    val passagePrefix: String,
    val assets: List<ModelAsset>,
) {
    init {
        require(schemaVersion == 1) { "Unsupported embedding manifest schema" }
        require(dimension > 0) { "Embedding dimension must be positive" }
        require(maxTokens > 0) { "Maximum token count must be positive" }
        require(inputNames == listOf("input_ids", "attention_mask")) {
            "Unexpected E5 input tensors"
        }
        require(normalizationRule == "L2") { "E5 output must use L2 normalization" }
        require(assets.isNotEmpty()) { "Embedding assets must not be empty" }
    }

    fun verify(openStream: (String) -> InputStream) {
        assets.forEach { asset ->
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            openStream(asset.path).use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                    size += count
                }
            }
            require(size == asset.sizeBytes) {
                "Embedding asset size mismatch: ${asset.path}"
            }
            require(digest.digest().toHex() == asset.sha256) {
                "Embedding asset hash mismatch: ${asset.path}"
            }
        }
    }

    companion object {
        private val JSON = Json { ignoreUnknownKeys = false }

        fun parse(value: String): EmbeddingModelManifest =
            JSON.decodeFromString<EmbeddingModelManifest>(value)
    }
}

/** Records the expected identity and size of one bundled model file. */
@Serializable
data class ModelAsset(
    val path: String,
    val sha256: String,
    val sizeBytes: Long,
) {
    init {
        require(path.isNotBlank() && !path.startsWith("/")) {
            "Embedding asset path must be relative"
        }
        require(sha256.matches(Regex("^[0-9a-f]{64}$"))) {
            "Embedding asset hash must be SHA-256"
        }
        require(sizeBytes > 0) { "Embedding asset size must be positive" }
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
