package com.caddie.context.embedding

/** Produces normalized query and document vectors without exposing an inference backend. */
interface EmbeddingProvider : AutoCloseable {
    val modelId: String
    val dimension: Int

    suspend fun embedQuery(text: String): FloatArray

    suspend fun embedDocument(text: String): FloatArray
}
