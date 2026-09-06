package com.caddie.context.persistence

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caddie.context.personal.PersonalContextStore
import com.caddie.context.personal.PersonalFact
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersonalContextStoreDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "personal-memory-test.db"
    private val keyAlias = "caddie_personal_memory_instrumentation_test"
    private var db: ContextDatabase? = null
    private fun open(): PersonalContextStore {
        db = Room.databaseBuilder(context, ContextDatabase::class.java, name).build()
        return PersonalContextStore(ContextRepository(db!!, AndroidKeystoreContextCipher(keyAlias)),
            resetKey = { AndroidKeystoreContextCipher.deleteKey(keyAlias) })
    }
    @After fun cleanup() {
        db?.close()
        context.deleteDatabase(name)
        AndroidKeystoreContextCipher.deleteKey(keyAlias)
    }

    @Test fun notes_survive_reopen_encrypted_and_clear_is_persistent() = runTest {
        var store = open()
        val fact = PersonalFact("00000000-0000-0000-0000-000000000001", "Submit report",
            "Final document report.pdf; reviewer@example.invalid")
        store.replace(listOf(fact), ownerConfirmed = true)
        assertEquals(1L, store.revision)
        val row = db!!.dao().activeContentRows("personal-facts-v1").single()
        assertFalse(row.ciphertext.toString(Charsets.UTF_8).contains("reviewer@example.invalid"))
        db!!.close()
        store = open()
        assertEquals(listOf(fact), store.read().facts)
        store.replace(listOf(fact.copy(text = "New report.pdf")), ownerConfirmed = true)
        assertEquals(1, db!!.dao().allGenerationIds().size)
        assertEquals("New report.pdf", store.read().facts.single().text)
        store.clear()
        assertFalse(java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.containsAlias(keyAlias))
        db!!.close()
        store = open()
        assertTrue(store.read().facts.isEmpty())
    }

    @Test fun invalid_update_preserves_notes_and_empty_save_replaces_all() = runTest {
        val store = open()
        val fact = PersonalFact("00000000-0000-0000-0000-000000000001", "Topic", "Fact")
        store.replace(listOf(fact), ownerConfirmed = true)
        try { store.replace(listOf(fact.copy(text = "")), ownerConfirmed = true); fail("Should reject") }
        catch (_: IllegalArgumentException) { }
        assertEquals(listOf(fact), store.read().facts)
        store.replace(emptyList(), ownerConfirmed = true)
        assertTrue(store.read().facts.isEmpty())
        assertEquals(1, db!!.dao().allGenerationIds().size)
    }

    @Test fun explicit_reset_removes_unreadable_notes_without_touching_other_owners() = runTest {
        val store = open()
        val fact = PersonalFact("00000000-0000-0000-0000-000000000001", "Topic", "Fact")
        store.replace(listOf(fact), ownerConfirmed = true)
        val other = ContextRepository(db!!, AndroidKeystoreContextCipher())
        other.activate(ContextCorpusGeneration("other-generation",
            listOf(ContextContentInput("other-id", "other-owner", "a".repeat(64), "other content".toByteArray())),
            emptyList(), emptyList(), emptyList()))
        db!!.openHelper.writableDatabase.execSQL("UPDATE context_content SET ciphertext = X'00' WHERE owner_id = 'personal-facts-v1'")
        try { store.read(); fail("Should reject corrupt ciphertext") }
        catch (_: ContextPersistenceResetRequired) { }
        store.clear()
        assertTrue(store.read().facts.isEmpty())
        assertEquals("other content", other.activeContent("other-owner").single().toString(Charsets.UTF_8))
        store.replace(listOf(fact), ownerConfirmed = true)
        assertEquals(listOf(fact), store.read().facts)
    }

    @Test fun persisted_note_reaches_request_with_real_local_e5_after_reopen() = runTest {
        var store = open()
        store.replace(listOf(PersonalFact("00000000-0000-0000-0000-000000000001",
            "submit report", "Final report.pdf; reviewer@example.invalid; https://example.invalid")), ownerConfirmed = true)
        db!!.close()
        store = open()
        val embedder = com.caddie.context.embedding.OnnxE5Embedder.fromAssets(context)
        val provider = com.caddie.app.composition.AndroidContextRequestFactoryProvider(
            catalogLoader = { com.caddie.context.skill.SkillCatalog(emptyList()) },
            embedderFactory = { embedder }, personalLoader = store::read,
            personalRevision = { store.revision },
        )
        try {
            val factory = provider.forTask("submit report", com.caddie.agent.core.ConversationRequestFactory("Normal"))
            val snapshot = com.caddie.agent.core.RunSnapshot(
                runId = com.caddie.agent.core.RunId("personal-test"),
                state = com.caddie.agent.core.RunState.RUNNING,
                messages = emptyList(),
            )
            assertTrue(factory.create(snapshot, emptyList()).messages.any { "reviewer@example.invalid" in it.content })
            store.clear()
            assertFalse(factory.create(snapshot, emptyList()).messages.any { "reviewer@example.invalid" in it.content })
        } finally { provider.close() }
    }

    @Test fun confirmed_save_finishes_and_invalidates_when_editor_coroutine_is_cancelled() = runTest {
        open()
        val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
        val release = java.util.concurrent.CountDownLatch(1)
        val cipher = object : ContextCipher by AndroidKeystoreContextCipher(keyAlias) {
            override fun encrypt(plaintext: ByteArray, aad: ContextAad): ContextCipherResult<EncryptedContextEnvelope> {
                entered.complete(Unit)
                check(release.await(10, java.util.concurrent.TimeUnit.SECONDS))
                return AndroidKeystoreContextCipher(keyAlias).encrypt(plaintext, aad)
            }
        }
        val store = PersonalContextStore(ContextRepository(db!!, cipher))
        val fact = PersonalFact("00000000-0000-0000-0000-000000000001", "Topic", "Fact")
        val saving = launch(kotlinx.coroutines.Dispatchers.Default) { store.replace(listOf(fact), ownerConfirmed = true) }
        entered.await()
        saving.cancel()
        release.countDown()
        saving.join()
        assertEquals(1L, store.revision)
        assertEquals(listOf(fact), store.read().facts)
    }

    @Test fun unconfirmed_and_policy_rejected_notes_never_reach_storage() = runTest {
        val store = open()
        val fact = PersonalFact("00000000-0000-0000-0000-000000000001", "Topic", "Fact")
        try { store.replace(listOf(fact)); fail("Should require confirmation") }
        catch (_: IllegalArgumentException) { }
        try { store.replace(listOf(fact.copy(text = "Password: synthetic-only")), ownerConfirmed = true); fail("Should reject secret") }
        catch (_: IllegalArgumentException) { }
        assertTrue(store.read().facts.isEmpty())
        assertEquals(0L, store.revision)
    }
}
