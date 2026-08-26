package com.caddie.context.embedding

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Reports a local tokenizer or embedding inference failure without enabling a network fallback. */
class EmbeddingFailure(message: String, cause: Throwable) : Exception(message, cause)

/** Runs pinned XLM-R tokenization and E5 embedding inference entirely on the device. */
class OnnxE5Embedder private constructor(
    private val manifest: EmbeddingModelManifest,
    private val environment: OrtEnvironment,
    rawTokenizer: RawTokenizer,
    private val embeddingSession: OrtSession,
    private val dispatcher: CoroutineDispatcher,
) : EmbeddingProvider {
    override val modelId: String = manifest.modelId
    override val dimension: Int = manifest.dimension

    private val operationMutex = Mutex()
    private val nativeLock = ReentrantLock()
    private val closed = AtomicBoolean(false)
    private val tokenizer = E5Tokenizer(
        manifest.maxTokens,
        rawTokenizer,
    )

    override suspend fun embedQuery(text: String): FloatArray =
        embed(manifest.queryPrefix, text)

    override suspend fun embedDocument(text: String): FloatArray =
        embed(manifest.passagePrefix, text)

    internal suspend fun tokenizeForDiagnostics(prefix: String, text: String): TokenizedInput =
        runNative { tokenizer.encode(prefix, text) }

    private suspend fun embed(prefix: String, text: String): FloatArray =
        runNative {
            val input = tokenizer.encode(prefix, text)
            OnnxTensor.createTensor(environment, arrayOf(input.tokenIds)).use { tokenIds ->
                OnnxTensor.createTensor(environment, arrayOf(input.attentionMask)).use { mask ->
                    embeddingSession.run(
                        mapOf(
                            manifest.inputNames[0] to tokenIds,
                            manifest.inputNames[1] to mask,
                        ),
                    ).use { result ->
                        @Suppress("UNCHECKED_CAST")
                        val batch = result.get(manifest.outputName).orElseThrow {
                            IllegalArgumentException(
                                "Missing embedding output ${manifest.outputName}",
                            )
                        }.value as Array<FloatArray>
                        require(batch.size == 1) { "Expected one embedding result" }
                        EmbeddingMath.normalize(batch.single(), manifest.dimension)
                    }
                }
            }
        }

    private suspend fun <T> runNative(block: () -> T): T =
        withContext(dispatcher) {
            coroutineContext.ensureActive()
            try {
                operationMutex.withLock {
                    nativeLock.withLock {
                        ensureOpen()
                        coroutineContext.ensureActive()
                        block()
                    }
                }.also {
                    coroutineContext.ensureActive()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                throw EmbeddingFailure("Local E5 inference failed", failure)
            }
        }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        nativeLock.withLock {
            embeddingSession.close()
        }
    }

    private fun ensureOpen() {
        check(!closed.get()) { "Embedding provider is closed" }
    }

    companion object {
        private const val MANIFEST_PATH = "context/model/model-manifest.json"

        suspend fun fromAssets(
            context: Context,
            dispatcher: CoroutineDispatcher = Dispatchers.Default,
        ): OnnxE5Embedder = withContext(Dispatchers.IO) {
            val assets = context.assets
            val manifest = assets.open(MANIFEST_PATH)
                .bufferedReader(Charsets.UTF_8)
                .use { EmbeddingModelManifest.parse(it.readText()) }
            manifest.verify(assets::open)

            val modelAsset = manifest.assets.single {
                it.path.endsWith("multilingual-e5-small-int8.onnx")
            }
            val tokenizerAsset = manifest.assets.single {
                it.path.endsWith("sentencepiece.bpe.model")
            }
            val modelFile = materialize(context, modelAsset)
            val rawTokenizer = assets.open(tokenizerAsset.path).use {
                SentencePieceUnigramTokenizer.fromModel(it)
            }
            val environment = OrtEnvironment.getEnvironment()
            val embeddingSession = OrtSession.SessionOptions().use { options ->
                environment.createSession(modelFile.absolutePath, options)
            }
            OnnxE5Embedder(
                manifest,
                environment,
                rawTokenizer,
                embeddingSession,
                dispatcher,
            )
        }

        private fun materialize(context: Context, asset: ModelAsset): File {
            val directory = File(context.noBackupFilesDir, "context-model").apply {
                check(mkdirs() || isDirectory) { "Cannot create private model directory" }
            }
            val target = File(directory, "${asset.sha256}-${File(asset.path).name}")
            if (target.isFile && target.length() == asset.sizeBytes && target.sha256() == asset.sha256) {
                return target
            }
            val temporary = File.createTempFile("${target.name}.", ".tmp", directory)
            try {
                context.assets.open(asset.path).use { input ->
                    temporary.outputStream().buffered().use { output ->
                        input.copyTo(output)
                    }
                }
                check(temporary.length() == asset.sizeBytes && temporary.sha256() == asset.sha256) {
                    "Copied model asset failed integrity validation"
                }
                try {
                    Files.move(
                        temporary.toPath(),
                        target.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                } catch (_: Exception) {
                    Files.move(
                        temporary.toPath(),
                        target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                }
            } finally {
                temporary.delete()
            }
            return target
        }
    }
}

private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().buffered().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
