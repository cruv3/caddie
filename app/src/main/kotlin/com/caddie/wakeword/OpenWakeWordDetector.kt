package com.caddie.wakeword

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import java.io.Closeable
import java.nio.FloatBuffer

/** Runs the bundled openWakeWord ONNX pipeline entirely on the phone. */
class OpenWakeWordDetector(
    private val context: Context,
    threshold: Float = DEFAULT_THRESHOLD,
    refractorySamples: Int = SAMPLE_RATE,
) : Closeable {
    private var environment: OrtEnvironment? = null
    private var melSession: OrtSession? = null
    private var embeddingSession: OrtSession? = null
    private var wakeSession: OrtSession? = null
    private val melBuffer = ArrayDeque<FloatArray>(MEL_BUFFER_SIZE)
    private val embeddingBuffer = ArrayDeque<FloatArray>(EMBED_BUFFER_SIZE)
    private val melWindows = MelOverlapWindow(FRAME_SAMPLES, MEL_OVERLAP_SAMPLES)
    private val trigger = WakeWordTrigger(threshold, refractorySamples)

    val detectionThreshold: Float get() = trigger.threshold
    var initialized = false
        private set

    fun initialize(): Boolean = try {
        environment = OrtEnvironment.getEnvironment()
        melSession = loadSession("wakeword/melspectrogram.onnx")
        embeddingSession = loadSession("wakeword/embedding_model.onnx")
        wakeSession = loadSession("wakeword/hey_jarvis_v0.1.onnx")
        initialized = true
        true
    } catch (error: Throwable) {
        Log.w(TAG, "wake-word models unavailable", error)
        close()
        false
    }

    fun process(samples: ShortArray): Float? {
        if (!initialized || samples.size != FRAME_SAMPLES) return null
        runMel(melWindows.next(samples))?.forEach { frame ->
            if (melBuffer.size >= MEL_BUFFER_SIZE) melBuffer.removeFirst()
            melBuffer.addLast(frame)
        } ?: return null
        if (melBuffer.size < MEL_WINDOW) return null

        val embedding = runEmbedding(melBuffer.toList().takeLast(MEL_WINDOW)) ?: return null
        if (embeddingBuffer.size >= EMBED_BUFFER_SIZE) embeddingBuffer.removeFirst()
        embeddingBuffer.addLast(embedding)
        if (embeddingBuffer.size < EMBED_WINDOW) return null
        return runWakeWord(embeddingBuffer.toList().takeLast(EMBED_WINDOW))
    }

    fun shouldFire(score: Float?): Boolean = trigger.evaluate(score, FRAME_SAMPLES)

    fun reset() {
        melBuffer.clear()
        embeddingBuffer.clear()
        melWindows.reset()
        trigger.reset()
    }

    override fun close() {
        listOf(melSession, embeddingSession, wakeSession).forEach { session ->
            try {
                session?.close()
            } catch (_: Throwable) {
            }
        }
        melSession = null
        embeddingSession = null
        wakeSession = null
        environment = null
        initialized = false
    }

    private fun loadSession(assetPath: String): OrtSession {
        val bytes = context.assets.open(assetPath).use { it.readBytes() }
        return environment!!.createSession(bytes, OrtSession.SessionOptions())
    }

    private fun runMel(samples: ShortArray): Array<FloatArray>? {
        val env = environment ?: return null
        val session = melSession ?: return null
        val floats = FloatArray(samples.size) { samples[it].toFloat() }
        OnnxTensor.createTensor(env, FloatBuffer.wrap(floats), longArrayOf(1, samples.size.toLong())).use { tensor ->
            session.run(mapOf(session.inputNames.first() to tensor)).use { result ->
                @Suppress("UNCHECKED_CAST")
                val output = result[0].value as Array<Array<Array<FloatArray>>>
                return Array(output[0][0].size) { row ->
                    FloatArray(output[0][0][row].size) { column ->
                        output[0][0][row][column] / 10f + 2f
                    }
                }
            }
        }
    }

    private fun runEmbedding(frames: List<FloatArray>): FloatArray? {
        val env = environment ?: return null
        val session = embeddingSession ?: return null
        val flat = FloatArray(MEL_WINDOW * MEL_BINS)
        frames.forEachIndexed { index, frame ->
            System.arraycopy(frame, 0, flat, index * MEL_BINS, MEL_BINS)
        }
        OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(flat),
            longArrayOf(1, MEL_WINDOW.toLong(), MEL_BINS.toLong(), 1),
        ).use { tensor ->
            session.run(mapOf(session.inputNames.first() to tensor)).use { result ->
                @Suppress("UNCHECKED_CAST")
                val output = result[0].value as Array<Array<Array<FloatArray>>>
                return output[0][0][0]
            }
        }
    }

    private fun runWakeWord(embeddings: List<FloatArray>): Float? {
        val env = environment ?: return null
        val session = wakeSession ?: return null
        val flat = FloatArray(EMBED_WINDOW * EMBED_DIM)
        embeddings.forEachIndexed { index, embedding ->
            System.arraycopy(embedding, 0, flat, index * EMBED_DIM, EMBED_DIM)
        }
        OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(flat),
            longArrayOf(1, EMBED_WINDOW.toLong(), EMBED_DIM.toLong()),
        ).use { tensor ->
            session.run(mapOf(session.inputNames.first() to tensor)).use { result ->
                @Suppress("UNCHECKED_CAST")
                val output = result[0].value as Array<FloatArray>
                return output[0][0]
            }
        }
    }

    companion object {
        private const val TAG = "OpenWakeWord"
        const val SAMPLE_RATE = 16_000
        const val FRAME_SAMPLES = 1_280
        const val MEL_OVERLAP_SAMPLES = 480
        const val DEFAULT_THRESHOLD = 0.18f
        private const val MEL_BINS = 32
        private const val MEL_WINDOW = 76
        private const val MEL_BUFFER_SIZE = 96
        private const val EMBED_DIM = 96
        private const val EMBED_WINDOW = 16
        private const val EMBED_BUFFER_SIZE = 20
    }
}
