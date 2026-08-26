package com.caddie.context.persistence

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContextRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "context-repository-test.db"
    private var database: ContextDatabase? = null

    @After
    fun close() {
        database?.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun four_tables_activate_transactionally_and_survive_restart_encrypted() = runTest {
        val runtimeFile = context.getDatabasePath("caddie-runtime.db")
        val runtimeStamp = runtimeFile.takeIf { it.exists() }?.let { it.length() to it.lastModified() }
        val repository = openRepository()

        repository.activate(generation("g1"))

        assertEquals(listOf("private content"), repository.activeContent("owner").map { String(it) })
        assertEquals(1, database!!.dao().activeVectorCount("owner", "model-e5", 384))
        assertEquals(listOf(ProjectionMetadata("frontier-7", HASH)), database!!.dao().activeProjectionMetadata("owner"))
        assertEquals(listOf(ReplaySafetyMetadata("ELIGIBLE", "verified")), database!!.dao().activeReplaySafety("owner"))
        val raw = database!!.dao().activeContentRows("owner").single()
        assertFalse(String(raw.ciphertext).contains("private content"))

        database!!.close()
        database = null
        val reopened = openRepository()
        assertEquals(listOf("private content"), reopened.activeContent("owner").map { String(it) })
        assertEquals(runtimeStamp, runtimeFile.takeIf { it.exists() }?.let { it.length() to it.lastModified() })
    }

    @Test
    fun exception_after_staging_rolls_back_without_dirty_generation() = runTest {
        val repository = openRepository()
        repository.activate(generation("g1"))

        try {
            repository.activate(generation("g2")) { error("injected") }
            fail("Expected injected failure")
        } catch (_: IllegalStateException) {
            Unit
        }

        assertEquals(setOf("g1"), database!!.dao().allGenerationIds().toSet())
        assertEquals(listOf("private content"), repository.activeContent("owner").map { String(it) })
    }

    @Test
    fun `corrupt references rollback and stale vectors are excluded`() = runTest {
        val repository = openRepository()
        repository.activate(generation("g1"))
        val corrupt = generation("g2").copy(
            vectors = generation("g2").vectors.map { it.copy(contentId = "missing") },
        )

        try {
            repository.activate(corrupt)
            fail("Expected validation failure")
        } catch (_: IllegalArgumentException) {
            Unit
        }

        assertEquals(1, database!!.dao().activeVectorCount("owner", "model-e5", 384))
        assertEquals(0, database!!.dao().activeVectorCount("owner", "old-model", 384))
        assertEquals(setOf("g1"), database!!.dao().allGenerationIds().toSet())
    }

    @Test
    fun `activating one owner never deactivates another owner`() = runTest {
        val repository = openRepository()
        repository.activate(generation("g1"))
        val secondOwner = generation("g2").let { generation ->
            generation.copy(
                contents = generation.contents.map { it.copy(id = "content-2", ownerId = "owner-2") },
                vectors = generation.vectors.map { it.copy(id = "vector-2", contentId = "content-2", ownerId = "owner-2") },
                projections = generation.projections.map { it.copy(id = "projection-2", ownerId = "owner-2") },
                replays = generation.replays.map { it.copy(id = "replay-2", ownerId = "owner-2") },
            )
        }

        repository.activate(secondOwner)

        assertEquals(listOf("private content"), repository.activeContent("owner").map { String(it) })
        assertEquals(listOf("private content"), repository.activeContent("owner-2").map { String(it) })
        assertEquals(setOf("g1", "g2"), database!!.dao().allGenerationIds().toSet())
    }

    private fun openRepository(): ContextRepository {
        database = Room.databaseBuilder(context, ContextDatabase::class.java, databaseName).build()
        return ContextRepository(database!!, AndroidKeystoreContextCipher())
    }

    private fun generation(id: String) = ContextCorpusGeneration(
        generationId = id,
        contents = listOf(ContextContentInput("content-1", "owner", HASH, "private content".toByteArray())),
        vectors = listOf(ContextVectorInput("vector-1", "content-1", "owner", HASH, "model-e5", 384, byteArrayOf(1, 2))),
        projections = listOf(ContextProjectionInput("projection-1", "owner", HASH, "frontier-7", byteArrayOf(3))),
        replays = listOf(ContextReplayInput("replay-1", "owner", "trajectory", "rev-1", "ELIGIBLE", "verified", byteArrayOf(4))),
    )

    private companion object {
        val HASH = "a".repeat(64)
    }
}
