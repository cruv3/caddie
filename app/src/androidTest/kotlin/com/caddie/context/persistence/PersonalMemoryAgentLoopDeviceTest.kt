package com.caddie.context.persistence

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caddie.agent.core.*
import com.caddie.app.composition.AndroidContextRequestFactoryProvider
import com.caddie.app.runtime.*
import com.caddie.context.embedding.EmbeddingProvider
import com.caddie.context.personal.*
import com.caddie.context.skill.SkillCatalog
import com.caddie.executor.accessibility.*
import com.caddie.tool.mcp.client.McpClientManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Scripted model, real normal AgentLoop/tool admission/Room/Keystore/RAG. No remote model calls. */
@RunWith(AndroidJUnit4::class)
class PersonalMemoryAgentLoopDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "memory-agent-loop-test.db"
    private val alias = "memory_agent_loop_test_key"
    private var db: ContextDatabase? = null
    private fun open(): PersonalContextStore {
        db = Room.databaseBuilder(context, ContextDatabase::class.java, databaseName).build()
        return PersonalContextStore(ContextRepository(db!!, AndroidKeystoreContextCipher(alias)))
    }
    @After fun cleanup() {
        db?.close()
        context.deleteDatabase(databaseName)
        AndroidKeystoreContextCipher.deleteKey(alias)
    }

    @Test fun model_proposal_survives_reopen_and_is_retrieved_but_conflicts_and_study_are_gated() = runTest {
        var memory = open()
        val firstModel = ScriptedModel("I prefer concise answers.")
        var host = host(memory, firstModel)
        assertTrue(host.run("I prefer concise answers.") is NativeTaskResult.Completed)
        assertTrue(firstModel.requests.first().tools.any { it.name == "caddie.memory_propose" })
        assertTrue(firstModel.requests.last().messages.any { "AUTO_SAVE" in it.content })
        val saved = memory.read().facts.single()
        assertEquals("user-task", saved.evidence?.source)
        assertEquals("I prefer concise answers.", saved.evidence?.quote)
        host.close()
        db!!.close()

        memory = open() // Fresh store/connection: not an in-memory cache assertion.
        val laterModel = ScriptedModel()
        host = host(memory, laterModel)
        assertTrue(host.run("What response length do I prefer?") is NativeTaskResult.Completed)
        assertTrue(laterModel.requests.first().messages.any { saved.text in it.content && "Personal reference" in it.content })
        host.close()

        val conflictModel = ScriptedModel("I prefer detailed answers.")
        host = host(memory, conflictModel)
        assertTrue(host.run("I prefer detailed answers.") is NativeTaskResult.Completed)
        assertTrue(conflictModel.requests.last().messages.any { "PENDING_CONFIRMATION" in it.content })
        assertEquals(saved.text, memory.read().facts.single().text)
        assertEquals(1, memory.read().pending.size)
        assertTrue(memory.read().retrievableFacts.isEmpty())
        host.close()

        val gatedModel = ScriptedModel()
        host = host(memory, gatedModel)
        host.run("What response length do I prefer?")
        assertTrue(gatedModel.requests.first().messages.none { "Personal reference" in it.content })
        host.run("study task", NativeRunProfile(oversight(), ConversationRequestFactory("study prompt")))
        assertFalse(gatedModel.requests.last().tools.any { it.name == "caddie.memory_propose" })
        assertTrue(gatedModel.requests.last().messages.none { "Personal reference" in it.content })
        host.close()
    }

    @Test fun actual_answer_is_evidence_but_generated_question_and_yes_are_not() = runTest {
        val memory = open()
        for (answer in listOf("yes", "I prefer metric units.")) {
            val model = ScriptedModel("I prefer metric units.", askFirst = true)
            val host = host(memory, model)
            val question = async(start = CoroutineStart.UNDISPATCHED) {
                host.runner.events.first { it is NativeAgentEvent.QuestionAsked } as NativeAgentEvent.QuestionAsked
            }
            val task = async { host.run("Help with my display preferences") }
            assertTrue(host.runner.answerActiveQuestion(question.await().questionId, answer))
            assertTrue(task.await() is NativeTaskResult.Completed)
            if (answer == "yes") {
                assertTrue(memory.read().facts.isEmpty())
                assertTrue(model.requests.any { r -> r.messages.any { "REJECT" in it.content } })
            } else assertEquals("user-answer", memory.read().facts.single().evidence?.source)
            host.close()
        }
    }

    @Test fun model_cannot_save_ui_fact_or_credentials_through_the_real_loop() = runTest {
        val memory = open()
        for ((task, proposed) in listOf(
            "Look at the page" to "I prefer metric units.",
            "My password is synthetic-secret" to "My password is synthetic-secret",
        )) {
            val model = ScriptedModel(proposed)
            val host = host(memory, model)
            assertTrue(host.run(task) is NativeTaskResult.Completed)
            assertTrue(model.requests.any { r -> r.messages.any { "REJECT" in it.content } })
            assertTrue(memory.read().facts.isEmpty())
            assertTrue(memory.read().pending.isEmpty())
            host.close()
        }
    }

    private fun host(memory: PersonalContextStore, model: ModelClient): NativeRuntimeHost {
        val journal = Journal()
        val provider = AndroidContextRequestFactoryProvider(
            catalogLoader = { SkillCatalog(emptyList()) },
            embedderFactory = { object : EmbeddingProvider {
                override val modelId = "deterministic-test"
                override val dimension = 2
                override suspend fun embedQuery(text: String) = floatArrayOf(1f, 0f)
                override suspend fun embedDocument(text: String) = floatArrayOf(1f, 0f)
                override fun close() = Unit
            } },
            personalLoader = memory::read,
            personalRevision = { memory.revision },
        )
        return NativeRuntimeAssembler(model, journal, journal,
            McpClientManager(connectionFactory = { error("unused") }, scope = CoroutineScope(Job())),
            oversight(), ConversationRequestFactory("Use the provided tools."),
            normalRequestFactoryProvider = provider::forTask,
            memoryProposer = memory::propose,
        ).create(object : ExecutionGateway {
            override suspend fun observe() = UiObservation("snapshot", 1,
                SnapshotCompleteness.ALL_INTERACTIVE_WINDOWS, emptyList())
            override suspend fun performSemantic(target: SemanticTarget, action: RequestedAction): ActionOutcome =
                error("No action is authorized by this test")
        })
    }

    private class ScriptedModel(private val proposal: String? = null, private val askFirst: Boolean = false) : ModelClient {
        val requests = mutableListOf<ModelRequest>()
        override fun stream(request: ModelRequest) = run {
            requests += request
            val call = if (requests.size == 1 && askFirst) {
                ModelDelta.ToolCall(ToolCallId("question"), "caddie.ask_user", """{"question":"Do you prefer metric units?"}""")
            } else if (requests.size == (if (askFirst) 2 else 1) && proposal != null) {
                ModelDelta.ToolCall(ToolCallId("proposal"), "caddie.memory_propose",
                    JSONObject().put("title", "Response preference").put("text", proposal)
                        .put("evidence_quote", proposal).toString())
            } else if (request.messages.lastOrNull()?.content?.contains("REJECT") == true) {
                ModelDelta.ToolCall(ToolCallId("observe"), "android.observe", "{}")
            } else if (request.tools.any { it.name == "caddie.complete" }) {
                ModelDelta.ToolCall(ToolCallId("complete-${requests.size}"), "caddie.complete", """{"message":"done"}""")
            } else return@run flowOf(ModelDelta.Text("done"), ModelDelta.Completed)
            flowOf(call, ModelDelta.Completed)
        }
    }
    private class Journal : SessionStore, ActionAttemptJournal {
        private val records = linkedMapOf<RunId, MutableList<RunRecord>>()
        override suspend fun snapshot(runId: RunId) = reduce(records[runId].orEmpty(), recoveredProcess = false)
        override suspend fun append(event: RunRecord) { records.getOrPut(event.runId) { mutableListOf() } += event }
        override suspend fun dispatched(record: RunRecord.ActionDispatched): ActionDispatchClaim {
            append(record); return ActionDispatchClaim.CLAIMED
        }
        override suspend fun executed(record: RunRecord.ActionExecuted) = append(record)
        override suspend fun terminal(record: RunRecord.ActionTerminal) = append(record)
    }
    private fun oversight() = object : OversightPolicy {
        override suspend fun approve(call: ModelDelta.ToolCall) = OversightDecision(true)
    }
}
