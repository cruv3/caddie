package com.caddie.tool.android

import com.caddie.agent.core.ActionAttemptJournal
import com.caddie.agent.core.ActionDispatchClaim
import com.caddie.agent.core.ModelDelta
import com.caddie.agent.core.RunId
import com.caddie.agent.core.RunRecord
import com.caddie.agent.core.ToolCallId
import com.caddie.agent.core.ToolContinuation
import com.caddie.executor.accessibility.ActionOutcome
import com.caddie.executor.accessibility.ExecutionGateway
import com.caddie.executor.accessibility.RequestedAction
import com.caddie.executor.accessibility.SemanticActionExecutor
import com.caddie.executor.accessibility.SemanticTarget
import com.caddie.executor.accessibility.SnapshotCompleteness
import com.caddie.executor.accessibility.UiAction
import com.caddie.executor.accessibility.UiNode
import com.caddie.executor.accessibility.UiObservation
import com.caddie.executor.accessibility.UiWindow
import com.caddie.executor.accessibility.UiWindowType
import com.caddie.executor.accessibility.VerifiedActionExecutor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidToolRegistryTest {
    @Test
    fun `normal definitions require a short English why without changing study definitions`() {
        val normal = registry(requireHumanNarration = true)
            .definitions()
            .single { it.name == "android.click" }
            .inputSchemaJson
        val study = registry(requireHumanNarration = false)
            .definitions()
            .single { it.name == "android.click" }
            .inputSchemaJson

        assertTrue(normal.contains("\"why\""))
        assertTrue(normal.contains("\"required\":[\"target\",\"postcondition\",\"why\"]"))
        assertFalse(study.contains("\"why\""))
    }
    @Test
    fun `registry exposes only semantic Android operations`() {
        val registry = registry(FakeGateway(observation("Settings")), RecordingJournal())

        val definitions = registry.definitions()

        assertEquals(
            setOf(
                "android.observe",
                "android.click",
                "android.long_click",
                "android.set_text",
                "android.set_checked",
                "android.scroll",
                "android.back",
                "android.open_app",
                "android.open_url",
            ),
            definitions.mapTo(mutableSetOf()) { it.name },
        )
        definitions.forEach {
            assertFalse(it.inputSchemaJson.contains("\"x\""))
            assertFalse(it.inputSchemaJson.contains("\"y\""))
        }
    }

    @Test
    fun `back is journaled once and verified against a semantic postcondition`() = runTest {
        val gateway = FakeGateway(observation("Home"))
        val journal = RecordingJournal()
        val registry = registry(gateway, journal)

        val result = registry.execute(
            RunId("r1"),
            ModelDelta.ToolCall(
                ToolCallId("back"),
                "android.back",
                """{"postcondition":{"target":{"text":"Home"}}}""",
            ),
        )

        assertTrue(result.contentJson.contains("VERIFIED"))
        assertEquals(RequestedAction.Back, gateway.lastAction)
        assertEquals(
            listOf("BACK"),
            journal.records.filterIsInstance<RunRecord.ActionDispatched>().map { it.actionKind },
        )
    }

    @Test
    fun `back can be restricted to dismissing a visible input method`() = runTest {
        val gateway = FakeGateway(observation("Draft"))
        val registry = registry(gateway, RecordingJournal())

        val result = registry.execute(
            RunId("r1"),
            ModelDelta.ToolCall(
                ToolCallId("back"),
                "android.back",
                """{"dismiss_input_method_only":true,"postcondition":{"target":{"text":"Draft"}}}""",
            ),
        )

        assertTrue(result.contentJson.contains("VERIFIED"))
        assertEquals(RequestedAction.DismissInputMethod, gateway.lastAction)
    }

    @Test
    fun `app and https navigation are journaled and verified`() = runTest {
        val gateway = FakeGateway(observation("Ready"))
        val journal = RecordingJournal()
        val registry = registry(gateway, journal)

        val app = registry.execute(
            RunId("r1"),
            ModelDelta.ToolCall(
                ToolCallId("app"),
                "android.open_app",
                """{"package_name":"com.caddie.studybank","postcondition":{"target":{"text":"Ready"}}}""",
            ),
        )
        val url = registry.execute(
            RunId("r1"),
            ModelDelta.ToolCall(
                ToolCallId("url"),
                "android.open_url",
                """{"url":"https://example.test/path","postcondition":{"target":{"text":"Ready"}}}""",
            ),
        )

        assertTrue(app.contentJson.contains("VERIFIED"))
        assertTrue(url.contentJson.contains("VERIFIED"))
        assertEquals(
            listOf(
                RequestedAction.OpenApp("com.caddie.studybank"),
                RequestedAction.OpenUrl("https://example.test/path"),
            ),
            gateway.actions,
        )
        assertEquals(
            listOf("OPEN_APP", "OPEN_URL"),
            journal.records.filterIsInstance<RunRecord.ActionDispatched>().map { it.actionKind },
        )
    }

    @Test
    fun `open url rejects non-https schemes before journal or platform`() = runTest {
        val gateway = FakeGateway(observation("Ready"))
        val journal = RecordingJournal()
        val registry = registry(gateway, journal)

        val failure = runCatching {
            registry.execute(
                RunId("r1"),
                ModelDelta.ToolCall(
                    ToolCallId("url"),
                    "android.open_url",
                    """{"url":"intent://unsafe","postcondition":{"target":{"text":"Ready"}}}""",
                ),
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(emptyList<RequestedAction>(), gateway.actions)
        assertEquals(emptyList<RunRecord>(), journal.records)
    }

    @Test
    fun `click uses stable run scoped attempt and verifies postcondition`() = runTest {
        val gateway = FakeGateway(observation("Wi-Fi"))
        val journal = RecordingJournal()
        val registry = registry(gateway, journal)
        val call = ModelDelta.ToolCall(
            ToolCallId("c1"),
            "android.click",
            """
            {
              "target":{"text":"Settings"},
              "postcondition":{"target":{"text":"Wi-Fi"},"exists":true}
            }
            """.trimIndent(),
        )

        val result = registry.execute(RunId("r1"), call)

        assertTrue(result.contentJson.contains("VERIFIED"))
        assertEquals(1, gateway.performCount)
        assertEquals("Settings", gateway.lastTarget?.text)
        val dispatched = journal.records.filterIsInstance<RunRecord.ActionDispatched>().single()
        assertEquals("r1:c1", dispatched.attemptId.value)
        assertTrue(dispatched.selectorFingerprint?.startsWith("sha256:") == true)
        assertTrue(dispatched.postconditionFingerprint?.startsWith("sha256:") == true)
        assertEquals("android.semantic-postcondition.v1", dispatched.recoverySpecId)
    }

    @Test
    fun `invalid semantic target fails before action journal or platform`() = runTest {
        val gateway = FakeGateway(observation("Wi-Fi"))
        val journal = RecordingJournal()
        val registry = registry(gateway, journal)
        val call = ModelDelta.ToolCall(
            ToolCallId("c1"),
            "android.click",
            """{"target":{},"postcondition":{"target":{"text":"Wi-Fi"},"exists":true}}""",
        )

        val failure = runCatching { registry.execute(RunId("r1"), call) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(0, gateway.performCount)
        assertEquals(emptyList<RunRecord>(), journal.records)
    }

    @Test
    fun `observe returns window barriers and supported semantic actions`() = runTest {
        val registry = registry(FakeGateway(observation("Settings")), RecordingJournal())

        val result = registry.execute(
            RunId("r1"),
            ModelDelta.ToolCall(ToolCallId("c1"), "android.observe", "{}"),
        )

        assertTrue(result.contentJson.contains("interactionBarrier"))
        assertTrue(result.contentJson.contains("CLICK"))
        assertTrue(result.contentJson.contains("Settings"))
    }

    @Test
    fun `observe bounds model context while retaining late actionable nodes`() = runTest {
        val nodes = (0 until 240).map { index ->
            UiNode(
                observationNodeId = "node-$index",
                windowId = 1,
                text = "item-$index",
                enabled = true,
                visibleToUser = true,
                clickable = index == 239,
                actions = if (index == 239) setOf(UiAction.CLICK) else emptySet(),
            )
        }
        val registry = registry(
            FakeGateway(observation(nodes = nodes)),
            RecordingJournal(),
        )

        val result = registry.execute(
            RunId("r1"),
            ModelDelta.ToolCall(ToolCallId("observe"), "android.observe", "{}"),
        )

        assertEquals(200, result.contentJson.windowed("observationNodeId".length)
            .count { it == "observationNodeId" })
        assertTrue(result.contentJson.contains("node-239"))
        assertTrue(result.contentJson.contains("\"truncated\":true"))
    }

    @Test
    fun `incomplete verification snapshot never confirms a mutation`() = runTest {
        val gateway = FakeGateway(
            observation(
                text = "Wi-Fi",
                completeness = SnapshotCompleteness.PARTIAL_INTERACTIVE_WINDOWS,
            ),
        )
        val registry = registry(gateway, RecordingJournal())

        val result = registry.execute(
            RunId("r1"),
            ModelDelta.ToolCall(
                ToolCallId("c1"),
                "android.click",
                """{"target":{"text":"Settings"},"postcondition":{"target":{"text":"Wi-Fi"}}}""",
            ),
        )

        assertTrue(result.isError)
        assertTrue(result.contentJson.contains("POSTCONDITION_UNMET"))
        assertEquals(ToolContinuation.PAUSE_FOR_VERIFICATION, result.continuation)
    }

    @Test
    fun `action unavailable before acceptance lets the model re-observe`() = runTest {
        val gateway = FakeGateway(
            current = observation("Maps"),
            actionOutcome = ActionOutcome.ActionUnavailable,
        )
        val registry = registry(gateway, RecordingJournal())

        val result = registry.execute(
            RunId("r1"),
            ModelDelta.ToolCall(
                ToolCallId("app"),
                "android.open_app",
                """{"package_name":"com.caddie.studytelegram","postcondition":{"target":{"package_name":"com.caddie.studytelegram"}}}""",
            ),
        )

        assertTrue(result.isError)
        assertTrue(result.contentJson.contains("ACTION_UNAVAILABLE"))
        assertEquals(ToolContinuation.CONTINUE, result.continuation)
    }

    @Test
    fun `target moved by participant lets the model re-observe without retrying the action`() = runTest {
        val gateway = FakeGateway(
            current = observation("Maps"),
            actionOutcome = ActionOutcome.TargetMissing,
        )
        val registry = registry(gateway, RecordingJournal())

        val result = registry.execute(
            RunId("r1"),
            ModelDelta.ToolCall(
                ToolCallId("click"),
                "android.click",
                """{"target":{"text":"Directions"},"postcondition":{"target":{"text":"Route"}}}""",
            ),
        )

        assertTrue(result.isError)
        assertTrue(result.contentJson.contains("TARGET_MISSING"))
        assertEquals(ToolContinuation.CONTINUE, result.continuation)
    }

    @Test
    fun `package existence postcondition accepts multiple nodes from the opened app`() = runTest {
        val packageName = "com.google.android.apps.maps"
        val gateway = FakeGateway(
            observation(
                nodes = listOf(
                    node("maps-root", packageName),
                    node("maps-content", packageName),
                ),
            ),
        )
        val registry = registry(gateway, RecordingJournal())

        val result = registry.execute(
            RunId("r1"),
            ModelDelta.ToolCall(
                ToolCallId("url"),
                "android.open_url",
                """{"url":"https://example.test/route","postcondition":{"target":{"package_name":"$packageName"},"exists":true}}""",
            ),
        )

        assertTrue(result.contentJson.contains("VERIFIED"))
    }

    @Test
    fun `url navigation verifies the package resolved by Android instead of model text`() = runTest {
        val gateway = FakeGateway(
            current = observation(
                nodes = listOf(node("chrome-root", "com.android.chrome")),
            ),
            destinationPackage = "com.android.chrome",
        )
        val registry = registry(gateway, RecordingJournal())

        val result = registry.execute(
            RunId("r1"),
            ModelDelta.ToolCall(
                ToolCallId("url"),
                "android.open_url",
                """{"url":"https://example.test/path","postcondition":{"target":{"text":"invented page title"}}}""",
            ),
        )

        assertTrue(result.contentJson.contains("VERIFIED"))
        assertEquals(ToolContinuation.CONTINUE, result.continuation)
    }

    @Test
    fun `resolved navigation package can be verified in an active partial window`() = runTest {
        val gateway = FakeGateway(
            current = observation(
                nodes = listOf(node("chrome-root", "com.android.chrome")),
                completeness = SnapshotCompleteness.PARTIAL_INTERACTIVE_WINDOWS,
            ),
            destinationPackage = "com.android.chrome",
        )
        val registry = registry(gateway, RecordingJournal())

        val result = registry.execute(
            RunId("r1"),
            ModelDelta.ToolCall(
                ToolCallId("url-partial"),
                "android.open_url",
                """{"url":"https://example.test/path","postcondition":{"target":{"text":"ignored"}}}""",
            ),
        )

        assertTrue(result.contentJson.contains("VERIFIED"))
        assertEquals(ToolContinuation.CONTINUE, result.continuation)
    }

    @Test
    fun `resolved navigation package is not verified from an inactive background window`() = runTest {
        val background = UiWindow(
            id = 1,
            type = UiWindowType.APPLICATION,
            layer = 1,
            active = false,
            focused = false,
            interactionBarrier = false,
            nodes = listOf(node("chrome-root", "com.android.chrome")),
        )
        val gateway = FakeGateway(
            current = UiObservation(
                id = "background",
                capturedAtElapsedRealtimeMillis = 1,
                completeness = SnapshotCompleteness.PARTIAL_INTERACTIVE_WINDOWS,
                inputWindows = listOf(background),
            ),
            destinationPackage = "com.android.chrome",
        )
        val registry = registry(gateway, RecordingJournal())

        val result = registry.execute(
            RunId("r1"),
            ModelDelta.ToolCall(
                ToolCallId("url-background"),
                "android.open_url",
                """{"url":"https://example.test/path","postcondition":{"target":{"text":"ignored"}}}""",
            ),
        )

        assertTrue(result.isError)
        assertEquals(ToolContinuation.PAUSE_FOR_VERIFICATION, result.continuation)
    }

    private fun registry(
        gateway: FakeGateway,
        journal: RecordingJournal,
    ) = AndroidToolRegistry(
        gateway = gateway,
        executor = VerifiedActionExecutor(journal, SemanticActionExecutor(gateway), 100),
    )

    private fun registry(requireHumanNarration: Boolean): AndroidToolRegistry {
        val gateway = FakeGateway(observation("Settings"))
        return AndroidToolRegistry(
            gateway = gateway,
            executor = VerifiedActionExecutor(
                RecordingJournal(),
                SemanticActionExecutor(gateway),
                100,
            ),
            requireHumanNarration = requireHumanNarration,
        )
    }

    private class FakeGateway(
        private val current: UiObservation,
        private val actionOutcome: ActionOutcome = ActionOutcome.Accepted,
        private val destinationPackage: String? = null,
    ) : ExecutionGateway {
        var performCount = 0
        var lastAction: RequestedAction? = null
        var lastTarget: SemanticTarget? = null
        val actions = mutableListOf<RequestedAction>()

        override suspend fun observe(): UiObservation = current

        override fun resolveDestinationPackages(action: RequestedAction): Set<String> =
            destinationPackage?.let(::setOf).orEmpty()

        override suspend fun performSemantic(
            target: SemanticTarget,
            action: RequestedAction,
        ): ActionOutcome {
            performCount += 1
            lastTarget = target
            lastAction = action
            actions += action
            return actionOutcome
        }
    }

    private class RecordingJournal : ActionAttemptJournal {
        val records = mutableListOf<RunRecord>()

        override suspend fun dispatched(record: RunRecord.ActionDispatched): ActionDispatchClaim {
            records += record
            return ActionDispatchClaim.CLAIMED
        }

        override suspend fun executed(record: RunRecord.ActionExecuted) {
            records += record
        }

        override suspend fun terminal(record: RunRecord.ActionTerminal) {
            records += record
        }
    }

    private fun observation(
        text: String,
        completeness: SnapshotCompleteness = SnapshotCompleteness.ALL_INTERACTIVE_WINDOWS,
    ) = observation(
        nodes = listOf(
            UiNode(
                observationNodeId = "node-1",
                windowId = 1,
                text = text,
                enabled = true,
                visibleToUser = true,
                clickable = true,
                actions = setOf(UiAction.CLICK),
            ),
        ),
        completeness = completeness,
    )

    private fun observation(
        nodes: List<UiNode>,
        completeness: SnapshotCompleteness = SnapshotCompleteness.ALL_INTERACTIVE_WINDOWS,
    ) = UiObservation(
        id = "snapshot-1",
        capturedAtElapsedRealtimeMillis = 1,
        completeness = completeness,
        inputWindows = listOf(
            UiWindow(
                id = 1,
                type = UiWindowType.APPLICATION,
                layer = 0,
                active = true,
                focused = true,
                interactionBarrier = false,
                nodes = nodes,
            ),
        ),
    )

    private fun node(id: String, packageName: String) = UiNode(
        observationNodeId = id,
        windowId = 1,
        packageName = packageName,
        enabled = true,
        visibleToUser = true,
    )
}
