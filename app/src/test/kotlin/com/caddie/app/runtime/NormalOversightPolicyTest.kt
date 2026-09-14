package com.caddie.app.runtime

import com.caddie.agent.core.ModelDelta
import com.caddie.agent.core.OversightDecision
import com.caddie.agent.core.ToolCallId
import com.caddie.study.StudyGate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalOversightPolicyTest {
    @Test
    fun `observation remains automatic`() = runTest {
        val gate = RecordingGate()
        val decision = NormalOversightPolicy(gate).approve(call("android.observe"))

        assertTrue(decision.approved)
        assertEquals(emptyList<ModelDelta.ToolCall>(), gate.calls)
    }

    @Test
    fun `ordinary real action remains automatic in normal C3 mode`() = runTest {
        val gate = RecordingGate()
        val action = call("android.click", """{"target":{"text":"Route starten"}}""")

        val decision = NormalOversightPolicy(gate).approve(action)

        assertTrue(decision.approved)
        assertEquals(emptyList<ModelDelta.ToolCall>(), gate.calls)
    }

    @Test
    fun `critical semantic action uses the shared human confirmation gate`() = runTest {
        val gate = RecordingGate(OversightDecision(approved = true))
        val action = call("android.click", """{"target":{"text":"Überweisung ausführen"}}""")

        val decision = NormalOversightPolicy(gate).approve(action)

        assertTrue(decision.approved)
        assertEquals(listOf(action), gate.calls)
        assertEquals("„Überweisung ausführen“ bestätigen", gate.confirmationTexts.single())
    }

    @Test
    fun `English Send labels require confirmation and respect rejection`() = runTest {
        for (label in listOf("Send", "SEND", "Send feedback")) {
            val gate = RecordingGate()
            val action = call("android.click", """{"target":{"text":"$label"}}""")

            val decision = NormalOversightPolicy(gate).approve(action)

            assertFalse(decision.approved)
            assertEquals(listOf(action), gate.calls)
        }
    }

    @Test
    fun `English Send in resolved and IME submission labels requires confirmation`() = runTest {
        val gate = RecordingGate()
        val policy = NormalOversightPolicy(gate) { listOf("Send") }
        val click = call("android.click", """{"target":{"text":"Compose"}}""")
        val submit = call(
            "android.set_text",
            """{"target":{"text":"Message"},"text":"Hello","submit":true,"postcondition":{"target":{"text":"Send"}}}""",
        )

        assertFalse(policy.approve(click).approved)
        assertFalse(NormalOversightPolicy(gate).approve(submit).approved)
        assertEquals(listOf(click, submit), gate.calls)
    }

    @Test
    fun `sender label remains an ordinary navigation target`() = runTest {
        val gate = RecordingGate()

        val decision = NormalOversightPolicy(gate).approve(
            call("android.click", """{"target":{"text":"Sender details"}}"""),
        )

        assertTrue(decision.approved)
        assertTrue(gate.calls.isEmpty())
    }

    @Test
    fun `benign clear-field action is not mistaken for deletion`() = runTest {
        val gate = RecordingGate()

        val decision = NormalOversightPolicy(gate).approve(
            call("android.click", """{"target":{"content_description":"Suchfeld löschen"}}"""),
        )

        assertTrue(decision.approved)
        assertTrue(gate.calls.isEmpty())
    }

    @Test
    fun `critical MCP tool name is gated even without Android target`() = runTest {
        val gate = RecordingGate(OversightDecision(approved = true))
        val action = call("bank.transfer_money", """{"amount":"80.40","currency":"EUR"}""")

        val decision = NormalOversightPolicy(gate).approve(action)

        assertTrue(decision.approved)
        assertEquals(listOf(action), gate.calls)
    }

    @Test
    fun `resolved accessibility label closes resource-id-only risk gap`() = runTest {
        val gate = RecordingGate(OversightDecision(approved = true))
        val action = call(
            "android.click",
            """{"target":{"resource_id":"confirm_button"},"postcondition":{"target":{"text":"Erfolg"}}}""",
        )
        val policy = NormalOversightPolicy(gate) { listOf("Überweisung ausführen") }

        val decision = policy.approve(action)

        assertTrue(decision.approved)
        assertEquals(listOf(action), gate.calls)
        assertEquals("„Überweisung ausführen“ bestätigen", gate.confirmationTexts.single())
    }

    @Test
    fun `resource-id-only click fails closed even when current label looks harmless`() = runTest {
        val gate = RecordingGate(OversightDecision(approved = true))
        val action = call(
            "android.click",
            """{"target":{"resource_id":"primary_button"},"postcondition":{"target":{"text":"Weiter"}}}""",
        )

        NormalOversightPolicy(gate) { listOf("Weiter") }.approve(action)

        assertEquals(listOf(action), gate.calls)
        assertEquals("„Weiter“ bestätigen", gate.confirmationTexts.single())
    }

    @Test
    fun `search history deletion is not treated as clearing a search field`() = runTest {
        val gate = RecordingGate(OversightDecision(approved = true))
        val action = call("android.click", """{"target":{"text":"Suchverlauf löschen"}}""")

        NormalOversightPolicy(gate).approve(action)

        assertEquals(listOf(action), gate.calls)
    }

    @Test
    fun `remote send tool is consequential`() = runTest {
        val gate = RecordingGate(OversightDecision(approved = true))
        val action = call("mcp__mail__send_email", """{"to":"anna@example.invalid"}""")

        NormalOversightPolicy(gate).approve(action)

        assertEquals(listOf(action), gate.calls)
    }

    @Test
    fun `unknown remote mutation fails closed while remote reads remain automatic`() = runTest {
        val gate = RecordingGate(OversightDecision(approved = true))
        val policy = NormalOversightPolicy(gate)
        val mutation = call("mcp__custom__do_thing", "{}")

        policy.approve(mutation)
        val read = policy.approve(call("mcp__custom__get_status", "{}"))

        assertEquals(listOf(mutation), gate.calls)
        assertTrue(read.approved)
    }

    private fun call(
        name: String,
        arguments: String = """{"target":{"text":"Senden"}}""",
    ) = ModelDelta.ToolCall(
        ToolCallId("call-1"),
        name,
        arguments,
    )

    private class RecordingGate(
        private val decision: OversightDecision = OversightDecision(approved = false),
    ) : StudyGate {
        val calls = mutableListOf<ModelDelta.ToolCall>()
        val confirmationTexts = mutableListOf<String?>()

        override suspend fun confirmStep(
            call: ModelDelta.ToolCall,
            confirmationText: String?,
        ): OversightDecision {
            calls += call
            confirmationTexts += confirmationText
            return decision
        }

        override suspend fun confirmFinal(
            calls: List<ModelDelta.ToolCall>,
            summaryLines: List<String>,
        ): OversightDecision = error("normal oversight never batches confirmations")
    }
}
