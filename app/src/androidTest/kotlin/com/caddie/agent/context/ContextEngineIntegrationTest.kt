package com.caddie.agent.context

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.caddie.agent.core.AgentMessage
import com.caddie.agent.core.ModelRequest
import com.caddie.agent.core.RequestFactory
import com.caddie.agent.core.RunId
import com.caddie.agent.core.RunSnapshot
import com.caddie.agent.core.RunState
import com.caddie.agent.core.ToolDefinition
import com.caddie.context.embedding.OnnxE5Embedder
import com.caddie.context.rag.ContextBundle
import com.caddie.context.rag.RagRequestFactory
import com.caddie.context.retrieval.ContextRetriever
import com.caddie.context.skill.AssetSkillLoader
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration test wiring Context Engine into the Agent Loop request path.
 *
 * Verifies:
 * - Corpus loads from assets, embedder initializes, vectors computed
 * - Retriever finds relevant skills for a query
 * - RagRequestFactory injects context into model request
 * - Context engine survives multiple queries without degradation
 */
@RunWith(AndroidJUnit4::class)
class ContextEngineIntegrationTest {

    private lateinit var context: Context
    private var embedder: OnnxE5Embedder? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        embedder?.close()
    }

    @Test
    @LargeTest
    fun `RagRequestFactory injects RAG context from live corpus`() = runTest {
        // 1. Load corpus and compute embeddings
        val loader = AssetSkillLoader.from(context.assets)
        val catalog = loader.load()
        embedder = OnnxE5Embedder.fromAssets(context)

        val skillEmbeddings = catalog.all.associate { skill ->
            val passage = "passage: ${skill.title} ${skill.description} ${skill.triggers.joinToString(" ")}"
            val vector = embedder!!.embedDocument(passage)
            skill.id to vector
        }

        // 2. Retrieve context for query
        val retriever = ContextRetriever(catalog, embedder, skillEmbeddings)
        val retrieval = retriever.retrieve("Open Wi-Fi settings")
        assertNotNull(retrieval)
        assertTrue("Should find relevant skills", retrieval.skills.isNotEmpty())
        val bundle = ContextBundle.create(retrieval)

        // 3. Inject context via RagRequestFactory
        val baseFactory = RecordingRequestFactory()
        val ragFactory = RagRequestFactory(baseFactory) { _ -> bundle }

        val snapshot = RunSnapshot(
            runId = RunId("run-1"),
            state = RunState.RUNNING,
            messages = listOf(AgentMessage(AgentMessage.Role.USER, "Open Wi-Fi settings")),
        )
        val request = ragFactory.create(snapshot, listOf(ToolDefinition("wifi.open", "Open", "{}")))

        // 4. Verify context injected
        val contextMsgs = request.messages.filter {
            it.content.contains("reference-only")
        }
        assertTrue("RAG context injected", contextMsgs.isNotEmpty())
        assertTrue("Contains skill ID", request.messages.any {
            it.content.contains(retrieval.skills.first().skill.id)
        })

        println("ContextEngineIntegrationTest: ${retrieval.skills.size} skills injected")
    }

    @Test
    @LargeTest
    fun `context engine degrades gracefully on empty query`() = runTest {
        val loader = AssetSkillLoader.from(context.assets)
        val catalog = loader.load()
        embedder = OnnxE5Embedder.fromAssets(context)
        val skillEmbeddings = catalog.all.associate { skill ->
            val passage = "passage: ${skill.title} ${skill.description} ${skill.triggers.joinToString(" ")}"
            val vector = embedder!!.embedDocument(passage)
            skill.id to vector
        }
        val retriever = ContextRetriever(catalog, embedder, skillEmbeddings)

        val result = retriever.retrieve("")
        assertEquals(0, result.skills.size)
        assertEquals(0, result.hints.size)
    }

    @Test
    @LargeTest
    fun `context engine survives multiple queries p95 under 500ms`() = runTest {
        val loader = AssetSkillLoader.from(context.assets)
        val catalog = loader.load()
        embedder = OnnxE5Embedder.fromAssets(context)
        val skillEmbeddings = catalog.all.associate { skill ->
            val passage = "passage: ${skill.title} ${skill.description} ${skill.triggers.joinToString(" ")}"
            val vector = embedder!!.embedDocument(passage)
            skill.id to vector
        }
        val retriever = ContextRetriever(catalog, embedder, skillEmbeddings)

        val queries = listOf(
            "Open Wi-Fi settings",
            "Play a movie",
            "Brightness to 30",
            "Timer 5 minutes",
            "Open Clock app",
        )
        val timings = mutableListOf<Long>()
        for (query in queries) {
            val start = SystemClock.elapsedRealtime()
            val result = retriever.retrieve(query)
            timings.add(SystemClock.elapsedRealtime() - start)
            assertNotNull("Query '$query' returns result", result)
        }

        val p95 = timings.sorted()[((timings.size * 0.95).toInt()).coerceAtMost(timings.size - 1)]
        assertTrue("p95 under 500ms (got ${p95}ms)", p95 < 500)
        println("ContextEngineIntegrationTest: ${queries.size} queries, p95=${p95}ms")
    }

    private class RecordingRequestFactory : RequestFactory {
        override fun create(snapshot: RunSnapshot, tools: List<ToolDefinition>): ModelRequest {
            val request = ModelRequest(snapshot.runId, snapshot.messages, tools)
            return request
        }
    }
}
