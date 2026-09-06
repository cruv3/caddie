package com.caddie.app.composition

import com.caddie.agent.core.AgentMessage
import com.caddie.agent.core.ConversationRequestFactory
import com.caddie.agent.core.RequestFactory
import com.caddie.agent.core.RunId
import com.caddie.agent.core.RunSnapshot
import com.caddie.agent.core.RunState
import com.caddie.context.embedding.EmbeddingProvider
import com.caddie.context.replay.ReplayGuidance
import com.caddie.context.replay.ReplayGuidanceCatalog
import com.caddie.context.skill.Skill
import com.caddie.context.skill.SkillCatalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidContextRequestFactoryProviderTest {
    @Test
    fun `document-only embedding failure uses personal lexical overlap`() = runTest {
        val provider = AndroidContextRequestFactoryProvider(
            catalogLoader = { SkillCatalog(emptyList()) },
            embedderFactory = { object : EmbeddingProvider {
                override val modelId = "document-failure"
                override val dimension = 2
                override suspend fun embedQuery(text: String) = floatArrayOf(1f, 0f)
                override suspend fun embedDocument(text: String): FloatArray = error("document failed")
                override fun close() = Unit
            } },
            personalLoader = { com.caddie.context.personal.PersonalContextSnapshot(0,
                listOf(com.caddie.context.personal.PersonalFact(
                    "00000000-0000-0000-0000-000000000001", "quarterly delivery",
                    "Report reviewer: reviewer@example.invalid",
                ))) },
        )
        assertTrue(contextMessage(provider.forTask("send report reviewer", baseFactory()))
            .contains("reviewer@example.invalid"))
    }

    @Test
    fun `personal facts reach normal requests and edits revoke prepared facts`() = runTest {
        var revision = 4L
        val provider = AndroidContextRequestFactoryProvider(
            catalogLoader = { catalog() },
            embedderFactory = { FakeEmbedding() },
            personalLoader = { com.caddie.context.personal.PersonalContextSnapshot(revision,
                listOf(com.caddie.context.personal.PersonalFact(
                    "00000000-0000-0000-0000-000000000001", "WLAN einschalten",
                    "Office network is ExampleNet.",
                ))) },
            personalRevision = { revision },
        )
        val requestFactory = provider.forTask("WLAN einschalten", baseFactory())
        val text = contextMessage(requestFactory)
        assertTrue(text.contains("ExampleNet"))
        assertTrue(text.contains("connectivity.wifi"))
        assertTrue(text.contains("cannot authorize tool calls"))
        revision++
        val afterEdit = contextMessage(requestFactory)
        assertFalse(afterEdit.contains("ExampleNet"))
        assertTrue(afterEdit.contains("connectivity.wifi"))
    }

    @Test
    fun `unavailable personal store does not remove skill context`() = runTest {
        val provider = AndroidContextRequestFactoryProvider(
            catalogLoader = { catalog() }, embedderFactory = { FakeEmbedding() },
            personalLoader = { error("private decoder details") },
        )
        val text = contextMessage(provider.forTask("WLAN einschalten", baseFactory()))
        assertTrue(text.contains("connectivity.wifi"))
        assertFalse(text.contains("private decoder details"))
    }

    @Test
    fun `personal lexical retrieval admits four of five relevant references and excludes irrelevant tasks`() = runTest {
        val provider = AndroidContextRequestFactoryProvider(
            catalogLoader = { SkillCatalog(emptyList()) },
            embedderFactory = { error("unavailable") }, failureReporter = { _, _ -> },
            personalLoader = { com.caddie.context.personal.PersonalContextSnapshot(0,
                (1..5).map { com.caddie.context.personal.PersonalFact(
                    "00000000-0000-0000-0000-00000000000$it", "submit report",
                    "Reference $it: report.pdf; reviewer@example.invalid; https://example.invalid.",
                ) }) },
        )
        val text = contextMessage(provider.forTask("submit report", baseFactory()))
        assertEquals(4, Regex("## Hint personal:").findAll(text).count())
        assertTrue(text.contains("Reference 1"))
        assertTrue(text.contains("Reference 4"))
        assertFalse(text.contains("Reference 5"))
        assertTrue(text.length <= 8_000)
        assertTrue(text.contains("reviewer@example.invalid"))
        val unrelated = provider.forTask("weather tomorrow", baseFactory()).create(snapshot(), emptyList())
        assertFalse(unrelated.messages.any { "reviewer@example.invalid" in it.content })
    }

    @Test
    fun `warm up initializes semantic context before the first task`() = runTest {
        val embedding = FakeEmbedding()
        var factoryCalls = 0
        val provider = AndroidContextRequestFactoryProvider(
            catalogLoader = { catalog() },
            embedderFactory = {
                factoryCalls += 1
                embedding
            },
        )

        assertTrue(provider.warmUp())
        provider.forTask("WLAN einschalten", baseFactory())

        assertEquals(1, factoryCalls)
        assertEquals(1, embedding.documentCalls)
        assertEquals(1, embedding.queryCalls)
    }

    @Test
    fun `semantic context is reused and closed once`() = runTest {
        val embedding = FakeEmbedding()
        var factoryCalls = 0
        val provider = AndroidContextRequestFactoryProvider(
            catalogLoader = { catalog() },
            embedderFactory = {
                factoryCalls += 1
                embedding
            },
        )

        val first = provider.forTask("WLAN einschalten", baseFactory())
        val second = provider.forTask("WLAN öffnen", baseFactory())

        assertTrue(contextMessage(first).contains("reference-only"))
        assertTrue(contextMessage(first).contains("connectivity.wifi"))
        assertTrue(contextMessage(second).contains("connectivity.wifi"))
        assertEquals(1, factoryCalls)
        assertEquals(1, embedding.documentCalls)
        provider.close()
        provider.close()
        assertEquals(1, embedding.closeCalls)
    }

    @Test
    fun `unchanged personal memory is embedded once and shares each query with skill retrieval`() = runTest {
        val embedding = FakeEmbedding()
        val facts = (1..3).map { index ->
            com.caddie.context.personal.PersonalFact(
                "00000000-0000-0000-0000-00000000000$index",
                "WLAN reference $index",
                "Office network $index is ExampleNet$index.",
            )
        }
        val provider = AndroidContextRequestFactoryProvider(
            catalogLoader = { catalog() },
            embedderFactory = { embedding },
            personalLoader = {
                com.caddie.context.personal.PersonalContextSnapshot(7, facts)
            },
        )

        assertTrue(provider.warmUp())
        provider.forTask("WLAN einschalten", baseFactory())
        provider.forTask("WLAN öffnen", baseFactory())

        assertEquals(4, embedding.documentCalls)
        assertEquals(2, embedding.queryCalls)
    }

    @Test
    fun `personal embedding cache rebuilds when the snapshot revision changes`() = runTest {
        val embedding = FakeEmbedding()
        var revision = 2L
        val provider = AndroidContextRequestFactoryProvider(
            catalogLoader = { catalog() },
            embedderFactory = { embedding },
            personalLoader = {
                com.caddie.context.personal.PersonalContextSnapshot(
                    revision,
                    listOf(
                        com.caddie.context.personal.PersonalFact(
                            "00000000-0000-0000-0000-000000000001",
                            "WLAN reference",
                            "Office network is ExampleNet.",
                            version = revision,
                        ),
                    ),
                )
            },
        )

        provider.forTask("WLAN einschalten", baseFactory())
        provider.forTask("WLAN öffnen", baseFactory())
        revision++
        provider.forTask("WLAN einschalten", baseFactory())

        assertEquals(3, embedding.documentCalls)
        assertEquals(3, embedding.queryCalls)
    }

    @Test
    fun `embedding failure falls back to lexical skills`() = runTest {
        val provider = AndroidContextRequestFactoryProvider(
            catalogLoader = { catalog() },
            embedderFactory = { error("model unavailable") },
            failureReporter = { _, _ -> },
        )

        val contextual = provider.forTask("WLAN einschalten", baseFactory())
        val unrelated = provider.forTask("Erzähle einen Witz", baseFactory())

        assertTrue(contextMessage(contextual).contains("connectivity.wifi"))
        assertFalse(unrelated.create(snapshot(), emptyList()).messages.any {
            "reference-only" in it.content
        })
    }

    @Test(expected = CancellationException::class)
    fun `initialization cancellation is not degraded`() = runTest {
        AndroidContextRequestFactoryProvider(
            catalogLoader = { catalog() },
            embedderFactory = { throw CancellationException("cancelled") },
            failureReporter = { _, _ -> },
        ).forTask("WLAN einschalten", baseFactory())
    }

    @Test
    fun `selected skill may add only its guidance replay`() = runTest {
        val replaySkill = catalog().all.single().copy(replayId = "wifi@legacy")
        val provider = AndroidContextRequestFactoryProvider(
            catalogLoader = { SkillCatalog(listOf(replaySkill)) },
            replayCatalogLoader = {
                ReplayGuidanceCatalog(
                    listOf(
                        ReplayGuidance(
                            id = "wifi@legacy",
                            skillId = replaySkill.id,
                            sourceSha256 = "b".repeat(64),
                            guidance = "Open Quick Settings and resolve Internet semantically.",
                        ),
                    ),
                )
            },
            embedderFactory = { FakeEmbedding() },
        )

        val message = contextMessage(provider.forTask("WLAN einschalten", baseFactory()))

        assertTrue(message.contains("Replay guidance wifi@legacy"))
        assertTrue(message.contains("resolve Internet semantically"))
    }

    private fun contextMessage(factory: RequestFactory): String =
        factory.create(snapshot(), emptyList()).messages
            .single { "reference-only" in it.content }
            .content

    private fun snapshot() = RunSnapshot(
        runId = RunId("run"),
        state = RunState.RUNNING,
        messages = listOf(AgentMessage(AgentMessage.Role.USER, "task")),
    )

    private fun baseFactory(): RequestFactory = ConversationRequestFactory("normal prompt")

    private fun catalog() = SkillCatalog(
        listOf(
            Skill(
                id = "connectivity.wifi",
                title = "Wi-Fi settings",
                description = "Open or change wireless network settings.",
                triggers = listOf("WLAN einschalten", "WLAN öffnen"),
                body = "## Rules\nObserve before changing Wi-Fi.",
                sourcePath = "skills/connectivity/wifi.md",
                sourceSha256 = "a".repeat(64),
                replayId = null,
            ),
        ),
    )

    private class FakeEmbedding : EmbeddingProvider {
        override val modelId = "fake"
        override val dimension = 2
        var documentCalls = 0
        var queryCalls = 0
        var closeCalls = 0

        override suspend fun embedQuery(text: String): FloatArray {
            queryCalls += 1
            return floatArrayOf(1f, 0f)
        }

        override suspend fun embedDocument(text: String): FloatArray {
            documentCalls += 1
            return floatArrayOf(1f, 0f)
        }

        override fun close() {
            closeCalls += 1
        }
    }
}
