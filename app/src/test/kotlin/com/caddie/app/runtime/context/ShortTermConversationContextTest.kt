package com.caddie.app.runtime.context

import com.caddie.agent.core.AgentMessage
import com.caddie.agent.core.AgentToolCall
import com.caddie.agent.core.ModelRequest
import com.caddie.agent.core.RequestFactory
import com.caddie.agent.core.RunId
import com.caddie.agent.core.RunSnapshot
import com.caddie.agent.core.RunState
import com.caddie.agent.core.ToolCallId
import com.caddie.agent.core.ToolDefinition
import com.caddie.app.runtime.NativeTaskResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortTermConversationContextTest {
    private var now = 1_000L

    @Test
    fun `completed normal run is available to a follow-up within ttl`() {
        val context = memory()
        val firstRun = snapshot(
            runId = "run-1",
            task = "Öffne den Chat mit Anna",
            toolName = "android.click",
            arguments = "{\"text\":\"Anna\"}",
        )

        context.decorate(BaseFactory).create(firstRun, emptyList())
        context.record(
            "Öffne den Chat mit Anna",
            NativeTaskResult.Completed(firstRun.runId, "Annas Chat ist geöffnet."),
        )

        val followUp = context.decorate(BaseFactory).create(
            snapshot("run-2", "Schreib ihr, dass ich später komme"),
            emptyList(),
        )
        val prompt = followUp.messages.first().content

        assertTrue(prompt.contains("Öffne den Chat mit Anna"))
        assertTrue(prompt.contains("android.click"))
        assertTrue(prompt.contains("Annas Chat ist geöffnet"))
        assertTrue(prompt.contains("reference only", ignoreCase = true))
    }

    @Test
    fun `expired entries and entries beyond the limit are excluded`() {
        val context = memory(ttlMillis = 300_000L, maxEntries = 3)
        (1..4).forEach { index ->
            val run = snapshot("run-$index", "task-$index")
            context.decorate(BaseFactory).create(run, emptyList())
            context.record("task-$index", NativeTaskResult.Completed(run.runId, "done-$index"))
            now += 1_000L
        }

        val limited = context.decorate(BaseFactory).create(
            snapshot("run-5", "follow-up"),
            emptyList(),
        ).messages.first().content
        assertFalse(limited.contains("task-1"))
        assertTrue(limited.contains("task-2"))
        assertTrue(limited.contains("task-4"))

        now += 300_001L
        val expired = context.decorate(BaseFactory).create(
            snapshot("run-6", "later"),
            emptyList(),
        ).messages.first().content
        assertFalse(expired.contains("SHORT-TERM"))
        assertFalse(expired.contains("task-4"))
    }

    @Test
    fun `clear removes context and unfinished runs are never retained`() {
        val context = memory()
        val paused = snapshot("run-paused", "private unfinished task")
        context.decorate(BaseFactory).create(paused, emptyList())
        context.record(
            "private unfinished task",
            NativeTaskResult.Paused(paused.runId, RunState.PAUSED_NETWORK),
        )
        val completed = snapshot("run-complete", "remember me")
        context.decorate(BaseFactory).create(completed, emptyList())
        context.record("remember me", NativeTaskResult.Completed(completed.runId, "done"))
        context.clear()

        val request = context.decorate(BaseFactory).create(
            snapshot("run-next", "new task"),
            emptyList(),
        )
        val prompt = request.messages.first().content
        assertFalse(prompt.contains("private unfinished task"))
        assertFalse(prompt.contains("remember me"))
    }

    @Test
    fun `action trace excludes observation and skill plumbing and keeps only latest actions`() {
        val context = memory()
        val runId = RunId("run-actions")
        val calls = buildList {
            add(toolMessage(runId, 0, "android.observe"))
            add(toolMessage(runId, 1, "smartphone_get_skill_wifi"))
            (1..9).forEach { index ->
                add(toolMessage(runId, index + 1, "android.action-$index"))
            }
        }
        val snapshot = RunSnapshot(
            runId = runId,
            state = RunState.RUNNING,
            messages = listOf(AgentMessage(AgentMessage.Role.USER, "many actions")) + calls,
        )

        context.decorate(BaseFactory).create(snapshot, emptyList())
        context.record("many actions", NativeTaskResult.Completed(runId, "done"))
        val prompt = context.decorate(BaseFactory).create(
            snapshot("run-next", "follow-up"),
            emptyList(),
        ).messages.first().content

        assertFalse(prompt.contains("android.observe"))
        assertFalse(prompt.contains("smartphone_get_skill"))
        assertFalse(prompt.contains("android.action-1 "))
        assertTrue(prompt.contains("android.action-2 "))
        assertTrue(prompt.contains("android.action-9 "))
    }

    @Test
    fun `oversized newest entry remains available within the prompt budget`() {
        val context = memory()
        val old = snapshot("run-old", "small older task")
        context.decorate(BaseFactory).create(old, emptyList())
        context.record("small older task", NativeTaskResult.Completed(old.runId, "old result"))
        val newestRunId = RunId("run-newest")
        val newest = RunSnapshot(
            runId = newestRunId,
            state = RunState.RUNNING,
            messages = listOf(AgentMessage(AgentMessage.Role.USER, "newest important task")) +
                (1..8).map { index ->
                    AgentMessage(
                        role = AgentMessage.Role.ASSISTANT,
                        content = "",
                        toolCall = AgentToolCall(
                            id = ToolCallId("large-$index"),
                            name = "android.large-$index",
                            argumentsJson = "{\"value\":\"${"x".repeat(1_000)}\"}",
                        ),
                    )
                },
        )
        context.decorate(BaseFactory).create(newest, emptyList())
        context.record(
            "newest important task",
            NativeTaskResult.Completed(newestRunId, "newest important result"),
        )

        val prompt = context.decorate(BaseFactory).create(
            snapshot("run-follow-up", "follow-up"),
            emptyList(),
        ).messages.first().content

        assertTrue(prompt.contains("newest important task"))
        assertTrue(prompt.contains("newest important result"))
    }

    private fun memory(
        ttlMillis: Long = 300_000L,
        maxEntries: Int = 3,
    ) = ShortTermConversationContext(
        ttlMillis = ttlMillis,
        maxEntries = maxEntries,
        nowMillis = { now },
    )

    private fun snapshot(
        runId: String,
        task: String,
        toolName: String? = null,
        arguments: String = "{}",
    ): RunSnapshot = RunSnapshot(
        runId = RunId(runId),
        state = RunState.RUNNING,
        messages = buildList {
            add(AgentMessage(AgentMessage.Role.USER, task))
            if (toolName != null) {
                add(
                    AgentMessage(
                        role = AgentMessage.Role.ASSISTANT,
                        content = "",
                        toolCall = AgentToolCall(
                            id = ToolCallId("call-$runId"),
                            name = toolName,
                            argumentsJson = arguments,
                        ),
                    ),
                )
                add(
                    AgentMessage(
                        role = AgentMessage.Role.TOOL,
                        content = "{\"ok\":true}",
                        toolCallId = ToolCallId("call-$runId"),
                    ),
                )
            }
        },
    )

    private fun toolMessage(
        runId: RunId,
        index: Int,
        name: String,
    ) = AgentMessage(
        role = AgentMessage.Role.ASSISTANT,
        content = "",
        toolCall = AgentToolCall(
            id = ToolCallId("${runId.value}-$index"),
            name = name,
            argumentsJson = "{\"index\":$index}",
        ),
    )

    private object BaseFactory : RequestFactory {
        override fun create(
            snapshot: RunSnapshot,
            tools: List<ToolDefinition>,
        ) = ModelRequest(
            runId = snapshot.runId,
            messages = listOf(
                AgentMessage(AgentMessage.Role.SYSTEM, "base system prompt"),
            ) + snapshot.messages,
            tools = tools,
        )
    }
}
