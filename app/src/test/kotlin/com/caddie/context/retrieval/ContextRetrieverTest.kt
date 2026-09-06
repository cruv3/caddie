package com.caddie.context.retrieval

import com.caddie.context.embedding.EmbeddingProvider
import com.caddie.context.skill.Skill
import com.caddie.context.skill.SkillCatalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextRetrieverTest {
    @Test
    fun `normalization preserves Unicode triggers and word boundaries`() = runTest {
        val skill = skill("chrome", triggers = listOf("öffne chrome"))
        val retriever = retriever(
            skills = listOf(skill),
            queryVector = floatArrayOf(0f, 1f),
            skillVectors = mapOf(skill.id to floatArrayOf(1f, 0f)),
        )

        val match = retriever.retrieve("  O\u0308FFNE \n CHROME  ")
        val nonBoundary = retriever.retrieve("wiederöffne chrome")

        assertEquals(listOf("chrome"), match.skills.map { it.skill.id })
        assertEquals(0.15f, match.skills.single().score.triggerBoost, 0.0001f)
        assertEquals(1, match.skills.single().score.exactTriggerCount)
        assertTrue(nonBoundary.skills.isEmpty())
    }

    @Test
    fun `semantic threshold exact exception and token cap are auditable`() = runTest {
        val threshold = skill("threshold", title = "special action display panel controls")
        val below = skill("below", title = "special action display panel controls")
        val triggered = skill("triggered", triggers = listOf("special action"))
        val retriever = retriever(
            skills = listOf(threshold, below, triggered),
            queryVector = floatArrayOf(1f, 0f),
            skillVectors = mapOf(
                threshold.id to floatArrayOf(0.55f, 0.83516467f),
                below.id to floatArrayOf(0.54f, 0.841665f),
                triggered.id to floatArrayOf(0f, 1f),
            ),
        )

        val result = retriever.retrieve("special action display panel controls")

        assertEquals(listOf("threshold", "triggered"), result.skills.map { it.skill.id })
        assertEquals(RetrievalMode.SEMANTIC, result.mode)
        assertEquals(0.05f, result.skills.single { it.skill.id == "threshold" }.score.tokenBoost, 0.0001f)
        assertEquals(0.15f, result.skills.single { it.skill.id == "triggered" }.score.triggerBoost, 0.0001f)
    }

    @Test
    fun `ordering and result limits are deterministic`() = runTest {
        val skills = (1..5).map { skill("skill-$it") }
        val hints = (1..5).map { ContextHint("hint-$it", "shared hint") }
        val retriever = retriever(
            skills = skills,
            queryVector = floatArrayOf(1f, 0f),
            skillVectors = skills.associate { it.id to floatArrayOf(1f, 0f) },
            hints = hints.map { it.copy(embedding = floatArrayOf(1f, 0f)) },
        )

        val first = retriever.retrieve("unrelated")
        val second = retriever.retrieve("unrelated")

        assertEquals(listOf("skill-1", "skill-2", "skill-3"), first.skills.map { it.skill.id })
        assertEquals(listOf("hint-1", "hint-2", "hint-3", "hint-4"), first.hints.map { it.id })
        assertEquals(first, second)
    }

    @Test
    fun `generic hint retrieval returns four of five relevant hints`() = runTest {
        val hints = (1..5).map { ContextHint("hint-$it", "shared hint", embedding = floatArrayOf(1f, 0f)) }

        val result = retriever(
            skills = emptyList(),
            queryVector = floatArrayOf(1f, 0f),
            skillVectors = emptyMap(),
            hints = hints,
        ).retrieve("shared")

        assertEquals(listOf("hint-1", "hint-2", "hint-3", "hint-4"), result.hints.map { it.id })
    }

    @Test
    fun `unsafe or empty queries never guess`() = runTest {
        val skill = skill("chrome", triggers = listOf("open chrome"))
        val retriever = retriever(
            skills = listOf(skill),
            queryVector = floatArrayOf(1f, 0f),
            skillVectors = mapOf(skill.id to floatArrayOf(1f, 0f)),
        )

        listOf(
            "",
            "the and please",
            "do not open chrome",
            "öffne chrome nicht",
            "open and close chrome",
        ).forEach { query ->
            assertTrue(retriever.retrieve(query).skills.isEmpty())
        }
    }

    @Test
    fun `embedding failure enters conservative lexical mode`() = runTest {
        val exact = skill("exact", triggers = listOf("open chrome"))
        val overlap = skill("overlap", title = "network internet settings")
        val weak = skill("weak", title = "network clock")
        val provider = FakeEmbeddingProvider { throw IllegalStateException("model unavailable") }
        val retriever = ContextRetriever(
            catalog = SkillCatalog(listOf(exact, overlap, weak)),
            embeddingProvider = provider,
            skillEmbeddings = emptyMap(),
        )

        val result = retriever.retrieve("open chrome network internet")

        assertEquals(RetrievalMode.DEGRADED_LEXICAL, result.mode)
        assertEquals(listOf("exact", "overlap"), result.skills.map { it.skill.id })
    }

    @Test
    fun `wrong vector dimensions fail before retrieval`() {
        val skill = skill("invalid")

        assertThrows(IllegalArgumentException::class.java) {
            ContextRetriever(
                catalog = SkillCatalog(listOf(skill)),
                embeddingProvider = FakeEmbeddingProvider { floatArrayOf(1f, 0f) },
                skillEmbeddings = mapOf(skill.id to floatArrayOf(1f)),
            )
        }
    }

    @Test
    fun `cancellation is never converted to lexical fallback`() = runTest {
        val skill = skill("chrome", triggers = listOf("open chrome"))
        val retriever = ContextRetriever(
            catalog = SkillCatalog(listOf(skill)),
            embeddingProvider = FakeEmbeddingProvider { throw CancellationException("cancelled") },
            skillEmbeddings = emptyMap(),
        )

        var cancellation: CancellationException? = null
        try {
            retriever.retrieve("open chrome")
        } catch (caught: CancellationException) {
            cancellation = caught
        }
        assertEquals("cancelled", cancellation?.message)
    }

    private fun retriever(
        skills: List<Skill>,
        queryVector: FloatArray,
        skillVectors: Map<String, FloatArray>,
        hints: List<ContextHint> = emptyList(),
    ): ContextRetriever = ContextRetriever(
        catalog = SkillCatalog(skills),
        embeddingProvider = FakeEmbeddingProvider { queryVector.copyOf() },
        skillEmbeddings = skillVectors,
        hints = hints,
    )

    private fun skill(
        id: String,
        title: String = id,
        triggers: List<String> = emptyList(),
    ): Skill = Skill(
        id = id,
        title = title,
        description = "Description for $id",
        triggers = triggers,
        body = "Rules for $id",
        sourcePath = "$id.md",
        sourceSha256 = "a".repeat(64),
        replayId = null,
    )

    private class FakeEmbeddingProvider(
        private val result: suspend (String) -> FloatArray,
    ) : EmbeddingProvider {
        override val modelId: String = "test"
        override val dimension: Int = 2

        override suspend fun embedQuery(text: String): FloatArray = result(text)

        override suspend fun embedDocument(text: String): FloatArray = result(text)

        override fun close() = Unit
    }
}
