package com.caddie.context.compaction

import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCompactorTest {
    private val compactor = SessionCompactor(setOf("tap_element", "read_screen"), maxSummaryChars = 500)

    @Test
    fun `only middle complete turns are summarized and last four stay verbatim`() {
        val fixed = listOf(item("system", ContextItemKind.SYSTEM), item("task", ContextItemKind.ORIGINAL_TASK))
        val turns = (0..6).flatMap(::completeTurn)

        val result = compactor.compact(fixed + turns) as CompactionResult.Compacted
        val summary = result.items.single { it.kind == ContextItemKind.SUMMARY }

        assertEquals((0..2).flatMap { completeTurn(it).map(ContextItem::id) }, summary.sourceRefs.map(ContextSourceRef::id))
        assertTrue(result.items.containsAll(fixed))
        assertTrue(result.items.containsAll((3..6).flatMap(::completeTurn)))
        assertFalse(summary.content.contains("large private payload"))
        assertTrue(summary.content.contains("tap_element"))
        assertTrue(summary.content.length <= 500)
    }

    @Test
    fun `unpaired unknown and safety events are always protected`() {
        val protected = listOf(
            item("unpaired", ContextItemKind.TOOL_CALL, "u", "unknown_tool", callId = "missing"),
            item("oversight", ContextItemKind.OVERSIGHT),
            item("pause", ContextItemKind.PAUSE),
            item("resume", ContextItemKind.RESUME),
            item("action", ContextItemKind.ACTION),
            item("recovery", ContextItemKind.RECOVERY),
        )
        val input = listOf(item("system", ContextItemKind.SYSTEM), item("task", ContextItemKind.ORIGINAL_TASK)) +
            completeTurn(0) + protected + (1..5).flatMap(::completeTurn)

        val result = compactor.compact(input) as CompactionResult.Compacted

        assertTrue(result.items.containsAll(protected))
        assertTrue(result.items.contains(input[0]))
        assertTrue(result.items.contains(input[1]))
    }

    @Test
    fun `previous summary is replaced and source provenance is retained`() {
        val oldSource = ContextSourceRef("old", hash("old"))
        val oldSummary = item("old-summary", ContextItemKind.SUMMARY).copy(sourceRefs = listOf(oldSource))
        val input = listOf(item("system", ContextItemKind.SYSTEM), oldSummary) + (0..5).flatMap(::completeTurn)

        val once = compactor.compact(input) as CompactionResult.Compacted
        val summary = once.items.single { it.kind == ContextItemKind.SUMMARY }
        val twice = compactor.compact(once.items)

        assertTrue(oldSource in summary.sourceRefs)
        assertFalse(once.items.contains(oldSummary))
        assertEquals(once, twice)
    }

    @Test
    fun `invalid source hashes reject compaction without changing input`() {
        val invalid = item("bad", ContextItemKind.SYSTEM).copy(sha256 = "invalid")
        val input = listOf(invalid) + (0..5).flatMap(::completeTurn)

        val result = compactor.compact(input) as CompactionResult.Rejected

        assertEquals(input, result.original)
    }

    @Test
    fun `mismatched tool result metadata protects the whole turn`() {
        val mismatched = completeTurn(0).map {
            if (it.kind == ContextItemKind.TOOL_RESULT) it.copy(toolName = "read_screen") else it
        }
        val input = mismatched + (1..5).flatMap(::completeTurn)

        val result = compactor.compact(input) as CompactionResult.Compacted

        assertTrue(result.items.containsAll(mismatched))
    }

    @Test
    fun `protected coverage and idempotence hold for zero through twelve turns`() {
        for (count in 0..12) {
            val protected = listOf(
                item("system-$count", ContextItemKind.SYSTEM),
                item("task-$count", ContextItemKind.ORIGINAL_TASK),
                item("oversight-$count", ContextItemKind.OVERSIGHT),
            )
            val input = protected + (0 until count).flatMap(::completeTurn)
            val first = compactor.compact(input) as CompactionResult.Compacted

            assertTrue(first.items.containsAll(protected))
            assertEquals(first, compactor.compact(first.items))
            first.items.filter { it.kind == ContextItemKind.SUMMARY }.forEach {
                assertTrue(it.content.length <= 500)
                assertEquals(it.sourceRefs.map(ContextSourceRef::id).distinct(), it.sourceRefs.map(ContextSourceRef::id))
            }
        }
    }

    private fun completeTurn(index: Int): List<ContextItem> {
        val turn = "turn-$index"
        val call = "call-$index"
        return listOf(
            item("assistant-$index", ContextItemKind.ASSISTANT, turn, content = "large private payload"),
            item("call-$index", ContextItemKind.TOOL_CALL, turn, "tap_element", call, content = "large UI payload"),
            item(
                "result-$index",
                ContextItemKind.TOOL_RESULT,
                turn,
                "tap_element",
                call,
                outcome = "success",
                transition = "screen_changed",
                content = "large UI result",
            ),
        )
    }

    private fun item(
        id: String,
        kind: ContextItemKind,
        turnId: String? = null,
        toolName: String? = null,
        callId: String? = null,
        outcome: String? = null,
        transition: String? = null,
        content: String = id,
    ) = ContextItem(id, hash(content), kind, content, turnId, callId, toolName, outcome, transition)

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
