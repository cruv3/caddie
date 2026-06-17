package com.caddie.wakeword

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import java.io.Closeable
import java.nio.FloatBuffer

/**
 * openWakeWord detection pipeline.
 *
 * Pipeline:
 *   16-bit PCM @ 16kHz, frames of 1280 samples (80ms)
 *     -> melspectrogram.onnx        : (1, 1280) float -> (1, 1, 5, 32) float
 *     -> embedding_model.onnx       : (1, 76, 32, 1) float -> (1, 1, 1, 96) float
 *        (called every 8 mel frames; needs ≥76 mel frames in buffer)
 *     -> hey_jarvis_v0.1.onnx       : (1, 16, 96) float -> (1, 1) float [0..1]
 *
 * Models must be present in `assets/wakeword/`. See the README there.
 */
class OpenWakeWordDetector(
    private val context: Context,
    private val threshold: Float = 0.25f,
    private val refractorySamples: Int = 16_000,  // 1s lockout after a hit
) : Closeable {

    private var env: OrtEnvironment? = null
    private var melSession: OrtSession? = null
    private var embedSession: OrtSession? = null
    private var wakeSession: OrtSession? = null

    private val melBuffer = ArrayDeque<FloatArray>(96)  // FloatArray(32) per frame
    private val embeddingBuffer = ArrayDeque<FloatArray>(20)  // FloatArray(96)
    private var melFramesSinceLastEmbed = 0
    private var samplesSinceLastHit = Int.MAX_VALUE

    var initialized: Boolean = false
        private set

    fun initialize(): Boolean {
        return try {
            env = OrtEnvironment.getEnvironment()
            melSession = loadSession("wakeword/melspectrogram.onnx")
            embedSession = loadSession("wakeword/embedding_model.onnx")
            wakeSession = loadSession("wakeword/hey_jarvis_v0.1.onnx")
            initialized = true
            true
        } catch (t: Throwable) {
            Log.w(TAG, "wake-word models missing or invalid: ${t.message}")
            close()
            false
        }
    }

    private fun loadSession(assetPath: String): OrtSession {
        val bytes = context.assets.open(assetPath).use { it.readBytes() }
        return env!!.createSession(bytes, OrtSession.SessionOptions())
    }

    /**
     * Push 1280 int16 audio samples (80ms @ 16kHz) into the pipeline.
     * Returns the wake-word score for the most recent inference, or null if
     * not enough buffered context yet. A score >= threshold means the
     * keyword was detected; the caller can also use [shouldFire].
     */
    fun process(samples: ShortArray): Float? {
        if (!initialized) return null
        if (samples.size != FRAME_SAMPLES) {
            Log.w(TAG, "expected $FRAME_SAMPLES samples per frame, got ${samples.size}")
            return null
        }
        if (samplesSinceLastHit < Int.MAX_VALUE) {
            samplesSinceLastHit += samples.size
        }

        val mel = runMel(samples) ?: return null
        for (frame in mel) {
            if (melBuffer.size >= MEL_BUFFER_SIZE) melBuffer.removeFirst()
            melBuffer.addLast(frame)
            melFramesSinceLastEmbed++
        }

        var newEmbeddings = 0
        while (melBuffer.size >= MEL_WINDOW && melFramesSinceLastEmbed >= MEL_STRIDE) {
            val embedding = runEmbedding(melBuffer.toList().takeLast(MEL_WINDOW))
            if (embedding != null) {
                if (embeddingBuffer.size >= EMBED_BUFFER_SIZE) embeddingBuffer.removeFirst()
                embeddingBuffer.addLast(embedding)
                newEmbeddings++
            }
            melFramesSinceLastEmbed -= MEL_STRIDE
        }
        if (newEmbeddings == 0 || embeddingBuffer.size < EMBED_WINDOW) return null

        return runWakeWord(embeddingBuffer.toList().takeLast(EMBED_WINDOW))
    }

    fun shouldFire(score: Float?): Boolean {
        if (score == null) return false
        if (samplesSinceLastHit < refractorySamples) return false
        if (score < threshold) return false
        samplesSinceLastHit = 0
        return true
    }

    private fun runMel(samples: ShortArray): Array<FloatArray>? {
        val env = env ?: return null
        val session = melSession ?: return null
        val floats = FloatArray(samples.size) { samples[it].toFloat() }
        val tensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(floats),
            longArrayOf(1L, samples.size.toLong()),
        )
        tensor.use {
            val name = session.inputNames.first()
            session.run(mapOf(name to tensor)).use { result ->
                @Suppress("UNCHECKED_CAST")
                val out = result[0].value as Array<Array<Array<FloatArray>>>
                // Shape (1, 1, T, 32) — flatten to T frames of 32 bins
                val tFrames = out[0][0]
                return Array(tFrames.size) { i ->
                    FloatArray(tFrames[i].size) { j ->
                        // openWakeWord normalization: (mel / 10) + 2
                        (tFrames[i][j] / 10f) + 2f
                    }
                }
            }
        }
    }

    private fun runEmbedding(melFrames: List<FloatArray>): FloatArray? {
        require(melFrames.size == MEL_WINDOW)
        val env = env ?: return null
        val session = embedSession ?: return null
        val flat = FloatArray(MEL_WINDOW * MEL_BINS)
        for (i in 0 until MEL_WINDOW) {
            System.arraycopy(melFrames[i], 0, flat, i * MEL_BINS, MEL_BINS)
        }
        val tensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(flat),
            longArrayOf(1L, MEL_WINDOW.toLong(), MEL_BINS.toLong(), 1L),
        )
        tensor.use {
            val name = session.inputNames.first()
            session.run(mapOf(name to tensor)).use { result ->
                @Suppress("UNCHECKED_CAST")
                val out = result[0].value as Array<Array<Array<FloatArray>>>
                return out[0][0][0]
            }
        }
    }

    private fun runWakeWord(embeddings: List<FloatArray>): Float? {
        require(embeddings.size == EMBED_WINDOW)
        val env = env ?: return null
        val session = wakeSession ?: return null
        val flat = FloatArray(EMBED_WINDOW * EMBED_DIM)
        for (i in 0 until EMBED_WINDOW) {
            System.arraycopy(embeddings[i], 0, flat, i * EMBED_DIM, EMBED_DIM)
        }
        val tensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(flat),
            longArrayOf(1L, EMBED_WINDOW.toLong(), EMBED_DIM.toLong()),
        )
        tensor.use {
            val name = session.inputNames.first()
            session.run(mapOf(name to tensor)).use { result ->
                @Suppress("UNCHECKED_CAST")
                val out = result[0].value as Array<FloatArray>
                return out[0][0]
            }
        }
    }

    fun reset() {
        melBuffer.clear()
        embeddingBuffer.clear()
        melFramesSinceLastEmbed = 0
        samplesSinceLastHit = Int.MAX_VALUE
    }

    override fun close() {
        try { melSession?.close() } catch (_: Throwable) {}
        try { embedSession?.close() } catch (_: Throwable) {}
        try { wakeSession?.close() } catch (_: Throwable) {}
        melSession = null
        embedSession = null
        wakeSession = null
        env = null
        initialized = false
    }

    companion object {
        private const val TAG = "OpenWakeWord"

        const val SAMPLE_RATE = 16_000
        const val FRAME_SAMPLES = 1280  // 80ms

        private const val MEL_BINS = 32
        private const val MEL_WINDOW = 76     // model input frames
        private const val MEL_STRIDE = 8      // run embedding every 8 new mel frames
        private const val MEL_BUFFER_SIZE = 96

        private const val EMBED_DIM = 96
        private const val EMBED_WINDOW = 16   // wakeword model input length
        private const val EMBED_BUFFER_SIZE = 20
    }
}
