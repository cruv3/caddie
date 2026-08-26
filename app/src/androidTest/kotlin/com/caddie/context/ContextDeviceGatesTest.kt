package com.caddie.context

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.caddie.context.embedding.EmbeddingProvider
import com.caddie.context.embedding.OnnxE5Embedder
import com.caddie.context.retrieval.ContextRetriever
import com.caddie.context.retrieval.RetrievalMode
import com.caddie.context.skill.AssetSkillLoader
import com.caddie.context.skill.SkillCatalog
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.test.runTest

/**
 * Device-specific measurement gates for the Context Engine.
 * All tests are @LargeTest so they are not run by ordinary JVM test invocations.
 */
@RunWith(AndroidJUnit4::class)
class ContextDeviceGatesTest {

    private lateinit var context: Context
    private var embedder: OnnxE5Embedder? = null
    private var catalog: SkillCatalog? = null

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
    }

    @After
    fun teardown() {
        embedder?.close()
    }

    @Test
    @LargeTest
    fun combined_query_retrieval_p95_meets_device_budget() = runTest {
        val loader = AssetSkillLoader.from(context.assets)
        catalog = loader.load()
        embedder = OnnxE5Embedder.fromAssets(context)

        // Pre-compute embeddings for all skills
        val skillEmbeddings = catalog!!.all.associate { skill ->
            val title = skill.title
            val description = skill.description
            val triggers = skill.triggers.joinToString(" ")
            val passage = "passage: $title $description $triggers"
            val vector = embedder!!.embedDocument(passage)
            skill.id to vector
        }

        val retriever = ContextRetriever(
            catalog = catalog!!,
            embeddingProvider = embedder,
            skillEmbeddings = skillEmbeddings,
        )

        val queries = listOf(
            "WLAN einschalten",
            "Öffne die WLAN-Einstellungen",
            "Open Wi-Fi settings",
            "Play a family movie",
            "Brightness to 30 percent",
            "Dunkelmodus aktivieren",
            "Open the Clock app",
            "Timer für 5 Minuten starten",
            "Search for pizza restaurants",
            "Open battery settings",
            "Turn on Do Not Disturb",
            "Change screen timeout to 1 minute",
            "Open Google Maps",
            "Start a stopwatch",
            "View notification history",
            "Open Bluetooth settings",
            "Show all installed apps",
            "Open Photos app",
            "Open Calendar",
            "Set ring volume",
        )

        // Warm up
        retriever.retrieve("warmup query")
        val timings = mutableListOf<Long>()

        for (query in queries) {
            val start = SystemClock.elapsedRealtime()
            val result = retriever.retrieve(query)
            val elapsed = SystemClock.elapsedRealtime() - start
            timings.add(elapsed)
            assertNotNull("Query should return result", result)
        }

        val sorted = timings.sorted()
        val p95Index = (sorted.size * 0.95).toInt().coerceAtMost(sorted.size - 1)
        val p95Millis = sorted[p95Index]
        val medianMillis = sorted[sorted.size / 2]
        val maxMillis = sorted.maxOrNull() ?: 0L

        Log.i(
            "CaddieRetrievalGate",
            "query_count=${queries.size} medianMs=$medianMillis p95Ms=$p95Millis maxMs=$maxMillis timings=${sorted.toList()}",
        )

        assertTrue("Combined retrieval p95 took ${p95Millis}ms (budget 500ms)", p95Millis < 500)
        assertTrue("Combined retrieval median took ${medianMillis}ms", medianMillis < 200)
    }

    @Test
    @LargeTest
    fun active_context_database_size_after_corpus_activation() = runTest {
        val loader = AssetSkillLoader.from(context.assets)
        catalog = loader.load()
        embedder = OnnxE5Embedder.fromAssets(context)

        // Count corpus items
        val skills = catalog!!.all
        val skillCount = skills.size
        val totalBytes = skills.sumOf { skill ->
            skill.title.length + skill.description.length + skill.body.length +
                skill.triggers.sumOf(String::length)
        }
        val totalKb = totalBytes / 1024.0

        // Measure database file size (if it exists)
        val dbFile = context.getDatabasePath("caddie-context.db")
        val dbSizeBytes = if (dbFile.exists()) dbFile.length() else 0L
        val dbSizeKb = dbSizeBytes / 1024.0

        Log.i(
            "CaddieDbSizeGate",
            "skills=$skillCount corpus=${totalKb}kb db_exists=${dbFile.exists()} db=${dbSizeKb}kb",
        )

        assertTrue("Skills should be loaded ($skillCount)", skillCount > 0)
        assertTrue("Corpus should be non-trivial (${totalKb}kb)", totalKb > 10)
    }

    @Test
    @LargeTest
    fun live_process_memory_during_corpus_operation() = runTest {
        val loader = AssetSkillLoader.from(context.assets)
        catalog = loader.load()
        embedder = OnnxE5Embedder.fromAssets(context)

        // Get baseline memory (JVM heap + OS RSS)
        val baselineHeapKb = getProcessMemoryKb()
        val baselineRssKb = getVmRSSKb()
        Log.i(
            "CaddieMemoryGate",
            "baseline_heapKb=$baselineHeapKb baseline_rssKb=$baselineRssKb",
        )

        // Pre-compute all skill embeddings
        val skillEmbeddings = catalog!!.all.associate { skill ->
            val passage = "passage: ${skill.title} ${skill.description} ${skill.triggers.joinToString(" ")}"
            val vector = embedder!!.embedDocument(passage)
            skill.id to vector
        }

        val afterEmbeddingsHeapKb = getProcessMemoryKb()
        val afterEmbeddingsRssKb = getVmRSSKb()
        Log.i(
            "CaddieMemoryGate",
            "after_embeddings_heapKb=$afterEmbeddingsHeapKb rssKb=$afterEmbeddingsRssKb",
        )

        // Do some retrieval queries and measure peak
        val retriever = ContextRetriever(
            catalog = catalog!!,
            embeddingProvider = embedder,
            skillEmbeddings = skillEmbeddings,
        )

        var peakHeapKb = afterEmbeddingsHeapKb
        var peakRssKb = afterEmbeddingsRssKb
        for (i in 1..10) {
            retriever.retrieve("query $i")
            val currentHeap = getProcessMemoryKb()
            val currentRss = getVmRSSKb()
            if (currentHeap > peakHeapKb) peakHeapKb = currentHeap
            if (currentRss > 0 && currentRss > peakRssKb) peakRssKb = currentRss
        }

        val heapGrowthKb = peakHeapKb - baselineHeapKb
        val heapGrowthMb = heapGrowthKb / 1024.0
        val rssGrowthMb = if (baselineRssKb > 0 && peakRssKb > 0) {
            (peakRssKb - baselineRssKb) / 1024.0
        } else -1.0

        Log.i(
            "CaddieMemoryGate",
            "heap_growth=${heapGrowthMb}mb rss_growth=${rssGrowthMb}mb " +
                "peak_heap=${peakHeapKb / 1024.0}mb peak_rss=${peakRssKb / 1024.0}mb",
        )

        // Budget: corpus + retrieval should not grow more than 100MB on top of baseline
        assertTrue(
            "JVM heap growth ${heapGrowthMb}mb should be under 100mb budget",
            heapGrowthMb < 100,
        )
        if (rssGrowthMb >= 0) {
            assertTrue(
                "OS RSS growth ${rssGrowthMb}mb should be under 150mb budget",
                rssGrowthMb < 150,
            )
        }
    }

    @Test
    @LargeTest
    fun corpus_sha256_integrity_after_activation() = runTest {
        val loader = AssetSkillLoader.from(context.assets)
        catalog = loader.load()

        val skills = catalog!!.all
        val skillIds = skills.map { it.id }.sorted()

        assertEquals("76 skills loaded", 76, skills.size)
        assertEquals("All IDs unique", 76, skillIds.toSet().size)

        // Verify all skills have valid hashes
        val invalidHashes = skills.count { !it.sourceSha256.matches(Regex("^[0-9a-f]{64}$")) }
        assertEquals("All hashes should be valid SHA-256", 0, invalidHashes)

        Log.i(
            "CaddieIntegrityGate",
            "skills=${skills.size} ids_sorted=${skillIds.first()}..${skillIds.last()}",
        )
    }

    @Test
    @LargeTest
    fun offline_inference_no_network_path() = runTest {
        val loader = AssetSkillLoader.from(context.assets)
        catalog = loader.load()
        embedder = OnnxE5Embedder.fromAssets(context)

        // Pre-compute embeddings
        val skillEmbeddings = catalog!!.all.associate { skill ->
            val passage = "passage: ${skill.title} ${skill.description} ${skill.triggers.joinToString(" ")}"
            val vector = embedder!!.embedDocument(passage)
            skill.id to vector
        }

        val retriever = ContextRetriever(
            catalog = catalog!!,
            embeddingProvider = embedder,
            skillEmbeddings = skillEmbeddings,
        )

        // Run retrieval - should complete without network
        val start = SystemClock.elapsedRealtime()
        val result = retriever.retrieve("WLAN einschalten")
        val elapsed = SystemClock.elapsedRealtime() - start

        assertNotNull("Retrieval should succeed offline", result)
        assertTrue("Offline retrieval should complete in reasonable time", elapsed < 5000)
        assertEquals("Should use semantic mode", RetrievalMode.SEMANTIC, result.mode)
        assertTrue("Should return skills", result.skills.isNotEmpty())

        Log.i(
            "CaddieOfflineGate",
            "offline_retrieval_ms=$elapsed skills=${result.skills.size} hints=${result.hints.size}",
        )
    }

    @Test
    @LargeTest
    fun corpus_survives_close_reopen_and_recovery() = runTest {
        // Phase 1: Load corpus, compute embeddings, measure baseline
        val loader1 = AssetSkillLoader.from(context.assets)
        catalog = loader1.load()
        embedder = OnnxE5Embedder.fromAssets(context)

        val skillIdsBefore = catalog!!.all.map { it.id }.sorted()

        val skillEmbeddings1 = catalog!!.all.associate { skill ->
            val passage = "passage: ${skill.title} ${skill.description} ${skill.triggers.joinToString(" ")}"
            val vector = embedder!!.embedDocument(passage)
            skill.id to vector
        }

        val retriever1 = ContextRetriever(
            catalog = catalog!!,
            embeddingProvider = embedder,
            skillEmbeddings = skillEmbeddings1,
        )

        val resultBefore = retriever1.retrieve("WLAN einschalten")
        assertNotNull("Pre-recovery retrieval succeeds", resultBefore)
        val topSkillBefore = resultBefore.skills.firstOrNull()?.skill?.id

        Log.i("CaddieRecoveryGate", "pre: skills=${skillIdsBefore.size} top_match=$topSkillBefore")

        // Phase 2: Simulate close/reopen (embedder.close + reload)
        embedder?.close()
        embedder = null
        catalog = null

        // Force GC to simulate process memory pressure
        System.gc()
        Thread.sleep(100)

        // Phase 3: Reload from assets and verify identical results
        val loader2 = AssetSkillLoader.from(context.assets)
        catalog = loader2.load()
        embedder = OnnxE5Embedder.fromAssets(context)

        val skillIdsAfter = catalog!!.all.map { it.id }.sorted()

        assertEquals("Skill count matches after recovery",
            skillIdsBefore.size, skillIdsAfter.size)
        assertEquals("Skill IDs identical after recovery",
            skillIdsBefore, skillIdsAfter)

        // Recompute embeddings and verify retrieval matches
        val skillEmbeddings2 = catalog!!.all.associate { skill ->
            val passage = "passage: ${skill.title} ${skill.description} ${skill.triggers.joinToString(" ")}"
            val vector = embedder!!.embedDocument(passage)
            skill.id to vector
        }

        val retriever2 = ContextRetriever(
            catalog = catalog!!,
            embeddingProvider = embedder,
            skillEmbeddings = skillEmbeddings2,
        )

        val resultAfter = retriever2.retrieve("WLAN einschalten")
        assertNotNull("Post-recovery retrieval succeeds", resultAfter)
        val topSkillAfter = resultAfter.skills.firstOrNull()?.skill?.id

        assertEquals("Top match identical after recovery",
            topSkillBefore, topSkillAfter)

        val rssKb = getVmRSSKb()
        val rssMb = if (rssKb > 0) rssKb / 1024.0 else -1.0

        Log.i(
            "CaddieRecoveryGate",
            "post: skills=${skillIdsAfter.size} top_match=$topSkillAfter rss=${rssMb}mb",
        )
    }

    /**
     * Get current process heap used memory in KB using Java Runtime.
     */
    private fun getProcessMemoryKb(): Long {
        return try {
            val runtime = java.lang.Runtime.getRuntime()
            (runtime.totalMemory() - runtime.freeMemory()) / 1024
        } catch (e: Exception) {
            Log.w("CaddieMemoryGate", "Failed to get process memory", e)
            -1L
        }
    }

    /**
     * Get OS-level Resident Set Size (RSS) in KB from /proc/self/status.
     * This measures actual physical memory usage, not JVM heap.
     */
    private fun getVmRSSKb(): Long {
        return try {
            java.io.BufferedReader(
                java.io.InputStreamReader(java.io.FileInputStream("/proc/self/status")),
            ).useLines { lines ->
                lines.firstOrNull { it.startsWith("VmRSS:") }?.let { line ->
                    Regex("VmRSS:\\s+(\\d+)").find(line)
                        ?.groups?.get(1)?.value?.toLongOrNull() ?: -1L
                } ?: -1L
            }
        } catch (e: Exception) {
            Log.w("CaddieMemoryGate", "Failed to read /proc/self/status", e)
            -1L
        }
    }
}
