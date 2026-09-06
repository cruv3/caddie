package com.caddie.app.composition

import android.content.Context
import android.util.Log
import com.caddie.agent.core.RequestFactory
import com.caddie.context.embedding.EmbeddingProvider
import com.caddie.context.embedding.OnnxE5Embedder
import com.caddie.context.replay.AssetReplayGuidanceLoader
import com.caddie.context.replay.ReplayGuidanceCatalog
import com.caddie.context.rag.ContextBundle
import com.caddie.context.rag.RagRequestFactory
import com.caddie.context.rag.RetrievedReplayCandidate
import com.caddie.context.retrieval.ContextRetriever
import com.caddie.context.retrieval.ContextHint
import com.caddie.context.personal.PersonalContextSnapshot
import com.caddie.context.personal.PersonalContextStore
import com.caddie.context.skill.AssetSkillLoader
import com.caddie.context.skill.SkillCatalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Adds reusable on-device skill context to normal agent requests. */
class AndroidContextRequestFactoryProvider internal constructor(
    private val catalogLoader: () -> SkillCatalog,
    private val replayCatalogLoader: () -> ReplayGuidanceCatalog = {
        ReplayGuidanceCatalog.empty()
    },
    private val embedderFactory: suspend () -> EmbeddingProvider,
    private val initializationDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val personalLoader: suspend () -> PersonalContextSnapshot = { PersonalContextSnapshot(0, emptyList()) },
    private val personalRevision: () -> Long = { 0 },
    private val failureReporter: (String, Throwable) -> Unit = { message, error ->
        Log.w("CaddieContext", message, error)
    },
) {
    private val stateMutex = Mutex()
    private var state: State = State.Uninitialized

    /** Loads skills, replay guidance, and embeddings before the first task. */
    suspend fun warmUp(): Boolean = readyContext() != null

    suspend fun forTask(task: String, delegate: RequestFactory): RequestFactory {
        val context = readyContext() ?: return delegate
        val retrieval = try {
            context.retriever.retrieve(task)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            failureReporter("Context retrieval failed; continuing without context", failure)
            return delegate
        }
        val personal = try {
            personalLoader()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Personal payloads and decoder errors must never enter logs.
            null
        }
        val personalHints = try {
            val hints = personal?.facts.orEmpty().map { fact ->
                val text = "Owner-confirmed note, not independently verified: ${fact.title}\n${fact.text}"
                val embedding = try {
                    context.embedder?.embedDocument(text)?.takeIf { vector ->
                        vector.size == context.embedder.dimension &&
                            vector.all { it.isFinite() } && vector.any { it != 0f }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) { null }
                ContextHint("personal:${fact.id}", text, listOf(fact.title), embedding)
            }
            val personalEmbedder = context.embedder.takeIf { hints.all { it.embedding != null } }
            if (hints.isEmpty()) emptyList() else ContextRetriever(
                SkillCatalog(emptyList()), personalEmbedder, emptyMap(),
                if (personalEmbedder == null) hints.map { it.copy(embedding = null) } else hints,
            ).retrieve(task).hints
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) { emptyList() }
        if (retrieval.skills.isEmpty() && retrieval.hints.isEmpty() && personalHints.isEmpty()) return delegate
        val replay = retrieval.skills.firstNotNullOfOrNull { retrieved ->
            val replayId = retrieved.skill.replayId ?: return@firstNotNullOfOrNull null
            context.replays.get(replayId)
                ?.takeIf { it.skillId == retrieved.skill.id }
                ?.let {
                    RetrievedReplayCandidate(
                        id = it.id,
                        guidance = it.guidance,
                        sourceSha256 = it.sourceSha256,
                    )
                }
        }
        val baseBundle = ContextBundle.create(retrieval, replay)
        val bundle = ContextBundle.create(
            retrieval.copy(hints = retrieval.hints + personalHints), replay,
        )
        return RagRequestFactory(delegate) {
            // A settings edit invalidates already prepared personal context on the next model request.
            if (personal != null && personalRevision() == personal.revision) bundle else baseBundle
        }
    }

    suspend fun close() {
        stateMutex.withLock {
            val current = state
            if (current is State.Ready) runCatching { current.embedder?.close() }
            state = State.Closed
        }
    }

    private suspend fun readyContext(): State.Ready? = stateMutex.withLock {
        when (val current = state) {
            State.Uninitialized -> initialize().also { state = it }.readyOrNull()
            is State.Ready -> current
            State.Unavailable, State.Closed -> null
        }
    }

    private suspend fun initialize(): State {
        val catalog = try {
            withContext(initializationDispatcher) { catalogLoader() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            failureReporter("Skill corpus could not be loaded; continuing without context", failure)
            return State.Unavailable
        }
        val replays = try {
            withContext(initializationDispatcher) { replayCatalogLoader() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            failureReporter("Replay assets could not be loaded; continuing without replay", failure)
            ReplayGuidanceCatalog.empty()
        }
        var embedder: EmbeddingProvider? = null
        return try {
            embedder = embedderFactory()
            val embeddings = catalog.all.associate { skill ->
                val document = buildString {
                    append(skill.title)
                    append(' ')
                    append(skill.description)
                    append(' ')
                    append(skill.triggers.joinToString(" "))
                }
                skill.id to embedder.embedDocument(document)
            }
            State.Ready(
                retriever = ContextRetriever(catalog, embedder, embeddings),
                replays = replays,
                embedder = embedder,
            )
        } catch (cancelled: CancellationException) {
            runCatching { embedder?.close() }
            throw cancelled
        } catch (failure: Exception) {
            runCatching { embedder?.close() }
            failureReporter(
                "Local embeddings unavailable; using lexical skill retrieval",
                failure,
            )
            State.Ready(
                retriever = ContextRetriever(catalog, null, emptyMap()),
                replays = replays,
                embedder = null,
            )
        }
    }

    private fun State.readyOrNull(): State.Ready? = this as? State.Ready

    private sealed interface State {
        data object Uninitialized : State
        data class Ready(
            val retriever: ContextRetriever,
            val replays: ReplayGuidanceCatalog,
            val embedder: EmbeddingProvider?,
        ) : State
        data object Unavailable : State
        data object Closed : State
    }

    companion object {
        fun production(context: Context): AndroidContextRequestFactoryProvider {
            val appContext = context.applicationContext
            val personal = PersonalContextStore.production(appContext)
            return AndroidContextRequestFactoryProvider(
                catalogLoader = { AssetSkillLoader.from(appContext.assets).load() },
                replayCatalogLoader = {
                    AssetReplayGuidanceLoader.from(appContext.assets).load()
                },
                embedderFactory = { OnnxE5Embedder.fromAssets(appContext) },
                personalLoader = personal::read,
                personalRevision = { personal.revision },
            )
        }
    }
}
