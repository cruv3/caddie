package com.caddie.context.persistence

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caddie.context.personal.PersonalContextStore
import com.caddie.context.personal.PersonalFact
import com.caddie.context.personal.MemoryOutcome
import com.caddie.context.personal.MemoryProposal
import com.caddie.context.personal.TrustedMemoryEvidence
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
    private fun evidence(text: String) = TrustedMemoryEvidence(
        runId = "run", turnId = "turn", source = "user-task", text = text, observedAtMillis = 1,
    )
    private fun proposal(text: String, quote: String = text, title: String = "Preference") =
        MemoryProposal(title = title, text = text, evidenceQuote = quote)
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
        assertEquals(fact.copy(updatedAtMillis = store.read().facts.single().updatedAtMillis), store.read().facts.single())
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
        assertEquals(fact.copy(updatedAtMillis = store.read().facts.single().updatedAtMillis), store.read().facts.single())
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
        assertEquals(fact.copy(updatedAtMillis = store.read().facts.single().updatedAtMillis), store.read().facts.single())
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
        val persisted = store.read().facts.single()
        assertTrue(persisted.updatedAtMillis > 0)
        assertEquals(fact.copy(updatedAtMillis = persisted.updatedAtMillis), persisted)
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

    @Test fun legacy_array_reopens_then_next_write_uses_envelope() = runTest {
        val store = open()
        val legacy = PersonalFact("00000000-0000-0000-0000-000000000001", "Topic", "Fact")
        ContextRepository(db!!, AndroidKeystoreContextCipher(keyAlias)).activate(ContextCorpusGeneration(
            "legacy", listOf(ContextContentInput("personal-facts-v1", "personal-facts-v1", "a".repeat(64),
                Json.encodeToString(listOf(legacy)).toByteArray())), emptyList(), emptyList(), emptyList(),
        ))
        assertEquals(listOf(legacy), store.read().facts)
        store.replace(listOf(legacy), ownerConfirmed = true)
        assertTrue(db!!.dao().activeContentRows("personal-facts-v1").single().ciphertext.isNotEmpty())
        val decrypted = ContextRepository(db!!, AndroidKeystoreContextCipher(keyAlias)).activeContent("personal-facts-v1").single().toString(Charsets.UTF_8)
        assertTrue(decrypted.trimStart().startsWith("{"))
    }

    @Test fun supported_preference_autosaves_with_runtime_evidence() = runTest {
        val store = open()
        val text = "I prefer metric units"
        val result = store.propose(proposal(text), evidence(text))
        assertEquals(MemoryOutcome.AUTO_SAVE, result.outcome)
        assertTrue(result.changed)
        val saved = store.read().facts.single()
        assertEquals("Measurement units", saved.title)
        assertEquals("user-explicit", saved.provenance)
        assertEquals(text, saved.evidence!!.quote)
        assertEquals("preference.units", saved.memoryKey)
    }

    @Test fun conflicting_proposal_is_pending_and_not_retrievable() = runTest {
        val store = open()
        store.propose(proposal("I prefer metric units"), evidence("I prefer metric units"))
        val result = store.propose(proposal("I prefer imperial units"), evidence("I prefer imperial units"))
        assertEquals(MemoryOutcome.PENDING_CONFIRMATION, result.outcome)
        val snapshot = store.read()
        assertEquals(1, snapshot.facts.size)
        assertEquals(1, snapshot.pending.size)
        assertTrue(snapshot.retrievableFacts.isEmpty())
        assertEquals(snapshot.facts.single().version, snapshot.pending.single().targetVersion)
    }

    @Test fun proposal_review_requires_confirmation_and_uses_compare_and_swap() = runTest {
        val store = open()
        store.propose(proposal("I prefer English"), evidence("I prefer English"))
        val pending = store.propose(proposal("I prefer German"), evidence("I prefer German"))
        try { store.reviewCandidate(pending.id!!, true); fail("Should require owner confirmation") }
        catch (_: IllegalArgumentException) { }
        store.propose(proposal("from now on, I prefer French"), evidence("from now on, I prefer French"))
        val stale = store.reviewCandidate(pending.id!!, true, ownerConfirmed = true)
        assertEquals(MemoryOutcome.REJECT, stale.outcome)
        assertEquals(1, store.read().pending.size)
        val rejected = store.reviewCandidate(pending.id!!, false, ownerConfirmed = true)
        assertTrue(rejected.changed)
        assertTrue(store.read().pending.isEmpty())
    }

    @Test fun owner_review_preserves_inference_origin_and_manual_edit_clears_stale_evidence() = runTest {
        val store = open()
        val inferred = store.propose(MemoryProposal("Favorite color", "The user may prefer blue", "Blue looks pleasant", inferred = true), evidence("Blue looks pleasant"))
        store.reviewCandidate(inferred.id!!, true, ownerConfirmed = true)
        val approved = store.read().facts.single()
        assertEquals("model-inference-confirmed", approved.provenance)
        assertEquals("Blue looks pleasant", approved.evidence?.quote)
        store.replace(listOf(approved.copy(text = "Green is preferred")), ownerConfirmed = true)
        val edited = store.read().facts.single()
        assertEquals("owner-entered-and-confirmed", edited.provenance)
        assertNull(edited.evidence)
        assertNull(edited.memoryKey)
        assertEquals(approved.version + 1, edited.version)
    }

    @Test fun repurposed_note_cannot_be_overwritten_or_suppressed_by_unrelated_model_target() = runTest {
        val store = open()
        store.propose(proposal("I prefer concise answers."), evidence("I prefer concise answers."))
        val old = store.read().facts.single()
        store.replace(listOf(old.copy(title = "Supervisor", text = "Reviewer A")), ownerConfirmed = true)
        val correction = "From now on, I prefer detailed answers."
        assertEquals(MemoryOutcome.AUTO_SAVE, store.propose(proposal(correction), evidence(correction)).outcome)
        assertEquals("Reviewer A", store.read().facts.first { it.id == old.id }.text)
        val unrelated = "My final document is report.pdf."
        val attack = store.propose(MemoryProposal("Final document", unrelated, unrelated,
            targetId = old.id, targetVersion = old.version + 1), evidence(unrelated))
        assertEquals(MemoryOutcome.REJECT, attack.outcome)
        assertTrue(store.read().pending.isEmpty())
        assertEquals(2, store.read().retrievableFacts.size)
    }

    @Test fun maximum_title_is_supported_and_byte_overflow_preserves_readable_generation() = runTest {
        val store = open()
        val first = "A reusable harmless note"
        assertEquals(MemoryOutcome.PENDING_CONFIRMATION, store.propose(proposal(first, title = "T".repeat(80)), evidence(first)).outcome)
        assertEquals(MemoryOutcome.PENDING_CONFIRMATION, store.propose(proposal("Another reusable note", title = "İ".repeat(80)), evidence("Another reusable note")).outcome)
        store.clear()
        repeat(32) { index ->
            val text = "$index " + "界".repeat(690)
            val result = store.propose(proposal(text, title = "Topic $index"), evidence(text))
            store.reviewCandidate(result.id!!, true, ownerConfirmed = true)
        }
        var overflowRefused = false
        for (index in 0 until 16) {
            val before = store.read()
            val text = "Pending $index " + "界".repeat(680)
            try { store.propose(proposal(text, title = "Pending $index"), evidence(text)) }
            catch (_: IllegalArgumentException) {
                assertEquals(before, store.read())
                overflowRefused = true
                break
            }
        }
        assertTrue("UTF-8 capacity must be checked before activation", overflowRefused)
        assertEquals(32, store.read().facts.size)
    }

    @Test fun disabled_learning_and_capacity_never_evict_notes() = runTest {
        var store = open()
        store.setLearningEnabled(false)
        db!!.close()
        store = open()
        assertFalse(store.read().learningEnabled)
        assertEquals(MemoryOutcome.REJECT, store.propose(proposal("I prefer metric units"), evidence("I prefer metric units")).outcome)
        assertTrue(store.read().facts.isEmpty())
        val full = (1..PersonalContextStore.MAX_FACTS).map { index ->
            PersonalFact(java.util.UUID.randomUUID().toString(), "Topic $index", "Fact $index")
        }
        store.replace(full, ownerConfirmed = true)
        store.setLearningEnabled(true)
        assertEquals(MemoryOutcome.REJECT, store.propose(proposal("I prefer metric units"), evidence("I prefer metric units")).outcome)
        assertEquals(PersonalContextStore.MAX_FACTS, store.read().facts.size)
    }

    @Test fun pending_queue_capacity_rejects_without_eviction() = runTest {
        val store = open()
        repeat(PersonalContextStore.MAX_PENDING) { index ->
            val quote = "fact ${index + 1}"
            val result = store.propose(
                proposal("A durable $quote", quote, "Topic ${index + 1}"),
                evidence("The user stated $quote now"),
            )
            assertEquals(MemoryOutcome.PENDING_CONFIRMATION, result.outcome)
        }
        val overflow = store.propose(proposal("A durable fact 17", "fact 17", "Topic 17"), evidence("The user stated fact 17 now"))
        assertEquals(MemoryOutcome.REJECT, overflow.outcome)
        assertEquals(PersonalContextStore.MAX_PENDING, store.read().pending.size)
        assertTrue(store.read().facts.isEmpty())
    }
}
