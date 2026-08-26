package com.caddie.context.embedding

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnnxE5EmbedderTest {
    @Test
    fun tokenizer_matches_pinned_upstream_fixtures() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val embedder = OnnxE5Embedder.fromAssets(context)

        try {
            assertArrayEquals(
                longArrayOf(0, 41, 1294, 12, 161675, 599, 204111, 2),
                embedder.tokenizeForDiagnostics("query: ", "WLAN einschalten").tokenIds,
            )
            assertArrayEquals(
                longArrayOf(0, 46692, 12, 13527, 5140, 9, 6159, 53550, 7, 2),
                embedder.tokenizeForDiagnostics(
                    "passage: ",
                    "Open Wi-Fi settings",
                ).tokenIds,
            )
            assertArrayEquals(
                longArrayOf(0, 41, 1294, 12, 2),
                embedder.tokenizeForDiagnostics("query: ", "").tokenIds,
            )
            assertArrayEquals(
                longArrayOf(0, 41, 1294, 12, 13779, 101811, 21754, 6, 246511, 2),
                embedder.tokenizeForDiagnostics("query: ", "Grüß dich 👋").tokenIds,
            )
        } finally {
            embedder.close()
        }
    }

    @Test
    fun embeddings_run_fully_on_device_and_can_reopen() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        repeat(2) {
            val embedder = OnnxE5Embedder.fromAssets(context)
            try {
                val germanQuery = embedder.embedQuery("WLAN einschalten")
                val germanRelated = embedder.embedDocument("Öffne die WLAN-Einstellungen.")
                val englishQuery = embedder.embedQuery("open Wi-Fi settings")
                val englishRelated = embedder.embedDocument("Open the Wi-Fi settings.")
                val unrelated = embedder.embedDocument("Play a family movie.")

                assertEquals(384, germanQuery.size)
                assertTrue(dotProduct(germanQuery, germanRelated) > dotProduct(germanQuery, unrelated))
                assertTrue(dotProduct(englishQuery, englishRelated) > dotProduct(englishQuery, unrelated))
                assertEquals(1f, dotProduct(germanQuery, germanQuery), 0.0001f)
            } finally {
                embedder.close()
                embedder.close()
            }
        }
    }

    @Test
    fun concurrent_initialization_uses_independent_temporary_files() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(File(context.noBackupFilesDir, "context-model").deleteRecursively())

        val embedders = coroutineScope {
            List(2) { async { OnnxE5Embedder.fromAssets(context) } }.awaitAll()
        }
        embedders.forEach(OnnxE5Embedder::close)

        val files = File(context.noBackupFilesDir, "context-model").listFiles().orEmpty()
        assertEquals(1, files.count { it.name.endsWith("multilingual-e5-small-int8.onnx") })
        assertTrue(files.none { it.name.endsWith(".tmp") })
    }

    @Test
    @LargeTest
    fun cold_start_and_twenty_warm_embeddings_meet_device_budget() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(File(context.noBackupFilesDir, "context-model").deleteRecursively())
        val coldStartedAt = SystemClock.elapsedRealtime()
        val embedder = OnnxE5Embedder.fromAssets(context)
        val coldMillis = SystemClock.elapsedRealtime() - coldStartedAt

        try {
            embedder.embedQuery("warm up")
            val warmMillis = (1..20).map { index ->
                val startedAt = SystemClock.elapsedRealtime()
                embedder.embedQuery("WLAN Einstellung $index")
                SystemClock.elapsedRealtime() - startedAt
            }.sorted()
            val p95Millis = warmMillis[18]
            Log.i(
                "CaddieEmbeddingGate",
                "coldMs=$coldMillis warmMs=$warmMillis p95Ms=$p95Millis",
            )

            assertTrue("Cold initialization took ${coldMillis}ms", coldMillis < 5_000)
            assertTrue("Warm embedding p95 took ${p95Millis}ms", p95Millis < 1_000)
        } finally {
            embedder.close()
        }
    }

    private fun dotProduct(left: FloatArray, right: FloatArray): Float =
        left.indices.sumOf { index ->
            (left[index] * right[index]).toDouble()
        }.toFloat()
}
