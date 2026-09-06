package com.caddie.tool.interaction

import com.caddie.agent.core.*
import com.caddie.context.personal.*
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PersonalMemoryToolRegistryTest {
    private val evidence = TrustedMemoryEvidence("run", "user-1", "user-task", "I prefer concise answers.", 1)
    private fun args() = JSONObject().put("title", "Preference").put("text", evidence.text).put("evidence_quote", evidence.text)
    private fun call(args: JSONObject) = ModelDelta.ToolCall(ToolCallId("propose"), PersonalMemoryToolRegistry.TOOL_NAME, args.toString())

    @Test fun `model cannot submit provenance confirmation or arbitrary JSON fields`() = runTest {
        var writes = 0
        val registry = PersonalMemoryToolRegistry({ _, _ -> writes++; MemoryWriteResult(MemoryOutcome.AUTO_SAVE, "saved") }, { evidence })
        listOf("provenance", "ownerConfirmed", "verified_task_result").forEach { field ->
            assertTrue(registry.execute(RunId("run"), call(args().put(field, true))).isError)
        }
        assertEquals(0, writes)
        val result = registry.execute(RunId("run"), call(args()))
        assertFalse(result.isError)
        assertEquals(1, writes)
    }

    @Test fun `stale evidence prevents writer call and absent service exposes no tool`() = runTest {
        val registry = PersonalMemoryToolRegistry({ _, _ -> error("must not run") }, { evidence }, { _, _ -> false })
        assertTrue(registry.execute(RunId("run"), call(args())).isError)
        assertTrue(PersonalMemoryToolRegistry(null, { evidence }).definitions().isEmpty())
    }

    @Test fun `proposal count is bounded and storage errors never disclose payload`() = runTest {
        var writes = 0
        val registry = PersonalMemoryToolRegistry({ _, _ -> writes++; error("private payload") }, { evidence })
        repeat(9) {
            val result = registry.execute(RunId("run"), call(args()))
            assertTrue(result.isError)
            assertFalse(result.contentJson.contains("private payload"))
        }
        assertEquals(8, writes)
    }
}
