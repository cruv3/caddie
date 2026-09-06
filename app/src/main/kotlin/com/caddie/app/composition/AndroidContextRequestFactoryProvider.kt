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
    private val personalStateMutex = Mutex()
    private var state: State = State.Uninitialized
    private var personalState: PersonalState? = null

    /** Loads skills, replay guidance, and embeddings before the first task. */
    suspend fun warmUp(): Boolean {
        val context = readyContext() ?: return false
        loadPersonal()?.let { readyPersonalContext(context, it) }
        return true
    }

    suspend fun forTask(task: String, delegate: RequestFactory): RequestFactory {
        val context = readyContext() ?: return delegate
        val personal = loadPersonal()
        val preparedPersonal = personal?.let { readyPersonalContext(context, it) }
        val retrieval = try {
            when {
                preparedPersonal == null -> context.retriever.retrieve(task)
                preparedPersonal.includesBase -> preparedPersonal.retriever.retrieve(task)
                else -> context.retriever.retrieve(task).copy(
                    hints = preparedPersonal.retriever.retrieve(task).hints,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            failureReporter("Context retrieval failed; continuing without context", failure)
            return delegate
        }
        if (retrieval.skills.isEmpty() && retrieval.hints.isEmpty()) return delegate
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
        val baseBundle = ContextBundle.create(retrieval.copy(hints = emptyList()), replay)
        val bundle = ContextBundle.create(retrieval, replay)
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
        personalStateMutex.withLock { personalState = null }
    }

    private suspend fun loadPersonal(): PersonalContextSnapshot? = try {
        personalLoader()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // Personal payloads and decoder errors must never enter logs.
        null
    }

    private suspend fun readyPersonalContext(
        context: State.Ready,
        snapshot: PersonalContextSnapshot,
    ): PersonalState? = personalStateMutex.withLock {
        personalState?.takeIf { it.revision == snapshot.revision }?.let { return@withLock it }
        val previousHints = personalState?.hintsByFact.orEmpty()
        val hintsByFact = snapshot.retrievableFacts.associate { fact ->
            val key = PersonalFactKey(fact.id, fact.version)
            val hint = previousHints[key] ?: run {
                val text = "Personal reference (${fact.provenance}, not independently verified), id=${fact.id}, version=${fact.version}: ${fact.title}\n${fact.text}"
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
            key to hint
        }
        val hints = hintsByFact.values.toList()
        if (hints.isEmpty()) {
            personalState = null
            return@withLock null
        }
        val includesBase = context.embedder == null || hints.all { it.embedding != null }
        val retriever = if (includesBase) {
            ContextRetriever(
                context.catalog,
                context.embedder,
                context.skillEmbeddings,
                hints,
            )
        } else {
            ContextRetriever(
                SkillCatalog(emptyList()),
                null,
                emptyMap(),
                hints.map { it.copy(embedding = null) },
            )
        }
        PersonalState(
            snapshot.revision,
            retriever,
            includesBase,
            hintsByFact,
        ).also { personalState = it }
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
                catalog = catalog,
                skillEmbeddings = embeddings,
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
                catalog = catalog,
                skillEmbeddings = emptyMap(),
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
            val catalog: SkillCatalog,
            val skillEmbeddings: Map<String, FloatArray>,
        ) : State
        data object Unavailable : State
        data object Closed : State
    }

    private data class PersonalState(
        val revision: Long,
        val retriever: ContextRetriever,
        val includesBase: Boolean,
        val hintsByFact: Map<PersonalFactKey, ContextHint>,
    )

    private data class PersonalFactKey(val id: String, val version: Long)

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
